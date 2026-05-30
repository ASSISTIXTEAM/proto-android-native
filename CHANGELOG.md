# Changelog

All notable changes to the **PROTO Android** open client source are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.6] — 2026-05-30

**versionCode:** 110 · **Tag:** [v1.1.6](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.6)

**Stable** release — fixes crashes and storage issues from **1.1.5**. Recommended for all builds.

### Fixed

- **Launch crashes on Android 11+** — settings and local data moved to protected app storage (`ProtoPersistentStorage`)
- **Corrupted DB / prefs recovery** — automatic rebuild when Room or DataStore files are damaged
- **PROTO Cells stability** — safer background sync, repair, and maintenance loops
- Crashes and edge cases in settings, onboarding, and chat flows

### Improved

- **Cells-P encoding** — 7 data stripes + 1 XOR parity shard (8 total); one missing data shard recovers automatically
- Cells stats UI in Settings (node tier, storage quota)
- Legacy 7-shard mirror blobs still supported as fallback

### Notes

- **Do not use [1.1.5](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5)** for production — marked **unstable** (known crashes).

---

## [1.1.5] — 2026-05-30 · ⚠️ UNSTABLE

**versionCode:** 107 · **Tag:** [v1.1.5](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5)

> **Not recommended.** Known launch crashes on Android 11+ and Cells-related instability.  
> **Use [1.1.6](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.6) instead.**

First public **PROTO Cells** release. Kept in history for reference only.

### Added

- **PROTO Cells** — AES-256-GCM encrypted media mesh (`ProtoCellsManager`, `data/cells/*`)
- Mandatory enrollment, Cells UI, media from 8 KB, background sync
- [docs/PROTO_CELLS.md](docs/PROTO_CELLS.md)

---

## [1.1.2] — 2026-05-30

**versionCode:** 103 · **Tag:** [v1.1.2](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2)

Refinement release: smarter Pulse, offline polish, and chat-list UX.

### Improved

- **Chat Pulse**, **Assistix language**, **Archive UX**, **Offline** polish
- Connectivity advisor, app update UI, WebRTC ICE, API origin fallback

---

## [1.1.1] — 2026-05-30

**versionCode:** 102 · **Tag:** [v1.1.1](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1)

First stable public source release after the early `1.0.99` preview.

---

## [1.0.99] — 2026-05-30

**versionCode:** 99 · Early public preview

- Initial open-source client drop

[1.1.6]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.6
[1.1.5]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5
[1.1.2]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2
[1.1.1]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1
[1.0.99]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.0.99
