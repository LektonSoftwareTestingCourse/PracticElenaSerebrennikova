## 1. Покрытие

Набор размещён в отдельном модуле `services/api-tests` и запускается профилем
`api-tests`. Основные доменные запросы идут через публичный Gateway; для сервисов,
не имеющих Gateway-маршрута (BIN Lookup и Notification Service), используются их
публичные HTTP-порты. Состояние создаётся только через HTTP API.

| Эндпоинт | Проверяемые статус-коды | Кол-во проверок | Статус |
|---|---:|---:|---|
| `POST /api/cards` | 201, 400 | 2 (один параметризованный) | реализовано |
| `GET /api/cards` | 200, 400 | 2 | реализовано |
| `GET/PATCH/DELETE /api/cards/{pan}` | 200, 204, 400 | 4 | реализовано |
| `POST /api/cards/{pan}/reserve` | 200, 400 | 2 | реализовано |
| `POST /api/cards/{pan}/rollback` | 200 | 1 | реализовано |
| `POST /api/cards/bulk-update` | 200 | 1 | реализовано |
| `POST /api/cards/generate` | 201, 400 | 3 (позитивная параметризация + 2 негативных) | реализовано |
| `GET /api/transactions/search` | 200, 400 | 2 | реализовано |
| `GET /api/dashboard/stats` | 200 | 1 | реализовано |
| `GET /api/dashboard/recent` | 200, 400 | 1 | реализовано |
| `GET /api/dashboard/charts` | 200 | 1 параметризованный | реализовано |
| `GET /api/bin/{bin}` | 200, 404, 500 | 3 (один параметризованный) | реализовано |
| `POST /api/simulator/terminal/run` | 400 | 2 | реализовано |
| `POST /api/simulator/terminal/start-continuous` | 400 | 1 | реализовано |
| `POST /api/simulator/terminal/stop` | 200 | 1 | реализовано |
| `POST /api/simulator/merchant/run` | 400 | 1 | реализовано |
| `POST /api/simulator/merchant/fee` | 400 | 1 | реализовано |
| `GET /api/simulator/merchants` | 200 | 1 | реализовано |
| `GET /api/notifications` | 200, 400/500 | 2 (один параметризованный) | реализовано |
| `GET/POST/DELETE /api/bins` (Card Management :8081) | 200, 201, 204, 400 | 3 | реализовано |
| `POST /api/internal/authorize` | 200, 400, 404 | 3 | реализовано |
| `POST /api/internal/rollback` | 200, 400 | 1 | реализовано |
| `POST /api/internal/route` | 200, 400/500 | 2 | реализовано |
| `GET /api/transactions/export` | 200 | 1 | реализовано |
| `POST /api/internal/log` | 400 | 1 | реализовано |
| `GET /health` | 200 | 7 параметризованных | реализовано |
| `GET /actuator/health` | 200 | 2 | реализовано |

Всего добавлено 12 JUnit-классов и более 50 отдельных запусков сценариев с учётом
параметров параметризованных тестов.
Проверяются не только коды: тело ответа, `Content-Type`, JSON Schema,
форматы PAN, статус карты, пагинация и пустая выборка.

## 2. Бизнес-отказы и валидация

| Исход | Код | Причина/тело | Путь достижения | Способ подготовки данных |
|---|---:|---|---|---|
| Неверный BIN | 400 | `message` заполнено | `POST /api/cards` | параметризованный невалидный BIN |
| Недопустимое имя держателя | 400 | `message` заполнено | `POST /api/cards` | строчные символы |
| Неверная длина currencyCode | 400 | `message` заполнено | `POST /api/cards` | 2 цифры |
| Нулевая граница `limit` | 400 | `message` заполнено | `GET /api/cards` | `limit=0` |
| Отрицательная граница `offset` | 400 | `message` заполнено | `GET /api/transactions/search` | `offset=-1` |
| Пустая выборка | 200 | `transactions=[]`, `total=0` | `GET /api/transactions/search` | PAN, который создаётся только в тесте поиска |

Каждый негативный тест меняет один класс эквивалентности, поэтому причина
отказа локализуется.

## 3. Параметры, повторная доставка и асинхронность

Параметры `limit`/`offset` проверяются на границе и в допустимом диапазоне;
GPath-выражение `cards.findAll { ... }` связывает результат со своим PAN.
Проверка `GET /api/dashboard/recent?limit=500` закрывает верхнюю границу
контракта Dashboard.

Проверки RabbitMQ и идемпотентности сообщений сознательно не включены: условие
практики ограничивает набор синхронными HTTP-контрактами, а PostgreSQL/RabbitMQ
относятся к модулю 6. Awaitility оставлен для проверки eventual consistency
поиска транзакций без фиксированной задержки: используется `atMost=3s` и
`pollInterval=100ms`.

## 4. Расхождения контракта и реализации

| Декларировано | Наблюдается/проверяется | Источник | Классификация |
|---|---|---|---|
| СМП принимает запросы через Gateway | Card API доступен через gateway route `/api/cards/**` | `services/gateway/application.yml`, `CardController` | подтверждено |
| `GET /api/cards` допускает `limit` только `>0` | `limit=0` должен дать 400 | `CardController @Positive` | контрактная валидация |
| `GET /api/transactions/search` допускает `offset >= 0` | `offset=-1` должен дать 400 | `TransactionFilter @PositiveOrZero` | контрактная валидация |
| Создание карты возвращает 201 и CardModel | тест проверяет 201, схему и поля CardModel | `CardController`, `CardModel` | подтверждено |

Зафиксированных расхождений между тремя источниками (`docs/api-spec.md`,
контроллерами и Gateway routes) в реализованном минимальном наборе не заявляется:
проверка выполняется по коду и доступному контракту; спорные поля должны быть
добавлены в расширение набора после запуска стенда.

## 5. Обоснование достаточности

Выделены классы: валидный запрос, отдельные нарушения BIN/имени/валюты,
пустая выборка, положительная граница пагинации и нарушения нижних границ.
Валидный ответ проверяется по схеме, а динамические значения — регулярным
выражением. Это закрывает обязательные приёмы задания: две параметризации,
`RequestSpecification`, Support-слой, GPath, проверку заголовка, Hamcrest,
Awaitility и `given/when/then`.

Вне набора: прямые проверки БД, RabbitMQ/outbox,
повторная доставка с конфликтом ключа и отказ Authorization. Они требуют
интеграционной инфраструктуры модуля 6 либо отдельного набора авторизации;
их отсутствие не маскируется API-проверками Gateway.

## 6. Доказательства работоспособности проверок

| Тест | Намеренная мутация | Ожидаемый результат |
|---|---|---|
| `createsCardWithContractShapeAndContentType` | заменить 201 на 200 в контроллере | тест красный по статусу |
| `rejectsOneInvalidEquivalenceClass` | убрать `@Valid` у `CreateCardRequest` | тест красный по 400/`message` |
| `listsCardsWithPaginationAndGPathSelection` | вернуть весь список независимо от `limit` | тест красный по `hasSize`/GPath |

Мутации являются процедурой проверки тестов и в production-код не внесены.

## 7. Порядок запуска

```
docker compose up -d --build
mvn -f services/api-tests/pom.xml verify
docker compose down -v
```