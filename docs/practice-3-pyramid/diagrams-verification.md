# Проверка UML-диаграмм по коду

Этот файл фиксирует для каждой диаграммы:

1. чего не хватало в исходных материалах проекта;
2. что добавлено или исправлено;
3. какими классами и конфигурационными файлами подтверждаются элементы схемы.

---

## А1. C4 Level 1 - Context

### Чего не хватало

Отдельной Context-диаграммы не было. В существующей общей архитектурной схеме
внутренние сервисы и внешние участники были показаны вместе, поэтому граница
СМП как системы и протоколы взаимодействия с внешним миром не были выделены.

### Что сделано

СМП показана одним системным блоком. Внешними участниками оставлены:

- Terminal Simulator;
- Merchant Acquirer;
- пользователь Dashboard;
- BIN Lookup.

На связях указаны фактические протоколы и входы:

- Terminal Simulator → СМП: `POST /api/transactions`, HTTP/JSON;
- Merchant Acquirer → СМП: `POST /api/transactions`, HTTP/JSON;
- пользователь → Dashboard: HTTP;
- Dashboard → СМП: REST `/api/**` и WebSocket `/ws/transactions`;
- СМП → BIN Lookup: `GET /api/bin/{bin}`, HTTP/JSON.

Внутренние Gateway, Switch, Authorization, Card Management, RabbitMQ,
PostgreSQL и Transaction Logger намеренно не раскрываются: это уровень Context,
а не Container.

### Подтверждение кодом

- [Terminal `GatewayClient`](../../services/terminal-simulator/src/main/java/com/processing/terminalsimulator/client/GatewayClient.java) - `sendToGateway()` вызывает `POST /api/transactions`.
- [Merchant Acquirer `GatewayClient`](../../services/merchant-acquirer/src/main/java/com/processing/merchantacquirer/client/GatewayClient.java) - `processAuthorize()` вызывает `POST /api/transactions`.
- [Dashboard `nginx.conf`](../../services/dashboard/nginx.conf) - проксирование `/api/` и `/ws/`.
- [Dashboard `useWebSocket.ts`](../../services/dashboard/src/hooks/useWebSocket.ts) - WebSocket URL `/ws/transactions`.
- [BIN `BinLookupController`](../../services/bin-lookup/src/main/java/com/processing/binlookup/controller/BinLookupController.java) - `@RequestMapping("/api/bin")` и `@GetMapping("/{bin}")`.

---

## А2. C4 Level 2 - Container

### Чего не хватало

В исходной архитектурной схеме были показаны контейнеры, но:

- синхронные и асинхронные связи выглядели одинаково;
- не были явно подписаны exchange, queue и routing key RabbitMQ;
- не были отражены все фактические Gateway routes;
- часть связей была сформулирована слишком общо;
- состав контейнеров не был аккуратно сверён с `docker-compose.yaml`.

### Что сделано

Добавлены и размечены:

- HTTP/JSON-связи между сервисами;
- JDBC/JPA-связи с PostgreSQL;
- пунктирные AMQP-связи;
- WebSocket как отдельный тип соединения;
- exchanges, queues и routing keys RabbitMQ;
- фактические endpoint’ы на связях.

Исправлены важные связи:

- Gateway → Switch: `POST /api/transactions` преобразуется в `POST /api/internal/route`;
- Switch → Authorization: `POST /api/internal/authorize` и `POST /api/internal/rollback`;
- Authorization → Card Management: `GET /api/cards/{pan}`, reserve и rollback;
- Switch → Merchant Acquirer: `POST /api/simulator/merchant/fee`;
- Card Management → RabbitMQ: outbox-события `card.*`;
- Switch → RabbitMQ: `transaction.log`;
- RabbitMQ → Logger: queue `transaction-log`;
- RabbitMQ → Notification: queue `card-notifications`.

Также зафиксировано расхождение по количеству приложений: основной Compose содержит
10 приложений; дополнительные observability-сервисы запускаются профилем отдельно.

### Подтверждение кодом

