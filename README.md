# BVI_Mutual_HRI_SLAM

A research prototype for **Mutual Human-Robot Interaction (HRI)** aimed at **Blind and Visually-Impaired (BVI)** users in a **SLAM** (Simultaneous Localization and Mapping) context.

The system has two parts:

1. **`Mobile_Application/`** — an Android client. The user records a **spoken command**, optionally attaches a **camera photo**, the clip is **transcribed by OpenAI on the phone**, and the audio + transcript are **uploaded to a desktop receiver**.
2. **`Computer_Receiver/`** — a small Python desktop app (HTTP server + GUI) that receives clips from the phone, lets you **read the transcript** and **play the audio**.

---

## End-to-end flow

```
[ Phone ]                                  [ Your computer ]

record audio ──▶ transcribe via OpenAI ──▶ transcript shown on phone
     │                                            │
     │  POST audio + transcript                   │
     └──────────────▶ http://<pc-ip>:8000/upload ─┴─▶ Python receiver
                                                       ├─ HTTP server (Flask)
                                                       └─ Desktop GUI (Tkinter):
                                                          list clips · show
                                                          transcript · play audio
```

The phone and computer must be on the **same Wi-Fi network**.

---

## Repository layout — where each thing lives

```
BVI_Mutual_HRI_SLAM/
├── README.md                      # this file
│
├── Mobile_Application/            # Android app (Java, XML layouts)
│   ├── .env                       # SECRETS (gitignored, you create this): OpenAI key + server URL
│   ├── .env.example               # template for .env (committed)
│   ├── config.properties          # non-secret config (committed): transcription model
│   ├── build.gradle               # root Gradle config (AGP 8.2.2)
│   └── app/
│       ├── build.gradle           # deps, SDK levels, reads .env -> BuildConfig
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/slam_hri_mobile_application/
│           │   ├── Intro.java          # splash screen
│           │   ├── Menu.java           # main menu
│           │   ├── SpeechRequest.java  # record audio (+ recording animation)
│           │   ├── SpeechConfirm.java  # play, transcribe, show transcript, upload
│           │   ├── CamImg.java         # camera preview + capture + front/back flip
│           │   ├── ImageReview.java    # review captured photo
│           │   ├── OpenAiClient.java   # calls OpenAI transcription API
│           │   └── ServerUploader.java # uploads audio + transcript to the PC
│           └── res/
│               ├── layout/        # one XML layout per screen
│               ├── values/        # colors, dimens, strings, themes, styles
│               ├── values-night/  # dark-theme color/theme overrides
│               ├── drawable/      # vector icons + backgrounds
│               └── mipmap-*/      # launcher icons
│
└── Computer_Receiver/            # Python desktop receiver
    ├── receiver.py               # Flask HTTP server + Tkinter GUI (one process)
    ├── auto_deploy.sh            # one-command setup + run (venv, install, start)
    ├── requirements.txt          # Flask
    ├── README.md                 # receiver-specific docs
    ├── .gitignore                # ignores received/, .venv/ and __pycache__
    └── received/                 # (created at runtime) saved clips + transcripts
```

---

## Requirements

**Mobile app**
* Android Studio (with the Android SDK) and a JDK
* An Android device or emulator on **Android 7.0 (API 24)** or higher
* A **camera** and **microphone**
* An **OpenAI API key** (for transcription)

**Desktop receiver**
* Python 3.9+ (Tkinter ships with standard CPython)
* `pip install -r Computer_Receiver/requirements.txt` (installs Flask)
* Audio playback: macOS uses built-in `afplay`; Windows opens the default player; Linux uses `ffplay` (install `ffmpeg`)

---

## Configuration

There are **two** config files for the mobile app, by design:

| File | Committed? | Holds | You edit it? |
|------|-----------|-------|--------------|
| `Mobile_Application/.env` | **No** (gitignored) | **Secrets**: OpenAI key + your computer's IP/URL | **Yes — you must create it** |
| `Mobile_Application/config.properties` | Yes | **Non-secret config**: the transcription model | Only to change the model |

Both are read by `app/build.gradle` at build time and exposed via `BuildConfig`.

### 1. You MUST create the `.env` file

The repo does **not** ship a `.env` (it's gitignored so secrets never get
committed). Create it from the template before building:

```bash
cd Mobile_Application
cp .env.example .env
```

Then open `Mobile_Application/.env` and set both values:

```properties
# Your OpenAI API key (used by the phone to transcribe audio)
OPENAI_API_KEY=sk-your-real-key-here

# Your computer's LAN IP + the receiver port 8000 (no trailing /upload).
# This must match the URL that receiver.py prints on startup.
SERVER_URL=http://192.168.1.153:8000
```

