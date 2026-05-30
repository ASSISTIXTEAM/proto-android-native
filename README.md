<div align="center">

# PROTO · Android

**Нативный клиент мессенджера PROTO** — Kotlin, Jetpack Compose, звонки, виджеты, Assistix AI.

[![Version](https://img.shields.io/badge/version-1.1.1-FF6B00?style=for-the-badge)](https://github.com/ASSISTIXTEAM/proto-android-native/releases)
[![Platform](https://img.shields.io/badge/Platform-Android_8%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-PASAL_v1.0-CC0000?style=for-the-badge)](LICENSE)
[![Stars](https://img.shields.io/github/stars/ASSISTIXTEAM/proto-android-native?style=for-the-badge&logo=github&label=Stars)](https://github.com/ASSISTIXTEAM/proto-android-native/stargazers)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=for-the-badge)](CONTRIBUTING.md)

[proto.su](https://proto.su) · [Issues](https://github.com/ASSISTIXTEAM/proto-android-native/issues) · [Contributing](CONTRIBUTING.md) · [License](LICENSE)

*Публичный релиз **1.1.1** · client-only source*

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

> **Бэкенда нет.** Сервер, ключи продакшена и инфраструктура в репо не публикуются.

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
| **versionName** | `1.1.1` |
| **versionCode** | `102` |
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
