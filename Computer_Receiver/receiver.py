"""
Desktop receiver for the BVI Mutual HRI SLAM mobile app.

Runs a small HTTP server that the phone uploads to (mode, audio clip, transcript,
and — in image mode — the photo plus a GPT-5 description), and a Tkinter GUI to
browse received clips, read the transcript and description, preview the image,
and play the audio.

Flow:
    phone records -> transcribes via OpenAI (and, in image mode, describes the
    photo with GPT-5) -> POSTs everything here -> this GUI shows the mode,
    transcript, image + description, and plays the audio.

Run:
    pip install -r requirements.txt
    python receiver.py

The phone's SERVER_URL (in Mobile_Application/.env) must point at this machine,
e.g. http://192.168.1.153:8000  (same Wi-Fi network).
"""

import datetime
import os
import platform
import socket
import subprocess
import threading

from flask import Flask, request, jsonify

import tkinter as tk
from tkinter import ttk, scrolledtext

# Pillow is optional: if present we show inline image previews; if not, the GUI
# still works and offers an "Open image" button instead.
try:
    from PIL import Image, ImageTk
    HAVE_PIL = True
except Exception:
    HAVE_PIL = False

PORT = 8000
SAVE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "received")
os.makedirs(SAVE_DIR, exist_ok=True)

# Shared state between the server thread and the GUI thread.
# Each record: {"time", "mode", "audio", "image", "transcript", "description"}
records = []
records_lock = threading.Lock()


# ---------------------------------------------------------------------------
# HTTP server (runs in a background thread)
# ---------------------------------------------------------------------------
app = Flask(__name__)


@app.route("/", methods=["GET"])
def index():
    with records_lock:
        count = len(records)
    return (
        "<html><body style='font-family:sans-serif;padding:24px'>"
        "<h2>HRI SLAM Transcription Receiver</h2>"
        "<p>The server is running and reachable. ✅</p>"
        f"<p>Clips received so far: <b>{count}</b></p>"
        "<p>The phone uploads to <code>POST /upload</code>. "
        "Health check: <code>GET /health</code>.</p>"
        "</body></html>"
    )


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/upload", methods=["POST"])
def upload():
    transcript = request.form.get("transcript", "")
    description = request.form.get("description", "")
    mode = request.form.get("mode", "") or "speech"
    audio = request.files.get("audio")
    image = request.files.get("image")

    ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")

    audio_path = ""
    if audio is not None:
        audio_path = os.path.join(SAVE_DIR, f"clip_{stamp}.mp4")
        audio.save(audio_path)

    image_path = ""
    if image is not None:
        image_path = os.path.join(SAVE_DIR, f"clip_{stamp}.jpg")
        image.save(image_path)

    # Keep sidecar text files too, so the data survives a restart.
    with open(os.path.join(SAVE_DIR, f"clip_{stamp}.txt"), "w", encoding="utf-8") as fh:
        fh.write(transcript)
    if description:
        with open(os.path.join(SAVE_DIR, f"clip_{stamp}_description.txt"), "w", encoding="utf-8") as fh:
            fh.write(description)

    record = {
        "time": ts,
        "mode": mode,
        "audio": audio_path,
        "image": image_path,
        "transcript": transcript,
        "description": description,
    }
    with records_lock:
        records.append(record)

    preview = transcript[:60].replace("\n", " ")
    print(
        f"[received] {ts}  mode: {mode}  transcript: {preview!r}  "
        f"audio: {os.path.basename(audio_path)}  "
        f"image: {os.path.basename(image_path) if image_path else '-'}  "
        f"description: {len(description)} chars"
    )

    return jsonify({"status": "ok"})


def run_server():
    # threaded=True so uploads don't block; use_reloader=False so it can live
    # inside a background thread.
    app.run(host="0.0.0.0", port=PORT, threaded=True, use_reloader=False)


