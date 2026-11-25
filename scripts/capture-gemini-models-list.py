#!/usr/bin/env python3
"""Capture Gemini's live model list as a contract fixture for the model-not-listed hint.

Sends the request the plugin sends (DirectApiService.buildListModelsRequest for Gemini: GET
/v1beta/models?pageSize=1000 with header x-goog-api-key) and writes the raw response, untrimmed, to
src/test/resources/fixtures/ai/gemini/live/models-list.json. The full list is kept, because the hint's tests check
membership: an alias such as gemini-flash-latest must be found in it.

The key is read from $GEMINI_API_KEY and is never printed or written; the response is checked for it before it is
saved. The script refuses to save a response that carries nextPageToken, since that would be a partial list.

Usage: GEMINI_API_KEY=... scripts/capture-gemini-models-list.py
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

URL = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "src/test/resources/fixtures/ai/gemini/live/models-list.json"


def main():
    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        sys.exit("GEMINI_API_KEY is not set")
    request = urllib.request.Request(URL, headers={"x-goog-api-key": key})
    with urllib.request.urlopen(request, timeout=60) as response:
        body = response.read().decode("utf-8")
    if key in body:
        sys.exit("the response contains the key; not saved")
    parsed = json.loads(body)
    if parsed.get("nextPageToken"):
        sys.exit("the response is a partial page (nextPageToken); not saved")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(parsed, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(REPO)}: {len(parsed.get('models', []))} models")


if __name__ == "__main__":
    main()
