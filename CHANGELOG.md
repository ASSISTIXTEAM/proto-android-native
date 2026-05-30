# Changelog

All notable changes to the **PROTO Android** open client source are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.5] — 2026-05-30

**versionCode:** 107 · **Tag:** [v1.1.5](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5)

**PROTO Cells** — mandatory encrypted shard mesh for media. See [docs/PROTO_CELLS.md](docs/PROTO_CELLS.md).

### Added

- **PROTO Cells** — AES-256-GCM encrypted media split into 7 gzip-compressed shards, distributed across devices (`ProtoCellsManager`, `data/cells/*`)
- **Mandatory enrollment** — every signed-in user auto-volunteers (768 MB default quota)
- **Cells UI** — dedicated explainer screen in onboarding and Settings (`ProtoCellsScreen`)
- **Media threshold** — blobs from **8 KB** enter the Cells mesh automatically
- **Background sync** — `syncMyHolds`, repair, heartbeat every 2 minutes
- **What's New 1.1.5** dialog

### Improved

- Smarter mesh for small groups (triple replication, repair from local copy)
- `ProtoMediaResolver` falls back to Cells when CDN relay expires
- API client: full `/api/cells.php` action surface in `ProtoApi.kt`

### Documentation

- [docs/PROTO_CELLS.md](docs/PROTO_CELLS.md) — architecture, crypto, lifecycle, code map (RU + EN summary)

---

## [1.1.2] — 2026-05-30

**versionCode:** 103 · **Tag:** [v1.1.2](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2)

Refinement release: smarter Pulse, offline polish, and chat-list UX.

### Improved

- **Chat Pulse** — full conversation context and contact-aware Assistix replies
- **Assistix language** — AI replies match app language without bracket translations
- **Archive UX** — pull chat list down to open archive folder; deeper swipe for pin/archive
- **Offline** — queued messages, cached avatars, and link preview persistence
- **Connectivity advisor**, app update UI, WebRTC ICE, API origin fallback
- Localization (EN / RU / IT)

---

## [1.1.1] — 2026-05-30

**versionCode:** 102 · **Tag:** [v1.1.1](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1)

First stable public source release after the early `1.0.99` preview.

### Added

- **Chat Pulse**, **Assistix usage budget**, **Offline vault**, **Connectivity advisor**
- **Archive folder row**, **What's New dialog**

---

## [1.0.99] — 2026-05-30

**versionCode:** 99 · Early public preview

- Initial open-source client drop
- Jetpack Compose UI, WebRTC calls, whisper.cpp STT, Glance widgets
- PASAL license and contributor documentation

[1.1.5]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5
[1.1.2]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2
[1.1.1]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1
[1.0.99]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.0.99