| Key | What it is | Example |
|-----|------------|---------|
| `OPENAI_API_KEY` | OpenAI key, used by the phone for transcription | `sk-...` |
| `SERVER_URL` | The desktop receiver's address on your LAN | `http://192.168.1.153:8000` |

Exposed as `BuildConfig.OPENAI_API_KEY` and `BuildConfig.SERVER_URL`.

> ⚠️ The phone and the computer **must be on the same Wi-Fi network** for the
> upload to work. If your computer's IP changes, update `SERVER_URL` and rebuild.

> 🔒 Security note: with this design the OpenAI key is compiled into the APK —
> fine for a private prototype, but don't distribute the built APK.

### 2. The transcription model lives in `config.properties`

This is non-secret, so it's committed. Change it here to swap models:

```properties
# Examples: gpt-4o-mini-transcribe, gpt-4o-transcribe, whisper-1
TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
```

Exposed as `BuildConfig.TRANSCRIPTION_MODEL` and used by `OpenAiClient`.

---

## Setup & run (full system)

### 1. Start the desktop receiver

**Easiest — use the deploy script** (creates a virtualenv, installs Flask, and
starts the receiver). Make it executable once, then run it:

```bash
cd Computer_Receiver
chmod +x auto_deploy.sh   # one time, to make it executable
./auto_deploy.sh
```

**Or do it manually:**

```bash
cd Computer_Receiver
pip install -r requirements.txt
python receiver.py
```

On start it prints (and shows in the GUI title bar) the URL it is listening on,
e.g. `http://192.168.1.153:8000`. A **desktop window** opens — that is the GUI
(it is a native window, not a web page, so it has no URL of its own).

### 2. Point the phone at your computer

In `Mobile_Application/.env` set `SERVER_URL` to your computer's LAN IP and port
`8000`, and set your `OPENAI_API_KEY`.

### 3. Build & run the app

```bash
cd Mobile_Application
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or set sdk.dir in local.properties
./gradlew assembleDebug          # build a debug APK
./gradlew installDebug           # build + install on a connected device/emulator
```

Or open `Mobile_Application/` in Android Studio and press **Run**. Launcher
entry point: the `Intro` activity.

### 4. Use it

Record a command → on the **Confirm request** screen the transcript appears →
tap **Send request** → the clip + transcript show up in the desktop GUI, where
you can press **Play**.

> Tip: send **after** the transcript appears on the phone. Tapping "Send" while
> it still says "Transcribing…" uploads an empty transcript.

---

## The desktop receiver (`Computer_Receiver/`)

`receiver.py` runs **two things in one process**:

**A) HTTP server (Flask)** — receives data from the phone. Endpoints:

| URL | Method | Used by | Purpose |
|-----|--------|---------|---------|
| `http://<pc-ip>:8000/` | GET | you (browser) | "server is running ✅" status page |
| `http://<pc-ip>:8000/health` | GET | you (browser) | JSON `{"status":"ok"}` |
| `http://<pc-ip>:8000/upload` | POST | **the phone** | multipart `audio` file + `transcript` text |

Uploads are saved to `Computer_Receiver/received/` as `clip_<timestamp>.mp4`
plus a matching `clip_<timestamp>.txt` transcript (folder is gitignored).

**B) Desktop GUI (Tkinter)** — the window that opens on your screen:

* a list of received clips (newest auto-selected),
* a transcript pane for the selected clip,
* **Play / Stop** buttons for the selected clip's audio,
* a live status line and the listening URL in the title bar.

The server runs in a background thread; the GUI polls shared state every ~0.8s
so new clips appear automatically.

---

## Mobile app reference

### Tech stack

| Aspect | Choice |
|--------|--------|
| Language | Java |
| UI | Android XML layouts + `ConstraintLayout` (no Jetpack Compose) |
| Components | Material Components (`com.google.android.material:material`) |
| Camera | CameraX (`androidx.camera:*`), front/back switching |
| Audio | `android.media.MediaRecorder` / `MediaPlayer` |
| Networking | OkHttp (`com.squareup.okhttp3:okhttp`) |
| Transcription | OpenAI (`gpt-4o-mini-transcribe` by default; set in `config.properties`) |
| Min / Target SDK | 24 / 34 |
| Package | `com.example.slam_hri_mobile_application` |

### Screens / classes

Each screen is a single `AppCompatActivity` with one matching layout in
`res/layout/`. Navigation is manual, via `Intent`s. The action bar is hidden.

