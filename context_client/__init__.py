"""Client utilities for the context service."""

from .context_reader import ContextFetchError, fetch_context

__all__ = ["ContextFetchError", "fetch_context"]
