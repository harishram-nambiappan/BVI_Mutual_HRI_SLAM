# BVI_Mutual_HRI_SLAM

A research prototype for **Mutual Human-Robot Interaction (HRI)** aimed at **Blind and Visually-Impaired (BVI)** users in a **SLAM** (Simultaneous Localization and Mapping) context.

**Scenario:** a person walks an indoor public space (e.g. an airport) holding the
phone and reports **obstacles / points of interest** ("banana here") so robots can
understand and navigate the environment. The phone captures the spoken report and,
optionally, a photo; OpenAI turns the audio into a **transcript** and the photo into
a structured **obstacle description**; everything is sent to a desktop app for review.

The system has two parts:

1. **`Mobile_Application/`** — an Android client. The user records a **spoken
   command** and optionally captures a **photo**. On the phone, OpenAI
   **transcribes** the audio and **describes** the photo (as a JSON obstacle
   object). The audio, transcript, photo, description, and mode are **uploaded to
   the desktop receiver**.
2. **`Computer_Receiver/`** — a Python desktop app (HTTP server + GUI) that
   receives reports and lets you **read the transcript & description, preview the
   photo, and play the audio**.

> Status: prototype. There is **no robot integration yet** — the desktop app just
> collects and displays the reports.

---

## End-to-end flow

```
[ Phone ]                                              [ Your computer ]

speech only:
  record audio ─▶ OpenAI transcribe ─▶ transcript on phone
                                           │ POST audio + transcript + mode
                                           ▼
speech + image:                       http://<pc-ip>:8000/upload ─▶ Python receiver
  capture photo ─▶ OpenAI vision (GPT)                                ├─ HTTP server (Flask)
     ─▶ JSON obstacle description on phone                            └─ Desktop GUI (Tkinter):
  ─▶ add speech ─▶ record + transcribe                                   reports list · mode badge
     ─▶ POST audio + transcript + image + description + mode              transcript · description
                                                                          photo preview · play audio
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
│   ├── config.properties          # non-secret config (committed): models + prompts
│   ├── build.gradle               # root Gradle config (AGP 8.2.2)
│   └── app/
│       ├── build.gradle           # deps, SDK levels, reads .env + config.properties -> BuildConfig
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/slam_hri_mobile_application/
│           │   ├── Intro.java          # splash screen
│           │   ├── Menu.java           # main menu (speech / speech+image)
│           │   ├── SpeechRequest.java  # record audio (+ recording animation)
│           │   ├── SpeechConfirm.java  # play, transcribe, gate Send, upload
│           │   ├── CamImg.java         # camera preview + capture + front/back flip
│           │   ├── ImageReview.java    # review photo: Proceed / Retake
│           │   ├── ImageAnalysis.java  # "Analyzing image" screen (vision + scan animation)
│           │   ├── OpenAiClient.java   # OpenAI audio transcription
│           │   ├── OpenAiVision.java   # OpenAI photo description (JSON)
│           │   └── ServerUploader.java # uploads audio+transcript+image+description+mode
│           └── res/
│               ├── layout/        # one XML layout per screen
│               ├── values/        # colors, dimens, strings, themes, styles
│               ├── values-night/  # dark-theme color/theme overrides
│               ├── drawable/      # vector icons, gradients, pulse/scan backgrounds
│               └── mipmap-*/      # launcher icons
│
└── Computer_Receiver/            # Python desktop receiver
    ├── receiver.py               # Flask HTTP server + Tkinter GUI (one process)
    ├── receiver_icon.png         # app icon shown in the GUI window/header
    ├── auto_deploy.sh            # one-command setup + run (venv, install, start)
    ├── requirements.txt          # Flask + Pillow
    ├── README.md                 # receiver-specific docs
    ├── .gitignore                # ignores received/, .venv/, __pycache__
    └── received/                 # (created at runtime) saved clips/photos/transcripts
```

---

## Requirements

**Mobile app**
* Android Studio (with the Android SDK) and a JDK
* An Android device or emulator on **Android 7.0 (API 24)** or higher
* A **camera** and **microphone**
* An **OpenAI API key** (used for both transcription and photo description)

**Desktop receiver**
* Python 3.9+ **with Tkinter (Tk)** for the GUI (Anaconda / python.org include it;
  Homebrew Python often does not — `auto_deploy.sh` auto-picks a Tk-capable Python)
* Dependencies: **Flask** + **Pillow** (`pip install -r Computer_Receiver/requirements.txt`)
* Audio playback: macOS uses built-in `afplay`; Windows opens the default player;
  Linux uses `ffplay` (install `ffmpeg`)