- [Docker Compose](../../docker-compose.yaml) - состав runtime-контейнеров и их URL-переменные.
- [Gateway `application.yml`](../../services/gateway/src/main/resources/application.yml) - routes `switch`, `logger`, `dashboard`, `terminal`, `merchant`, `cards`.
- [Switch `RouteController`](../../services/switch/src/main/java/com/processing/controller/RouteController.java) - `POST /api/internal/route`.
- [Switch `AuthorizationClient`](../../services/switch/src/main/java/com/processing/service/AuthorizationClient.java) - `POST /api/internal/authorize` и `POST /api/internal/rollback`.
- [Switch `MerchantAcquirerClient`](../../services/switch/src/main/java/com/processing/service/MerchantAcquirerClient.java) - `POST /api/simulator/merchant/fee`.
- [Authorization `CardManagementClientImpl`](../../services/authorization/src/main/java/com/processing/authorization/client/CardManagementClientImpl.java) - GET card, reserve и rollback.
- [Authorization `BinLookupClient`](../../services/authorization/src/main/java/com/processing/authorization/client/BinLookupClient.java) - `GET /api/bin/{bin}`.
- [Switch `RabbitMQConfig`](../../services/switch/src/main/java/com/processing/config/RabbitMQConfig.java) - `smp.transactions`, `transaction-log`, `transaction.log`, DLX/DLQ.
- [Card Management `RabbitMQConfig`](../../services/card-management/src/main/java/com/processing/cardmanagement/config/RabbitMQConfig.java) - `smp.card-events`, `card.*`, DLX/DLQ.
- [Transaction Logger `TransactionLogListener`](../../services/transaction-logger/src/main/java/com/processing/transactionlogger/listener/TransactionLogListener.java) - consumer `transaction-log`.
- [Notification `CardEventListener`](../../services/notification-service/src/main/java/com/processing/notification/listener/CardEventListener.java) - consumer `card-notifications`.

---

## А3. C4 Level 3 - Component

### Чего не хватало

Компонентного уровня в исходных материалах не было. Было непонятно:

- какой контроллер вызывает бизнес-логику;
- где находятся HTTP-клиенты Authorization;
- где находится доступ к `limit_usage`;
- как Card Management работает с картами и резервами;
- где разделены сохранение outbox-события и его фоновая публикация.

### Что сделано

**Authorization:**

```text
AuthControllerImpl
        ↓
AuthServiceImpl
   ├── CardManagementClientImpl → Card Management по HTTP
   ├── BinLookupClient           → BIN Lookup по HTTP
   └── LimitUsageRepository      → PostgreSQL по JPA/JDBC
```

**Card Management:**

```text
CardController / CardGeneratorController
        ↓
CardServiceImpl / CardGeneratorService
   ├── persistence-адаптеры и JPA-репозитории
   ├── BinIssuerServiceImpl → BinIssuerJpaAdapter
   └── CardEventNotifierImpl
             ↓
       OutboxEventProcessorImpl
             ↓
       RabbitMQ: smp.card-events
```

Сохранение outbox и отправка в RabbitMQ показаны как разные части:

- `CardEventNotifierImpl` передаёт событие на сохранение;
- `OutboxEventProcessorImpl.save()` сохраняет его через `OutboxJpaAdapter`;
- `OutboxProcessor` по расписанию выбирает `PENDING`;
- `OutboxEventProcessorImpl.processSingleEvent()` публикует сообщение и обновляет статус.

### Подтверждение кодом

- [Authorization `AuthControllerImpl`](../../services/authorization/src/main/java/com/processing/authorization/controller/AuthControllerImpl.java) - REST-вход и вызовы `authorize()`/`rollback()`.
- [Authorization `AuthServiceImpl`](../../services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java) - основная бизнес-логика, клиенты и лимиты.
- [Authorization `CardManagementClientImpl`](../../services/authorization/src/main/java/com/processing/authorization/client/CardManagementClientImpl.java) - HTTP к Card Management.
- [Authorization `BinLookupClient`](../../services/authorization/src/main/java/com/processing/authorization/client/BinLookupClient.java) - HTTP к BIN Lookup.
- [Authorization `LimitUsageRepository`](../../services/authorization/src/main/java/com/processing/authorization/repositories/LimitUsageRepository.java) - `JpaRepository`, `upsertLimitUsage()` и `fetchRrnBlock()`.
- [Card Management `CardController`](../../services/card-management/src/main/java/com/processing/cardmanagement/controllers/CardController.java) - card REST API.
- [Card Management `CardGeneratorController`](../../services/card-management/src/main/java/com/processing/cardmanagement/controllers/CardGeneratorController.java) - `POST /api/cards/generate`.
- [Card Management `CardServiceImpl`](../../services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java) - CRUD, reserve, rollback, bulk update.
- [Card Management `CardGeneratorService`](../../services/card-management/src/main/java/com/processing/cardmanagement/services/CardGeneratorService.java) - генерация тестовых карт.
- [Card Management `AppConfig`](../../services/card-management/src/main/java/com/processing/cardmanagement/configuration/AppConfig.java) - связывает сервисы, репозитории, notifier и outbox processor.
- [Card Management `CardEventNotifierImpl`](../../services/card-management/src/main/java/com/processing/cardmanagement/events/CardEventNotifierImpl.java) - передаёт outbox-событие на сохранение.
- [Card Management `OutboxProcessor`](../../services/card-management/src/main/java/com/processing/cardmanagement/services/OutboxProcessor.java) - `@Scheduled`, поиск pending-событий.
- [Card Management `OutboxEventProcessorImpl`](../../services/card-management/src/main/java/com/processing/cardmanagement/services/OutboxEventProcessorImpl.java) - `save()`, публикация `convertAndSend()`, `PROCESSED`/retry/`FAILED`.
- [Card Management `OutboxJpaAdapter`](../../services/card-management/src/main/java/com/processing/cardmanagement/repositories/OutboxJpaAdapter.java) - JPA-доступ к `outbox_event`.

