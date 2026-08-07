"""Client for the Imagine SDK (cloud LLM) -- backup used when the local genieX server is unreachable/fails."""

from imagine import ImagineClient
from imagine.exceptions import ImagineException

from . import config


class ImagineInferenceError(RuntimeError):
    """Raised when the Imagine SDK cannot be reached or returns an unusable response."""


def chat_completion(system_prompt: str, user_prompt: str) -> str:
    """Send a chat completion request to the Imagine SDK and return the reply text.

    Raises:
        ImagineInferenceError: If the client isn't configured, the request
            fails, or the response has no usable content.
    """
    try:
        client = ImagineClient(
            endpoint=config.IMAGINE_API_ENDPOINT or None,
            api_key=config.IMAGINE_API_KEY or None,
            timeout=int(config.IMAGINE_TIMEOUT),
        )
    except ValueError as error:
        raise ImagineInferenceError(f"Imagine SDK not configured: {error}") from error

    try:
        response = client.chat(
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            model=config.IMAGINE_MODEL,
        )
    except ImagineException as error:
        raise ImagineInferenceError(f"Imagine SDK request failed: {error}") from error

    try:
        content = response.choices[0].message.content
    except (IndexError, AttributeError) as error:
        raise ImagineInferenceError(
            "Imagine SDK response was not in the expected chat-completion format"
        ) from error

    if not content:
        raise ImagineInferenceError("Imagine SDK returned an empty response")
    return content
