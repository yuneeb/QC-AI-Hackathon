# Presentation Notes — Physical-Context MCP Tool

Source material for slides. Organized so each `##` section is roughly one
slide. Written for a mixed technical/non-technical audience — plain-language
framing first, technical detail as sub-bullets.

---

## 1. The Idea (title / hook slide)

- Chatbots like Claude are smart, but blind to the room they're being used in
- We built a tool that gives an AI assistant **live physical-world awareness**:
  temperature, distance to nearby objects, location, motion, device state
- The assistant can now answer questions like *"should I turn the heater
  on?"* or *"am I still moving?"* with real data instead of guesses
- Two sensor sources feeding it, two tiers of AI interpreting it, one
  standard protocol (MCP) connecting it all to Claude

---

## 2. Architecture at a Glance

```
Arduino UNO Q (temp/distance)   --\        Imagine SDK (cloud, 1st choice)
                                    >-- context builder --<  genieX on-device LLM (2nd choice)
Samsung S25 phone (location/motion)-/        raw readings (last resort)
                                                    |
                                                    v
                                          MCP tool over stdio
                                                    |
                                                    v
                                              Claude (chat)
```

- Sensors → context builder → local-first LLM interpretation → MCP tool → Claude
- Every layer degrades gracefully: live data → cached sample data; cloud LLM →
  local LLM → raw readings. The tool **never crashes**, it always returns
  something useful
- This "always returns something" design matters for a live demo — no
  blocking on a flaky sensor or a down server

---

## 3. Sensor Source #1 — Arduino UNO Q

- The Arduino **UNO Q** is the key detail here: it's not just an MCU, it
  pairs a microcontroller with an **onboard Qualcomm chip capable of running
  Linux** — Qualcomm silicon on the sensing side too, not just the inference
  side
- Modulino sensor modules over I2C: a thermometer and a distance sensor
- The MCU side (`sketch.ino`) exposes `get_temperature()` and
  `get_distance()` as RPC calls over the Arduino Bridge
- The Linux side (`python/main.py`) polls those RPCs and serves the latest
  reading as JSON over plain HTTP (`:9000/data`)
- Example live reading pulled during this session:
  `{"temperature": 23°C, "distance": 17 (units per sensor), "timestamp": "..."}`

---

## 4. Sensor Source #2 — Samsung Galaxy S25

- A phone app streams a **rolling window of 5 snapshots, 5 seconds apart**
  (last ~20s of history) instead of one static reading
- Per snapshot: GPS location + accuracy, speed, bearing, altitude;
  accelerometer / gyroscope / magnetometer / pressure / ambient light /
  step count; device state (Wi-Fi, battery, screen on/off, ringer mode, call
  state)
- Why a *window* and not one snapshot: a single reading can't tell you if
  someone is stationary, walking, or travelling — comparing snapshots can
  - E.g. unchanging GPS + zero speed + no significant motion across 5
    snapshots → confidently "still," not just "reading said still once"
- The phone also ships its own on-device activity guess
  (`inferred_activity`) — we deliberately **strip that out** before it
  reaches the LLM, and instead have our own pipeline infer activity from the
  raw sensor series. Keeps the interpretation in one place and avoids
  trusting a black-box guess we can't verify

---

## 5. Fusing the Two — What Claude Actually Sees

- Both sources land in one `physical_context` module, are labeled
  live-vs-fallback, and merged into a single prompt
- Two output modes from the same MCP tool call:
  - **Raw mode** (`use_llm=False`, default) — labeled JSON straight from the
    sensors, Claude interprets it itself. Fast, no LLM latency
  - **Interpreted mode** (`use_llm=True`) — passed through the LLM stack
    first, comes back as a short plain-English paragraph
- Live demo pulled during this session (interpreted mode):

  > *"The user is currently in a still position, likely indoors, with a
  > temperature of 23°C... The device is connected to Wi-Fi and has a
  > battery level of 99%."*

  (location fields redacted here for the slide — the real response includes
  live GPS coordinates)

---

## 6. The Protocol — FastMCP

- **MCP (Model Context Protocol)** is the open standard for connecting an AI
  assistant to external tools/data — think "USB-C port for AI apps"
- We used **FastMCP**, a Python framework that turns a plain function into an
  MCP tool with almost no boilerplate:
  ```python
  @mcp.tool()
  def get_physical_context(use_llm: bool = False) -> str:
      return build_physical_context(use_llm=use_llm)
  ```
- Runs as a local **stdio server** — Claude Code spawns it as a subprocess
  and talks to it over stdin/stdout, no network port, no auth to manage
- Server ships **instructions** telling Claude *when* to reach for the tool
  (physical/environmental questions) and when to skip it — the model decides
  per-question, it's not forced into every response
- Registered once (`claude mcp add ...`), then any Claude Code session
  started in this project can call it like a native capability

---

