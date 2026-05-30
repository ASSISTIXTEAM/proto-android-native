# Changelog

All notable changes to the **PROTO Android** open client source are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.8] — 2026-05-30

**versionCode:** 112 · **Tag:** [v1.1.8](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.8)

**Stable** — Cells P2P, adaptive striping, health check UI.

### Added

- **Cells P2P** — shard transfer directly between chat members via WebRTC; server relay is fallback only
- **Adaptive striping** — small blobs use **3+1** parity (< 48 KB cipher), large files **7+1** (`ProtoCellsConfig.planForCipher`)
- **Health check** screen in Settings — Cells sync, Whisper, crash log buffer, build info
- **Anonymous crash reports** toggle (stack traces only)
- Multi-transfer progress UI, composer offline queue hints

### Improved

- Offline Cells hint moved to composer only — cleaner chat list
- Faster media downloads when peers are online in the same chat

---

## [1.1.6] — 2026-05-30

**versionCode:** 110 · **Tag:** [v1.1.6](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.6)

**Stable** — fixes crashes from **1.1.5**. Protected storage, DB recovery, Cells-P XOR parity.

### Fixed

- Launch crashes on Android 11+, corrupted DB/prefs recovery, Cells stability

---

## [1.1.5] — 2026-05-30 · ⚠️ UNSTABLE

**versionCode:** 107 · **Tag:** [v1.1.5](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5)

> **Not recommended.** Known crashes on Android 11+. Use **1.1.6+** instead.

First public **PROTO Cells** release.

---

## [1.1.2] — 2026-05-30

**versionCode:** 103 · Pulse, offline polish, archive UX.

---

## [1.1.1] — 2026-05-30

**versionCode:** 102 · First stable public source release.

---

## [1.0.99] — 2026-05-30

**versionCode:** 99 · Early public preview.

[1.1.8]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.8
[1.1.6]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.6
[1.1.5]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5
[1.1.2]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2
[1.1.1]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1
[1.0.99]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.0.99
