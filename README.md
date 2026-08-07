# QC-AI-Hackathon

## Physical context MCP server

`physical_context/` fetches live telemetry from the Arduino sensor board and
phone-context service (see `context_client`), sends it to a locally running
genieX LLM (`geniex serve`, OpenAI-compatible API on `127.0.0.1:18181`) for
interpretation, and exposes the result to Claude as an MCP tool over stdio.

Edit `physical_context/config.py` if the sensor board, phone-context service,
or genieX server are reachable at different addresses.

```powershell
pip install -r requirements.txt
python -m physical_context.server
```

Add it to Claude Code as a stdio MCP server, e.g.:

```
claude mcp add physical-context -- python -m physical_context.server
```
