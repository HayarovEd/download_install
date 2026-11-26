# Download & Install

Android-библиотека для проверки, фонового уведомления, загрузки и установки APK-обновлений приложений без Google Play.

## Возможности

- Проверка новой версии через JSON-эндпоинт сервера (`versionUrl`).
- Загрузка APK с сервера (`downloadUrl`) как внутри приложения (с прогрессом через `Flow<DownloadStatus>`), так и в фоне через `WorkManager`.
- Периодическая фоновая проверка обновлений через `WorkManager` (интервал от 1 часа) с дедупликацией уведомлений по версии.
- Системное уведомление о наличии новой версии с кнопкой **«Загрузить»** (старт скачивания прямо из уведомления) и переходом в приложение по нажатию.
- Автоматический запуск установки APK (через `FileProvider`) и кнопка установки в уведомлении после завершения фоновой загрузки.
- Локализация уведомлений и текстов ошибок (`values` / `values-ru`).

## Подключение (JitPack)

1. Добавьте JitPack в `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. Подключите библиотеку в `build.gradle.kts` модуля приложения:

```kotlin
dependencies {
    implementation("com.github.HayarovEd:download_install:1.0.2")
}
```

## Разрешения

Библиотека уже объявляет в своём манифесте `INTERNET`, `POST_NOTIFICATIONS` и `REQUEST_INSTALL_PACKAGES`.  
На Android 13+ (API 33+) приложение должно запросить runtime-разрешение `Manifest.permission.POST_NOTIFICATIONS` для показа уведомлений.

## Формат ответа сервера (`versionUrl`)

Библиотека ожидает JSON следующего вида (полями `group.name` и `name` формируется версия `${group.name}.${name}`, например `1.012`):

```json
{
  "id": "...",
  "name": "012",
  "hasFile": true,
  "createdDate": "2026-11-26T10:00:00Z",
  "group": {
    "id": "...",
    "name": "1",
    "app": {
      "id": "...",
      "name": "MyApp"
    }
  }
}
```

## Использование

Адреса проверки версии и загрузки APK передаются из приложения-потребителя и могут быть полностью разными:

```kotlin
val config = UpdateConfig(
    versionUrl = "https://example.com/api/app/version",
    downloadUrl = "https://cdn.example.com/releases/app-release.apk",
    downloadedFileName = "app_update.apk",
    checkIntervalHours = 1L
)

val downloadRepo = DownloadProvider.provideDownloader(application = application)
val installer = DownloadProvider.provideInstaller(application = application)
val refresher = DownloadProvider.provideRefresher(
    application = application,
    downloadRepository = downloadRepo,
    installer = installer
)
```

### 1. Фоновая проверка обновлений

Запустите планировщик в `Application.onCreate()`:

```kotlin
refresher.schedulePeriodicUpdateCheck(config)
```

Или передайте разные URL напрямую:

```kotlin
refresher.schedulePeriodicUpdateCheck(
    versionUrl = "https://example.com/api/app/version",
    downloadUrl = "https://cdn.example.com/releases/app-release.apk",
    downloadedFileName = "app_update.apk",
    repeatIntervalHours = 1L
)
```

При нажатии на тело уведомления библиотека открывает launch-активити приложения с `Intent`-экстрами:
- `EXTRA_OPEN_UPDATE` (`Boolean`) — `true`
- `EXTRA_LATEST_VERSION` (`String`) — номер новой версии

При нажатии на кнопку **«Загрузить»** в уведомлении автоматически запускается фоновая загрузка APK через `DownloadUpdateWorker`.

### 2. Ручная проверка и загрузка

```kotlin
when (val result = refresher.getLastVersion(config)) {
    is ResultDownloadWork.Success -> {
        val remoteVersion = result.data.lastVersion
        val currentVersion = context.getCurrentAppVersionName().orEmpty()
        if (isNewVersionAvailable(currentVersion, remoteVersion)) {
            // Скачивание в приложении или в фоне:
            refresher.updateInApp(config).collect { status ->
                // DownloadStatus.Started / InProgress / Success / Error
            }
        }
    }
    is ResultDownloadWork.Error -> {
        val message = result.error.asUiResultText().asString(context)
    }
}
```

## Тестирование публикации локально

```bash
./gradlew :download_install:testDebugUnitTest
./gradlew :download_install:publishReleasePublicationToMavenLocal
```
