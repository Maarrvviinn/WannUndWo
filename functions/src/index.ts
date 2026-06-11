import * as admin from "firebase-admin";
import {
  onDocumentCreated,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

type NotificationType =
  | "ABHOLUNG_ERSTELLT"
  | "ABHOLUNG_GEAENDERT"
  | "ABHOLUNG_ANGENOMMEN"
  | "ABHOLUNG_ABGELEHNT";

function isNotificationEnabledForType(
  userData: admin.firestore.DocumentData | undefined,
  type: NotificationType,
): boolean {
  const prefs = userData?.notificationPreferences ?? {};
  switch (type) {
    case "ABHOLUNG_ERSTELLT":
      return prefs.abholungErstellt !== false;
    case "ABHOLUNG_GEAENDERT":
      return prefs.abholungGeaendert !== false;
    case "ABHOLUNG_ANGENOMMEN":
      return prefs.abholungAngenommen !== false;
    case "ABHOLUNG_ABGELEHNT":
      return prefs.abholungAbgelehnt !== false;
    default:
      return true;
  }
}

// ─── Helper: Send FCM to a list of user IDs ───────────────────────────────────
async function sendToUsers(
  userIds: string[],
  type: NotificationType,
  title: string,
  body: string,
  abholungId: string,
): Promise<string[]> {
  if (userIds.length === 0) return [];
  const userDocs = await Promise.all(
    userIds.map((uid) => db.collection("users").doc(uid).get()),
  );

  const enabledUsers = userDocs.filter((d) =>
    isNotificationEnabledForType(d.data(), type),
  );

  const enabledUserIds = enabledUsers.map((d) => d.id);

  const tokens: string[] = userDocs
    .filter((d) => isNotificationEnabledForType(d.data(), type))
    .map((d) => d.data()?.fcmToken as string | undefined)
    .filter((t): t is string => !!t);
  if (tokens.length === 0) return enabledUserIds;

  const message: admin.messaging.MulticastMessage = {
    tokens,
    // Include `notification` so Firebase auto-handles delivery in background/killed
    // (reliable on all devices, no battery-opt issues).
    // `android.notification.channelId` ensures the correct channel is used.
    // `data` carries the abholungId for deep-link on tap, and title/body for
    // onMessageReceived when the app is in the foreground.
    notification: { title, body },
    data: { abholungId, title, body },
    android: {
      priority: "high",
      ttl: 86400000,
      notification: {
        channelId: "wannundwo_notifications",
        priority: "high",
        defaultSound: true,
        defaultVibrateTimings: true,
      },
    },
  };
  await messaging.sendEachForMulticast(message);
  return enabledUserIds;
}

// ─── Helper: Add In-App notification ─────────────────────────────────────────
async function addInAppNotification(
  userId: string,
  type: string,
  message: string,
  abholungId: string,
): Promise<void> {
  await db.collection("notifications").doc(userId).collection("items").add({
    type,
    message,
    abholungId,
    read: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

// ─── onCreate: Abholung erstellt ──────────────────────────────────────────────
export const onAbholungCreated = onDocumentCreated(
  "abholungen/{abholungId}",
  async (event) => {
    const abholung = event.data?.data();
    if (!abholung) return;
    const abholungId = event.params.abholungId;

    // Nur die erlaubten Abholer (recipientIds) benachrichtigen
    const notifyIds: string[] = (abholung.recipientIds ?? []).filter(
      (id: string) => id !== abholung.creatorId,
    );

    const creatorSnap = await db
      .collection("users")
      .doc(abholung.creatorId)
      .get();
    const creatorName: string = creatorSnap.data()?.name ?? "Jemand";
    const msg = `${creatorName} hat eine neue Abholung erstellt.`;

    const deliveredUserIds = await sendToUsers(
      notifyIds,
      "ABHOLUNG_ERSTELLT",
      "Neue Abholung",
      msg,
      abholungId,
    );
    for (const uid of deliveredUserIds) {
      await addInAppNotification(uid, "ABHOLUNG_ERSTELLT", msg, abholungId);
    }
  },
);

// ─── onUpdate: Status / Inhalt geändert ──────────────────────────────────────
export const onAbholungUpdated = onDocumentUpdated(
  "abholungen/{abholungId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;
    const abholungId = event.params.abholungId;

    const statusChanged = before.status !== after.status;
    const contentChanged =
      before.location !== after.location ||
      before.time !== after.time ||
      before.note !== after.note;

    if (!statusChanged && !contentChanged) return;

    if (statusChanged) {
      const respondedSnap = await db
        .collection("users")
        .doc(after.respondedBy)
        .get();
      const respondedName: string = respondedSnap.data()?.name ?? "Jemand";
      const abholungTime = before?.time?.toDate
        ? before.time.toDate()
        : new Date((before?.time?.seconds ?? 0) * 1000);
      const abholungTimeStr =
        abholungTime.toLocaleTimeString("de-DE", {
          hour: "2-digit",
          minute: "2-digit",
        }) + " Uhr";
      const isAccepted = after.status === "ANGENOMMEN";
      const responseNote: string = after.responseNote ?? "";
      const msg = isAccepted
        ? `${respondedName} hat deine Abholung um ${abholungTimeStr} angenommen${responseNote ? `: "${responseNote}"` : ""}`
        : `${respondedName} hat deine Abholung um ${abholungTimeStr} abgelehnt${responseNote ? `: "${responseNote}"` : ""}`;
      const notifTitle = isAccepted
        ? "Abholung angenommen ✓"
        : "Abholung abgelehnt";

      const statusType: NotificationType = isAccepted
        ? "ABHOLUNG_ANGENOMMEN"
        : "ABHOLUNG_ABGELEHNT";
      const deliveredUserIds = await sendToUsers(
        [after.creatorId],
        statusType,
        notifTitle,
        msg,
        abholungId,
      );
      for (const uid of deliveredUserIds) {
        await addInAppNotification(uid, statusType, msg, abholungId);
      }

      // Status-Änderung in Verlauf schreiben
      const changesCol = db
        .collection("abholungen")
        .doc(abholungId)
        .collection("changes");
      await changesCol.add({
        field: "status",
        oldValue: before.status ?? "",
        newValue: after.status ?? "",
        changedBy: after.respondedBy ?? after.creatorId ?? "",
        changedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } else if (contentChanged) {
      // Aenderungen als Verlauf speichern
      const changesCol = db
        .collection("abholungen")
        .doc(abholungId)
        .collection("changes");
      const changedBy: string = after.creatorId ?? "";
      const changedAt = admin.firestore.FieldValue.serverTimestamp();
      if (before.time?.seconds !== after.time?.seconds) {
        const fmt = (ts: { seconds: number }) =>
          new Date(ts.seconds * 1000).toLocaleString("de-DE", {
            weekday: "short",
            day: "2-digit",
            month: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
          });
        await changesCol.add({
          field: "time",
          oldValue: fmt(before.time),
          newValue: fmt(after.time),
          changedBy,
          changedAt,
        });
      }
      if (before.location !== after.location) {
        await changesCol.add({
          field: "location",
          oldValue: before.location ?? "",
          newValue: after.location ?? "",
          changedBy,
          changedAt,
        });
      }
      if (before.note !== after.note) {
        await changesCol.add({
          field: "note",
          oldValue: before.note ?? "",
          newValue: after.note ?? "",
          changedBy,
          changedAt,
        });
      }

      const updaterSnap = await db
        .collection("users")
        .doc(after.creatorId)
        .get();
      const updaterName: string = updaterSnap.data()?.name ?? "Jemand";

      // Build specific messages per changed field
      const msgs: string[] = [];
      if (before.time?.seconds !== after.time?.seconds) {
        msgs.push(`${updaterName} hat die Uhrzeit einer Abholung geändert.`);
      }
      if (before.location !== after.location) {
        msgs.push(`${updaterName} hat den Ort einer Abholung geändert.`);
      }
      if (before.note !== after.note) {
        msgs.push(`${updaterName} hat die Notiz einer Abholung geändert.`);
      }
      const msg =
        msgs.length > 0
          ? msgs.join(" ")
          : `${updaterName} hat eine Abholung geändert.`;
      // Nur die erlaubten Abholer benachrichtigen
      const notifyIds: string[] = (after.recipientIds ?? []).filter(
        (id: string) => id !== after.creatorId,
      );

      const deliveredUserIds = await sendToUsers(
        notifyIds,
        "ABHOLUNG_GEAENDERT",
        "Abholung geändert",
        msg,
        abholungId,
      );
      for (const uid of deliveredUserIds) {
        await addInAppNotification(uid, "ABHOLUNG_GEAENDERT", msg, abholungId);
      }
    }
  },
);

// ─── HTTPS Callable: Create member account ───────────────────────────────────
interface CreateMemberData {
  name: string;
  email: string;
  password: string;
  haushaltsId: string;
}

export const createMemberAccount = onCall<CreateMemberData>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Nicht angemeldet");
  }

  const { name, email, password, haushaltsId } = request.data;

  if (!name || !email || !password || !haushaltsId) {
    throw new HttpsError("invalid-argument", "Fehlende Felder");
  }

  const haushaltSnap = await db.collection("haushalte").doc(haushaltsId).get();
  if (
    !haushaltSnap.exists ||
    haushaltSnap.data()?.adminId !== request.auth.uid
  ) {
    throw new HttpsError(
      "permission-denied",
      "Nur Admins können Mitglieder hinzufügen",
    );
  }

  const userRecord = await admin
    .auth()
    .createUser({ email, password, displayName: name });

  await db
    .collection("users")
    .doc(userRecord.uid)
    .set({
      id: userRecord.uid,
      name,
      email,
      haushaltsId,
      permissions: {
        canCreateAbholung: true,
        canEditAbholung: true,
        canDeleteAbholung: false,
      },
      fcmToken: "",
      notificationPreferences: {
        abholungErstellt: true,
        abholungGeaendert: true,
        abholungAngenommen: true,
        abholungAbgelehnt: true,
      },
    });

  await db
    .collection("haushalte")
    .doc(haushaltsId)
    .update({
      memberIds: admin.firestore.FieldValue.arrayUnion(userRecord.uid),
    });

  return { uid: userRecord.uid };
});

// ─── HTTPS Callable: Erinnerung an Empfänger senden ──────────────────────────
interface SendReminderData {
  abholungId: string;
  note?: string;
}

export const sendReminderToRecipients = onCall<SendReminderData>(
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Nicht angemeldet");
    }

    const { abholungId, note } = request.data;
    if (!abholungId)
      throw new HttpsError("invalid-argument", "abholungId fehlt");

    const abholungSnap = await db
      .collection("abholungen")
      .doc(abholungId)
      .get();
    if (!abholungSnap.exists)
      throw new HttpsError("not-found", "Abholung nicht gefunden");

    const abholung = abholungSnap.data()!;

    // Nur der Ersteller darf erinnern
    if (abholung.creatorId !== request.auth.uid) {
      throw new HttpsError(
        "permission-denied",
        "Nur der Ersteller kann erinnern",
      );
    }

    const recipientIds: string[] = (abholung.recipientIds ?? []).filter(
      (id: string) => id !== abholung.creatorId,
    );
    if (recipientIds.length === 0) return { sent: 0 };

    const creatorSnap = await db
      .collection("users")
      .doc(abholung.creatorId)
      .get();
    const creatorName: string = creatorSnap.data()?.name ?? "Jemand";
    const abholungTime = new Date((abholung.time?.seconds ?? 0) * 1000);
    const timeStr =
      abholungTime.toLocaleTimeString("de-DE", {
        hour: "2-digit",
        minute: "2-digit",
      }) + " Uhr";

    const defaultMsg = `${creatorName} erinnert dich: Abholung um ${timeStr}`;
    const body =
      (note ?? "").trim() !== "" ? `${defaultMsg} – ${note}` : defaultMsg;

    const deliveredUserIds = await sendToUsers(
      recipientIds,
      "ABHOLUNG_ERSTELLT",
      "Erinnerung: Abholung",
      body,
      abholungId,
    );
    for (const uid of deliveredUserIds) {
      await addInAppNotification(uid, "ABHOLUNG_ERSTELLT", body, abholungId);
    }

    return { sent: recipientIds.length };
  },
);
