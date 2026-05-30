## PROTO Android **1.1.1** — public client source

Первый **стабильный** open-source релиз Android-клиента PROTO после раннего preview `1.0.99`.

**versionCode:** `102` · **License:** [PASAL v1.0](LICENSE) · **Backend:** not included (client only)

---

### Highlights

- **Chat Pulse** — Assistix прямо в контексте чата: быстрые подсказки, обсуждение переписки
- **Offline vault** — профили и данные переживают офлайн и перезапуск
- **Connectivity advisor** — мягкое предупреждение про VPN / не-RU маршрут (без блокировок)
- **Assistix budget UI** — видимость лимитов AI-использования
- **Archive row** — архив чатов через pull-жест в списке
- **What's New** — диалог новинок после обновления
- Улучшения списка чатов, навигации, Assistix tab, app update flow, l10n

---

### For contributors

```bash
git clone https://github.com/ASSISTIXTEAM/proto-android-native.git
cp secrets.properties.example secrets.properties
cp local.properties.example local.properties
# Android Studio → Run
```

Read [CONTRIBUTING.md](https://github.com/ASSISTIXTEAM/proto-android-native/blob/main/CONTRIBUTING.md) — PRs that **improve PROTO** are welcome. Competing messengers are out of scope.

---

### Stack

Kotlin · Jetpack Compose · Room · WebRTC · whisper.cpp · Glance widgets · Firebase hooks

---

### Links

- [proto.su](https://proto.su) — official product
- [Full changelog](https://github.com/ASSISTIXTEAM/proto-android-native/blob/main/CHANGELOG.md)
- team@proto.su

**Made by ASSISTIX TEAM**
