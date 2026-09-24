# Test-design ядра: Authorization и Card-Management

## 1. Классы эквивалентности

**Правила:** позитивные и негативные классы выделяются раздельно; на каждый класс - минимум один представитель (тест-кейс); один невалидный класс - один тест-кейс, смешивать невалидные значения нельзя.

### 1.1 Authorization Service

Алгоритм проверки строго упорядочен: существование карты -> статус -> срок действия -> дневной лимит -> месячный лимит -> баланс -> одобрение.

| ID | Поле / условие | Класс | Тип | Представитель | Ожидаемый результат |
|---|---|---|---|---|---|
| EC-AUTH-01 | Наличие карты | карта найдена | позитивный | PAN существующей карты | Проверки продолжаются |
| EC-AUTH-02 | Наличие карты | карта не найдена | негативный | PAN из 16 цифр, отсутствующий в CMS | DECLINED, `responseCode = "14"` |
| EC-AUTH-03 | Статус карты | `ACTIVE` | позитивный | `status = ACTIVE` | Проверки продолжаются |
| EC-AUTH-04 | Статус карты | `INACTIVE` | негативный | `status = INACTIVE` | DECLINED, `CARD_INACTIVE` |
| EC-AUTH-05 | Статус карты | `BLOCKED` | негативный | `status = BLOCKED` | DECLINED, `CARD_BLOCKED` |
| EC-AUTH-06 | Статус карты | `EXPIRED` | негативный | `status = EXPIRED` | DECLINED, `responseCode = "54"` |
| EC-AUTH-07 | Срок действия | `expiryDate >=` текущего месяца | позитивный | MMYY текущего месяца | Проверки продолжаются |
| EC-AUTH-08 | Срок действия | `expiryDate <` текущего месяца | негативный | MMYY предыдущего месяца | DECLINED, `responseCode = "54"` |
| EC-AUTH-09 | Формат PAN | корректный (16 цифр, Луна) | позитивный | валидный PAN | Проверки продолжаются |
| EC-AUTH-10 | Формат PAN | неверная длина | негативный | PAN из 15 или 17 цифр | DECLINED, `responseCode = "12"` |
| EC-AUTH-11 | Формат PAN | не проходит Луна | негативный | 16 цифр, неверная контрольная | DECLINED, `responseCode = "14"` |
| EC-AUTH-12 | Сумма vs `dailyLimit` | сумма укладывается | позитивный | `usage + amount <= dailyLimit` | Проверки продолжаются |
| EC-AUTH-13 | Сумма vs `dailyLimit` | сумма превышает | негативный | `usage + amount > dailyLimit` | DECLINED, `responseCode = "61"` |
| EC-AUTH-14 | Сумма vs `monthlyLimit` | сумма укладывается | позитивный | `mUsage + amount <= monthlyLimit` | Проверки продолжаются |
| EC-AUTH-15 | Сумма vs `monthlyLimit` | сумма превышает | негативный | `mUsage + amount > monthlyLimit` | DECLINED, `responseCode = "61"` |
| EC-AUTH-16 | Сумма vs `availableBalance` | средств достаточно | позитивный | `amount <= availableBalance` | APPROVED, `responseCode = "00"` |
| EC-AUTH-17 | Сумма vs `availableBalance` | средств недостаточно | негативный | `amount > availableBalance` | DECLINED, `responseCode = "51"` |
| EC-AUTH-18 | Доступность Card Management | доступен | позитивный | CMS отвечает 200 | Решение выносится по алгоритму |
| EC-AUTH-19 | Доступность Card Management | недоступен / таймаут | негативный | CMS не отвечает | DECLINED, `responseCode = "05"`, `ISSUER_TIMEOUT` |
| EC-AUTH-20 | Доступность Bin Lookup | доступен | позитивный | bin-lookup отвечает 200 | `issuerId` из bin-lookup |
| EC-AUTH-21 | Доступность Bin Lookup | недоступен | позитивный (graceful degradation) | bin-lookup не отвечает | Fallback на `issuerId` из BIN-таблицы, решение выносится |

