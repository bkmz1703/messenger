# Месенжер (Messenger)

Нативный Android-мессенджер на Kotlin + Jetpack Compose с Firebase в качестве бэкенда. Анонимный вход по нику, чаты 1-на-1 и групповые, медиа (фото / видео / файлы / голосовые), статусы (24-часовые истории) и аудио/видео-звонки через WebRTC c сигналингом по Firestore.

## Стек

| Слой | Технологии |
| --- | --- |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Хранение / Auth | Firebase Auth (Anonymous), Firestore, Cloud Messaging |
| Медиа-хостинг | [Cloudinary](https://cloudinary.com) (free-tier, unsigned upload) |
| Звонки | WebRTC (`io.getstream:stream-webrtc-android`) + Firestore signaling |
| Изображения | Coil |
| Локальные настройки | Jetpack DataStore |

`minSdk 24`, `targetSdk 34`, JDK 17, Kotlin 2.0, Compose Compiler plugin 2.0+.

## Структура

```
app/src/main/java/ru/ilyakirollov/messenger/
├── MessengerApp.kt              # Application + Hilt + notification channels
├── MainActivity.kt              # Single-Activity Compose host
├── di/                          # Hilt modules (Firebase singletons)
├── data/
│   ├── model/                   # Firestore POKO модели (User, Chat, Message, Status, CallSession)
│   ├── prefs/                   # DataStore (UserPreferences)
│   └── repository/              # Auth / User / Chat / Status / Call repositories
├── notifications/               # FCM service
├── call/                        # WebRTC session manager + foreground service
├── util/                        # VoiceRecorder, Format helpers
└── ui/
    ├── theme/                   # Material 3 theme
    ├── auth/                    # LoginScreen + AuthViewModel (анонимный вход)
    ├── home/                    # HomeScreen (4 таба: чаты, статусы, звонки, профиль)
    ├── chats/                   # Список чатов
    ├── chat/                    # Экран чата (текст, медиа, голосовые, звонки)
    ├── newchat/                 # Поиск пользователей и создание группы
    ├── status/                  # Статусы: список, композер, viewer (24ч)
    ├── calls/                   # История звонков
    ├── call/                    # Экран звонка (аудио / видео, WebRTC)
    └── profile/                 # Профиль (смена ника, выход)
```

## Настройка Firebase (обязательно перед запуском)

1. Создайте проект в [Firebase Console](https://console.firebase.google.com).
2. **Authentication → Sign-in method → Anonymous → Enable.**
3. **Firestore Database → Create database** (любой регион, production mode). Импортируйте правила из [`firebase/firestore.rules`](firebase/firestore.rules) и индексы из [`firebase/firestore.indexes.json`](firebase/firestore.indexes.json).
4. **Cloud Messaging** — включается автоматически, отдельных настроек не нужно.
5. Добавьте Android-приложение в проекте Firebase с **package name `ru.ilyakirollov.messenger`**, скачайте `google-services.json` и положите в `app/google-services.json` (файл в `.gitignore`, в репозиторий не попадает).
6. (Опционально, требует Blaze-плана) задеплойте Cloud Function из [`firebase/functions`](firebase/functions) — она шлёт FCM-уведомления собеседникам при появлении новых сообщений и звонков. Без неё пуши работать не будут, остальное — да.

После добавления `google-services.json` Gradle-плагин включится автоматически (см. [`app/build.gradle.kts`](app/build.gradle.kts)).

> Firebase Storage **не используется**. Для медиа-файлов (фото/видео/голосовые/файлы) используется Cloudinary — см. ниже.

## Настройка Cloudinary (медиа-аплоады)

Cloudinary заменяет Firebase Storage и работает на free-tier без привязки карты.

1. Зарегистрируйтесь на [cloudinary.com](https://cloudinary.com/users/register_free) (можно через Google).
2. На Dashboard в правом верхнем углу запишите **Cloud name** (вида `dxx123abc`).
3. **Settings → Upload → Upload presets → Add upload preset.** Имя — например `messenger_unsigned`, **Signing Mode = Unsigned** (важно). Сохраните.
4. Положите в файл `local.properties` (он gitignored, лежит рядом с `settings.gradle.kts`) две строки:
   ```properties
   cloudinary.cloudName=<ваш cloud name>
   cloudinary.uploadPreset=messenger_unsigned
   ```
5. Пересоберите проект — значения попадут в `BuildConfig.CLOUDINARY_CLOUD_NAME` / `BuildConfig.CLOUDINARY_UPLOAD_PRESET` и подхватятся в [`CloudinaryUploader`](app/src/main/java/ru/ilyakirollov/messenger/data/upload/CloudinaryUploader.kt).

Без этих настроек приложение собирается, но отправка медиа упадёт с ошибкой «Cloudinary не сконфигурирован».

## Сборка

```bash
# Установить Android SDK (cmdline-tools), platforms;android-34, build-tools;34.0.0
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

APK собирается в `app/build/outputs/apk/debug/app-debug.apk`.

Для установки на устройство/эмулятор:

```bash
./gradlew :app:installDebug
```

## Что уже работает в MVP

- [x] Анонимный вход по нику с авто-цветом аватара
- [x] Список чатов с непрочитанными счётчиками
- [x] Поиск пользователей по нику и создание 1-на-1 чата
- [x] Создание группового чата
- [x] Текстовые сообщения (read receipts через `readBy`)
- [x] Отправка фото / видео / файлов через системный пикер (через Cloudinary)
- [x] Запись и отправка голосовых сообщений (hold-to-record)
- [x] Статусы с фоном или картинкой; авто-исчезание через 24ч (фильтр на клиенте, GC через TTL)
- [x] Аудио и видео-звонки 1-на-1 через WebRTC + Firestore signaling
- [x] Управление микрофоном / камерой / громкой связью / переключение камер
- [x] Foreground service на время звонка (microphone+camera type)
- [x] FCM-сервис на клиенте (для серверных пушей)
- [x] История звонков
- [x] Смена ника и выход

## Что ещё стоит докрутить

- Серверный код Cloud Functions для отправки пушей при `messages` / `calls`. Заготовка в `firebase/functions`.
- TURN-сервер: текущая конфигурация WebRTC использует только публичные STUN-серверы Google. На реальной NAT-симметричной сети понадобится TURN (например, [Coturn](https://github.com/coturn/coturn) или managed-сервис).
- Шифрование E2E (текущая реализация шифрует только на TLS-уровне Firebase ↔ клиент).
- Удаление сообщений, реакции, ответы (reply), пересылка.
- Поддержка iOS — текущая кодовая база только Android.

## Лицензия

Apache-2.0
