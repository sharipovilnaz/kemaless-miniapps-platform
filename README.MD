# MiniApp Platform

Платформа для централизованного управления и мониторинга miniapp, а также безопасного запуска miniapp из host-приложений через SSO.

В этом README описаны две части проекта:

- `core` - backend платформы MiniApp на Spring Boot;
- `miniapp-sdk` - Java SDK для backend-сервиса host-приложения.

## Какую проблему решает проект

Обычная интеграция miniapp с host-приложением быстро приводит к проблемам:

- host-приложение вынуждено передавать пользовательские данные и роли напрямую в miniapp;
- miniapp часто получает слишком много доверия к данным из браузера;
- logout из host-приложения не отзывает miniapp-сессии;
- разные host-приложения используют разные роли, и miniapp приходится знать чужие role codes;
- сложно централизованно проверять доступ, аудит и lifecycle токенов.

Платформа решает это через модель `Platform -> Realm -> Realm Client -> MiniApp`.

Host backend авторизуется в платформе по `realmCode + clientId + clientSecret`, получает host service token и вызывает launch endpoint.
Платформа проверяет realm, client, miniapp, role mapping и access policy, затем выдает короткоживущий одноразовый launch ticket.
В URL miniapp уходит только ticket. Miniapp обменивает ticket на свою miniapp-сессию через SSO exchange и получает собственные access/refresh tokens.

В итоге подключение внешнего приложения происходит 1 раз, а количество добавлений miniapp - неограничено.

## Требования

- Java 21
- Docker с Docker Compose
- Maven wrapper используется из репозитория: `mvnw` / `mvnw.cmd`

## Запуск miniApp platform 

### 0. Клонировать репозиторий 

```bash
git clone https://github.com/kemalthes/miniapp-platfrom.git
```

### 1. Подготовить `.env`

Минимально важные переменные:

```env
PLATFORM_ADMIN_USERNAME=admin
PLATFORM_ADMIN_PASSWORD=strong-password
PLATFORM_BACKEND_SECRET_KEY=very-long-secret-key-with-at-least-32-chars

POSTGRES_DATABASE=platform_db
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_PORT=15432
```

При первом старте miniApp platform создаст root-пользователя из env. При повторном запуске пароль и роли root-пользователя не перетираются.

### 2. Собрать backend jar

```bash
cd core
./mvnw clean package -DskipTests
cd ..
```

### 3. Запустить платформу через Docker Compose

```bash
docker compose up --build
```

По текущему `docker-compose.yml` сервис доступен на:

```text
http://localhost:8080
```

Admin UI и API находятся на одном host:

```text
Admin UI: http://localhost:8080
API:      http://localhost:8080/api
```

Данные для первого входа (если не переопределяли):

```text
username: admin
password: strong-password
```

## Настройка Realm и MiniApp в платформе

Перед использованием SDK нужно один раз подготовить данные в admin panel:

1. Войти в admin panel под root.
2. Создать `Realm`, например `bank`.
3. Сохранить выданные `clientId` и `clientSecret`.
4. Создать realm roles, например `USER`, `PREMIUM_CLIENT`, `VIP`.
5. Настроить role mappings:

```text
External host role -> Realm role
MB_PREMIUM         -> PREMIUM_CLIENT
CARD_OWNER         -> USER
BANK_VIP_2025      -> VIP
```

6. Создать или подключить MiniApp к Realm.
7. Указать `entryUrl` miniapp, например:

```text
http://localhost:8091
```

8. Настроить access policy:

Пример в формате JSON:
```json
{
  "accessType": "ROLE_BASED",
  "roleMatchMode": "ANY",
  "requiredRoles": ["PREMIUM_CLIENT", "VIP"]
}
```

9. Опубликовать MiniApp.

Важно: host-приложение отправляет свои external roles. Платформа сама маппит их в realm roles. Незамапленные роли не дают доступ.

## Установка miniapp-sdk

SDK пока используется как локальный Maven artifact.

PowerShell:

```powershell
cd miniapp-sdk
.\mvnw.cmd clean install -DskipTests
cd ..
```

Bash:

```bash
cd miniapp-sdk
./mvnw clean install -DskipTests
cd ..
```

После этого SDK можно подключить в Spring Boot backend host-приложения:

```xml
<dependency>
    <groupId>com.kemaless</groupId>
    <artifactId>miniapp-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Конфигурация SDK

SDK использует Spring Boot auto-configuration и создает bean `MiniAppPlatformClient`, если включена настройка `miniapp.platform.enabled`.

Пример `application.yaml` в host backend:

```yaml
miniapp:
  platform:
    enabled: true
    url: http://localhost:8080
    realm-code: bank
    client-id: default-bank
    client-secret: raw-secret-shown-once
    connect-timeout: PT5S
    read-timeout: PT30S
```

## Использование SDK

### Получить каталог опубликованных miniapp

```java
@Service
public class HostMiniAppService {

    private final MiniAppPlatformClient platformClient;

    public HostMiniAppService(MiniAppPlatformClient platformClient) {
        this.platformClient = platformClient;
    }

    public List<MiniAppInfo> catalog() {
        return platformClient.getCatalog();
    }
}
```

### Запустить miniapp для пользователя

```java
    public String launchCreditCalculator() {
        UserContext user = UserContext.builder()
                .externalUserId("user-123")
                .email("user@mail.com")
                .roles(List.of("MB_PREMIUM", "CARD_OWNER"))
                .attributes(Map.of(
                        "segment", "premium",
                        "city", "Kazan"
                ))
                .build();
        MiniAppLaunchRequest request = MiniAppLaunchRequest.builder()
                .user(user)
                .device(DeviceContext.builder()
                        .platform("web")
                        .deviceId("browser-session-1")
                        .build())
                .context(Map.of(
                        "theme", "dark",
                        "locale", "ru"
                ))
                .build();
        LaunchResult result = platformClient.launchMiniApp("credit-calculator", request);
        return result.getLaunchUrl();
    }
```

В URL находится только одноразовый ticket. Access token и refresh token туда не передаются.

### Logout пользователя из host-приложения

При logout host-приложение должно отозвать все miniapp-сессии пользователя в Realm:

```java
public void logout(String externalUserId) {
    platformClient.logoutUser(externalUserId);
}
```

### Introspection miniapp token

Если host backend или miniapp backend хочет проверить miniapp access token через платформу:

``` java
var result = platformClient.introspectToken(token);

if (!result.isActive()) {
    throw new IllegalStateException("MiniApp token is inactive");
}
```

## Observability

Также возникает задача отслеживать и мониторить miniapp.
Поэтому добавлен observability слой: технические логи, audit events и метрики.
А также — AI Assistant для анализа логов, метрик и audit events.
Его задача — помочь администраторам и разработчикам быстрее понимать, почему произошла ошибка.
Мы рассматриваем его как RAG-систему, ассистент может объяснить проблему простым языком.

В итоге:
Для администратора — это единая панель управления miniapp, ролями, публикацией и статистикой.

Для разработчика host-приложения — это одна интеграция через SDK вместо интеграции с каждым miniapp.

Для пользователя — это бесшовный запуск miniapp без повторного логина.

А для эксплуатации — это аудит, метрики, логи и AI Assistant для быстрого анализа ошибок.