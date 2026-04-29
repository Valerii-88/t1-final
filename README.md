# Limit Service

REST-сервис дневных пользовательских лимитов на Spring Boot.

Что делает сервис:
- хранит доступный лимит пользователя
- автоматически создает пользователя с дефолтным лимитом при первом обращении
- поддерживает `reserve`, `confirm`, `cancel` по `operationId`
- каждый день в `00:00` сбрасывает доступный лимит всех пользователей к текущему дефолтному значению

Требуемый стек:
- Java 17
- PostgreSQL

Переменные окружения:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_LIMIT_RESET_ZONE`

Запуск:

```bash
mvn spring-boot:run
```

Основные endpoint-ы:

```bash
curl http://localhost:8080/api/v1/limits/user-1
```

```bash
curl -X POST http://localhost:8080/api/v1/limits/reservations ^
  -H "Content-Type: application/json" ^
  -d "{\"operationId\":\"op-1\",\"userId\":\"user-1\",\"amount\":2500.00}"
```

```bash
curl -X POST http://localhost:8080/api/v1/limits/reservations/op-1/confirm
```

```bash
curl -X POST http://localhost:8080/api/v1/limits/reservations/op-1/cancel
```