## 7. Cloud Inference — Imagine SDK on Qualcomm Cloud AI 100

- **1st choice** interpreter: fast, gives the best-quality natural-language
  summary
- Runs on **Qualcomm Cloud AI 100** accelerator chips via the **Imagine**
  platform — cloud-hosted inference, called over a simple chat-completions
  SDK
- Model: Llama-3.1-8B
- If the Imagine endpoint is unreachable or errors out, the pipeline falls
  through automatically — no manual intervention, no crash, just a quieter
  fallback

---

## 8. Local Inference — genieX + Qualcomm AI Hub (on-device NPU)

- **2nd choice**, used only if the cloud path fails — but this is the piece
  we want to highlight: **the whole model runs on-device, on this laptop's
  NPU**
- Stack:
  - **Qualcomm AI Hub** — used to get/optimize a model for Qualcomm's NPU
    (Neural Processing Unit) silicon
  - **QUAD** — used in preparing/quantizing the model for efficient
    on-device execution
  - **genieX** — the local runtime that actually serves the model, exposing
    an OpenAI-compatible `/v1/chat/completions` API on `localhost:18181`
- Model: `Qwen3-4B`, quantized (`W4A16`) to run efficiently on-NPU
- No API key, no network call, no cloud dependency — this path works with
  Wi-Fi off
- Real quirks worth being upfront about (shows this is genuinely running
  live, not a canned demo):
  - Can take **up to ~2 minutes** for a full response — genieX/NPU serving
    characteristic at this model size, not a bug
  - Qwen3 emits inline `<think>...</think>` reasoning — stripped
    automatically before the user ever sees it
  - Occasionally runs past a coherent answer into repeating output
    (`Readonly Readonly...`) — capped and truncated with a repeat-detector

---

## 9. Why On-Device / Local Inference Matters

- **Privacy** — the biggest one. This pipeline is reading someone's live
  GPS location, motion pattern, and environment. When it runs on genieX
  locally, that data **never leaves the laptop** — no cloud request, nothing
  logged by a third party
- **No connectivity dependency** — works offline, on a private LAN, or
  anywhere Wi-Fi/cellular isn't guaranteed
- **Latency floor is predictable** — no network round trip, no provider
  rate limits or outages to wait on
- **Cost** — no per-token cloud billing for the fallback path
- **Trade-off, stated honestly** — local inference on a 4B model is slower
  and slightly lower quality than the cloud 8B model. That's *why* this is a
  tiered system: cloud-first for speed/quality, local as a resilient,
  private fallback that still fully works

---

## 10. Why Give a Chatbot Physical-World Context At All

- Today's assistants answer from text alone — they don't know if you're
  cold, moving, indoors, or about to walk into traffic
- Grounding answers in live sensor data turns generic advice into situated
  advice:
  - *"Should I wear a jacket?"* → answered with the room's actual temperature,
    not a generic guess
  - *"Am I still on the move?"* → answered from real accelerometer/GPS
    history, not asked back to the user
- This is the same shift assistants already made from "no memory" to
  "remembers our conversation" — this is "aware of your physical situation"
  as the next layer of context
- Because the tool is self-describing (the MCP instructions tell Claude
  *when* to use it), this isn't a special mode — it's just part of how the
  assistant reasons, automatically, only when relevant

---

## 11. Looking Forward — Toward a True Smart Home

- Today this pipeline is **read-only**: sensors → interpretation → answer
- The natural next step is closing the loop — **acting**, not just reporting:
  - Auto-adjust lights based on ambient light + whether anyone's in the room
  - Auto-adjust thermostat from live temperature + occupancy/motion, instead
    of a fixed schedule
  - Proactive, not just reactive, suggestions — *"You've been still for 20
    minutes and the room's dropped to 18°C, want the heat on?"* — instead of
    waiting to be asked
- The privacy case only gets stronger here: a system that's deciding to
  change your thermostat or lights based on your location and motion is
  exactly the kind of sensitive inference that should stay **on-device**
  wherever possible
- Same architecture, more sensors and more actuators — the sensor board and
  phone are just the first two inputs; the same context-builder pattern
  extends to switches, locks, thermostats, and other Qualcomm-powered edge
  devices

---

## 12. Closing / Summary

- Two real sensor sources (Arduino UNO Q board + Samsung S25 phone) feeding
  one context pipeline
- Two-tier LLM interpretation: **Qualcomm Cloud AI 100** via Imagine for
  speed/quality, **on-device Qualcomm NPU** via genieX for privacy and
  resilience, raw data as a last-resort fallback
- Exposed to Claude over the open **MCP** standard via **FastMCP** — a few
  lines of Python became a tool Claude can reason about and call on its own
- Fully working end-to-end today, live-tested during this session, including
  the on-device fallback path
- Vision: same pattern, more sensors, closing the loop from "aware" to
  "acting" — a genuinely context-aware smart home, privacy-preserving by
  default
