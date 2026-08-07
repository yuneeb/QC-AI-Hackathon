"""Client for the locally running genieX LLM server (OpenAI-compatible chat API)."""

import json
import re
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from . import config


class LLMInferenceError(RuntimeError):
    """Raised when the local LLM cannot be reached or returns an unusable response."""


_THINK_BLOCK_RE = re.compile(r"<think>.*?</think>", re.DOTALL)

# Matches a short chunk (2-30 chars) repeated 6+ times in a row -- the
# degenerate "010101..." / "Readonly Readonly..." tail genieX sometimes
# produces once it runs past its coherent answer.
_DEGENERATE_REPEAT_RE = re.compile(r"(.{2,30}?)(?:\1){6,}", re.DOTALL)


def _strip_thinking(text: str) -> str:
    """Remove <think>...</think> reasoning blocks that Qwen3 emits inline."""
    return _THINK_BLOCK_RE.sub("", text).strip()


def _truncate_degenerate_tail(text: str) -> str:
    """Cut off a degenerate repeating tail, keeping the coherent prefix."""
    match = _DEGENERATE_REPEAT_RE.search(text)
    if match is None:
        return text
    return text[: match.start()].rstrip()


def chat_completion(system_prompt: str, user_prompt: str) -> str:
    """Send a chat completion request to genieX and return the cleaned reply text.

    Raises:
        LLMInferenceError: If the request fails, times out, or the response
            cannot be parsed into a usable chat message.
    """
    payload = {
        "model": config.GENIEX_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "max_tokens": config.LLM_MAX_TOKENS,
    }
    request = Request(
        config.GENIEX_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )

    try:
        with urlopen(request, timeout=config.LLM_TIMEOUT) as response:
            raw = response.read().decode("utf-8")
    except HTTPError as error:
        raise LLMInferenceError(f"genieX returned HTTP {error.code}: {error.reason}") from error
    except URLError as error:
        raise LLMInferenceError(
            f"Could not reach genieX at {config.GENIEX_URL}: {error.reason}. "
            "Is 'geniex serve' running?"
        ) from error
    except TimeoutError as error:
        raise LLMInferenceError("Timed out waiting for genieX to respond") from error
    except UnicodeDecodeError as error:
        raise LLMInferenceError("genieX response was not valid UTF-8") from error

    try:
        parsed = json.loads(raw)
        content = parsed["choices"][0]["message"]["content"]
    except (json.JSONDecodeError, KeyError, IndexError, TypeError) as error:
        raise LLMInferenceError(
            "genieX response was not in the expected chat-completion format"
        ) from error

    cleaned = _truncate_degenerate_tail(_strip_thinking(content))
    if not cleaned:
        raise LLMInferenceError(
            "genieX returned an empty or entirely degenerate response"
        )
    return cleaned
