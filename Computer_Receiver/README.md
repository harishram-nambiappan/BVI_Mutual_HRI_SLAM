# Computer Receiver

A desktop app that receives **reports** from the phone (audio + transcript, and in
image mode a photo + GPT obstacle description) and lets you **read the transcript &
description, preview the photo, and play the audio** in a clean GUI.

It runs two things in one process:
- an HTTP server (Flask) the phone uploads to, and
- a Tkinter GUI to browse received reports.

## Data flow

```
speech only:   record audio -> OpenAI transcribe -> POST audio + transcript + mode
speech+image:  capture photo -> OpenAI vision (JSON description) -> add speech
               -> record + transcribe -> POST audio + transcript + image + description + mode
   -> GUI shows the mode, transcript, description, photo preview, and plays the audio
```

## Requirements

- Python 3.9+ **with Tkinter (Tk)** support for the GUI. Anaconda and the
  python.org installers include it; Homebrew's Python often does **not**
  (`brew install python-tk` adds it). `auto_deploy.sh` auto-picks a Tk-capable
  Python for you.
- `pip install -r requirements.txt`
- Audio playback:
  - macOS: uses the built-in `afplay` (no install)
  - Windows: opens the file with the default player
  - Linux: uses `ffplay` (install `ffmpeg`)

## Run

### Easiest: the deploy script

`auto_deploy.sh` creates a local virtualenv (`.venv`), installs Flask + Pillow,
picks a Tk-capable Python, and starts the receiver. Make it executable once, then
run it:

```bash
cd Computer_Receiver
chmod +x auto_deploy.sh   # one time
./auto_deploy.sh
```

### Or run manually

```bash
cd Computer_Receiver
pip install -r requirements.txt
python receiver.py
```

On start it prints the URL it is listening on, e.g. `http://192.168.1.153:8000`.
The GUI title bar shows the same URL.

## Point the phone at it

1. Make sure the phone and computer are on the **same Wi-Fi**.
2. In `Mobile_Application/.env`, set:
   ```
   SERVER_URL=http://<your-computer-ip>:8000
   ```
3. Rebuild/run the app. Record (and optionally capture/analyze a photo), then
   **Send request** to upload the report.

## Notes

- Uploads are saved under `Computer_Receiver/received/` (gitignored) as
  `clip_<timestamp>.mp4` (audio), `clip_<timestamp>.jpg` (photo, image mode),
  `clip_<timestamp>.txt` (transcript), and `clip_<timestamp>_description.txt`.
- Endpoints:
  - `GET /` — status page · `GET /health` — JSON status
  - `POST /upload` — multipart: `audio` (file), `transcript`, `mode`, and in image
    mode `image` (file) + `description`
- Image previews use **Pillow** (and apply EXIF orientation). Without Pillow the
  GUI still works and offers an **Open image** button instead.
- If the phone can't connect: check the IP, that both devices share the Wi-Fi,
  and that your firewall allows incoming connections on port 8000.
