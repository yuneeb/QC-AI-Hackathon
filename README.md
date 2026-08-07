# QC-AI-Hackathon

## Overview

This project is a **physical real-world context builder** for AI assistants:
it pulls live sensor telemetry — temperature, distance, location, motion,
device state — from an Arduino sensor board and a phone, interprets it, and
exposes it to Claude as an MCP tool, so its answers are grounded in what's
actually happening around the user instead of a guess. Interpretation runs
on Qualcomm silicon end to end: cloud inference via the Imagine SDK on
**Qualcomm Cloud AI 100** as the fast, high-quality first choice, with a
fully **on-device** fallback running locally on the **Snapdragon X Elite**
NPU (via genieX) when privacy, offline resilience, or zero cloud dependency
matter more than raw speed. In practice, this turns a chatbot from a
text-only advisor into a situationally aware one — able to tell someone if
they need a jacket, whether they're still moving, or if it's safe to leave
the heater on, using real data about their surroundings instead of
assumptions.

## Setup

1. **Clone the repo** and set up a virtual environment inside it:
   ```powershell
   git clone <this-repo-url>
   cd QC-AI-Hackathon
   python -m venv .venv
   .venv\Scripts\activate
   ```

2. **Install dependencies.** On win-arm64, plain `pip install` can fail
   building `cryptography` from source, so pin it via a constraints file:
   ```powershell
   pip install -r requirements.txt
   echo cryptography==46.0.3 > constraints.txt
   pip install -c constraints.txt fastmcp
   ```
   On other platforms the constraints file isn't needed — a plain
   `pip install -r requirements.txt` is enough. The Imagine SDK is
   distributed as a local wheel rather than via PyPI; install it the same
   way (`pip install imagine_sdk-<version>-py3-none-any.whl`) if you want
   the cloud inference path.

3. **(Optional) Start genieX**, the on-device fallback LLM, if you want the
   pipeline to work without Imagine SDK credentials:
   ```powershell
   geniex serve
   ```
   It listens on `http://127.0.0.1:18181` by default.

4. **Register the MCP server with Claude Code**, run once from the repo
   root (replace `<path-to-repo>` with the absolute path where you cloned
   it):
   ```powershell
   claude mcp add physical-context -e PYTHONPATH="<path-to-repo>" -- "<path-to-repo>\.venv\Scripts\python.exe" -m physical_context.server
   ```
   Verify it's connected:
   ```powershell
   claude mcp list
   ```
   You should see `physical-context ... ✔ Connected`.

5. **Launch Claude Code from the repo root.** The MCP server is registered
   at local scope, so it's only active in sessions started here. If you had
   a terminal open before step 4, open a new one — an already-running
   session won't see a newly registered server.
   ```powershell
   claude
   ```
6. **Install Arduino Code**
   - Open Arduino App Lab
   - Create a new user project
   - Drag and drop the following files into their equivalent files on the arduino
     * `sketch/sketch.ino`
     * `sketch/sketch.yaml`
     * `python/main.py`
     * `app.yaml`
   - Click Run
   - Open the device shell and observe the server serving data at `http://<arduino-ip>:9000/data`
8. **Ask a question that depends on the physical world**, e.g. "Is it too
   hot in here right now?" or "Am I moving?" Claude will call the
   `get_physical_context` tool automatically when it judges the question
   depends on real-world sensor/phone state.

   To force it deterministically instead of relying on Claude's judgment,
   just say so explicitly, e.g. "Use the physical context tool, then tell
   me if it's too hot in here."

If the live sensor board / phone-context service aren't reachable on your
network, the tool automatically falls back to the sample data in `data/`
and labels it as stale/sample, so the tool still works, it just won't be
live.

## Folder structure

```
QC-AI-Hackathon/
├── physical_context/       # the MCP pipeline
│   ├── config.py           # all tunables: URLs, model names, timeouts
│   ├── imagine_client.py   # cloud LLM (Qualcomm Cloud AI 100, 1st choice)
│   ├── genie_client.py     # on-device LLM (Snapdragon X Elite NPU, 2nd choice)
│   ├── context_builder.py  # fetch + fallback + LLM interpretation, never raises
│   └── server.py           # FastMCP stdio server, exposes get_physical_context
├── context_client/         # generic fetch_api_data(url) JSON-over-HTTP client
├── sensor_data/            # Arduino-side code (serves temp/distance over HTTP)
├── android-sensor-collector/ # phone-side app (serves location/motion snapshots)
├── data/                   # sample sensor.json / phone.json, used as fallback
├── main.py                 # standalone script, not part of the MCP pipeline
├── requirements.txt
├── LICENSE                  # MIT
└── CLAUDE.md                # detailed dev notes/context for future sessions
```

## License

MIT — see [LICENSE](LICENSE). Free to use, modify, and redistribute.