### 1.2 Card-Management Service

| ID | Поле / условие | Класс | Тип | Представитель | Ожидаемый результат |
|---|---|---|---|---|---|
| EC-CM-01 | `limit` (пагинация) | валидный (в допустимом диапазоне) | позитивный | `limit = 50` | 200, не более `limit` карт |
| EC-CM-02 | `limit` (пагинация) | невалидный (`<= 0`) | негативный | `limit = 0` | 400 |
| EC-CM-03 | `offset` (пагинация) | валидный (`>= 0`) | позитивный | `offset = 0` | 200 |
| EC-CM-04 | `offset` (пагинация) | невалидный (`< 0`) | негативный | `offset = -1` | 400 |
| EC-CM-05 | Фильтр `status` | известный статус | позитивный | `status = ACTIVE` | 200, только карты этого статуса |
| EC-CM-06 | Фильтр `status` | неизвестный статус | негативный | `status = FOO` | 400 или пустой список |
| EC-CM-07 | Фильтр `bin` | известный BIN | позитивный | `bin = 400000` | 200, только карты этого BIN |
| EC-CM-08 | Фильтр `bin` | неизвестный BIN | негативный | `bin = 999999` | 200, пустой список |
| EC-CM-09 | `bin` при создании | корректный (6 цифр) | позитивный | `bin = 400000` | 201, PAN из 16 цифр, статус `ACTIVE` |
| EC-CM-10 | `bin` при создании | некорректный (не 6 цифр) | негативный | `bin = 40000` | 400 |
| EC-CM-11 | `initialBalance` | положительный | позитивный | `initialBalance = 100000000` | 201, `availableBalance` равен значению |
| EC-CM-12 | `initialBalance` | нулевой | негативный | `initialBalance = 0` | 400 |
| EC-CM-13 | `initialBalance` | отрицательный | негативный | `initialBalance = -1` | 400 |
| EC-CM-14 | `dailyLimit` (создание) | положительный | позитивный | `dailyLimit = 15000000` | 201, `dailyLimit` сохранён |
| EC-CM-15 | `monthlyLimit` (создание) | положительный | позитивный | `monthlyLimit = 300000000` | 201, `monthlyLimit` сохранён |
| EC-CM-16 | `dailyLimit` (создание) | неположительный | негативный | `dailyLimit = 0` | 400 |
| EC-CM-17 | `monthlyLimit` (создание) | неположительный | негативный | `monthlyLimit = 0` | 400 |
| EC-CM-18 | `PATCH /api/cards/{pan}` | частичное обновление одного поля | позитивный | `{"status":"BLOCKED"}` | 200, изменено только это поле |
| EC-CM-19 | `PATCH /api/cards/{pan}` | пустое тело | негативный | `{}` | 400 |
| EC-CM-20 | `PATCH /api/cards/{pan}` | неизвестное поле | негативный | `{"foo": 1}` | 400 или поле проигнорировано |
| EC-CM-21 | `GET /api/cards/{pan}` | карта существует | позитивный | существующий PAN | 200 |
| EC-CM-22 | `GET /api/cards/{pan}` | карта отсутствует | негативный | отсутствующий PAN | 404 |
| EC-CM-23 | `DELETE /api/cards/{pan}` | карта существует | позитивный | существующий PAN | 200, `status = DELETED`, повторный GET -> 404 |
| EC-CM-24 | `DELETE /api/cards/{pan}` | карта уже удалена | негативный | повторный DELETE | 404 |
| EC-CM-25 | `POST /api/cards/generate` | `count` в допустимом диапазоне | позитивный | `count = 100` | 201, создано `count` карт |
| EC-CM-26 | `POST /api/cards/generate` | `count = 0` | негативный | `count = 0` | 400 |
| EC-CM-27 | `POST /api/cards/generate` | отрицательный `count` | негативный | `count = -1` | 400 |
| EC-CM-28 | `POST /api/cards/generate` | непустой список `bins` | позитивный | `bins = ["400000","400001"]` | карты распределены по BIN |
| EC-CM-29 | `POST /api/cards/generate` | пустой список `bins` | негативный | `bins = []` | 400 |

