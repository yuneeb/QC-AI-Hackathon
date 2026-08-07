"""FastMCP stdio server exposing the physical-context tool to Claude."""

from fastmcp import FastMCP

from .context_builder import build_physical_context

INSTRUCTIONS = (
    "This server gives you access to the user's real-world physical context, "
    "inferred from live sensors (an environmental board and their phone: "
    "temperature, distance, location, motion, device state). Call the "
    "get_physical_context tool whenever the user's question could depend on "
    "their physical surroundings, situation, or state -- e.g. questions about "
    "their environment, location, temperature, whether they're moving, or "
    "similar -- before answering, so your answer reflects reality instead of "
    "assumptions. Skip it for questions with no physical-world dependency. By "
    "default the readings are pre-interpreted by a local LLM; pass "
    "use_llm=False to instead get the raw labeled readings and interpret "
    "them yourself."
)

mcp = FastMCP("physical-context", instructions=INSTRUCTIONS)


@mcp.tool()
def get_physical_context(use_llm: bool = False) -> str:
    """Get the user's live physical surroundings (sensor board + phone).

    Fetches current readings from the environmental sensor board and the
    user's phone (location, motion, device state). Call this before
    answering any question where the user's real-world environment,
    location, movement, or physical state is relevant.

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