---

## Configuration

Two config files for the mobile app, by design:

| File | Committed? | Holds | You edit it? |
|------|-----------|-------|--------------|
| `Mobile_Application/.env` | **No** (gitignored) | **Secrets**: OpenAI key + your computer's IP/URL | **Yes — you must create it** |
| `Mobile_Application/config.properties` | Yes | **Non-secret config**: models + prompts | Optional (to tune models/prompts) |

Both are read by `app/build.gradle` at build time and exposed via `BuildConfig`.

### 1. You MUST create the `.env` file

The repo does **not** ship a `.env` (it's gitignored). Create it from the template:

```bash
cd Mobile_Application
cp .env.example .env
```

Then set both values in `Mobile_Application/.env`:

```properties
# Your OpenAI API key (used by the phone for transcription AND photo description)
OPENAI_API_KEY=sk-your-real-key-here

# Your computer's LAN IP + the receiver port 8000 (no trailing /upload).
# Must match the URL that receiver.py prints on startup.
SERVER_URL=http://192.168.1.153:8000
```

Exposed as `BuildConfig.OPENAI_API_KEY` and `BuildConfig.SERVER_URL`.

> ⚠️ The phone and the computer **must be on the same Wi-Fi network**. If your
> computer's IP changes, update `SERVER_URL` and rebuild.

> 🔒 Security note: with this design the OpenAI key is compiled into the APK —
> fine for a private prototype, but don't distribute the built APK.

### 2. Models and prompts live in `config.properties`

Non-secret, so it's committed. Edit here to change models or prompts (no code
changes needed):

```properties
# --- Speech-to-text (audio) ---
TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe   # or gpt-4o-transcribe, whisper-1
TRANSCRIPTION_PROMPT=                         # optional bias prompt; blank = none

# --- Vision (photo description) ---
VISION_MODEL=gpt-5                            # or gpt-4o
# NOTE: the system prompt MUST contain the word "json" (the app requests a JSON object).
VISION_SYSTEM_PROMPT=You analyze one photo ... compact JSON object ...
VISION_USER_PROMPT=Identify the main obstacle in this image as JSON.
```

Exposed as `BuildConfig.TRANSCRIPTION_MODEL`, `BuildConfig.TRANSCRIPTION_PROMPT`,
`BuildConfig.VISION_MODEL`, `BuildConfig.VISION_SYSTEM_PROMPT`,
`BuildConfig.VISION_USER_PROMPT`. Prompts are escaped automatically, so quotes in
the JSON keys are fine.

> The vision call uses `response_format: json_object`, which OpenAI requires the
> prompt to mention **"json"**. If you remove "json" from `VISION_SYSTEM_PROMPT`,
> the vision request will error.

---

## Setup & run (full system)

### 1. Start the desktop receiver

**Easiest — the deploy script** (creates a virtualenv, installs Flask + Pillow,
picks a Tk-capable Python, and starts the receiver):

```bash
cd Computer_Receiver
chmod +x auto_deploy.sh   # one time, to make it executable
./auto_deploy.sh
```

**Or manually:**

```bash
cd Computer_Receiver
pip install -r requirements.txt
python receiver.py
```

On start it prints (and shows in the GUI header) the URL it is listening on, e.g.
`http://192.168.1.153:8000`. A **desktop window** opens — that is the GUI (a native
window, not a web page, so it has no URL of its own).

### 2. Point the phone at your computer

In `Mobile_Application/.env`, set `SERVER_URL` to your computer's LAN IP + port
`8000`, and set `OPENAI_API_KEY`.

### 3. Build & run the app

```bash
cd Mobile_Application
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or set sdk.dir in local.properties
./gradlew assembleDebug          # build a debug APK
./gradlew installDebug           # build + install on a connected device/emulator
```

Or open `Mobile_Application/` in Android Studio and press **Run**. Launcher entry
point: the `Intro` activity.

### 4. Use it

* **Speech only:** Menu → *Speech command* → record → on **Confirm request** the
  transcript appears → **Send request**.
* **Speech + image:** Menu → *Speech with image* → frame & **Capture** (flip
  front/back as needed) → **Proceed** → the **Analyzing image** screen shows the
  GPT obstacle description → **Add speech** → record → **Send request**.

The report (audio, transcript, photo, description, mode) appears in the desktop
GUI, where you can read it, preview the photo, and **Play** the audio.

