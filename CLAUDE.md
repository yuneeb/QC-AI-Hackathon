# CLAUDE.md — QC-AI-Hackathon

Guidance for Claude Code when working in this repo.

## What this project is

A "smart AI context builder": pull live sensor telemetry, interpret it with a
locally running LLM, and expose the interpretation to Claude (this app) as an
MCP tool over stdio, so Claude can answer physical-world-dependent questions
accurately.

```
Arduino board (temp/distance)  --\        Imagine SDK (cloud, 1st choice)
                                   >-- physical_context.context_builder --< genieX LLM (on-device, 2nd choice) --> MCP tool --> Claude
Phone app (location/motion/etc) --/        raw readings (both unavailable)
```

## User's working preferences for this repo (explicit, from prior sessions)

- Keep the implementation focused. Don't add features or refactor beyond
  what's asked.
- Confirm with the user before adding any additional features — don't assume.
- If unsure about a design/config decision, ask rather than guess.
- Handle failures gracefully — nothing in `physical_context` should raise an
  unhandled exception up to the MCP tool caller.
- The user does their own live/interactive testing (e.g. wiring this into a
  real Claude session and asking questions). Don't assume that's been done.
- **Never run a delete/overwrite command (`rm -rf`, `Remove-Item -Recurse`,
  etc.) without explicit confirmation first** — an earlier session
  accidentally deleted an untracked `npu-build/` directory this way.

## Layout

- `context_client/` — generic `fetch_api_data(url)` JSON-over-HTTP client with
  a custom `APIFetchError`. Used by both `main.py` and `physical_context`.
- `main.py` — original standalone script, just fetches + prints raw sensor and
  phone JSON. Not part of the MCP pipeline; left as-is.
- `data/sensor.json`, `data/phone.json` — sample telemetry, also used as
  **fallback data** when the live endpoints are unreachable (see below).
- `sensor_data/` — Arduino-side code (`python/main.py`, `sketch/`) that serves
  raw temperature/distance readings over HTTP on port 9000. Runs on the board,
  not on this machine.
- `physical_context/` — the pipeline built for this project:
  - `config.py` — **all** tunables live here: sensor/phone/genieX/Imagine SDK
    URLs, model names, timeouts, `LLM_MAX_TOKENS`, fallback file paths. Edit
    this file first if something needs to change.
  - `imagine_client.py` — calls the Imagine SDK (`imagine.ImagineClient`,
    cloud-hosted) chat endpoint. **1st-choice** LLM interpreter. Needs
    `IMAGINE_API_KEY` / `IMAGINE_API_ENDPOINT` — set as env vars, or filled
    into `config.py` (both left blank/placeholder as of last session; see
    Status).
  - `genie_client.py` — calls genieX's OpenAI-compatible
    `/v1/chat/completions` (on-device). **2nd-choice**, used only if Imagine
    fails. Strips `<think>...</think>` reasoning blocks (Qwen3 emits them
    inline) and truncates degenerate repeating output (see Known quirks).
  - `context_builder.py` — fetches sensor + phone data (live, falling back to
    the local sample files in `data/` on failure), builds the prompt, tries
    Imagine SDK then genieX for interpretation, and always returns a string
    — never raises. If both LLMs fail, returns the raw labeled readings.
  - `server.py` — FastMCP stdio server; one tool, `get_physical_context`,
    with instructions telling Claude when to call it.

## Environment / setup

- `.venv/` at repo root has `fastmcp` installed. This machine is native
  **win-arm64** Python — plain `pip install fastmcp` tries to build
  `cryptography` from source via Rust/maturin and fails (no MSVC link step
  configured for that toolchain here). Workaround used: install with a
  constraint pinning `cryptography==46.0.3` (last version with a `win_arm64`
  wheel on PyPI as of this writing), e.g.:
  ```
  echo cryptography==46.0.3 > constraints.txt
  .venv/Scripts/python.exe -m pip install -c constraints.txt fastmcp
  ```
- `geniex serve` must be running separately (`http://127.0.0.1:18181`) for
  on-device LLM inference to work (2nd-choice fallback). Confirmed
  OpenAI-compatible: `/v1/models`, `/v1/chat/completions`. Models available
  on this box: `google/gemma-4-E4B-it-qat-q4_0-gguf:Q4_0`,
  `qualcomm/Qwen3-4B:W4A16` (currently configured, in `config.py`).