---

## 2. Граничные значения (границы, BVA)

**Методика (BVA):** для каждой границы берётся три точки - **ON** (значение ровно на границе), **OFF ниже** (на единицу меньше) и **OFF выше** (на единицу больше). Все денежные суммы - в минорных единицах (копейки): 1  р = 100. Для сравнений используется неравенство из ТЗ.

### 2.1 Authorization Service

Базовые значения для примеров: `dailyLimit = 15000000` (150 000  р), `monthlyLimit = 300000000`, `availableBalance = 100000000`.

| ID | Граница | ON (ровно на границе) | OFF ниже | OFF выше | Ожидание |
|---|---|---|---|---|---|
| BV-AUTH-01 | Дневной лимит: `usage + amount <= dailyLimit` | `usage + amount = 15000000` | `usage + amount = 14999999` | `usage + amount = 15000001` | ON/OFF ниже -> APPROVED; OFF выше -> DECLINED, `"61"` |
| BV-AUTH-02 | Месячный лимит: `mUsage + amount <= monthlyLimit` | `mUsage + amount = 300000000` | `mUsage + amount = 299999999` | `mUsage + amount = 300000001` | ON/OFF ниже -> APPROVED; OFF выше -> DECLINED, `"61"` |
| BV-AUTH-03 | Баланс: `amount <= availableBalance` | `amount = availableBalance = 100000000` | `amount = 99999999` | `amount = 100000001` | ON (баланс станет 0) / OFF ниже -> APPROVED; OFF выше -> DECLINED, `"51"` |
| BV-AUTH-04 | Накопление дневного расхода (после серии одобренных транзакций) | `daily_amount = dailyLimit` | `daily_amount = dailyLimit - 1` | `daily_amount = dailyLimit + 1` | На границе и ниже - следующая операция ещё проверяется; выше - все последующие отклоняются, `"61"` |
| BV-AUTH-05 | Срок действия `expiryDate` (MMYY), первый день месяца | MMYY = текущий месяц | MMYY = предыдущий месяц | MMYY = следующий месяц | ON/след. месяц -> проверки продолжаются; предыдущий месяц -> DECLINED, `"54"` |
| BV-AUTH-06 | Срок действия `expiryDate` (MMYY), последний день месяца | MMYY = текущий месяц (последний день) | MMYY = прошлый месяц (последний день) | MMYY = следующий месяц (первый день) | Граница по календарю: смена месяца не должна ломать сравнение MMYY |
| BV-AUTH-07 | Смена года в `expiryDate` MMYY | `MMYY = "1226"` (декабрь 2026) | `MMYY = "1126"` | `MMYY = "0127"` (январь 2027) | Проверка корректно переходит через границу года (YY меняет разрядность) |
| BV-AUTH-08 | Длина PAN | 16 цифр | 15 цифр | 17 цифр | 16 -> обрабатывается; 15/17 -> DECLINED, `"12"` |
| BV-AUTH-09 | Минимальная сумма транзакции | `amount = 1` (1 копейка) | `amount = 0` | `amount = 2` | Ненулевая малая сумма обрабатывается; `amount = 0` - граница вырожденного кейса |

### 2.2 Card-Management Service

