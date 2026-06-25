# BVI_Mutual_HRI_SLAM

A research prototype for **Mutual Human-Robot Interaction (HRI)** aimed at
**Blind and Visually-Impaired (BVI)** users in a **SLAM** context.

**Scenario:** a person walks an indoor public space (e.g. an airport) holding the
phone and reports **obstacles** ("banana here") to help robots understand the
environment. The phone records the spoken report and, optionally, a photo; OpenAI
turns the audio into a **transcript** and the photo into a structured **obstacle
description**; everything is sent to a desktop app for review.

- **`Mobile_Application/`** — Android client (record, transcribe, describe, upload).
- **`Computer_Receiver/`** — Python desktop app (HTTP server + GUI) that displays
  reports: transcript, description, photo preview, and audio playback.

> Status: prototype — there is **no robot integration yet**; the desktop app just
> collects and displays reports.

> The phone and computer must be on the **same Wi-Fi network**.

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
  ─▶ add speech ─▶ record + transcribe                                   reports · transcript ·
     ─▶ POST audio + transcript + image + description + mode              description · photo · audio
```

---

## Quick start

### 1. Run the desktop receiver

Needs **Python 3.9+ with Tkinter**. The script makes a virtualenv, installs deps
(**Flask + Pillow**), picks a Tk-capable Python, and starts it:

```bash
cd Computer_Receiver
chmod +x auto_deploy.sh   # once
./auto_deploy.sh
```

(Manual alternative: `pip install -r requirements.txt && python receiver.py`.)

It prints the URL to use (e.g. `http://192.168.1.153:8000`) and opens the GUI
window.

### 2. Create the phone's `.env`

