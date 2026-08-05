"""Utilities for retrieving JSON data from HTTP APIs."""

import json
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class APIFetchError(RuntimeError):
    """Raised when API data cannot be retrieved or decoded."""


def fetch_api_data(url: str, timeout: float = 10.0) -> Any:
    """Fetch and return JSON data from an API URL.

    Args:
        url: API endpoint to request.
        timeout: Maximum number of seconds to wait for the response.

    Raises:
        APIFetchError: If the request fails, returns an HTTP error, or
            contains invalid JSON.
    """
    request = Request(url, headers={"Accept": "application/json"})

    try:
        with urlopen(request, timeout=timeout) as response:
            raw_data = response.read().decode("utf-8")
            return json.loads(raw_data)
    except HTTPError as error:
        raise APIFetchError(
            f"API returned HTTP {error.code}: {error.reason}"
        ) from error
    except URLError as error:
        raise APIFetchError(f"Could not reach API at {url}: {error.reason}") from error
    except TimeoutError as error:
        raise APIFetchError(f"Timed out while requesting API data from {url}") from error
    except UnicodeDecodeError as error:
        raise APIFetchError(f"API response from {url} was not valid UTF-8") from error
    except json.JSONDecodeError as error:
        raise APIFetchError(f"API response from {url} was not valid JSON") from error
