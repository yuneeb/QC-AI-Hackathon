"""Configuration for the physical-context pipeline.

Edit these values if the sensor board, phone-context service, or genieX
server are reachable at different addresses on your network.
"""

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

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

# Caps generation length -- keeps latency down and avoids the model running
# past its answer into repeating/degenerate output.
LLM_MAX_TOKENS = 400

# Timeouts, in seconds
SENSOR_TIMEOUT = 10.0
LLM_TIMEOUT = 180.0
