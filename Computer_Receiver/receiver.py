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
        "<h2>HRI SLAM Computer Receiver</h2>"
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
# Brand palette (matches the mobile app).
BG = "#EEF0F8"
CARD = "#FFFFFF"
PRIMARY = "#6C5CE7"
PRIMARY_DK = "#4B3FCB"
PRIMARY_TINT = "#E7E3FF"
ACCENT = "#00BFA6"
ACCENT_DK = "#00A38D"
TEXT = "#1B1C2E"
MUTED = "#6A6E8A"
BORDER = "#E2E4F0"
FIELD_BG = "#F6F7FD"

FONT = "Helvetica"
MONO = "Menlo"


class ReceiverGui:
    def __init__(self, root):
        self.root = root
        self.play_proc = None
        self.shown_count = 0
        self.selected_index = None
        self.thumb_img = None  # keep a ref so Tk doesn't GC the preview

        root.title("HRI SLAM - Transcription Receiver")
        root.geometry("1040x720")
        root.minsize(860, 600)
        root.configure(bg=BG)

        self._setup_styles()
        self._load_icon()
        self._build_header()
        self._build_body()

        self.refresh()

    # ---- setup helpers ----------------------------------------------------
    def _setup_styles(self):
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure(
            "Primary.TButton", background=PRIMARY, foreground="white",
            font=(FONT, 11, "bold"), borderwidth=0, focuscolor=PRIMARY,
            padding=(16, 9),
        )
        style.map("Primary.TButton",
                  background=[("active", PRIMARY_DK), ("disabled", "#C7C9DA")])
        style.configure(
            "Accent.TButton", background=ACCENT, foreground="white",
            font=(FONT, 11, "bold"), borderwidth=0, focuscolor=ACCENT,
            padding=(16, 9),
        )
        style.map("Accent.TButton",
                  background=[("active", ACCENT_DK), ("disabled", "#BFE7E0")])
        style.configure(
            "Ghost.TButton", background=CARD, foreground=TEXT,
            font=(FONT, 11), borderwidth=1, bordercolor=BORDER,
            focuscolor=CARD, padding=(16, 9),
        )
        style.map("Ghost.TButton", background=[("active", FIELD_BG)])

    def _load_icon(self):
        icon_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "receiver_icon.png"
        )
        self.icon_img = None
        self.icon_small = None
        if os.path.exists(icon_path):
            try:
                self.icon_img = tk.PhotoImage(file=icon_path)
                self.root.iconphoto(True, self.icon_img)
                self.icon_small = self.icon_img.subsample(6, 6)  # ~42px
            except Exception:
                self.icon_img = None
                self.icon_small = None

    def _card(self, parent):
        return tk.Frame(parent, bg=CARD, highlightbackground=BORDER,
                        highlightthickness=1, bd=0)

    def _build_header(self):
        url = f"http://{local_ip()}:{PORT}"
        header = self._card(self.root)
        header.pack(side=tk.TOP, fill=tk.X, padx=16, pady=(16, 8))
        inner = tk.Frame(header, bg=CARD)
        inner.pack(fill=tk.X, padx=16, pady=14)

        if self.icon_small is not None:
            tk.Label(inner, image=self.icon_small, bg=CARD).pack(side=tk.LEFT, padx=(0, 14))

        titles = tk.Frame(inner, bg=CARD)
        titles.pack(side=tk.LEFT, anchor="w")
        tk.Label(titles, text="HRI SLAM Receiver", bg=CARD, fg=TEXT,
                 font=(FONT, 17, "bold")).pack(anchor="w")
        tk.Label(titles, text=f"Server running at {url}  ·  set this as SERVER_URL on the phone",
                 bg=CARD, fg=MUTED, font=(FONT, 11)).pack(anchor="w", pady=(2, 0))

        tk.Label(inner, text="● Server running", bg=CARD, fg=ACCENT_DK,
                 font=(FONT, 11, "bold")).pack(side=tk.RIGHT)

    def _build_body(self):
        body = tk.Frame(self.root, bg=BG)
        body.pack(side=tk.TOP, fill=tk.BOTH, expand=True, padx=16, pady=(0, 8))
        body.grid_columnconfigure(0, weight=0, minsize=250)
        body.grid_columnconfigure(1, weight=1)
        body.grid_rowconfigure(0, weight=1)

        # ----- Left: reports list -----
        left = self._card(body)
        left.grid(row=0, column=0, sticky="nsew", padx=(0, 12))
        left_in = tk.Frame(left, bg=CARD)
        left_in.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)

        head = tk.Frame(left_in, bg=CARD)
        head.pack(fill=tk.X)
        tk.Label(head, text="REPORTS", bg=CARD, fg=MUTED,
                 font=(FONT, 10, "bold")).pack(side=tk.LEFT)
        self.count_label = tk.Label(head, text="0", bg=PRIMARY_TINT, fg=PRIMARY_DK,
                                    font=(FONT, 10, "bold"), padx=8)
        self.count_label.pack(side=tk.RIGHT)

        self.listbox = tk.Listbox(
            left_in, activestyle="none", exportselection=False,
            bg=CARD, fg=TEXT, font=(FONT, 12), bd=0, highlightthickness=0,
            selectbackground=PRIMARY_TINT, selectforeground=PRIMARY_DK,
            width=24,
        )
        self.listbox.pack(fill=tk.BOTH, expand=True, pady=(10, 0))
        self.listbox.bind("<<ListboxSelect>>", self.on_select)

        # ----- Right: detail -----
        right = self._card(body)
        right.grid(row=0, column=1, sticky="nsew")
        right_in = tk.Frame(right, bg=CARD)
        right_in.pack(fill=tk.BOTH, expand=True, padx=16, pady=16)

        # Top bar: mode badge + controls
        topbar = tk.Frame(right_in, bg=CARD)
        topbar.pack(fill=tk.X)
        self.mode_badge = tk.Label(topbar, text="—", bg=PRIMARY_TINT, fg=PRIMARY_DK,
                                   font=(FONT, 11, "bold"), padx=12, pady=5)
        self.mode_badge.pack(side=tk.LEFT)

        btns = tk.Frame(topbar, bg=CARD)
        btns.pack(side=tk.RIGHT)
        self.play_btn = ttk.Button(btns, text="▶  Play", style="Accent.TButton",
                                   command=self.play_audio)
        self.play_btn.pack(side=tk.LEFT)
        self.stop_btn = ttk.Button(btns, text="■  Stop", style="Ghost.TButton",
                                   command=self.stop_audio)
        self.stop_btn.pack(side=tk.LEFT, padx=(8, 0))
        self.open_img_btn = ttk.Button(btns, text="Open image", style="Ghost.TButton",
                                       command=self.open_image)
        self.open_img_btn.pack(side=tk.LEFT, padx=(8, 0))

        # Content: text column + image column
        content = tk.Frame(right_in, bg=CARD)
        content.pack(fill=tk.BOTH, expand=True, pady=(14, 0))
        content.grid_columnconfigure(0, weight=1)
        content.grid_columnconfigure(1, weight=0, minsize=280)
        content.grid_rowconfigure(0, weight=1)

        textcol = tk.Frame(content, bg=CARD)
        textcol.grid(row=0, column=0, sticky="nsew", padx=(0, 16))

        tk.Label(textcol, text="SPOKEN REPORT", bg=CARD, fg=MUTED,
                 font=(FONT, 10, "bold")).pack(anchor="w")
        self.transcript_box = self._make_text(textcol, height=5, font=(FONT, 13))
        self.transcript_box.pack(fill=tk.X, pady=(6, 14))

        tk.Label(textcol, text="IMAGE DESCRIPTION", bg=CARD, fg=MUTED,
                 font=(FONT, 10, "bold")).pack(anchor="w")
        self.description_box = self._make_text(textcol, height=10, font=(MONO, 11))
        self.description_box.pack(fill=tk.BOTH, expand=True, pady=(6, 0))

        imgcol = tk.Frame(content, bg=CARD)
        imgcol.grid(row=0, column=1, sticky="nsew")
        tk.Label(imgcol, text="PHOTO", bg=CARD, fg=MUTED,
                 font=(FONT, 10, "bold")).pack(anchor="w")
        self.image_label = tk.Label(imgcol, bg=FIELD_BG, fg=MUTED, bd=0,
                                    highlightbackground=BORDER, highlightthickness=1,
                                    width=34, height=18, wraplength=240,
                                    font=(FONT, 11))
        self.image_label.pack(fill=tk.BOTH, expand=True, pady=(6, 0))

        # Status bar
        self.status = tk.Label(self.root, text="Waiting for the phone to send a report…",
                               bg=BG, fg=MUTED, font=(FONT, 11), anchor="w")
        self.status.pack(side=tk.BOTTOM, fill=tk.X, padx=18, pady=(0, 12))

    def _make_text(self, parent, height, font):
        return scrolledtext.ScrolledText(
            parent, wrap=tk.WORD, height=height, font=font,
            bg=FIELD_BG, fg=TEXT, bd=0, relief=tk.FLAT,
            highlightbackground=BORDER, highlightthickness=1,
            padx=12, pady=10, insertbackground=TEXT,
        )

    # ---- data / actions ---------------------------------------------------
    def refresh(self):
        """Poll shared state and update the listbox when new reports arrive."""
        with records_lock:
            count = len(records)
            items = [self._list_label(r) for r in records]

        if count != self.shown_count:
            self.listbox.delete(0, tk.END)
            for label in items:
                self.listbox.insert(tk.END, label)
            self.shown_count = count
            self.count_label.config(text=str(count))
            # Auto-select the newest report.
            newest = count - 1
            self.listbox.selection_clear(0, tk.END)
            self.listbox.selection_set(newest)
            self.listbox.see(newest)
            self.display(newest)
            self.status.config(text=f"{count} report(s) received")

        self.root.after(800, self.refresh)

    @staticmethod
    def _list_label(rec):
        mode = rec.get("mode", "speech")
        icon = "🖼" if "image" in mode else "🎤"
        t = rec.get("time", "")
        clock = t.split(" ")[1] if " " in t else t
        return f"  {icon}  {clock}"

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
            self.mode_badge.config(text="—", bg=PRIMARY_TINT, fg=PRIMARY_DK)
            self.open_img_btn.pack_forget()
            return

        mode = rec.get("mode", "speech")
        if "image" in mode:
            self.mode_badge.config(text="  SPEECH + IMAGE  ", bg=ACCENT, fg="white")
        else:
            self.mode_badge.config(text="  SPEECH  ", bg=PRIMARY, fg="white")

        self.transcript_box.insert(tk.END, rec.get("transcript") or "(no transcript)")

        has_image = bool(rec.get("image")) and os.path.exists(rec.get("image"))
        if not has_image:
            # Speech-only: hide the image-related controls/preview.
            self.open_img_btn.pack_forget()
            self.description_box.insert(tk.END, "(speech only — no image)")
            self.image_label.config(text="No photo for this report")
        else:
            self.open_img_btn.pack(side=tk.LEFT, padx=(8, 0))
            self.description_box.insert(
                tk.END, rec.get("description") or "(no description)"
            )
            self.show_thumbnail(rec["image"])

    def show_thumbnail(self, image_path):
        """Render an inline preview if Pillow is available; otherwise hint to
        use the Open image button."""
        if not HAVE_PIL:
            self.image_label.config(
                text="Install Pillow to preview images here,\nor use 'Open image'."
            )
            return
        try:
            img = Image.open(image_path)
            # Honor the photo's EXIF orientation (otherwise it can appear
            # rotated/flipped since PIL doesn't apply it automatically).
            img = ImageOps.exif_transpose(img)
            img.thumbnail((260, 320))
            self.thumb_img = ImageTk.PhotoImage(img)
            self.image_label.config(image=self.thumb_img, text="")
        except Exception:
            self.image_label.config(text="(could not preview image)")

    def open_image(self):
        idx = self.selected_index
        with records_lock:
            rec = records[idx] if (idx is not None and 0 <= idx < len(records)) else None
        if not rec or not rec.get("image") or not os.path.exists(rec.get("image")):
            self.status.config(text="No image for this report")
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
            self.status.config(text="No audio file for this report")
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
