# Computer Receiver

A small desktop app that receives speech clips and transcripts from the phone
and lets you **play the audio** and **read the transcript** in a simple GUI.

It runs two things in one process:
- an HTTP server (Flask) the phone uploads to, and
- a Tkinter GUI to browse/play received clips.

## Data flow

```
phone records audio
   -> phone transcribes via OpenAI
   -> phone POSTs audio + transcript to this receiver (/upload)
   -> GUI shows the transcript and plays the audio
```

## Requirements

- Python 3.9+ (Tkinter ships with standard CPython installers)
- `pip install -r requirements.txt`
- Audio playback:
  - macOS: uses the built-in `afplay` (no install)
  - Windows: opens the file with the default player
  - Linux: uses `ffplay` (install `ffmpeg`)

## Run

### Easiest: the deploy script

`auto_deploy.sh` creates a local virtualenv (`.venv`), installs Flask, and starts
the receiver. Make it executable once, then run it:

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
3. Rebuild/run the app. On the **Confirm request** screen, tap **Send request**
   to upload the clip + transcript.

## Notes

- Uploads are saved under `Computer_Receiver/received/` (gitignored) as
  `clip_<timestamp>.mp4` plus a matching `.txt` transcript.
- Endpoints: `POST /upload` (form fields `audio` file + `transcript` text) and
  `GET /health`.
- If the phone can't connect: check the IP, that both devices share the Wi-Fi,
  and that your firewall allows incoming connections on port 8000.
