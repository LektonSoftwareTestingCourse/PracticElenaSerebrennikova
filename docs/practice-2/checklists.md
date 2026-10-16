# Чек-листы: дымовое тестирование и критический путь

Чек-лист определяет, что подлежит проверке, без конкретных входных данных, шагов и ожидаемых результатов. Свойства: краткость формулировок, пригодность к повторному применению, логическая полнота без дублирования. Области применения - дымовое тестирование, критический путь, каркас исследовательского тестирования.

## 1. Дымовое тестирование

Оценка работоспособности системы после сборки/развёртывания: поднимается ли и делает ли что-то полезное.

### Инфраструктура

- [ ] `docker compose up -d` завершается без ошибок
- [ ] `docker compose ps` - все контейнеры `Up`/healthy
- [ ] PostgreSQL доступна, миграции применены, таблицы существуют
- [ ] RabbitMQ доступен, Management UI открывается (`localhost:15672`)
- [ ] Очереди и exchange'ы созданы: `transaction-log`, `card-notifications` и их DLQ

### Health-check сервисов

- [ ] Gateway (`/health`)
- [ ] Card Management (`/health`)
- [ ] Switch (`/health`)
- [ ] Authorization (`/health`)
- [ ] Terminal Simulator (`/health`)
- [ ] Merchant Simulator (`/health`)
- [ ] Transaction Logger (`/health`)
- [ ] Bin Lookup (`/actuator/health`)
- [ ] Notification Service (`/actuator/health`)
- [ ] Web Dashboard открывается (`localhost:3000`)

### Транзакционная цепочка

- [ ] Gateway принимает `POST /api/transactions` и возвращает ответ
- [ ] Switch маршрутизирует транзакцию в Authorization
- [ ] Authorization возвращает решение (APPROVED/DECLINED) с кодом ответа
- [ ] Транзакция публикуется в RabbitMQ и потребляется Transaction Logger
- [ ] Web Dashboard показывает свежие транзакции и статистику
- [ ] Симуляторы запускаются: `POST /api/simulator/terminal/run`, `POST /api/simulator/merchant/run`

### Асинхронный контур

- [ ] Карточные события уходят через outbox в `smp.card-events`
- [ ] Notification Service сохраняет уведомления
- [ ] При недоступности consumer'а сообщения уходят в DLQ после исчерпания retry

---

## 2. Критический путь

Проверка главных пользовательских сценариев процессинговой цепочки.

### CP-1. Успешная покупка (happy path)

- [ ] Сгенерирована карта со статусом `ACTIVE` и достаточным балансом
- [ ] Транзакция отправлена через Terminal Simulator -> Gateway
- [ ] Gateway валидирует запрос и передаёт его в Switch
- [ ] Switch определяет BIN и маршрутизирует в Authorization
- [ ] Authorization получает данные карты из Card Management
- [ ] Authorization одобряет операцию (`responseCode = "00"`)
- [ ] Card Management резервирует сумму (`availableBalance` уменьшился на сумму)
- [ ] Сгенерированы `RRN` (12 цифр) и `authCode` (6 символов)
- [ ] Транзакция попала в Transaction Logger ровно один раз
- [ ] Web Dashboard отобразил транзакцию и обновил статистику

### CP-2. Возврат / разрезервирование (reversal)

- [ ] Для одобренной транзакции найден `rrn`
- [ ] Reversal (`mti = "0400"`) возвращает сумму в `availableBalance`
- [ ] Запись из `limit_usage` снята/скорректирована
- [ ] Операция отражена в логе и на дашборде

### CP-3. Decline-сценарии

- [ ] Карта не найдена -> отказ с `responseCode = "14"`
- [ ] Карта `INACTIVE` -> отказ `CARD_INACTIVE`
- [ ] Карта `BLOCKED` -> отказ `CARD_BLOCKED`
- [ ] Карта `EXPIRED` / истёкший `expiryDate` -> отказ `responseCode = "54"`
- [ ] Превышен дневной лимит -> отказ `responseCode = "61"`
- [ ] Превышен месячный лимит -> отказ `responseCode = "61"`
- [ ] Недостаточно средств -> отказ `responseCode = "51"`
- [ ] Card Management недоступен -> отказ `responseCode = "05"` (`ISSUER_TIMEOUT`)
- [ ] Отклонённая транзакция не изменяет баланс и лимиты

### CP-4. Асинхронный контур карточных событий

- [ ] Создание/обновление/удаление карты порождает событие через outbox
- [ ] Notification Service получает событие и сохраняет уведомление
- [ ] Сбой публикации не теряет событие (retry, затем FAILED, без потери данных карты)

### CP-5. Наблюдаемость

- [ ] `GET /api/dashboard/stats` отдаёт согласованные KPI
- [ ] `GET /api/dashboard/recent` отдаёт последние транзакции
- [ ] WebSocket (`/ws/transactions`) транслирует поток транзакций в реальном времени
- [ ] Поиск транзакций (`GET /api/transactions/search`) находит транзакцию по `rrn`

