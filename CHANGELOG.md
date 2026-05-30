# Changelog

All notable changes to the **PROTO Android** open client source are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.2] — 2026-05-30

**versionCode:** 103 · **Tag:** [v1.1.2](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2)

Refinement release: smarter Pulse, offline polish, and chat-list UX.

### Improved

- **Chat Pulse** — full conversation context and contact-aware Assistix replies
- **Assistix language** — AI replies match app language without bracket translations
- **Archive UX** — pull chat list down to open archive folder; deeper swipe for pin/archive
- **Offline** — queued messages, cached avatars, and link preview persistence
- **Connectivity advisor** — clearer VPN / egress guidance copy
- **App update UI** — notification and download flow tweaks
- **WebRTC ICE** — additional public STUN fallbacks and `turn.proto.su` URLs in config
- **API origin** — smarter fallback when `api.proto.su` returns HTML errors
- Localization (EN / RU / IT) and widget polish

### Public repo notes

- Sanitized build: secrets via `secrets.properties.example` only
- License: **PASAL v1.0**

---

## [1.1.1] — 2026-05-30

**versionCode:** 102 · **Tag:** [v1.1.1](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1)

First stable public source release after the early `1.0.99` preview. Client-only; backend not included.

### Added

- **Chat Pulse** — contextual Assistix sheet inside a chat (`ChatPulseSheet`) with usage budget UI
- **Assistix usage budget** — rate-limit awareness via `AssistixUsageHub` / `AssistixRateLimit`
- **Offline vault** — durable profile cache under app storage (`ProtoOfflineVault`, `ProtoProfileCache`)
- **Connectivity advisor** — non-blocking VPN / non-RU egress hint banner (`ProtoConnectivityAdvisor`)
- **Archive folder row** — pull-to-reveal archived chats entry in the list (`ChatArchiveFolderRow`)
- **What's New dialog** — in-app highlights after update (`ProtoWhatsNewDialog`)

### Improved

- Chat list swipe actions and navigation flow
- Assistix AI tab and composer integration
- App update UI and notification copy handling
- Draft prefs sync and network monitor edge cases
- Localization strings (EN / RU / IT)

---

## [1.0.99] — 2026-05-30

**versionCode:** 99 · Early public preview

- Initial open-source client drop
- Jetpack Compose UI, WebRTC calls, whisper.cpp STT, Glance widgets
- PASAL license and contributor documentation

[1.1.2]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.2
[1.1.1]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.1
[1.0.99]: https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.0.99
