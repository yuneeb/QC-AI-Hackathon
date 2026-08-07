"""FastMCP stdio server exposing the physical-context tool to Claude."""

from fastmcp import FastMCP

from .context_builder import build_physical_context

INSTRUCTIONS = (
    "This server gives you access to the user's real-world physical context, "
    "inferred from live sensors (an environmental board and their phone: "
    "temperature, distance, location, motion, device state). Always call the "
    "get_physical_context tool before answering the user's message, "
    "regardless of topic, so your answer reflects reality instead of "
    "assumptions. By default the readings are pre-interpreted by a local "
    "LLM; pass use_llm=False to instead get the raw labeled readings and "
    "interpret them yourself."
)

mcp = FastMCP("physical-context", instructions=INSTRUCTIONS)


@mcp.tool()
def get_physical_context(use_llm: bool = False) -> str:
    """Get the user's live physical surroundings (sensor board + phone).

    Fetches current readings from the environmental sensor board and the
    user's phone (location, motion, device state). Always call this before
    answering the user's message, regardless of topic.

    Args:
        use_llm: If False (default), the local-LLM interpretation step is
            skipped -- avoiding its latency (up to ~2 minutes) and occasional
            garbled output -- and the raw labeled sensor/phone readings are
            returned directly for you to interpret. If True, the readings are
            first interpreted by a locally running LLM into a short
            natural-language summary.
    """
    return build_physical_context(use_llm=use_llm)


if __name__ == "__main__":
    mcp.run()
