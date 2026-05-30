## PROTO Android **1.1.6** — stable

Исправляет вылеты и проблемы хранения из **1.1.5**. Рекомендуемая версия.

**versionCode:** `110` · **License:** [PASAL v1.0](LICENSE)

> ⚠️ **1.1.5** помечен как **нестабильный** (краши на Android 11+) — не используй для продакшена.

---

### Fixes

- Настройки и данные — в защищённом хранилище, без вылетов на Android 11+
- Автовосстановление при повреждении базы или prefs
- Стабильная фоновая синхронизация и repair **PROTO Cells**
- Фиксы в настройках, онбординге и чатах

### Cells-P

- **7 data-шардов + 1 XOR parity** — один пропавший data-шард восстанавливается автоматически
- Статистика узла в настройках

**Подробно:** [docs/PROTO_CELLS.md](https://github.com/ASSISTIXTEAM/proto-android-native/blob/main/docs/PROTO_CELLS.md)

---

**Made by ASSISTIX TEAM**
