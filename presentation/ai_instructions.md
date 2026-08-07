# Android Context

This is the Android side of the project — it runs on your phone and collects sensor data every 5 seconds, then makes it available for the rest of the pipeline to consume.

## What data gets collected

Pretty much everything the phone knows about itself:
- Motion sensors (accelerometer, gyroscope, linear accel, gravity, rotation, magnetometer)
- Environment (barometer, ambient light, proximity, temperature, humidity)
- GPS (location, speed, altitude, bearing)
- Device state (battery, screen on/off, ringer mode, DND, call state, Bluetooth devices, WiFi, network type, foreground app)
- An inferred activity label: `IN_VEHICLE`, `RUNNING`, `WALKING`, `STILL`, or `UNKNOWN` — figured out from speed + accelerometer + bluetooth, no Google APIs

---

## Step 1 — Install Android Studio

Download from https://developer.android.com/studio and install it. When it first opens, it'll prompt you to install the Android SDK — just click through the defaults and let it finish.

---

## Step 2 — Open this project

1. Open Android Studio
2. Click **File → Open**
3. Navigate to this repo and select the `android-sensor-collector/` folder (just this subfolder, not the whole repo)
4. Click **OK**
5. Wait for the Gradle sync to finish — you'll see a progress bar at the bottom. First time takes about a minute since it's downloading dependencies.

---

## Step 3 — Enable USB Debugging on your Samsung S23 Ultra

You need to unlock Developer Options first:

1. Go to **Settings → About Phone → Software Information**
2. Tap **Build Number** 7 times rapidly
3. It'll ask for your phone PIN — enter it and tap **Done** (not just OK)
4. You'll see "You are now a developer!"

Now enable USB Debugging:

5. Go back to the main **Settings** page and scroll to the bottom — **Developer Options** is now there
6. Tap **Developer Options** → scroll down to **USB Debugging** → toggle it **ON**
7. Tap **OK** on the confirmation dialog

---

## Step 4 — Connect your phone

1. Plug your phone into your laptop with a USB-C cable (use a data cable, not a charge-only cable — the one that came in the box works)
2. Pull down the notification shade on your phone and tap the **"USB connected"** notification
3. Select **File Transfer (MTP)**
4. A dialog will appear on your phone: **"Allow USB debugging?"** → tap **Allow** (check "Always allow from this computer" to avoid seeing it every time)

---

## Step 5 — Run the app

1. In Android Studio, look at the toolbar at the top — your phone should appear in the device dropdown (e.g. "Samsung SM-S918B")
2. If it doesn't appear, try **Tools → Device Manager** or unplug/replug the cable
3. Click the green **Run ▶** button
4. Android Studio will build and install the app — takes about 30 seconds
5. The app opens automatically on your phone

---

## Step 6 — Grant permissions

When the app opens, permission dialogs will pop up one by one. Tap **Allow** for all of them:
- Location (precise)
- Microphone
- Bluetooth
- Phone / call state

**One permission you have to grant manually** (for foreground app detection):
- Go to **Settings → Apps → Android Context → Special app access → Usage access**
- Toggle it **ON**

---

## Step 7 — Start collecting

1. Tap **Start Collection** in the app
2. Status changes to "● Running — collecting every 5s"
3. The app shows a URL at the top like `http://192.168.1.42:8080/context` — this is how teammates fetch the data
4. After 5 seconds, a live JSON preview appears on screen