> The app **blocks Send / Add speech until the OpenAI response returns** and shows
> a quick "Please wait — still processing…" toast if you tap early, so empty data
> is never uploaded.

---

## The desktop receiver (`Computer_Receiver/`)

`receiver.py` runs **two things in one process**:

**A) HTTP server (Flask)** — receives data from the phone. Endpoints:

| URL | Method | Used by | Purpose |
|-----|--------|---------|---------|
| `http://<pc-ip>:8000/` | GET | you (browser) | "server is running ✅" status page |
| `http://<pc-ip>:8000/health` | GET | you (browser) | JSON `{"status":"ok"}` |
| `http://<pc-ip>:8000/upload` | POST | **the phone** | multipart: `audio` (file), `transcript`, `mode`, and in image mode `image` (file) + `description` |

Each report is saved under `Computer_Receiver/received/` (gitignored) as
`clip_<timestamp>.mp4` (audio), `clip_<timestamp>.jpg` (photo, image mode),
`clip_<timestamp>.txt` (transcript), and `clip_<timestamp>_description.txt`.

**B) Desktop GUI (Tkinter)** — a clean, card-based window:

* a **Reports** list (mode icon 🎤 / 🖼 + time, newest auto-selected, count badge);
* a **mode badge** ("SPEECH" / "SPEECH + IMAGE");
* the **spoken report** (transcript) and **image description** (JSON);
* a **photo preview** (Pillow, EXIF-corrected so it isn't rotated/flipped);
* **Play / Stop** audio, and **Open image** (hidden for speech-only reports);
* a header that shows "● Server running" + the listening URL, plus a status line.

The server runs in a background thread; the GUI polls shared state every ~0.8s so
new reports appear automatically. Pillow is optional — without it the preview is
replaced by a hint and the **Open image** button.

---

## Mobile app reference

### Tech stack

| Aspect | Choice |
|--------|--------|
| Language | Java |
| UI | Android XML layouts + `ConstraintLayout` (no Jetpack Compose) |
| Components | Material Components (`com.google.android.material:material`) |
| Camera | CameraX (`androidx.camera:*`), front/back switching |
| Audio | `android.media.MediaRecorder` (AAC) / `MediaPlayer` |
| Networking | OkHttp (`com.squareup.okhttp3:okhttp`) |
| Transcription | OpenAI (`gpt-4o-mini-transcribe` by default; set in `config.properties`) |
| Vision / description | OpenAI (`gpt-5` by default; set in `config.properties`) |
| Min / Target SDK | 24 / 34 |
| Package | `com.example.slam_hri_mobile_application` |

### Screens / classes

Each screen is a single `AppCompatActivity` with one matching layout in
`res/layout/`. Navigation is manual, via `Intent`s. The action bar is hidden.

| Class | Layout | What it does |
|-------|--------|--------------|
| `Intro` | `activity_intro.xml` | Splash: logo, title, spinner for ~3s, then opens `Menu`. Launcher activity. |
| `Menu` | `activity_menu.xml` | Two choices: **Speech command** (→ `SpeechRequest`) or **Speech with image** (→ `CamImg`). |
| `SpeechRequest` | `activity_speech_request.xml` | Records audio (AAC) with a live **recording animation** (radar pulse + blinking dot + timer). Forwards any image path/description, then opens `SpeechConfirm`. |
| `SpeechConfirm` | `activity_speech_confirm.xml` | **Plays** the clip (playback animation), **auto-transcribes** via OpenAI, shows the transcript, and **Send request** uploads everything. Send is blocked (with a toast) until transcription returns. |
| `CamImg` | `activity_cam_img.xml` | CameraX preview with a **flip button** (front/back). Captures a photo (overwrites one file) and opens `ImageReview`. |
| `ImageReview` | `activity_image_review.xml` | Shows the captured photo (downsampled + EXIF-rotated). **Proceed** → `ImageAnalysis`; **Retake** → `CamImg`. |
| `ImageAnalysis` | `activity_image_analysis.xml` | **Analyzing image** screen: animated scan line + spinner while OpenAI describes the photo (JSON), shows the result, then **Add speech** (blocked with a toast until the result returns) → `SpeechRequest`. |

Helper classes:

* **`OpenAiClient`** — multipart POST of the audio to OpenAI transcription
  (`BuildConfig.TRANSCRIPTION_MODEL`, optional `TRANSCRIPTION_PROMPT`); returns text.
* **`OpenAiVision`** — base64 image → OpenAI chat completions
  (`BuildConfig.VISION_MODEL`, `VISION_SYSTEM_PROMPT`/`VISION_USER_PROMPT`,
  `response_format: json_object`); returns a JSON obstacle description.
* **`ServerUploader`** — multipart POST of `audio + transcript + mode + image +
  description` to `BuildConfig.SERVER_URL` + `/upload`.

### Navigation flow

```
Intro ──(3s)──▶ Menu
   ├─ "Speech command" ─────────────▶ SpeechRequest ─▶ SpeechConfirm ─▶ Menu
   │                                       ▲                 │  (transcribe + upload)
   │                                       └─── "Re-record" ─┘
   └─ "Speech with image" ─▶ CamImg ─▶ ImageReview ─(Proceed)▶ ImageAnalysis ─(Add speech)▶ SpeechRequest ─▶ SpeechConfirm
                                ▲            │  (describe photo via OpenAI here)
                                └─ "Retake" ─┘
```

### Audio & image handling notes

* Audio is recorded as **AAC** (44.1 kHz / 128 kbps) to
  `getFilesDir()/slam_hri_request_sample_1.mp4` — a universally playable file
  (earlier AMR output couldn't be played by macOS `afplay`).
* Photos are captured to a single app file (`getFilesDir()/captured_image.jpg`)
  that is **overwritten each time**, so the review/analysis screens always show
  the photo you just took.
* The image description is generated **once** on `ImageAnalysis` and carried
  forward to the upload (the confirm screen does not re-run vision).

### Resources & design system

| File | Purpose |
|------|---------|
| `res/values/colors.xml` / `res/values-night/colors.xml` | Semantic palette (brand, accent, danger, surfaces, text) + dark variant. |
| `res/values/themes.xml` / `res/values-night/themes.xml` | Material theme, gradient window background, status/nav bar colors, text/button styles. |
| `res/values/dimens.xml` | Spacing, sizing, corner-radius, typography tokens. |
| `res/values/strings.xml` | All UI copy, transcription/vision/upload status text, accessibility descriptions. |
| `res/drawable/` | Vector icons, gradient backgrounds, circular button + pulse/scan-ring backgrounds, launcher icon. |

**Accessibility (for BVI users):** large touch targets, high-contrast colors,
full dark-mode support, spoken-state announcements, and `contentDescription`s on
interactive elements.

### Permissions (`AndroidManifest.xml`)

`INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
`READ/WRITE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`. Camera and microphone are
requested at runtime. `usesCleartextTraffic="true"` is set so the phone can reach
the local `http://` receiver.

---

## Troubleshooting

* **Phone can't reach the computer** — confirm both are on the same Wi-Fi, that
  `SERVER_URL` matches the IP printed by `receiver.py`, and that your firewall
  allows incoming connections on port 8000 (macOS may prompt the first time).
* **`ModuleNotFoundError: No module named '_tkinter'`** — your Python lacks Tk.
  `auto_deploy.sh` auto-selects a Tk-capable Python; if running manually, use
  Anaconda/python.org Python, or `brew install python-tk` for Homebrew Python.
* **"Could not preview image" in the GUI** — install Pillow
  (`pip install -r requirements.txt`); the console prints the exact reason. The
  GUI tolerates slightly truncated uploads.
* **Photo looks rotated/flipped** — fixed: the GUI applies EXIF orientation. If a
  front-camera shot still looks mirrored, that's a capture-side mirror, not EXIF.
* **No transcript / description** — make sure `OPENAI_API_KEY` is set in `.env`
  and rebuild. A vision failure is shown inline (e.g. an HTTP error); if it
  mentions the model, switch `VISION_MODEL` to `gpt-4o` in `config.properties`.
* **Empty data on the desktop** — shouldn't happen now: Send / Add speech are
  blocked until the OpenAI response returns (with a "please wait" toast).
* **`GET /` shows 404** — old server build; restart `receiver.py`.
* **Computer's IP changed** — update `SERVER_URL` in `.env` and rebuild.

---

## Known limitations / next steps

* **API key in the APK** (phone calls OpenAI directly) — fine for a private
  prototype; for production, proxy OpenAI through a server so the key never ships.
* **Audio recording on older devices** — `MediaRecorder` is only constructed on
  API 31+, so recording will not work below that despite `minSdk 24`.
* **Vision latency** — `gpt-5` is slower than `gpt-4o`; sending full-resolution
  photos also adds time. Switch `VISION_MODEL` and/or downscale before sending to
  speed it up.
* **No robot integration yet** — the receiver currently just collects/displays
  reports; the SLAM/HRI backend and command streaming to the robot are future work.
