"""Utilities for retrieving context data from the local context service."""

import json
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


CONTEXT_URL = "http://10.73.51.106:8080/context"


class ContextFetchError(RuntimeError):
    """Raised when context data cannot be retrieved or decoded."""


def fetch_context(timeout: float = 10.0) -> Any:
    """Fetch and return JSON data from the context service.

    Args:
        timeout: Maximum number of seconds to wait for the response.

    Raises:
        ContextFetchError: If the request fails, returns an HTTP error, or
            contains invalid JSON.
    """
    request = Request(CONTEXT_URL, headers={"Accept": "application/json"})

    try:
        with urlopen(request, timeout=timeout) as response:
            raw_data = response.read().decode("utf-8")
            return json.loads(raw_data)
    except HTTPError as error:
        raise ContextFetchError(
            f"Context service returned HTTP {error.code}: {error.reason}"
        ) from error
    except URLError as error:
        raise ContextFetchError(f"Could not reach context service: {error.reason}") from error
    except TimeoutError as error:
        raise ContextFetchError("Timed out while requesting context data") from error
    except UnicodeDecodeError as error:
        raise ContextFetchError("Context service response was not valid UTF-8") from error
    except json.JSONDecodeError as error:
        raise ContextFetchError("Context service response was not valid JSON") from error
