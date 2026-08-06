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
