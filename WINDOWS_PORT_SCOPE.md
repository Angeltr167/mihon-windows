# Mihon Windows Port — Scope Decisions

This file records owner scope decisions that constrain the authoritative Windows port roadmap.

## Current product scope

- The Windows port is currently a **personal-use project**.
- The objective is a fully functional native Windows build for the owner's own use.
- Public/commercial distribution is **out of scope for now**.

## Explicitly deferred

The following are not requirements for the current P0–P13 implementation unless the owner later changes scope:

- Windows code-signing certificates
- Microsoft Store registration/submission
- commercial licensing or license-key systems
- paid distribution infrastructure
- publisher identity/EV certificate work
- reputation-building/signing work solely to suppress SmartScreen for public distribution

## Packaging implication

Phase 12 should still produce usable Windows artifacts (for example EXE/MSI or the most appropriate Compose Desktop native distribution) and Windows CI/package verification. Those artifacts may remain unsigned for personal use.

An unsigned build may trigger Windows/SmartScreen warnings on some machines; that is acceptable for the current private-use scope and is not a release blocker.

## Scope-change rule

Do not introduce signing, store, commercial-license, or public-release work unless the owner explicitly asks to change this decision. If public distribution becomes a goal later, treat that as a separate release-hardening workstream rather than a prerequisite for the personal-use Windows port.
