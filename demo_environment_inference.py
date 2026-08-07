"""Standalone demo: grab sensor + phone telemetry and use the Imagine SDK to
produce a human-readable summary of the user's physical environment -- no
Claude, no MCP server, just this pipeline. Run directly:

    .venv/Scripts/python.exe demo_environment_inference.py
"""

import json

from context_client import APIFetchError, fetch_api_data
from physical_context import config
from physical_context.context_builder import SYSTEM_PROMPT, _describe
from physical_context.imagine_client import ImagineInferenceError, chat_completion


def _fetch_with_fallback(url: str, fallback_path, label: str):
    """Try the live endpoint once; fall back to the local sample file on failure.

    Returns (data, is_live).
    """
    try:
        return fetch_api_data(url, timeout=config.SENSOR_TIMEOUT), True
    except APIFetchError as error:
        print(f"  {label}: live fetch failed ({error}); using local fallback sample")
        with open(fallback_path, "r", encoding="utf-8") as handle:
            return json.load(handle), False


def main() -> None:
    print("Fetching telemetry...")
    sensor_data, sensor_live = _fetch_with_fallback(
        config.SENSOR_DATA_URL, config.SENSOR_FALLBACK_FILE, "Environmental sensor"
    )
    phone_data, phone_live = _fetch_with_fallback(
        config.CONTEXT_URL, config.CONTEXT_FALLBACK_FILE, "Phone context"
    )

    user_prompt = "\n".join(
        [
            _describe("Environmental sensor readings", sensor_data, sensor_live, None),
            _describe("Phone sensor/location telemetry", phone_data, phone_live, None),
        ]
    )
    print("\nRaw telemetry sent to Imagine SDK:")
    print(user_prompt)

    print("\nAsking Imagine SDK to interpret it...")
    try:
        summary = chat_completion(SYSTEM_PROMPT, user_prompt)
    except ImagineInferenceError as error:
        print(f"Imagine SDK call failed: {error}")
        return

    print("\nHuman-readable environment summary:")
    print(summary)


if __name__ == "__main__":
    main()
