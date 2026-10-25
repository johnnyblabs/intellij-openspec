#!/usr/bin/env python3
"""Capture live Gemini generateContent responses as contract fixtures for DirectApiService.parseGeminiResponse.

Sends the request the plugin sends (DirectApiService.buildGeminiRequest: POST
/v1beta/models/<model>:generateContent, header x-goog-api-key, body {contents:[{parts:[{text}]}],
generationConfig:{maxOutputTokens}}) and records each raw response body under
src/test/resources/fixtures/ai/gemini/live/:

    generatecontent-text.json         a normal reply at the plugin's maxOutputTokens (16000); Gemini 3.x thinks
                                      by default, so this carries thought signatures
    generatecontent-max-tokens.json   a reply cut off by a tiny maxOutputTokens, where thinking can use the whole
                                      budget before any text is written

The key is read from $GEMINI_API_KEY and is never printed or written; each response is checked for it before it
is saved. A blocked-prompt response is not captured: there is no benign way to trigger one, so that case stays a
derived variant in DirectApiResponseContractTest.

Usage: GEMINI_API_KEY=... scripts/capture-gemini-generatecontent.py [model] [case-file]
    model      default gemini-3.8-flash, the plugin's default Gemini model
    case-file  capture only that case, e.g. generatecontent-max-tokens.json (when one case needs another model)
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "src/test/resources/fixtures/ai/gemini/live"
URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

CASES = {
    "generatecontent-text.json": ("Reply with exactly one word: ready", 16000),
    "generatecontent-max-tokens.json": ("Explain, step by step, why the sky looks blue during the day.", 16),
}


def call(key: str, model: str, prompt: str, max_tokens: int) -> str:
    body = {"contents": [{"parts": [{"text": prompt}]}], "generationConfig": {"maxOutputTokens": max_tokens}}
    request = urllib.request.Request(URL.format(model=model), data=json.dumps(body).encode(), method="POST",
                                     headers={"Content-Type": "application/json", "x-goog-api-key": key})
    for attempt in range(1, 5):
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read().decode()
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:500]
            if e.code == 503 and attempt < 4:
                # "This model is currently experiencing high demand": transient, so wait and try again.
                print(f"HTTP 503 for {model}, attempt {attempt}; retrying in {15 * attempt}s", file=sys.stderr)
                time.sleep(15 * attempt)
                continue
            sys.exit(f"HTTP {e.code} for {model}: {detail}")
        except TimeoutError:
            if attempt < 4:
                print(f"timed out for {model}, attempt {attempt}; retrying in {15 * attempt}s", file=sys.stderr)
                time.sleep(15 * attempt)
                continue
            sys.exit(f"{model} timed out on every attempt; try again later")


def main():
    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        sys.exit("Set GEMINI_API_KEY")
    model = sys.argv[1] if len(sys.argv) > 1 else "gemini-3.8-flash"
    OUT.mkdir(parents=True, exist_ok=True)
    only = sys.argv[2] if len(sys.argv) > 2 else None
    for name, (prompt, max_tokens) in CASES.items():
        if only and name != only:
            continue
        text = call(key, model, prompt, max_tokens)
        if key in text:
            sys.exit(f"refusing to save {name}: the response contains the API key")
        pretty = json.dumps(json.loads(text), indent=2) + "\n"
        (OUT / name).write_text(pretty)
        print(f"wrote {OUT / name}")


if __name__ == "__main__":
    main()
