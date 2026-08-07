"""Configuration for the physical-context pipeline.

Edit these values if the sensor board, phone-context service, or genieX
server are reachable at different addresses on your network.
"""

from pathlib import Path

from dotenv import load_dotenv

REPO_ROOT = Path(__file__).resolve().parent.parent
load_dotenv(REPO_ROOT / ".env")

# Arduino board: temperature + distance readings (see sensor_data/python/main.py)
SENSOR_DATA_URL = "http://10.73.51.136:9000/data"

# Phone context app: location, motion, device state (see data/phone.json for a sample)
CONTEXT_URL = "http://10.73.51.106:8080/context"

# Local files used when the live APIs above are unreachable. These hold the
# last-known/sample readings, not live data -- see build_physical_context().
SENSOR_FALLBACK_FILE = REPO_ROOT / "data" / "sensor.json"
CONTEXT_FALLBACK_FILE = REPO_ROOT / "data" / "phone.json"

# Locally running genieX LLM server, started with `geniex serve`
GENIEX_URL = "http://127.0.0.1:18181/v1/chat/completions"
GENIEX_MODEL = "qualcomm/Qwen3-4B:W4A16"

# Imagine SDK (cloud LLM) -- backup used when the local genieX server is
# unreachable or fails. Left blank -- real values come from .env (see
# .env.example), loaded above via load_dotenv() and read by the SDK itself
# from the IMAGINE_API_KEY / IMAGINE_API_ENDPOINT environment variables.
IMAGINE_API_KEY = ""
IMAGINE_API_ENDPOINT = ""
IMAGINE_MODEL = "Llama-3.1-8B"
IMAGINE_TIMEOUT = 60.0

# Caps generation length -- keeps latency down and avoids the model running
# past its answer into repeating/degenerate output.
LLM_MAX_TOKENS = 400

# Timeouts, in seconds
SENSOR_TIMEOUT = 10.0
LLM_TIMEOUT = 180.0

# Hard wall-clock budget for each live fetch (sensor and phone are budgeted
# separately). Once this elapses -- including any retries and the waits
# between them -- the fetch gives up and the local sample file in data/ is
# used instead. This is what keeps an unresponsive board from stalling the
# MCP tool call.
SENSOR_TOTAL_TIMEOUT = 30.0

# Retry policy for the sensor/phone live fetches, applied *within* the
# SENSOR_TOTAL_TIMEOUT budget above: try up to SENSOR_RETRY_ATTEMPTS times,
# waiting SENSOR_RETRY_INTERVAL seconds between attempts. Whichever limit is
# hit first ends the retrying.
SENSOR_RETRY_ATTEMPTS = 4
SENSOR_RETRY_INTERVAL = 5.0
