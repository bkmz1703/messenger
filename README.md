# Месенжер

Нативный Android-мессенджер на Kotlin + Jetpack Compose с Firebase в качестве бэкенда.
Чаты 1-на-1 и групповые, медиа (фото / видео / файлы / голосовые), статусы (24-часовые истории),
аудио/видео-звонки через WebRTC, регистрация по нику или по номеру телефона.

> **Доступен и работает в России.** Все используемые сервисы (Firebase, Cloudinary, Google STUN)
> открываются с любого российского оператора без VPN.

---

## Скачать APK

Последний релиз: **[v0.4.0 — phone auth + calls FAB + official channel](https://github.com/bkmz1703/messenger/releases/latest)**

[![Скачать APK](https://img.shields.io/badge/Скачать_APK-67_МБ-1976d2?style=for-the-badge&logo=android&logoColor=white)](https://github.com/bkmz1703/messenger/releases/latest/download/messenger.apk)

**Прямая ссылка на файл:** <https://github.com/bkmz1703/messenger/releases/latest/download/messenger.apk>

### Как установить
1. Откройте ссылку выше **на телефоне** в любом браузере (Chrome, Yandex, Samsung Internet).
2. Файл скачается в «Загрузки» (или через уведомление о скачивании).
3. Откройте файл → Android спросит разрешение на установку из неизвестных источников → разрешите для браузера/проводника.
4. Нажмите **Установить**.

> **Скачивание из России:** github.com обычно открывается у всех российских операторов
> без VPN. Если у вас не получилось — попробуйте сменить Wi-Fi на мобильный интернет
> (или наоборот) или используйте VPN **только** для скачивания. Само приложение после
> установки работает без VPN.

> **Если у вас уже стояла предыдущая сборка** — перед установкой:
> Настройки → Приложения → Месенжер → Хранилище → **Очистить данные** →
> удалить старую версию. С v0.4.0 мы перешли на стабильный отладочный ключ, и Android
> блокирует установку поверх версии, подписанной другим ключом.

### Скриншоты
*(скриншоты добавим в следующих релизах)*

---

## Что умеет

- 🔐 Анонимный вход по нику + регистрация по номеру телефона (Firebase Phone Auth)
- 💬 Чаты 1-на-1 и групповые с read-receipt
- 📷 Фото, видео, файлы, голосовые сообщения (hold-to-record), Cloudinary в качестве хостинга
- 🟢 Статусы с фоном или картинкой, авто-удаление через 24 часа
- 📞 Аудио и видео-звонки через WebRTC + Firestore signaling
- 📢 Официальный канал разработчика — read-only для всех, пишет только пользователь с ником `General_оф`
- 🎨 Смена аватара (цвет или фото из галереи) с пропагацией во все ваши чаты
- 🗑️ Удаление чатов, переименование, смена иконки группы

---

## Стек

| Слой | Технологии |
| --- | --- |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Хранение / Auth | Firebase Auth (Anonymous + Phone), Firestore, Cloud Messaging |
| Медиа-хостинг | [Cloudinary](https://cloudinary.com) (free-tier, unsigned upload) |
| Звонки | WebRTC (`io.getstream:stream-webrtc-android`) + Firestore signaling |
| Изображения | Coil |
| Локальные настройки | Jetpack DataStore |

`minSdk 24`, `targetSdk 34`, JDK 17, Kotlin 2.0, Compose Compiler plugin 2.0+.

---

## Сборка из исходников

```bash
git clone https://github.com/bkmz1703/messenger.git
cd messenger
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

Для запуска на устройстве/эмуляторе:

```bash
./gradlew :app:installDebug
```

### Перед сборкой нужно настроить Firebase

1. Создайте проект в [Firebase Console](https://console.firebase.google.com).
2. **Authentication → Sign-in method → Anonymous → Enable** (и **Phone → Enable**, если нужна phone-регистрация).
3. **Firestore Database → Create database** (production mode). Импортируйте правила из [`firebase/firestore.rules`](firebase/firestore.rules) и индексы из [`firebase/firestore.indexes.json`](firebase/firestore.indexes.json).
4. **Cloud Messaging** — включается автоматически.
5. Добавьте Android-приложение с **package name `ru.ilyakirollov.messenger`**, скачайте `google-services.json` и положите в `app/google-services.json`.
6. Для phone-auth добавьте SHA-1 отпечаток вашего отладочного ключа в Firebase: Project settings → Your apps → Add fingerprint.
7. (Опционально, Blaze) задеплойте Cloud Function из [`firebase/functions`](firebase/functions) для FCM-пушей при новых сообщениях.

### Cloudinary (медиа-аплоады)

1. Зарегистрируйтесь на [cloudinary.com](https://cloudinary.com/users/register_free).
2. **Settings → Upload → Upload presets → Add upload preset**, **Signing Mode = Unsigned**.
3. Положите в `local.properties`:
   ```properties
   cloudinary.cloudName=<ваш cloud name>
   cloudinary.uploadPreset=<имя preset>
   ```

---

## Структура

```
app/src/main/java/ru/ilyakirollov/messenger/
├── MessengerApp.kt              # Application + Hilt + notification channels
├── MainActivity.kt              # Single-Activity Compose host
├── di/                          # Hilt modules (Firebase singletons)
├── data/
│   ├── model/                   # Firestore POKO модели
│   ├── prefs/                   # DataStore (UserPreferences)
│   └── repository/              # Auth / User / Chat / Status / Call repositories
├── notifications/               # FCM service
├── call/                        # WebRTC session manager + foreground service
├── util/                        # VoiceRecorder, Format helpers
└── ui/
    ├── theme/                   # Material 3 theme
    ├── auth/                    # LoginScreen + AuthViewModel (анонимный + phone вход)
    ├── home/                    # HomeScreen (4 таба)
    ├── chats/                   # Список чатов
    ├── chat/                    # Экран чата
    ├── newchat/                 # Поиск пользователей и создание группы
    ├── status/                  # Статусы
    ├── calls/                   # История звонков
    ├── call/                    # Экран звонка
    └── profile/                 # Профиль
```

---

## Дорожная карта

- [ ] Cloud Functions для FCM-пушей о новых сообщениях (нужен Blaze)
- [ ] TURN-сервер для звонков на симметричных NAT
- [ ] E2E-шифрование сообщений
- [ ] Удаление сообщений, реакции, ответы, пересылка
- [ ] Релиз в RuStore + российское зеркало APK на Yandex Cloud
- [ ] iOS-клиент

## Лицензия

Apache-2.0