- `imagine-sdk` (0.4.2, installed from a local wheel —
  `~/Downloads/imagine_sdk-0.4.2-py3-none-any.whl`, win-arm64-compatible, no
  build-from-source issues) is the 1st-choice LLM. Pulled in `orjson` and
  `urllib3`; `httpx`/`pydantic` were already present from `fastmcp`.
  `ImagineClient(...)` raises `ValueError` at construction if
  `IMAGINE_API_KEY`/`IMAGINE_API_ENDPOINT` aren't set (env var or explicit
  arg) — `imagine_client.py` catches that and treats it the same as any
  other Imagine failure, falling through to genieX.

## Running / registering the MCP server

Standalone (for debugging):
```
.venv/Scripts/python.exe -m physical_context.server
```

Registered with Claude Code (stdio, **local scope** — only active when
`claude` is run from this directory):
```
claude mcp add physical-context -e PYTHONPATH="C:\Users\QCWorkshop15\Downloads\QC-AI-Hackathon" -- "C:\Users\QCWorkshop15\Downloads\QC-AI-Hackathon\.venv\Scripts\python.exe" -m physical_context.server
```
Verify with `claude mcp list` (expect `physical-context ... ✔ Connected`).
`-e PYTHONPATH=...` is there so `-m physical_context.server` resolves
regardless of Claude Code's own working directory when it spawns the
subprocess. If re-registering, `claude mcp remove physical-context` first.

A **new** `claude` session/terminal is required to pick up a newly
registered server — an already-running session won't see it.

## Known quirks (already handled, but good to know)

- `data/phone.json` originally had Python literal `True`/`False` instead of
  valid JSON `true`/`false` — fixed, since it's loaded via `json.load` as a
  fallback source.
- The live sensor board (`10.73.51.136:9000`) and phone-context service
  (`10.73.51.106:8080`) are on the hackathon LAN and may be unreachable from
  other networks — `context_builder` falls back to `data/sensor.json` /
  `data/phone.json` automatically and labels that data as stale/sample to the
  LLM (and, by extension, to Claude) so it isn't presented as live.
- The phone-context service's response shape changed: it now returns
  `{"snapshots": [...]}`, a rolling window of 5 readings taken 5s apart
  (last ~20s), newest first, instead of a single reading. Each snapshot
  still includes a phone-side `inferred_activity` (activity + confidence),
  but `context_builder._drop_inferred_activity()` strips that before it
  reaches the LLM/Claude — it's the phone app's own programmatic guess, and
  we want activity/motion (e.g. "is the user travelling") inferred from the
  raw snapshot series instead. `data/phone.json` (the fallback sample) was
  updated to match this shape.
- genieX (Qwen3-4B) has been observed taking **up to ~2 minutes** to respond
  to a realistic sensor+phone prompt — `LLM_TIMEOUT` in `config.py` is set to
  180s to accommodate that. If it's still too slow in practice, that's a
  genieX/NPU-serving characteristic to raise with the user, not something to
  silently "fix" by lowering the timeout.
- genieX has been observed running past a coherent answer into degenerate
  repeating output (e.g. `Readonly Readonly...010101...`) with no stop
  token. Mitigated two ways in `genie_client.py`: `max_tokens=400` request
  cap, plus a regex that truncates any detected repeating tail while keeping
  the coherent prefix. This is a heuristic, not a guarantee — if a user
  question depends on the tool's output being complete/clean, it's worth
  spot-checking real responses.

## Status as of last session

- Full pipeline (fetch -> fallback -> LLM -> stripped/truncated output) is
  implemented and smoke-tested end-to-end, including the fallback path (live
  endpoints unreachable from this network) and one full live genieX call.
- MCP server registered locally and shows connected.
- Imagine SDK wired in as the 1st-choice LLM (genieX now 2nd-choice
  fallback, raw readings as last resort) — `imagine_client.py` +
  `config.py` entries added, smoke-tested for the *failure* path only
  (`IMAGINE_API_KEY`/`IMAGINE_API_ENDPOINT` are still blank placeholders, so
  it correctly falls through to genieX). **Not yet tested against a real
  Imagine endpoint** — needs the user to fill in credentials (`config.py` or
  env vars) and confirm a live call works.
- **Not yet done:** user's own live testing of the tool from inside an actual
  Claude conversation (asking a physical-context-dependent question and
  checking Claude calls the tool and uses the result well).