| Class | Layout | What it does |
|-------|--------|--------------|
| `Intro` | `activity_intro.xml` | Splash: logo, title, loading spinner for ~3s, then opens `Menu`. Launcher activity. |
| `Menu` | `activity_menu.xml` | Two choices: **Speech command** (→ `SpeechRequest`) or **Speech with image** (→ `CamImg`). |
| `SpeechRequest` | `activity_speech_request.xml` | Records audio. Shows a live **recording animation** (radar pulse + blinking dot + timer) while recording. Saves the clip and opens `SpeechConfirm`. |
| `SpeechConfirm` | `activity_speech_confirm.xml` | **Plays** the clip (with playback animation), **auto-transcribes** it via OpenAI and shows the transcript, and on **Send request** **uploads** audio + transcript to the desktop receiver. |
| `CamImg` | `activity_cam_img.xml` | CameraX preview with a **flip button** to switch front/back cameras. Captures a photo and opens `ImageReview`. |
| `ImageReview` | `activity_image_review.xml` | Shows the just-captured photo (downsampled + EXIF-rotated). "Add speech" → `SpeechRequest`; "Retake" → `CamImg`. |

Helper classes:

* **`OpenAiClient`** — multipart POST of the audio to OpenAI's transcription
  endpoint using `BuildConfig.OPENAI_API_KEY`; returns the transcript text.
* **`ServerUploader`** — multipart POST of the audio + transcript to
  `BuildConfig.SERVER_URL` + `/upload`.

### Navigation flow

```
Intro ──(3s)──▶ Menu
                 ├─ "Speech command" ───────▶ SpeechRequest ──▶ SpeechConfirm ──▶ Menu
                 │                                 ▲                  │   (transcribe +
                 │                                 └──── "Re-record" ─┘    upload here)
                 └─ "Speech with image" ─▶ CamImg ──▶ ImageReview ──▶ SpeechRequest ...
                                              ▲            │
                                              └─ "Retake" ─┘
```

### Audio & image handling notes

* Audio is recorded to `getFilesDir()/slam_hri_request_sample_1.mp4`.
* Photos are captured to a single app file (`getFilesDir()/captured_image.jpg`)
  that is **overwritten each time**, so the review screen always shows the photo
  you just took (this fixed an earlier stale-image bug).

### Resources & design system

| File | Purpose |
|------|---------|
| `res/values/colors.xml` / `res/values-night/colors.xml` | Semantic palette (brand, accent, danger, surfaces, text) + dark variant. |
| `res/values/themes.xml` / `res/values-night/themes.xml` | Material theme, gradient window background, status/nav bar colors, text/button styles. |
| `res/values/dimens.xml` | Spacing, sizing, corner-radius, typography tokens. |
| `res/values/strings.xml` | All UI copy, transcription/upload status text, accessibility descriptions. |
| `res/drawable/` | Vector icons, gradient backgrounds, circular button + pulse-ring backgrounds, launcher icon. |

**Accessibility (for BVI users):** large touch targets, high-contrast colors,
full dark-mode support, spoken-state announcements, and `contentDescription`s on
interactive elements.

### Permissions (`AndroidManifest.xml`)

`INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
`READ/WRITE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`. Camera and microphone
are requested at runtime. `usesCleartextTraffic="true"` is set so the phone can
reach the local `http://` receiver.

---

## Troubleshooting

* **Phone can't reach the computer** — confirm both are on the same Wi-Fi, that
  `SERVER_URL` matches the IP printed by `receiver.py`, and that your firewall
  allows incoming connections on port 8000 (macOS may prompt the first time).
* **`GET /` shows 404** — you're on an old server build; restart `receiver.py`
  (the status page route was added). `/upload` and `/health` always exist.
* **Transcript empty on the desktop** — you likely tapped **Send** before
  transcription finished. Wait for the transcript to appear on the phone first.
* **No transcript on the phone** — make sure `OPENAI_API_KEY` is set in `.env`
  and you rebuilt the app.
* **Computer's IP changed** — update `SERVER_URL` in `.env` and rebuild.

---

## Known limitations / next steps

* **API key in the APK** (Option A design) — acceptable for a private prototype;
  for production, proxy OpenAI through a server so the key never ships.
* **Audio recording on older devices** — `MediaRecorder` is only constructed on
  API 31+, so recording will not work below that despite `minSdk 24`.
* **No robot integration yet** — the receiver currently just displays data; the
  SLAM/HRI backend and command streaming to the robot are future work.
