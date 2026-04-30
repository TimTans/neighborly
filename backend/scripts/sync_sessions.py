"""
sync scraper session files to/from supabase storage.

session files contain cloudflare-solved cookies that let the scrapers run
without a human in the loop. you save them locally by running
save_session.py / save_keyfood_session.py, then use this script to sync them
to supabase storage so github actions can download them before scraping.

usage (from the backend/ directory):
    # push local data/*_session.json files to the bucket
    PYTHONPATH=. uv run python scripts/sync_sessions.py upload

    # pull bucket contents into data/ (what github actions does)
    PYTHONPATH=. uv run python scripts/sync_sessions.py download

the bucket is named "scraper-sessions" and should be private. create it once
from the supabase dashboard (storage > new bucket > toggle off "public").
"""

import argparse
import sys
from pathlib import Path

from app.core.supabase import get_supabase

BUCKET = "scraper-sessions"
DATA_DIR = Path(__file__).parent.parent / "data"
SESSION_GLOB = "*_session.json"


def upload() -> int:
    client = get_supabase()
    files = sorted(DATA_DIR.glob(SESSION_GLOB))
    if not files:
        print(f"no {SESSION_GLOB} files in {DATA_DIR}")
        return 1

    for path in files:
        data = path.read_bytes()
        client.storage.from_(BUCKET).upload(
            path=path.name,
            file=data,
            file_options={"content-type": "application/json", "upsert": "true"},
        )
        print(f"uploaded {path.name} ({len(data)} bytes)")
    return 0


def download() -> int:
    client = get_supabase()
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    entries = client.storage.from_(BUCKET).list()
    session_names = [e["name"] for e in entries if e["name"].endswith("_session.json")]
    if not session_names:
        print(f"no session files found in bucket '{BUCKET}'")
        return 1

    for name in session_names:
        data = client.storage.from_(BUCKET).download(name)
        out = DATA_DIR / name
        out.write_bytes(data)
        print(f"downloaded {name} -> {out} ({len(data)} bytes)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["upload", "download"])
    args = parser.parse_args()
    return upload() if args.command == "upload" else download()


if __name__ == "__main__":
    sys.exit(main())