You can close the app — the service keeps running in the background (you'll see a persistent notification in your notification bar).

---

## Step 8 — Access the data

**Option A — HTTP from a laptop on the same WiFi (recommended):**
```bash
curl http://<ip-shown-in-app>:8080/context
```

**Option B — Pull the file via USB:**
```bash
adb pull /sdcard/Android/data/com.qc.hackathon.sensorcontext/files/context_snapshot.json
```

---

## Changing the collection interval

Default is 5 seconds. Change `COLLECTION_INTERVAL_MS` in `SensorCollectorService.kt` and re-run.

---

## Troubleshooting

**"No target device found" in Android Studio** → Make sure USB Debugging is on, cable is plugged in, and you selected File Transfer (not Charging only) on your phone.

**"Allow USB debugging?" dialog not appearing** → Unplug and replug the cable.

**`android.useAndroidX` error** → Make sure `gradle.properties` exists in the `android-sensor-collector/` folder (it's already there), then do **File → Sync Project with Gradle Files**.

**`Unable to delete directory` / file locking error** → Your project is inside OneDrive which locks build files. Fix: right-click the `android-sensor-collector/app/build/` folder in File Explorer → Properties → uncheck "Always keep on this device" to make it cloud-only. Or pause OneDrive sync while building (right-click OneDrive tray icon → Pause syncing).

**HTTP URL not showing** → Make sure your phone is connected to WiFi (not just cellular).


# Embedded Context

This is the embedded side of the project — it runs on an **Arduino UNO Q**, reads two onboard sensors every loop, and serves the latest reading over HTTP for the rest of the pipeline to consume.

The UNO Q is a dual-processor board: an **STM32 microcontroller (MCU)** for real-time sensor reads, and a **Linux-capable processor (MPU)** that runs Python. The two sides talk over Arduino's **Bridge** RPC layer. Everything is packaged as an **App Lab "App"** — a folder containing a sketch, a Python script, and a manifest — which App Lab compiles, deploys, and runs on both processors at once.

## What data gets collected

- **Temperature** (°C) — via a Modulino Thermo brick
- **Distance** (mm) — via a Modulino Distance (time-of-flight) brick
- A **timestamp** (`America/Los_Angeles`), added on the Python side when the reading is polled

The sketch also pulls in libraries for a barometer, IMU, and ambient light/color sensor (`Arduino_LPS22HB`, `Arduino_LSM6DSOX`, `Arduino_LTR381RGB`) as project dependencies, but only temperature and distance are currently wired up and exposed — the rest are available for anyone who wants to extend the sketch.

---

## Step 1 — Install Arduino App Lab

Download **Arduino App Lab** from https://www.arduino.cc/en/software (the App Lab section, PC-hosted variant for your OS). App Lab also ships pre-installed on the UNO Q itself if you'd rather run it directly on the board (Single-Board Computer mode) with a monitor and keyboard attached — the PC-hosted version is simpler for most people.

---

## Step 2 — Open this project

1. Open Arduino App Lab
2. Choose **Open App** (or the equivalent "open an existing project" option)
3. Navigate to this repo and select the `sensor_data/` folder — that's the whole App: `app.yaml`, `sketch/`, and `python/`
4. App Lab will read `app.yaml` and lay out the sketch and Python files in its project tree

---

## Step 3 — Wire up the Modulino bricks

1. Connect the **Modulino Thermo** and **Modulino Distance** bricks to the UNO Q's Qwiic/I2C connector, daisy-chained together
2. Double-check both are seated — the sketch calls `.begin()` on each at startup and will silently return `-1`/stale readings if a brick isn't detected

---

## Step 4 — Connect your UNO Q

1. Plug the UNO Q into your laptop with a USB-C cable, **or** make sure it's on the same WiFi network as App Lab (App Lab can detect it either way)
2. App Lab should show the board as connected in its device selector
3. If it doesn't appear, try unplugging/replugging, or restarting App Lab

---

## Step 5 — Run the App

1. Click **Run** (or **Launch**) in App Lab
2. App Lab will:
   - Compile `sketch/sketch.ino` and flash it to the STM32 MCU
   - Build the Python environment for `python/main.py` (installing anything in `requirements.txt`)
   - Launch both sides together
3. First run takes longer if any Bricks need their container image downloaded — subsequent runs are faster
4. Once running, the sketch exposes `get_temperature` and `get_distance` as RPC calls; the Python side polls both in a background loop and starts an HTTP server on **port 9000**

**Note:** Arduino serial output from the MCU side doesn't reliably show up in App Lab's serial monitor over WiFi — use a direct USB connection if you need to debug the sketch itself. Python output goes to App Lab's logs either way.

---

## Step 6 — Access the data

```bash
curl http://<uno-q-ip>:9000/data
```

Response:
```json
{
  "temperature": 27.8,
  "distance": 36,
  "timestamp": "2026-08-06 14:32:07"
}
```

This is the exact endpoint `physical_context/config.py` (`SENSOR_DATA_URL`) points at for the rest of the pipeline — if your UNO Q's IP changes, update it there.

---

## Changing the polling behavior

There's currently no explicit delay in `sensor_polling_loop()` in `python/main.py` — it calls `Bridge.call()` for temperature and distance back-to-back, as fast as `App.run()`'s loop ticks. To slow it down (e.g. to match the phone side's 5s cadence), add `time.sleep(N)` at the end of `sensor_polling_loop()`.

To add another sensor: instantiate it in `sketch.ino` (e.g. a `ModulinoMovement` for the IMU), call `.begin()` in `setup()`, add a getter function, register it with `Bridge.provide(...)`, then call it from `sensor_polling_loop()` in `python/main.py` and add it to `latest_telemetry`.

---

## Troubleshooting

**Board not showing up in App Lab** → Check the USB-C cable is a data cable, or confirm the UNO Q is on the same WiFi network. Restart App Lab.

**Readings stuck at `null` or `-1`** → A Modulino brick likely isn't detected on the I2C bus. Check the Qwiic cable seating and try re-running the App.

**`[ERROR] Bridge reading failed` in Python logs** → The MCU-side sketch either isn't running or crashed. Re-flash by clicking Run again in App Lab.

**First run is slow** → Expected if a Brick's container image hasn't been downloaded yet; it's cached after that.

**`curl` to port 9000 fails from another machine** → Confirm the UNO Q is on the same network as the machine you're curling from, and that port 9000 isn't blocked by a firewall on either end.