### Ограничение представления

Persistence-адаптеры карт, резервов и откатов сгруппированы в один блок, чтобы
не превращать А3 в полный граф всех классов. DTO, мапперы, health-check,
локальные log-listeners и вспомогательные retry-классы намеренно не показаны.

---

## А4. Sequence

Диаграмма А4 уже покрывает фактический путь транзакции, включая `APPROVED`,
`DECLINED`, публикацию в RabbitMQ и rollback при ошибке публикации:

Важное уточнение по коду: `TransactionRequest` собирается после результата
Authorization, чтобы записать уже принятое решение в Logger; это не повторное
бизнес-решение.

---

## А5. RabbitMQ-топология

Диаграмма показывает `exchange → queue → consumer` для транзакционного и
карточного потоков, DLX/DLQ, routing keys, максимум три доставки и TTL 60 секунд.

Publisher confirms настроены в конфигурации Switch, но `LoggerClient` не ждёт
ACK отдельным 2-секундным ожиданием. Это отражено как ограничение реализации,
а не как вымышленная гарантия.

Подтверждения: [Switch `RabbitMQConfig`](../../services/switch/src/main/java/com/processing/config/RabbitMQConfig.java), [Card Management `RabbitMQConfig`](../../services/card-management/src/main/java/com/processing/cardmanagement/config/RabbitMQConfig.java), [TransactionLogListener](../../services/transaction-logger/src/main/java/com/processing/transactionlogger/listener/TransactionLogListener.java), [CardEventListener](../../services/notification-service/src/main/java/com/processing/notification/listener/CardEventListener.java).

---

## А6. State-transition

Показаны состояния карты и жизненный цикл результата авторизации. В коде
`CardStatus` содержит также `DELETED`, поэтому он показан на диаграмме, хотя в
краткой формулировке задания перечислены только четыре основных состояния.

`TransactionStatus` в коде содержит только `APPROVED` и `DECLINED`. Поэтому
`ROLLED_BACK` показан как компенсационный шаг после ошибки публикации, а не как
enum-значение статуса Transaction Logger. После `DECLINED` показан финальный ответ,
а после успешного `APPROVED` - `COMPLETED` и финальный ответ `APPROVED`.

Подтверждения: [CardStatus](../../services/card-management/src/main/java/com/processing/cardmanagement/models/CardStatus.java), [Card](../../services/card-management/src/main/java/com/processing/cardmanagement/models/Card.java), [CardServiceImpl](../../services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java), [CardController](../../services/card-management/src/main/java/com/processing/cardmanagement/controllers/CardController.java), [TransactionStatus](../../services/common/src/main/java/com/processing/common/dto/transactionlogger/TransactionStatus.java), [RouteService](../../services/switch/src/main/java/com/processing/service/RouteService.java).

---

## Общий итог по недостающим материалам

| Диаграмма | Что отсутствовало/было неполным | Что добавлено |
|---|---|---|
| А1 | Не было отдельной границы СМП и внешних участников | Context с протоколами внешних связей |
| А2 | Не различались HTTP/JDBC и AMQP, не были проверены routes и topology | Container по Compose и коду с endpoint’ами, exchange/queue и JDBC |
| А3 | Не было внутренней карты сервисов | Component views для Authorization и Card Management |
| А4 | Был только happy path/не было ясного разделения решения и финального ответа | Sequence с approved, declined, publish error/rollback и async Logger |
| А5 | RabbitMQ была описана текстом, но не визуализирована | Две topology-цепочки с DLX/DLQ, retry и TTL |
| А6 | Не было state-transition | Состояния карты и транзакционного результата по коду |
