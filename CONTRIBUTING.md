# Как контрибьютить в PROTO Android

<div align="center">

**PR → ревью ASSISTIX TEAM → мерж в официальный PROTO**

[![License: PASAL](https://img.shields.io/badge/License-PASAL_v1.0-red?style=flat-square)](LICENSE)

</div>

Спасибо, что хочешь сделать PROTO лучше. Этот репозиторий — **не** стартовый kit для «своего мессенджера», а поле для улучшений **официального** Android-клиента.

---

## Мы рады

- 🐛 баг-репорты со steps to reproduce
- 🎨 UI/UX в стиле PROTO
- ⚡ perf, батарея, плавность списков
- ♿ a11y, локализация
- 📝 доки и понятные PR
- 🧪 тесты там, где они реально ловят регрессии

## Мы не принимаем

- 🚫 форки «под свой мессенджер» или white-label
- 🚫 бэкенд / серверный код (здесь только клиент)
- 🚫 секреты, prod-ключи, keystore
- 🚫 гигантские рефакторинги без обсуждения
- 🚫 `Co-authored-by` от ботов и IDE в коммитах — **автор PR = ты**

---

## Workflow

```text
Fork → feature/fix-название → commit → Push → Pull Request
                                              ↓
                                    ASSISTIX TEAM review
                                              ↓
                                    merge → официальный PROTO
```

1. **Fork** репозитория
2. Собери проект локально — [README.md](README.md)
3. Одна логическая задача = один PR
4. Для UI приложи **скриншоты / видео**
5. Жди ревью — мержит maintainer

Принятый код лицензируется под [PASAL v1.0](LICENSE); ты сохраняешь авторство PR, а PROTO получает право использовать патч.

---

## Стиль кода

- Пиши как в соседних файлах: Kotlin idioms, Compose, без лишней магии
- Минимальный diff — не reformat всего проекта
- Не коммить: `secrets.properties`, keystore, боевой `google-services.json`

---

## Кто в списке авторов

Maintainer и история коммитов — **[ASSISTIX TEAM](https://github.com/ASSISTIXTEAM)**.  
Контрибьюторы появляются после **мержа PR**, не через co-author trailers в чужих коммитах.

См. [AUTHORS.md](AUTHORS.md).

---

## Вопросы

- [GitHub Issues](https://github.com/ASSISTIXTEAM/proto-android-native/issues) — баги и идеи
- team@proto.su — всё остальное

---

<details>
<summary><b>English</b></summary>

Fork → branch → PR → ASSISTIX TEAM review → merge into official PROTO. No competing messengers. No secrets in commits. No bot co-author trailers. See [LICENSE](LICENSE).

</details>
