#!/usr/bin/env bash
#
# auto_deploy.sh
# One-command setup + run for the desktop transcription receiver.
# Creates a local virtual environment, installs dependencies, and starts
# receiver.py (HTTP server + Tkinter GUI).
#
# It specifically picks a Python that has Tkinter (Tk) support, because the
# GUI needs it. Some Pythons (e.g. Homebrew's by default) ship without Tk.
#
# Usage:
#   chmod +x auto_deploy.sh   # once, to make it executable
#   ./auto_deploy.sh
#
set -euo pipefail

# Always run from this script's own directory, so it works from anywhere.
cd "$(dirname "$0")"

# True if the given interpreter can import tkinter.
has_tk() { "$1" -c "import tkinter" >/dev/null 2>&1; }

# Find a base interpreter that actually has Tk support.
BASE_PY=""
for cand in python3 python python3.12 python3.11 python3.10 \
            /opt/homebrew/bin/python3 /usr/local/bin/python3 /usr/bin/python3; do
  if command -v "$cand" >/dev/null 2>&1 && has_tk "$cand"; then
    BASE_PY="$(command -v "$cand")"
    break
  fi
done

if [ -z "$BASE_PY" ]; then
  echo "Error: could not find a Python 3 with Tkinter (Tk) support." >&2
  echo "The GUI needs Tk. Fix it with one of:" >&2
  echo "  - macOS (Homebrew):   brew install python-tk" >&2
  echo "  - or use Anaconda / the python.org installer (both include Tk)." >&2
  echo "Then re-run ./auto_deploy.sh" >&2
  exit 1
fi

echo "Using Python: $BASE_PY"

# (Re)create the virtualenv if it's missing or its Python lacks Tk
# (e.g. a previous run built it from a Tk-less interpreter).
if [ ! -d ".venv" ] || ! has_tk ".venv/bin/python"; then
  echo "Creating virtual environment (.venv)..."
  rm -rf .venv
  "$BASE_PY" -m venv .venv
fi

# Activate it.
# shellcheck disable=SC1091
source .venv/bin/activate

# Install/upgrade dependencies.
echo "Installing dependencies..."
pip install --upgrade pip >/dev/null
pip install -r requirements.txt

# Launch the receiver (prints the URL to use, opens the GUI window).
echo "Starting the receiver..."
python receiver.py