The repo ships **no** `.env` (it's gitignored). Create it and fill in your values:

```bash
cd Mobile_Application
cp .env.example .env
```

```properties
OPENAI_API_KEY=sk-your-real-key-here        # used for transcription AND vision
SERVER_URL=http://192.168.1.153:8000        # your computer's LAN IP, must match receiver
```

### 3. Build & run the app

Needs Android Studio + SDK; device/emulator on **Android 7.0 (API 24)+** with a
camera and microphone.

```bash
cd Mobile_Application
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or set sdk.dir in local.properties
./gradlew installDebug                              # build + install on a connected device
```

Or open `Mobile_Application/` in Android Studio and press **Run** (launcher: `Intro`).

### 4. Use it

- **Speech only:** Menu → *Speech command* → record → **Send request**.
- **Speech + image:** Menu → *Speech with image* → **Capture** (flip front/back) →
  **Proceed** → the *Analyzing image* screen shows the obstacle description →
  **Add speech** → record → **Send request**.

The report appears in the desktop GUI. Send / Add speech are **blocked until the
OpenAI response returns** (a quick "please wait" toast shows if you tap early), so
nothing empty is uploaded.

---

## Configuration

| File | Committed? | Holds |
|------|-----------|-------|
| `Mobile_Application/.env` | No (gitignored — **you create it**) | Secrets: `OPENAI_API_KEY`, `SERVER_URL` |
| `Mobile_Application/config.properties` | Yes | Models + prompts |

Both are read by `app/build.gradle` and exposed via `BuildConfig`.

```properties
# config.properties
TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe   # or gpt-4o-transcribe, whisper-1
TRANSCRIPTION_PROMPT=                         # optional bias prompt; blank = none
VISION_MODEL=gpt-5                            # or gpt-4o
VISION_SYSTEM_PROMPT=...                      # MUST contain the word "json"
VISION_USER_PROMPT=...
```

Notes:
- 🔒 The OpenAI key is compiled into the APK (the phone calls OpenAI directly) —
  fine for a private prototype; don't distribute the built APK.
- The vision call uses `response_format: json_object`, so `VISION_SYSTEM_PROMPT`
  must mention **"json"** or the request errors.
- If your computer's IP changes, update `SERVER_URL` and rebuild.

---

## Repository layout

```
BVI_Mutual_HRI_SLAM/
├── README.md
├── Mobile_Application/                # Android app (Java, XML)
│   ├── .env / .env.example            # secrets (gitignored) / template
│   ├── config.properties             # models + prompts
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/.../slam_hri_mobile_application/   # Activities + OpenAI/upload helpers
│       └── res/                       # layout, values(+night), drawable, mipmap
└── Computer_Receiver/                # Python desktop receiver
    ├── receiver.py                   # Flask server + Tkinter GUI
    ├── receiver_icon.png
    ├── auto_deploy.sh                # one-command setup + run
    ├── requirements.txt              # Flask + Pillow
    ├── README.md                     # receiver details (endpoints, etc.)
    └── received/                     # (runtime) saved clips/photos/transcripts
```

---

## Mobile app reference

| Aspect | Choice |
|--------|--------|
| Language / UI | Java · XML layouts + `ConstraintLayout` (no Compose) · Material Components |
| Camera | CameraX (front/back switching) |
| Audio | `MediaRecorder` (AAC) / `MediaPlayer` |
| Networking | OkHttp |
| Transcription / Vision | OpenAI — models set in `config.properties` (`gpt-4o-mini-transcribe` / `gpt-5`) |
| Min / Target SDK · Package | 24 / 34 · `com.example.slam_hri_mobile_application` |

### Screens (one `AppCompatActivity` each, manual `Intent` navigation)

| Class | What it does |
|-------|--------------|
| `Intro` | Splash (~3s) → `Menu`. Launcher activity. |
| `Menu` | Choose **Speech command** (→ `SpeechRequest`) or **Speech with image** (→ `CamImg`). |
| `SpeechRequest` | Records audio (AAC) with a live recording animation; carries any image path/description forward → `SpeechConfirm`. |
| `SpeechConfirm` | Plays the clip, auto-transcribes, shows the transcript, and uploads on **Send** (blocked until transcription returns). |
| `CamImg` | CameraX preview with front/back **flip**; captures a photo → `ImageReview`. |
| `ImageReview` | Shows the photo (EXIF-rotated). **Proceed** → `ImageAnalysis`; **Retake** → `CamImg`. |
| `ImageAnalysis` | *Analyzing image* screen: scan animation while OpenAI describes the photo (JSON), then **Add speech** (blocked until result). |

```
Intro ─▶ Menu ─┬─ "Speech command" ─▶ SpeechRequest ─▶ SpeechConfirm ─▶ Menu
               └─ "Speech with image" ─▶ CamImg ─▶ ImageReview ─(Proceed)▶ ImageAnalysis ─(Add speech)▶ SpeechRequest ─▶ SpeechConfirm
```

Helpers: **`OpenAiClient`** (audio → transcript), **`OpenAiVision`** (photo → JSON
description), **`ServerUploader`** (POST `audio + transcript + mode + image +
description` to `SERVER_URL/upload`).

**Notes**
- Audio is recorded as AAC to `getFilesDir()/slam_hri_request_sample_1.mp4`
  (universally playable; earlier AMR output failed in macOS `afplay`).
- Photos overwrite a single file `getFilesDir()/captured_image.jpg`, so screens
  always show the latest shot.
- The image description is generated **once** on `ImageAnalysis` and carried to the
  upload (the confirm screen doesn't re-run vision).
- UI is centralized in `res/values` (colors + dark variant, dimens, strings,
  themes/styles). Accessibility: large targets, high contrast, dark mode, spoken
  announcements, `contentDescription`s.
- Permissions: `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
  `READ/WRITE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`. `usesCleartextTraffic`
  is enabled so the phone can reach the local `http://` receiver.

---

## Desktop receiver

`receiver.py` runs a Flask server + a Tkinter GUI in one process. The GUI shows a
reports list (🎤/🖼 + time), a mode badge, the transcript and JSON description, an
EXIF-correct photo preview, and **Play / Stop / Open image** controls. Reports are
saved under `received/` (gitignored).

See **`Computer_Receiver/README.md`** for endpoints, saved-file names, and details.

---

## Troubleshooting

- **Phone can't reach the computer** — same Wi-Fi? `SERVER_URL` matches the printed
  URL? Firewall allows incoming on port 8000 (macOS may prompt the first time).
- **`ModuleNotFoundError: No module named '_tkinter'`** — use a Tk-capable Python
  (Anaconda / python.org), or `brew install python-tk`; `auto_deploy.sh` handles this.
- **"Could not preview image"** — install Pillow (`requirements.txt`); the console
  prints the reason.
- **No transcript / description** — set `OPENAI_API_KEY` and rebuild. If a vision
  error mentions the model, switch `VISION_MODEL` to `gpt-4o`.
- **Audio won't play on desktop** — old AMR clips don't play; re-record (new clips
  are AAC).

---

## Known limitations / next steps

- API key ships inside the APK (phone calls OpenAI directly) — for production, proxy
  through a server.
- `MediaRecorder` is only constructed on API 31+, so recording won't work below that
  despite `minSdk 24`.
- `gpt-5` vision is slower than `gpt-4o`; downscaling photos before upload would help.
- No robot integration yet — SLAM/HRI backend and command streaming are future work.
