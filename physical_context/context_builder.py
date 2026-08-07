"""Fetches raw sensor/phone telemetry and turns it into a physical-context summary."""

import json
import time

from context_client import APIFetchError, fetch_api_data

from . import config
from .genie_client import LLMInferenceError, chat_completion
from .imagine_client import ImagineInferenceError
from .imagine_client import chat_completion as imagine_chat_completion

SYSTEM_PROMPT = (
    "You are a physical-context analyst for an AI assistant. You are given raw "
    "telemetry from sensors near the user (an environmental sensor board and/or "
    "their phone). Each reading is labeled either 'live' or 'stale sample "
    "fallback data' -- if a reading is stale sample fallback data, make clear in "
    "your summary that it may not reflect the user's current situation. Phone "
    "telemetry is a series of snapshots taken 5 seconds apart over the last "
    "~20 seconds, newest first -- use the change (or lack of change) across "
    "snapshots in location, speed, and motion sensors to judge whether the "
    "user is stationary, walking, or travelling; do not rely on any single "
    "snapshot alone. Write a short, plain-language summary (3-5 sentences) of "
    "the user's physical situation: environment (temperature, distance to "
    "nearest object), location and movement, and relevant device state. Only "
    "describe what the data supports -- do not guess or invent details. If a "
    "data source is marked unavailable, just omit it. Respond with only the "
    "summary, no preamble or headers."
)


def _load_fallback(path, label: str):
    """Load JSON from a local fallback file; return (data, None) or (None, error_message)."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle), None
    except FileNotFoundError:
        return None, f"{label} fallback file not found ({path})"
    except OSError as error:
        return None, f"{label} fallback file could not be read: {error}"
    except json.JSONDecodeError as error:
        return None, f"{label} fallback file contains invalid JSON: {error}"


def _fetch(url: str, fallback_path, label: str):
    """Fetch JSON from the live API, retrying on failure, then falling back
    to a local sample file.

    Retries up to config.SENSOR_RETRY_ATTEMPTS times, waiting
    config.SENSOR_RETRY_INTERVAL seconds between attempts.

    Returns (data, is_live, error_message). error_message is None unless both
    the live fetch and the fallback failed.
    """
    live_error = None
    for attempt in range(config.SENSOR_RETRY_ATTEMPTS):
        try:
            return fetch_api_data(url, timeout=config.SENSOR_TIMEOUT), True, None
        except APIFetchError as error:
            live_error = error
            if attempt < config.SENSOR_RETRY_ATTEMPTS - 1:
                time.sleep(config.SENSOR_RETRY_INTERVAL)

    fallback_data, fallback_error = _load_fallback(fallback_path, label)
    if fallback_data is not None:
        return fallback_data, False, None
    return None, False, (
        f"{label} unavailable: live fetch failed ({live_error}); "
        f"fallback also failed ({fallback_error})"
    )


def _drop_inferred_activity(phone_data):
    """Strip the phone app's own inferred_activity from each snapshot.

    That field is a programmatic guess from the phone app itself; we want
    the LLM inferring activity/motion from the raw sensor series instead.
    """
    if not isinstance(phone_data, dict):
        return phone_data
    snapshots = phone_data.get("snapshots")
    if not isinstance(snapshots, list):
        return phone_data
    return {
        **phone_data,
        "snapshots": [
            {k: v for k, v in snapshot.items() if k != "inferred_activity"}
            if isinstance(snapshot, dict)
            else snapshot
            for snapshot in snapshots
        ],
    }


def _round_numeric(value, key: str = None):
    """Recursively round floats for the LLM prompt to cut noisy precision/tokens.

    Latitude/longitude keep 4 decimal places (~11m resolution, still
    meaningful for location context); every other float is rounded to the
    nearest integer.
    """
    if isinstance(value, dict):
        return {k: _round_numeric(v, k) for k, v in value.items()}
    if isinstance(value, list):
        return [_round_numeric(v) for v in value]
    if isinstance(value, float):
        if key in ("latitude", "longitude"):
            return round(value, 4)
        return round(value)
    return value


def _describe(label: str, data, is_live: bool, error: str) -> str:
    if data is None:
        return f"{label}: unavailable ({error})"
    status = "live" if is_live else "stale sample fallback data, may not reflect current reality"
    return f"{label} ({status}): {json.dumps(_round_numeric(data))}"


def build_physical_context(use_llm: bool = False) -> str:
    """Fetch sensor + phone telemetry (live, falling back to local samples).

    If use_llm is True, the readings are interpreted into a short
    natural-language summary by the Imagine SDK (cloud), falling back to the
    local genieX LLM if Imagine is unreachable/fails. If False (default),
    no LLM is called and the raw labeled readings are returned as-is.

    Never raises: on partial or total failure it returns a plain-text message
    describing what went wrong, so the MCP tool always has something to return.
    """
    sensor_data, sensor_live, sensor_error = _fetch(
        config.SENSOR_DATA_URL, config.SENSOR_FALLBACK_FILE, "Environmental sensor"
    )
    phone_data, phone_live, phone_error = _fetch(
        config.CONTEXT_URL, config.CONTEXT_FALLBACK_FILE, "Phone context"
    )
    phone_data = _drop_inferred_activity(phone_data)

    if sensor_data is None and phone_data is None:
        return f"Physical context is unavailable right now: {sensor_error}; {phone_error}"

    user_prompt = "\n".join(
        [
            _describe("Environmental sensor readings", sensor_data, sensor_live, sensor_error),
            _describe("Phone sensor/location telemetry", phone_data, phone_live, phone_error),
        ]
    )

    if not use_llm:
        return user_prompt

    try:
        return imagine_chat_completion(SYSTEM_PROMPT, user_prompt)
    except ImagineInferenceError as imagine_error:
        try:
            return chat_completion(SYSTEM_PROMPT, user_prompt)
        except LLMInferenceError as genie_error:
            # Fall back to the raw readings so the tool still returns something useful.
            return (
                f"Imagine SDK interpretation failed ({imagine_error}); local "
                f"genieX fallback also failed ({genie_error}); returning raw "
                f"readings instead.\n{user_prompt}"
            )