def local_ip():
    """Best-effort guess of this machine's LAN IP for display."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


# ---------------------------------------------------------------------------
# GUI (runs on the main thread)
# ---------------------------------------------------------------------------
class ReceiverGui:
    def __init__(self, root):
        self.root = root
        self.play_proc = None
        self.shown_count = 0
        self.selected_index = None
        self.thumb_img = None  # keep a ref so Tk doesn't GC the preview

        root.title("HRI SLAM - Transcription Receiver")
        root.geometry("960x680")
        root.minsize(760, 540)

        # App icon (kept as attributes so Tk doesn't garbage-collect them).
        icon_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "receiver_icon.png"
        )
        self.icon_img = None
        self.icon_small = None
        if os.path.exists(icon_path):
            try:
                self.icon_img = tk.PhotoImage(file=icon_path)
                root.iconphoto(True, self.icon_img)
                # Smaller copy for the header (256px -> ~52px).
                self.icon_small = self.icon_img.subsample(5, 5)
            except Exception:
                self.icon_img = None
                self.icon_small = None

        url = f"http://{local_ip()}:{PORT}"
        header = ttk.Frame(root)
        header.pack(side=tk.TOP, fill=tk.X, padx=12, pady=(12, 6))
        if self.icon_small is not None:
            ttk.Label(header, image=self.icon_small).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Label(
            header,
            text=f"Listening on {url}   (set this as SERVER_URL on the phone)",
            font=("Helvetica", 12, "bold"),
        ).pack(side=tk.LEFT, anchor="w")

        body = ttk.Frame(root)
        body.pack(side=tk.TOP, fill=tk.BOTH, expand=True, padx=12, pady=6)

        # Left: list of received clips
        left = ttk.Frame(body)
        left.pack(side=tk.LEFT, fill=tk.Y)

        ttk.Label(left, text="Received clips").pack(side=tk.TOP, anchor="w")
        self.listbox = tk.Listbox(
            left, width=30, activestyle="dotbox", exportselection=False
        )
        self.listbox.pack(side=tk.TOP, fill=tk.Y, expand=True, pady=(4, 0))
        self.listbox.bind("<<ListboxSelect>>", self.on_select)

        # Right: details (mode, transcript, description, image, controls)
        right = ttk.Frame(body)
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(12, 0))

        self.mode_label = ttk.Label(right, text="Mode: -", font=("Helvetica", 13, "bold"))
        self.mode_label.pack(side=tk.TOP, anchor="w")

        # Audio controls
        controls = ttk.Frame(right)
        controls.pack(side=tk.TOP, fill=tk.X, pady=(6, 8))
        self.play_btn = ttk.Button(controls, text="▶ Play audio", command=self.play_audio)
        self.play_btn.pack(side=tk.LEFT)
        self.stop_btn = ttk.Button(controls, text="■ Stop", command=self.stop_audio)
        self.stop_btn.pack(side=tk.LEFT, padx=(8, 0))
        self.open_img_btn = ttk.Button(controls, text="Open image", command=self.open_image)
        self.open_img_btn.pack(side=tk.LEFT, padx=(8, 0))

        ttk.Label(right, text="Transcript (spoken report)").pack(side=tk.TOP, anchor="w")
        self.transcript_box = scrolledtext.ScrolledText(
            right, wrap=tk.WORD, font=("Helvetica", 13), height=6
        )
        self.transcript_box.pack(side=tk.TOP, fill=tk.X, pady=(4, 8))

        ttk.Label(right, text="Image description (GPT-5)").pack(side=tk.TOP, anchor="w")
        self.description_box = scrolledtext.ScrolledText(
            right, wrap=tk.WORD, font=("Helvetica", 13), height=8
        )
        self.description_box.pack(side=tk.TOP, fill=tk.BOTH, expand=True, pady=(4, 8))

        # Image preview (only populated when Pillow is available and a photo exists)
        self.image_label = ttk.Label(right)
        self.image_label.pack(side=tk.TOP, anchor="w")

        self.status = ttk.Label(root, text="Waiting for the phone to send a clip\u2026")
        self.status.pack(side=tk.BOTTOM, fill=tk.X, padx=12, pady=(0, 10))

        self.refresh()

    def refresh(self):
        """Poll shared state and update the listbox when new clips arrive."""
        with records_lock:
            count = len(records)
            items = [f"{r['time']}  ·  {r.get('mode', 'speech')}" for r in records]

        if count != self.shown_count:
            self.listbox.delete(0, tk.END)
            for label in items:
                self.listbox.insert(tk.END, label)
            self.shown_count = count
            # Auto-select the newest clip and show its transcript.
            newest = count - 1
            self.listbox.selection_clear(0, tk.END)
            self.listbox.selection_set(newest)
            self.listbox.see(newest)
            self.display(newest)
            self.status.config(text=f"{count} clip(s) received")

        self.root.after(800, self.refresh)

    def on_select(self, _event):
        sel = self.listbox.curselection()
        if sel:
            self.display(sel[0])

    def display(self, idx):
        """Show all fields for the record at idx (does not rely on Listbox
        selection state, which Tkinter can clear unexpectedly)."""
        with records_lock:
            rec = records[idx] if 0 <= idx < len(records) else None
        self.selected_index = idx

        self.transcript_box.delete("1.0", tk.END)
        self.description_box.delete("1.0", tk.END)
        self.image_label.config(image="", text="")
        self.thumb_img = None

        if not rec:
            self.mode_label.config(text="Mode: -")
            return

        mode = rec.get("mode", "speech")
        self.mode_label.config(text=f"Mode: {mode}")

        self.transcript_box.insert(tk.END, rec.get("transcript") or "(no transcript)")

        has_image = bool(rec.get("image")) and os.path.exists(rec.get("image"))
        if not has_image:
            self.description_box.insert(tk.END, "(speech only — no image)")
        else:
            self.description_box.insert(
                tk.END, rec.get("description") or "(no description)"
            )
            self.show_thumbnail(rec["image"])

    def show_thumbnail(self, image_path):
        """Render an inline preview if Pillow is available; otherwise hint to
        use the Open image button."""
        if not HAVE_PIL:
            self.image_label.config(
                text="(install Pillow to preview images here — or use 'Open image')"
            )
            return
        try:
            img = Image.open(image_path)
            img.thumbnail((360, 360))
            self.thumb_img = ImageTk.PhotoImage(img)
            self.image_label.config(image=self.thumb_img, text="")
        except Exception:
            self.image_label.config(text="(could not preview image)")

    def open_image(self):
        idx = self.selected_index
        with records_lock:
            rec = records[idx] if (idx is not None and 0 <= idx < len(records)) else None
        if not rec or not rec.get("image") or not os.path.exists(rec.get("image")):
            self.status.config(text="No image for this clip")
            return
        path = rec["image"]
        system = platform.system()
        try:
            if system == "Darwin":
                subprocess.Popen(["open", path])
            elif system == "Windows":
                os.startfile(path)  # type: ignore[attr-defined]
            else:
                subprocess.Popen(["xdg-open", path])
            self.status.config(text=f"Opened {os.path.basename(path)}")
        except Exception:
            self.status.config(text="Could not open the image")

    def play_audio(self):
        idx = self.selected_index
        with records_lock:
            rec = records[idx] if (idx is not None and 0 <= idx < len(records)) else None
        if not rec or not rec["audio"] or not os.path.exists(rec["audio"]):
            self.status.config(text="No audio file for this clip")
            return

        self.stop_audio()
        path = rec["audio"]
        system = platform.system()
        try:
            if system == "Darwin":
                self.play_proc = subprocess.Popen(["afplay", path])
            elif system == "Windows":
                os.startfile(path)  # type: ignore[attr-defined]
            else:
                self.play_proc = subprocess.Popen(
                    ["ffplay", "-nodisp", "-autoexit", path],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
            self.status.config(text=f"Playing {os.path.basename(path)}")
        except FileNotFoundError:
            self.status.config(
                text="No audio player found (install ffmpeg/ffplay on Linux)"
            )

    def stop_audio(self):
        if self.play_proc is not None and self.play_proc.poll() is None:
            self.play_proc.terminate()
        self.play_proc = None


def main():
    server_thread = threading.Thread(target=run_server, daemon=True)
    server_thread.start()

    print(f"Receiver running at http://{local_ip()}:{PORT}")
    print(f"Saving uploads to: {SAVE_DIR}")

    root = tk.Tk()
    ReceiverGui(root)
    root.mainloop()


if __name__ == "__main__":
    main()