| ID | Граница | ON (ровно на границе) | OFF ниже | OFF выше | Ожидание |
|---|---|---|---|---|---|
| BV-CM-01 | Длина PAN при создании | 16 цифр | 15 цифр | 17 цифр | 16 -> 201; 15/17 -> 400 |
| BV-CM-02 | Диапазон дневного лимита в генераторе | 5000000 (50 000  р, минимум) | 4999999 | 30000000 (300 000  р, максимум) | Значения вне диапазона не генерируются |
| BV-CM-03 | Диапазон баланса в генераторе | 1000000 (10 000  р, минимум) | 999999 | 50000000 (500 000  р, максимум) | Значения вне диапазона не генерируются |
| BV-CM-04 | `limit` пагинации (минимум) | `limit = 1` | `limit = 0` | `limit = 2` | `limit >= 1` -> 200; `limit = 0` -> 400 |
| BV-CM-05 | `offset` пагинации (минимум) | `offset = 0` | `offset = -1` | `offset = 1` | `offset >= 0` -> 200; `offset = -1` -> 400 |
| BV-CM-06 | `count` в генераторе (минимум) | `count = 1` | `count = 0` | `count = 2` | `count >= 1` -> 201; `count = 0` -> 400 |
| BV-CM-07 | Срок действия новой карты | `expiryDate =` текущая дата + 3 года (MMYY) | на месяц раньше | на месяц позже | Ровно +3 года от текущего месяца |

---

## 3. Попарное тестирование (pairwise)

**Инструмент:** PICT.

```text
pict model.txt > cases.txt
```

### 3.1 Параметры модели

| Параметр | Значения | Что моделирует |
|---|---|---|
| `card_found` | `found`, `not_found` | существование карты в CMS (шаг 1 алгоритма) |
| `card_status` | `ACTIVE`, `INACTIVE`, `BLOCKED`, `EXPIRED` | статус карты (шаг 2) |
| `pan_valid` | `valid`, `invalid` | корректность PAN (длина 16 + Луна) |
| `expiry` | `valid`, `current_month`, `expired` | срок действия `expiryDate` (MMYY, шаг 3) |
| `amount_vs_daily` | `below`, `equal`, `above` | сумма относительно дневного лимита (шаг 4) |
| `amount_vs_monthly` | `below`, `equal`, `above` | сумма относительно месячного лимита (шаг 5) |
| `amount_vs_balance` | `below`, `equal`, `above` | сумма относительно доступного баланса (шаг 6) |

### 3.2 Ограничения модели (constraints)

Порядок проверок в Authorization строгий, поэтому невозможные сочетания исключаются ограничениями: если сработала более ранняя проверка, более поздние (лимиты и баланс) не проверяются.

```text
IF [pan_valid] = "invalid" THEN [card_found] = "not_found";
IF [card_found] = "not_found" THEN [card_status] = "ACTIVE";
IF [card_found] = "not_found" THEN [expiry] = "valid";
IF [card_found] = "not_found" THEN [amount_vs_daily] = "below"
    AND [amount_vs_monthly] = "below" AND [amount_vs_balance] = "below";
IF [card_status] <> "ACTIVE" THEN [amount_vs_daily] = "below"
    AND [amount_vs_monthly] = "below" AND [amount_vs_balance] = "below";
IF [expiry] = "expired" THEN [amount_vs_daily] = "below"
    AND [amount_vs_monthly] = "below" AND [amount_vs_balance] = "below";
IF [pan_valid] = "invalid" THEN [amount_vs_daily] = "below"
    AND [amount_vs_monthly] = "below" AND [amount_vs_balance] = "below";
```

---

## 4. Тест-кейсы

Каждый тест-кейс фиксирует: идентификатор и связанное требование; вид (позитивный/негативный); предусловие, шаги и ожидаемый результат; источник (класс эквивалентности, граница или строка попарного набора).

**Правила:** несколько валидных значений допустимо объединять в один кейс; несколько невалидных - нельзя (один невалидный вход на кейс).

### 4.1 Тест-кейсы на основе классов эквивалентности и границ

