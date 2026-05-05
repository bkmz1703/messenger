/* eslint-disable max-len */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Push a notification when a new message is sent in a chat. The function reads the chat
 * doc to find recipients (every participant except the sender) and forwards their FCM
 * tokens to FCM with a high-priority notification.
 */
exports.notifyOnMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;
    const chatRef = admin.firestore().collection("chats").doc(event.params.chatId);
    const chatSnap = await chatRef.get();
    const chat = chatSnap.data();
    if (!chat) return;

    const recipients = (chat.participants || []).filter((u) => u !== message.senderId);
    if (recipients.length === 0) return;

    const userDocs = await Promise.all(
      recipients.map((u) => admin.firestore().collection("users").doc(u).get())
    );
    const tokens = userDocs
      .map((d) => d.get("fcmToken"))
      .filter((t) => typeof t === "string" && t.length > 0);

    if (tokens.length === 0) return;

    const title = message.senderNickname || "Новое сообщение";
    const body = previewBody(message);

    await admin.messaging().sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: {
        type: "message",
        chatId: event.params.chatId,
        messageId: event.params.messageId,
      },
      android: {
        priority: "high",
        notification: { channelId: "messages" },
      },
    });
  }
);

exports.notifyOnCall = onDocumentCreated(
  "calls/{callId}",
  async (event) => {
    const call = event.data?.data();
    if (!call || call.status !== "ringing" || !call.calleeId) return;

    const calleeSnap = await admin.firestore().collection("users").doc(call.calleeId).get();
    const token = calleeSnap.get("fcmToken");
    if (!token) return;

    const title = `${call.callerNickname || "Кто-то"} звонит`;
    const body = call.video ? "Входящий видеозвонок" : "Входящий звонок";

    await admin.messaging().send({
      token,
      notification: { title, body },
      data: {
        type: "call",
        callId: event.params.callId,
        video: call.video ? "true" : "false",
      },
      android: {
        priority: "high",
        notification: { channelId: "calls" },
      },
    });
  }
);

function previewBody(msg) {
  switch (msg.type) {
    case "image": return "📷 Фото";
    case "video": return "🎥 Видео";
    case "voice": return "🎙 Голосовое сообщение";
    case "file": return `📎 ${msg.fileName || "Файл"}`;
    default: return msg.text || "Новое сообщение";
  }
}
