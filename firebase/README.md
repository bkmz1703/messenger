# Firebase configuration

Эту директорию можно скопировать в корень проекта Firebase (`firebase init` уже выполнен) и развернуть:

```bash
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

Содержимое:

- `firestore.rules` — security rules для Firestore. Разрешают только участникам чата читать/писать сообщения.
- `firestore.indexes.json` — составные индексы (нужны для запроса списка чатов и входящих звонков).
- `storage.rules` — security rules для Firebase Storage (медиа в чатах + статусы).
- `firebase.json` — манифест для CLI.
- `functions/` — Cloud Functions на Node 20: отправляют FCM-пуши при новых сообщениях и звонках.

Перед деплоем установите `firebase-tools` (`npm i -g firebase-tools`) и выполните `firebase login`.
