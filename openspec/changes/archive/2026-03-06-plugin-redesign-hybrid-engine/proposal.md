# Proposal: Plugin Redesign — Hybrid Engine, Version Support, Workflow UX

## Summary

The OpenSpec IntelliJ plugin (v0.1.0) workflow actions (validate, propose, apply, archive) silently fail because they shell out to the `openspec` CLI with no detection, fallback, or proper output surfacing. This redesign introduces a hybrid engine that provides built-in validation and scaffolding that works without CLI, enhanced behavior when CLI is available, version-aware behavior, and proper workflow UX.

## Motivation

- CLI-only actions silently fail when CLI is not installed or not on PATH
- No built-in validation — all validation depends on external CLI
- No fallback scaffolding — proposing/initializing requires CLI
- No visibility into CLI status or command output
- Version-specific behavior not supported

## Impact

- All action classes modified for hybrid CLI/built-in behavior
- New settings infrastructure for version override, CLI path, and strictness
- New validation engine with built-in rules matching OpenSpec v1.2.0
- New scaffolding service with version-aware templates
- Tool window enhanced with CLI status indicator, context menus, and console tab
