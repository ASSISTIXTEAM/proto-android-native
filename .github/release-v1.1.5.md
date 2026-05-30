## PROTO Android **1.1.5** — PROTO Cells

Главная новинка: **PROTO Cells** — обязательная взаимная зашифрованная сеть хранения медиа.

**versionCode:** `107` · **License:** [PASAL v1.0](LICENSE)

---

### PROTO Cells

- Медиа **от 8 KB** шифруется (AES-256-GCM), делится на **7 шардов**, реплицируется **×3**
- Шарды gzip-сжаты и лежат на устройствах участников — сервер хранит **каталог**, не полные файлы
- Авто-участие для всех аккаунтов, фоновая синхронизация каждые 2 минуты
- Экран объяснения в онбординге и в Настройках

**Подробно:** [docs/PROTO_CELLS.md](https://github.com/ASSISTIXTEAM/proto-android-native/blob/main/docs/PROTO_CELLS.md)

---

### Also in this release

- What's New dialog для 1.1.5
- Repair / assemble fallback в `ProtoMediaResolver`
- Полный клиент `/api/cells.php`

---

**Made by ASSISTIX TEAM**
