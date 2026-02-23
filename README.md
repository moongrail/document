# Document Service Application

## Запуск приложения

1. **Запуск базы данных**
    - Используем терминал: `cd root -> docker-compose up`
    - Запустит PostgreSQL базу данных

2. **Запуск сервисов**
    - Заходим в Gradle проект
    - Для запуска основного сервиса `document-service` вызываем таску в document-root/application/`documentServiceRun`
    - Для запуска утилиты `generator` вызываем таску в document-root/application/`generatorRun`

3. **Порты приложений**
    - `document-service`: 80
    - `generator`: 81

## Проверка прогресса

- Логи утилиты `generator` можно отслеживать в консоли Gradle
- При успешном создании документов через генератор в логах будут сообщения вроде: Created documents [0 - 10]
- Логи `document-service` доступны в консоли Gradle или через лог-файл spam_logs, реализованы через AOP и дефолтно

## Описание функциональности

- `document-service` - основной сервис для работы с документами
- Есть воркеры которые запускаются для сабмита и апрува документов
- `generator` - утилита для массового создания документов

## Методы контроллеров

POSTMAN_COLLECTION.json, можно подёргать ручки в постмане для удобства, (для хеппи результата (воркеры будут мешать)
нужно проставлять статус DRAFT в базе у документа 1 id -> далее пройти весь цикл)

- `POST /documents` - создание нового документа
- `GET /documents/{id}` - получение документа с историей
- `GET /documents` - получение списка документов с фильтрацией и пагинацией
- `POST /documents/submit` - батч отправка документов на submit
- `POST /documents/approve` - батч одобрение документов на approve
- `POST /documents/approve/concurrent-spam` - спам метод параллельного concurrent
  одобрения(для постмана статус SUBMITTED у 1 id )
- `GET /documents/search` - поиск документов
- `POST /documents/create/batch ` - батч создание документов

- для норм цикла
 ```sql 
UPDATE document
set status = 'DRAFT'
WHERE id = 1;
```
- перед спаммом 
```sql
   UPDATE document
   set status = 'SUBMITTED'
   WHERE id = 1;
 ```