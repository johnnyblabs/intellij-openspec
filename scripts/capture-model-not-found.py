#!/usr/bin/env python3
"""Capture each Direct API provider's live "model not found" response as contract fixtures for DirectApiErrors.

A saved model that the provider has retired fails generation with a 404 whose body names the model. This sends the
plugin's own generation request (same endpoint and auth headers as DirectApiService) with a model id that no longer
exists, and records, under src/test/resources/fixtures/ai/<provider>/errors/, in DirectApiErrorsContractTest's
naming (the status is the three-digit token in the name):

    <endpoint>-404-model-not-found-retired.{json,headers}   a model the provider has retired
    <endpoint>-404-model-not-found-unknown.{json,headers}   a model id that never existed

<endpoint> is messages (Claude), chat-completions (OpenAI) or generatecontent (Gemini). Only the content-type and
request-id headers are kept. A response that isn't a 404 is not saved, because the name would misstate it.

Keys are read from the environment and are never printed or written; each body is checked for the key before it is
saved, and a run that doesn't come back as an error is not saved. A provider whose key is unset is skipped.

    ANTHROPIC_API_KEY  (optional ANTHROPIC_WORKSPACE_ID, needed for a key not scoped to a workspace)
    OPENAI_API_KEY     (optional OPENAI_ORGANIZATION, OPENAI_PROJECT)
    GEMINI_API_KEY

Usage: ANTHROPIC_API_KEY=... OPENAI_API_KEY=... GEMINI_API_KEY=... scripts/capture-model-not-found.py [provider...]
       provider is claude, openai or gemini; default all three. Each request asks for at most 16 output tokens.
"""
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FIXTURES = REPO / "src/test/resources/fixtures/ai"
ANTHROPIC_VERSION = "2023-06-01"  # DirectApiService.ANTHROPIC_VERSION
NEVER_EXISTED = "openspec-probe-no-such-model"

# provider -> (fixture dir, key env var, retired model id). Each retired id was a real model the provider has since
# shut down; if one ever answers 200 again the script stops instead of saving a non-error.
PROVIDERS = {
    "claude": ("claude", "messages", "ANTHROPIC_API_KEY", "claude-3-opus-20240229"),
    "openai": ("openai", "chat-completions", "OPENAI_API_KEY", "gpt-4-32k"),
    "gemini": ("gemini", "generatecontent", "GEMINI_API_KEY", "gemini-1.5-pro"),
}
KEPT_HEADERS = ("content-type", "request-id", "x-request-id")


def request(provider: str, key: str, model: str) -> urllib.request.Request:
    prompt = "Reply with one word: ready"
    if provider == "claude":
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {key}",
                   "anthropic-version": ANTHROPIC_VERSION}
        workspace = os.environ.get("ANTHROPIC_WORKSPACE_ID")
        if workspace:
            headers["anthropic-workspace-id"] = workspace
        body = {"model": model, "max_tokens": 16, "messages": [{"role": "user", "content": prompt}]}
        return urllib.request.Request("https://api.anthropic.com/v1/messages", json.dumps(body).encode(),
                                      headers, method="POST")
    if provider == "openai":
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {key}"}
        for env, header in (("OPENAI_ORGANIZATION", "OpenAI-Organization"), ("OPENAI_PROJECT", "OpenAI-Project")):
            if os.environ.get(env):
                headers[header] = os.environ[env]
        body = {"model": model, "max_completion_tokens": 16, "messages": [{"role": "user", "content": prompt}]}
        return urllib.request.Request("https://api.openai.com/v1/chat/completions", json.dumps(body).encode(),
                                      headers, method="POST")
    body = {"contents": [{"parts": [{"text": prompt}]}], "generationConfig": {"maxOutputTokens": 16}, "store": False}
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    return urllib.request.Request(url, json.dumps(body).encode(),
                                  {"Content-Type": "application/json", "x-goog-api-key": key}, method="POST")


def call(req: urllib.request.Request) -> tuple[int, str, str]:
    """The status, the body, and the kept headers as `name: value` lines."""
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            status, body, headers = response.status, response.read().decode(), response.headers
    except urllib.error.HTTPError as e:
        status, body, headers = e.code, e.read().decode(), e.headers
    kept = [f"{name}: {headers[name]}" for name in KEPT_HEADERS if headers.get(name) is not None]
    return status, body, "\n".join(kept) + "\n"


def capture(provider: str) -> bool:
    folder, endpoint, key_env, retired = PROVIDERS[provider]
    key = os.environ.get(key_env)
    if not key:
        print(f"{provider}: {key_env} not set, skipped")
        return True
    out = FIXTURES / folder / "errors"
    out.mkdir(parents=True, exist_ok=True)
    ok = True
    for suffix, model in (("retired", retired), ("unknown", NEVER_EXISTED)):
        case = f"{endpoint}-404-model-not-found-{suffix}"
        status, body, headers = call(request(provider, key, model))
        if key in body or key in headers:
            sys.exit(f"{provider} {case}: the response contains the API key; nothing saved")
        if status != 404:
            print(f"{provider} {case}: {model} answered {status}, not 404; nothing saved")
            ok = False
            continue
        try:
            body = json.dumps(json.loads(body), indent=2) + "\n"
        except ValueError:
            pass  # keep a non-JSON body exactly as sent
        (out / f"{case}.json").write_text(body)
        (out / f"{case}.headers").write_text(headers)
        print(f"{provider} {case}: {model} -> {status}, {len(body)} bytes")
    return ok


def main():
    chosen = sys.argv[1:] or list(PROVIDERS)
    unknown = [p for p in chosen if p not in PROVIDERS]
    if unknown:
        sys.exit(f"unknown provider(s): {', '.join(unknown)}; choose from {', '.join(PROVIDERS)}")
    results = [capture(p) for p in chosen]
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
