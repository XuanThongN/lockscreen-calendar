#!/data/data/com.termux/files/usr/bin/env python3
"""
LockCal Termux Live Companion
Fetches your Google / iCal calendars and displays all upcoming events
directly on your Android lock screen via termux-notification.
"""

import sys
import os
import json
import time
import urllib.request
import re
from datetime import datetime, timezone, timedelta
from subprocess import run, PIPE

CONFIG_FILE = os.path.expanduser("~/.lockcal.json")

def load_config():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r") as f:
                return json.load(f)
        except Exception:
            pass
    return {"calendars": [], "sync_interval_minutes": 30}

def save_config(cfg):
    with open(CONFIG_FILE, "w") as f:
        json.dump(cfg, f, indent=2)

def unfold_lines(text):
    lines = text.splitlines()
    unfolded = []
    current = None
    for line in lines:
        if line.startswith((" ", "\t")):
            if current is not None:
                current += line[1:]
        else:
            if current is not None:
                unfolded.append(current)
            current = line
    if current is not None:
        unfolded.append(current)
    return unfolded

def parse_ics_date(val):
    val = val.strip()
    try:
        if val.endswith("Z"):
            dt = datetime.strptime(val, "%Y%m%dT%H%M%SZ").replace(tzinfo=timezone.utc)
            return dt.astimezone()
        elif "T" in val:
            return datetime.strptime(val, "%Y%m%dT%H%M%S").astimezone()
        else:
            return datetime.strptime(val, "%Y%m%d").astimezone()
    except Exception:
        return datetime.now().astimezone()

def unescape_val(val):
    return val.replace(r"\n", " ").replace(r"\,", ",").replace(r"\;", ";").replace(r"\\", "\\").strip()

def fetch_events(url, cal_name):
    if url.startswith("webcal://"):
        url = "https://" + url[9:]
    req = urllib.request.Request(url, headers={"User-Agent": "LockCal-Termux/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        content = resp.read().decode("utf-8", errors="ignore")

    unfolded = unfold_lines(content)
    events = []
    in_event = False
    cur = {}

    for line in unfolded:
        line = line.strip()
        if line.upper() == "BEGIN:VEVENT":
            in_event = True
            cur = {"cal": cal_name}
            continue
        if line.upper() == "END:VEVENT":
            if in_event and "dtstart" in cur:
                events.append(cur)
            in_event = False
            continue
        if not in_event or ":" not in line:
            continue

        k, v = line.split(":", 1)
        prop = k.split(";")[0].upper().strip()
        if prop == "SUMMARY":
            cur["summary"] = unescape_val(v)
        elif prop == "DTSTART":
            cur["dtstart"] = parse_ics_date(v)
            cur["all_day"] = ";VALUE=DATE" in k.upper() or len(v.strip()) == 8
        elif prop == "DTEND":
            cur["dtend"] = parse_ics_date(v)
        elif prop == "LOCATION":
            cur["location"] = unescape_val(v)

    return events

def sync_all():
    cfg = load_config()
    cals = cfg.get("calendars", [])
    if not cals:
        print("No calendars configured! Add one with:")
        print("  python3 lockcal.py add \"My Calendar\" \"https://...ical_url...\"")
        return []

    all_events = []
    now = datetime.now().astimezone()
    limit = now + timedelta(days=14)

    for cal in cals:
        name = cal.get("name", "Calendar")
        url = cal.get("url", "")
        try:
            print(f"Fetching '{name}'...")
            evs = fetch_events(url, name)
            for e in evs:
                if e["dtstart"] <= limit and e.get("dtend", e["dtstart"]) >= (now - timedelta(hours=2)):
                    all_events.append(e)
            print(f"  -> Found {len(evs)} events")
        except Exception as err:
            print(f"  -> Failed to fetch '{name}': {err}")

    all_events.sort(key=lambda x: x["dtstart"])
    return all_events

def format_event_line(e):
    dt = e["dtstart"]
    now = datetime.now().astimezone()
    is_today = dt.date() == now.date()
    is_tomorrow = dt.date() == (now + timedelta(days=1)).date()

    if is_today:
        day_str = "Today"
    elif is_tomorrow:
        day_str = "Tmrw"
    else:
        day_str = dt.strftime("%a %b %d")

    if e.get("all_day"):
        time_str = "All Day"
    else:
        time_str = dt.strftime("%I:%M %p").lstrip("0")

    summary = e.get("summary", "(No Title)")
    return f"[{day_str} {time_str}] {summary} ({e['cal']})"

def post_lockscreen_notification(events):
    if not events:
        title = "LockCal: No Upcoming Events"
        content = "All calendars up to date. No events scheduled."
    else:
        title = f"Calendar ({len(events)} upcoming events)"
        preview_lines = [format_event_line(e) for e in events[:5]]
        content = "\n".join(preview_lines)

    cmd = [
        "termux-notification",
        "-i", "lockcal_notif",
        "-t", title,
        "-c", content,
        "--ongoing",
        "--priority", "high",
        "--alert-once"
    ]

    try:
        run(cmd, check=True)
        print("Successfully posted notification to lock screen!")
    except Exception as e:
        print(f"Failed to post notification: {e}")

def main():
    if len(sys.argv) < 2:
        print("LockCal Termux Companion")
        print("Usage:")
        print("  python3 lockcal.py sync          # Sync now & update lockscreen")
        print("  python3 lockcal.py add <name> <url> # Add calendar iCal URL")
        print("  python3 lockcal.py list          # List configured calendars")
        print("  python3 lockcal.py daemon        # Sync continuously in background")
        return

    action = sys.argv[1].lower()

    if action == "add":
        if len(sys.argv) < 4:
            print("Error: name and url required. Example:")
            print("  python3 lockcal.py add \"Work\" \"https://calendar.google.com/calendar/ical/...\"")
            return
        name = sys.argv[2]
        url = sys.argv[3]
        cfg = load_config()
        cfg.setdefault("calendars", []).append({"name": name, "url": url})
        save_config(cfg)
        print(f"Added calendar '{name}'. Running initial sync...")
        events = sync_all()
        post_lockscreen_notification(events)

    elif action == "list":
        cfg = load_config()
        cals = cfg.get("calendars", [])
        if not cals:
            print("No calendars added yet.")
        else:
            print(f"{len(cals)} configured calendar(s):")
            for i, c in enumerate(cals, 1):
                print(f"{i}. {c.get('name')}: {c.get('url')[:60]}...")

    elif action == "sync":
        events = sync_all()
        post_lockscreen_notification(events)
        print("\nUpcoming schedule:")
        for e in events[:10]:
            print(" • " + format_event_line(e))

    elif action == "daemon":
        cfg = load_config()
        interval = cfg.get("sync_interval_minutes", 30) * 60
        print(f"Starting LockCal daemon (updating every {interval//60} mins)... Press Ctrl+C to stop.")
        while True:
            try:
                events = sync_all()
                post_lockscreen_notification(events)
            except Exception as e:
                print(f"Error during sync: {e}")
            time.sleep(interval)

    else:
        print(f"Unknown command: {action}")

if __name__ == "__main__":
    main()
