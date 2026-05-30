<div align="center">

# PROTO · Android

**Нативный клиент мессенджера PROTO** — Kotlin, Jetpack Compose, звонки, виджеты, Assistix AI.

[![Version](https://img.shields.io/badge/version-1.1.6-FF6B00?style=for-the-badge)](https://github.com/ASSISTIXTEAM/proto-android-native/releases)
[![Platform](https://img.shields.io/badge/Platform-Android_8%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-PASAL_v1.0-CC0000?style=for-the-badge)](LICENSE)
[![Stars](https://img.shields.io/github/stars/ASSISTIXTEAM/proto-android-native?style=for-the-badge&logo=github&label=Stars)](https://github.com/ASSISTIXTEAM/proto-android-native/stargazers)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=for-the-badge)](CONTRIBUTING.md)

[proto.su](https://proto.su) · [Releases](https://github.com/ASSISTIXTEAM/proto-android-native/releases) · [Changelog](CHANGELOG.md) · [PROTO Cells](docs/PROTO_CELLS.md) · [Issues](https://github.com/ASSISTIXTEAM/proto-android-native/issues) · [Contributing](CONTRIBUTING.md) · [License](LICENSE)

*Публичный релиз **1.1.6** (stable) · client-only source · **PROTO Cells***

> ⚠️ **[1.1.5](https://github.com/ASSISTIXTEAM/proto-android-native/releases/tag/v1.1.5)** помечен как **нестабильный** — известны вылеты на Android 11+; используй **1.1.6**.

</div>

---

## О чём этот репозиторий

Исходники **официального Android-приложения PROTO**. Не шаблон «сделай свой Telegram», а живой клиент, который можно **собрать, изучить и улучшить**.

| Можно | Нельзя |
|-------|--------|
| Чинить баги и слать PR | Выпускать **конкурирующий мессенджер** |
| Улучшать UI/UX PROTO | Подменять бренд PROTO своим |
| Оптимизировать батарею и perf | Публиковать неофициальные «PROTO APK» |

Лицензия: **[PASAL v1.0](LICENSE)** — только для развития PROTO.

---

## Что внутри

| | Фича | Кратко |
|---|------|--------|
| 💬 | **Чаты & каналы** | DM, группы, лента каналов, реакции, медиа |
| 📞 | **Звонки** | Голос и видео через WebRTC |
| 🤖 | **Assistix AI** | Перевод, саммари, copilot в интерфейсе |
| 🎙️ | **Whisper STT** | Распознавание голосовых on-device ([whisper.cpp](vendor/whisper.cpp)) |
| 📱 | **Виджеты** | Glance-виджеты на домашний экран |
| 🔔 | **Push** | Firebase Cloud Messaging (свой проект) |
| 💾 | **Offline-first** | Room-кэш, черновики, локальные prefs |
| 🌍 | **i18n** | EN / RU / IT и др. |
| 🔐 | **Vault & PIN** | Защита чувствительных экранов |
| 📷 | **QR & deep links** | Вход по QR, ссылки `proto.su` |
| 🐝 | **[PROTO Cells](docs/PROTO_CELLS.md)** | Cells-P: 7 data + XOR parity, AES-256, gzip mesh |

> **Бэкенда нет.** Сервер, ключи продакшена и инфраструктура в репо не публикуются.

---

## PROTO Cells

<div align="center">

**Взаимное зашифрованное хранение медиа** — без гигантского сервера файлов и без полных копий на одном узле.

<img src="docs/assets/PROTO_Cells.png" alt="PROTO Cells — decentralized encrypted shard mesh" width="720">

*С **1.1.5** Cells обязателен; с **1.1.6** — кодирование **Cells-P** (XOR parity).*

</div>

| | |
|---|---|
| **Шифрование** | AES-256-GCM до отправки с телефона |
| **Шарды** | **Cells-P:** 7 data-полос + 1 XOR parity (8 total) |
| **Восстановление** | Один пропавший data-шард восстанавливается из parity |
| **Порог** | Медиа от **8 KB** автоматически попадает в mesh |
| **На диске** | Gzip-сжатые шарды (`PCGZ`) — держатель не видит plaintext |
| **Сервер** | Каталог + кратковременный relay, **не** полные файлы |

**Как это честно:** твоё медиа хостят другие устройства сети — значит, ты тоже хранишь их зашифрованные фрагменты (квота ~768 MB).

Подробная архитектура, crypto, API и карта кода → **[docs/PROTO_CELLS.md](docs/PROTO_CELLS.md)**

---

## Стек

```
Kotlin 2 · Jetpack Compose · Material 3 · Navigation Compose
Room · OkHttp · Coroutines · DataStore
WebRTC (Stream) · Media3 · Coil · CameraX · ML Kit QR
Firebase Messaging · WorkManager · Glance AppWidget
NDK / CMake · whisper.cpp (JNI)
```

**minSdk 26** · **targetSdk 35** · **JDK 17–21**

---

## Быстрый старт

```bash
git clone https://github.com/ASSISTIXTEAM/proto-android-native.git
cd proto-android-native

cp secrets.properties.example secrets.properties
cp local.properties.example local.properties
# local.properties → sdk.dir=...

# Android Studio → Run app
# или:
./gradlew :app:assembleDebug   # Windows: gradlew.bat
```

На **Windows** клонируй в путь **без кириллицы** (например `C:\dev\proto-android-native`).

| Файл | Зачем |
|------|--------|
| `secrets.properties` | API / WebSocket / TURN → `BuildConfig` |
| `app/google-services.json` | Firebase (заглушка в репо) |
| `local.properties` | Путь к Android SDK |

---

## Структура

```
app/src/main/java/org/assistix/proto/nativeapp/
├── ui/          экраны Compose, тема, l10n
├── data/        API-клиент, Room, WebRTC, realtime
├── widget/      домашние виджеты
└── update/      in-app update UI
app/src/main/cpp/          JNI-мост whisper
vendor/whisper.cpp/        vendored STT
```

---

## Как помочь проекту

1. Прочитай [CONTRIBUTING.md](CONTRIBUTING.md) и [LICENSE](LICENSE)
2. **Fork** → ветка → изменения → **Pull Request**
3. Команда **ASSISTIX TEAM** ревьюит; одобренное попадает в официальный PROTO

**Авторы репозитория:** только [ASSISTIX TEAM](https://github.com/ASSISTIXTEAM) и принятые контрибьюторы. См. [AUTHORS.md](AUTHORS.md).

---

## Теги / Topics

`android` · `kotlin` · `jetpack-compose` · `messenger` · `chat-app` · `webrtc` · `whisper` · `speech-to-text` · `room-database` · `firebase` · `material-design` · `proto` · `assistix` · `mobile` · `open-source-client`

---

## Версия

| | |
|---|---|
| **versionName** | `1.1.6` |
| **versionCode** | `110` |
| **Статус** | stable public client source |

---

## Поддержка

- Сайт: [proto.su](https://proto.su)
- Почта: team@proto.su
- Баги: [GitHub Issues](https://github.com/ASSISTIXTEAM/proto-android-native/issues)

---

<div align="center">

**PROTO** · made by **ASSISTIX TEAM**

Copyright © ASSISTIX TEAM · [PASAL v1.0](LICENSE)

</div>

---

<details>
<summary><b>English summary</b></summary>

**PROTO Android** is the official native client source (Kotlin / Jetpack Compose). Study it, build it, send PRs to improve PROTO — do not ship a competing messenger. Backend not included. License: [PASAL v1.0](LICENSE). Contributors: [ASSISTIX TEAM](https://github.com/ASSISTIXTEAM) only (+ merged PR authors).

</details>
