"""Quick manual check that the Imagine SDK is configured correctly. Run directly:

    .venv/Scripts/python.exe test_imagine.py
"""

from physical_context.imagine_client import ImagineInferenceError, chat_completion

PROMPT = "Tell me a story in 10 lines"


def main() -> None:
    try:
        print(chat_completion("You are a helpful assistant.", PROMPT))
    except ImagineInferenceError as error:
        print(f"Imagine SDK call failed: {error}")


if __name__ == "__main__":
    main()