| ID | Вид | Источник | Входные данные | Ожидаемый результат |
|---|---|---|---|---|
| TC-01 | позитивный | EC-AUTH-03 | Активная карта, сумма в пределах лимитов и баланса | APPROVED, `"00"`, `RRN` и `authCode` сгенерированы, баланс уменьшен |
| TC-02 | негативный | EC-AUTH-02 | PAN из 16 цифр, отсутствующий в CMS | DECLINED, `"14"` |
| TC-03 | негативный | EC-AUTH-04 | Карта `INACTIVE` | DECLINED, `CARD_INACTIVE` |
| TC-04 | негативный | EC-AUTH-05 | Карта `BLOCKED` | DECLINED, `CARD_BLOCKED` |
| TC-05 | негативный | EC-AUTH-06 | Карта `EXPIRED` | DECLINED, `"54"` |
| TC-06 | негативный | EC-AUTH-08 | `expiryDate` = предыдущий месяц (MMYY) | DECLINED, `"54"` |
| TC-07 | негативный | EC-AUTH-13 | `usage + amount > dailyLimit` | DECLINED, `"61"` |
| TC-08 | негативный | EC-AUTH-15 | `mUsage + amount > monthlyLimit` | DECLINED, `"61"` |
| TC-09 | негативный | EC-AUTH-17 | `amount > availableBalance` | DECLINED, `"51"` |
| TC-10 | негативный | EC-AUTH-19 | Card Management недоступен | DECLINED, `"05"`, `ISSUER_TIMEOUT` |
| TC-11 | негативный | EC-AUTH-11 | PAN не проходит алгоритм Луна | DECLINED, `"14"` |
| TC-12 | негативный | EC-AUTH-10 | PAN длиной 15 цифр | DECLINED, `"12"` |
| TC-13 | позитивный | EC-AUTH-21 | bin-lookup недоступен | Fallback на `issuerId` из BIN-таблицы, решение выносится |
| TC-14 | позитивный | BV-AUTH-01 (ON) | `usage + amount = dailyLimit` | APPROVED, `"00"` |
| TC-15 | негативный | BV-AUTH-01 (OFF выше) | `usage + amount = dailyLimit + 1` | DECLINED, `"61"` |
| TC-16 | позитивный | BV-AUTH-03 (ON) | `amount = availableBalance` | APPROVED, `"00"`, `availableBalance = 0` |
| TC-17 | негативный | BV-AUTH-03 (OFF выше) | `amount = availableBalance + 1` | DECLINED, `"51"` |
| TC-18 | позитивный | BV-AUTH-05 (ON) | `expiryDate` = текущий месяц (MMYY) | Проверки продолжаются |
| TC-19 | негативный | BV-AUTH-07 | `MMYY = "1126"` при текущем `"1226"` (граница года) | DECLINED, `"54"` |
| TC-20 | позитивный | EC-CM-09, EC-CM-11 | `bin = 400000`, `initialBalance = 100000000` | 201, PAN из 16 цифр, `status = ACTIVE`, баланс сохранён |
| TC-21 | негативный | EC-CM-10 | `bin = 40000` (5 цифр) | 400 |
| TC-22 | негативный | EC-CM-12 | `initialBalance = 0` | 400 |
| TC-23 | позитивный | EC-CM-14 | `dailyLimit = 15000000` (положительный) | 201, `dailyLimit` сохранён |
| TC-24 | негативный | EC-CM-16 | `dailyLimit = 0` | 400 |
| TC-25 | позитивный | EC-CM-15 | `monthlyLimit = 300000000` (положительный) | 201, `monthlyLimit` сохранён |
| TC-26 | негативный | EC-CM-17 | `monthlyLimit = 0` | 400 |
| TC-27 | негативный | EC-CM-22 | PAN несуществующей карты | 404 |
| TC-28 | позитивный | EC-CM-23 | Существующая карта | 200, `status = DELETED`, повторный GET -> 404 |
| TC-29 | позитивный | EC-CM-25 | `count = 100`, `bins = ["400000",...,"400004"]` | 201, создано 100 карт, распределены по BIN |
| TC-30 | негативный | EC-CM-26 | `count = 0` | 400 |
| TC-31 | негативный | BV-CM-04 (OFF ниже) | `limit = 0` | 400 |
| TC-32 | негативный | BV-CM-05 (OFF ниже) | `offset = -1` | 400 |
| TC-33 | негативный | BV-CM-01 (OFF ниже) | PAN длиной 15 цифр | 400 |

