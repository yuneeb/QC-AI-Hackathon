# QC-AI-Hackathon

## Physical context MCP server

`physical_context/` fetches live telemetry from the Arduino sensor board and
phone-context service (see `context_client`), sends it to an LLM for
interpretation (Imagine SDK, cloud, 1st choice; genieX, on-device, 2nd
choice; falling back to raw labeled readings if both are unavailable), and
exposes the result to Claude as an MCP tool (`get_physical_context`) over
stdio.

Edit `physical_context/config.py` if the sensor board, phone-context service,
Imagine SDK, or genieX server are reachable at different addresses.

## Running it (for judges)

1. **Install dependencies** (this machine is win-arm64; plain `pip install`
   can fail building `cryptography` from source, so pin it via a
   constraints file):
   ```powershell
   pip install -r requirements.txt
   echo cryptography==46.0.3 > constraints.txt
   pip install -c constraints.txt fastmcp
   ```
   The Imagine SDK is installed separately from a local wheel (not on
   PyPI) — see whoever set up this box if it's missing.

2. **(Optional) Start genieX**, the on-device fallback LLM, if you want the
   pipeline to work without Imagine SDK credentials:
   ```powershell
   geniex serve
   ```
   It listens on `http://127.0.0.1:18181`.

3. **Register the MCP server with Claude Code** (run once, from this repo's
   root — adjust the path if you cloned it elsewhere):
   ```powershell
   claude mcp add physical-context -e PYTHONPATH="C:\Users\QCWorkshop15\Downloads\QC-AI-Hackathon" -- "C:\Users\QCWorkshop15\Downloads\QC-AI-Hackathon\.venv\Scripts\python.exe" -m physical_context.server
   ```
   Verify it's connected:
   ```powershell
   claude mcp list
   ```
   You should see `physical-context ... ✔ Connected`.

4. **Launch Claude Code from this folder.** The MCP server is registered at
   local scope, so it's only active in sessions started here. If you had a
   terminal open before step 3, open a new one — an already-running
   session won't see a newly registered server.
   ```powershell
   claude
   ```

5. **Ask a question that depends on the physical world**, e.g. "Is it too
   hot in here right now?" or "Am I moving?" Claude will call the
   `get_physical_context` tool automatically when it judges the question
   depends on real-world sensor/phone state.

   To force it deterministically instead of relying on Claude's judgment,
   just say so explicitly, e.g. "Use the physical context tool, then tell
   me if it's too hot in here."

If the live sensor board / phone-context service aren't reachable from your
network, the tool automatically falls back to the sample data in `data/`
and labels it as stale/sample — so the demo still works, it just won't be
live.