### 4.2 Тест-кейсы из попарного набора (PICT)

| № строки `cases.txt` | `card_found` | `card_status` | `pan_valid` | `expiry` | `amount_vs_daily` | `amount_vs_monthly` | `amount_vs_balance` | TC ID | Ожидаемый результат |
|---|---|---|---|---|---|---|---|---|---|
| 1 | found | BLOCKED | valid | expired | below | below | below | TC-PW-01 | DECLINED, `CARD_BLOCKED` |
| 2 | found | INACTIVE | valid | current_month | below | below | below | TC-PW-02 | DECLINED, `CARD_INACTIVE` |
| 3 | found | ACTIVE | valid | valid | equal | equal | above | TC-PW-03 | DECLINED, `"51"` |
| 4 | found | INACTIVE | valid | valid | below | below | below | TC-PW-04 | DECLINED, `CARD_INACTIVE` |
| 5 | found | BLOCKED | valid | valid | below | below | below | TC-PW-05 | DECLINED, `CARD_BLOCKED` |
| 6 | found | ACTIVE | valid | current_month | above | below | above | TC-PW-06 | DECLINED, `"61"` |
| 7 | found | EXPIRED | valid | expired | below | below | below | TC-PW-07 | DECLINED, `"54"` |
| 8 | found | ACTIVE | valid | valid | below | above | equal | TC-PW-08 | DECLINED, `"61"` |
| 9 | found | ACTIVE | valid | current_month | equal | below | equal | TC-PW-09 | APPROVED, `"00"` |
| 10 | found | EXPIRED | valid | current_month | below | below | below | TC-PW-10 | DECLINED, `"54"` |
| 11 | found | ACTIVE | valid | valid | above | above | below | TC-PW-11 | DECLINED, `"61"` |
| 12 | found | ACTIVE | valid | expired | below | below | below | TC-PW-12 | DECLINED, `"54"` |
| 13 | found | ACTIVE | valid | current_month | above | equal | equal | TC-PW-13 | DECLINED, `"61"` |
| 14 | found | EXPIRED | valid | valid | below | below | below | TC-PW-14 | DECLINED, `"54"` |
| 15 | found | BLOCKED | valid | current_month | below | below | below | TC-PW-15 | DECLINED, `CARD_BLOCKED` |
| 16 | found | ACTIVE | valid | valid | below | equal | below | TC-PW-16 | APPROVED, `"00"` |
| 17 | found | ACTIVE | valid | current_month | below | above | above | TC-PW-17 | DECLINED, `"61"` |
| 18 | not_found | ACTIVE | valid | valid | below | below | below | TC-PW-18 | DECLINED, `"14"` |
| 19 | not_found | ACTIVE | invalid | valid | below | below | below | TC-PW-19 | DECLINED, `"14"` |
| 20 | found | INACTIVE | valid | expired | below | below | below | TC-PW-20 | DECLINED, `CARD_INACTIVE` |
| 21 | found | ACTIVE | valid | valid | equal | above | below | TC-PW-21 | DECLINED, `"61"` |


### 4.3 Вручную добавленные критичные сочетания

Попарное покрытие не гарантирует покрытие троек, поэтому в набор вручную добавляются критичные сочетания:

| ID | Сочетание | Ожидаемый результат |
|---|---|---|
| TC-MAN-01 | `card_status = ACTIVE` + `amount_vs_daily = above` + `amount_vs_balance = below` | DECLINED, `"61"` (лимит проверяется раньше баланса) |
| TC-MAN-02 | `card_status = ACTIVE` + `amount_vs_daily = below` + `amount_vs_monthly = above` | DECLINED, `"61"` |
| TC-MAN-03 | `card_status = ACTIVE` + `amount_vs_daily = below` + `amount_vs_balance = above` | DECLINED, `"51"` |
| TC-MAN-04 | `expiry = expired` + `amount_vs_balance = below` | DECLINED, `"54"` (срок проверяется раньше баланса) |