package com.hairgo.app.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import com.hairgo.app.models.ChatMessage;
import com.hairgo.app.models.Conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All Firestore access for the client-to-salon booking chat.
 *
 * <p>Data shape: one document in "conversations" per booking (the document id
 * IS the booking id, so a booking can never have two conversations) with a
 * "messages" subcollection under it. The conversation document carries a
 * "participants" array ([clientId, ownerId]) so the inbox query and the read
 * rule can be checked by Firestore without extra lookups.
 *
 * <p>First send: the conversation document and the first message are written
 * in ONE batch (create path - the rules verify the identity fields against
 * the booking). Later sends: the same batch only touches the last-message and
 * unread fields (update path - allowed by the rules' hasOnly check).
 */
public class ChatManager {

    /** Must match the 1000-character limit enforced by the security rules. */
    public static final int MAX_MESSAGE_LENGTH = 1000;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface ContextCallback {
        void onSuccess(ChatContext context);
        void onFailure(String errorMessage);
    }

    public interface SendCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface MessagesCallback {
        void onSuccess(List<ChatMessage> messages);
        void onFailure(String errorMessage);
    }

    public interface ConversationsCallback {
        void onSuccess(List<Conversation> conversations);
        void onFailure(String errorMessage);
    }

    /**
     * Everything needed to read, send and secure one booking's conversation.
     * viewerIsOwner tells the UI which side of the bubble layout to use.
     */
    public static class ChatContext {
        public final String bookingId;
        public final String salonId;
        public final String clientId;
        public final String ownerId;
        public final String clientName;
        public final String salonName;
        public final String viewerUid;
        public final boolean viewerIsOwner;

        ChatContext(String bookingId, String salonId, String clientId, String ownerId,
                    String clientName, String salonName, String viewerUid, boolean viewerIsOwner) {
            this.bookingId = bookingId;
            this.salonId = salonId;
            this.clientId = clientId;
            this.ownerId = ownerId;
            this.clientName = clientName;
            this.salonName = salonName;
            this.viewerUid = viewerUid;
            this.viewerIsOwner = viewerIsOwner;
        }
    }

    /**
     * Resolves who may chat about this booking: reads the booking (client) and
     * its salon (owner), then checks the signed-in user is one of the two.
     * Deliberately reads the raw booking document - a cancelled booking is
     * soft-deleted, and people may still need to talk about it.
     */
    public void loadContext(String bookingId, ContextCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onFailure("Please log in again.");
            return;
        }
        final String uid = user.getUid();

        db.collection("bookings").document(bookingId).get()
                .addOnSuccessListener(bookingDoc -> {
                    if (!bookingDoc.exists()) {
                        callback.onFailure("Booking not found.");
                        return;
                    }
                    String clientId = bookingDoc.getString("clientID");
                    String salonId = bookingDoc.getString("salonID");
                    if (clientId == null || clientId.isEmpty()
                            || salonId == null || salonId.isEmpty()) {
                        callback.onFailure("This booking cannot be messaged.");
                        return;
                    }
                    String clientName = asString(bookingDoc.getString("clientName"));
                    String salonName = asString(bookingDoc.getString("salonName"));

                    db.collection("salons").document(salonId).get()
                            .addOnSuccessListener(salonDoc -> {
                                String ownerId = salonDoc.exists()
                                        ? asString(salonDoc.getString("ownerID")) : "";
                                if (ownerId.isEmpty()) {
                                    callback.onFailure("The salon for this booking was not found.");
                                    return;
                                }
                                boolean viewerIsOwner = uid.equals(ownerId);
                                if (!viewerIsOwner && !uid.equals(clientId)) {
                                    // Neither participant: the security rules would
                                    // deny every read anyway, so fail early and clearly.
                                    callback.onFailure("You cannot open this conversation.");
                                    return;
                                }
                                callback.onSuccess(new ChatContext(bookingId, salonId, clientId,
                                        ownerId, clientName, salonName, uid, viewerIsOwner));
                            })
                            .addOnFailureListener(e ->
                                    callback.onFailure(asString(e.getMessage())));
                })
                .addOnFailureListener(e -> callback.onFailure(asString(e.getMessage())));
    }

    /**
     * Live listener on one booking's messages, oldest first. Remove the
     * returned registration when the screen closes. Messages still being
     * written have a null timestamp; they are sorted to the bottom so a
     * just-sent message never jumps to the top of the list.
     */
    public ListenerRegistration listenToMessages(String bookingId, MessagesCallback callback) {
        return db.collection("conversations").document(bookingId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(asString(error.getMessage()));
                        return;
                    }
                    if (snapshots == null) return;

                    List<ChatMessage> messages = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatMessage message = ChatMessage.fromMap(doc.getId(), doc.getData());
                        if (message != null) messages.add(message);
                    }
                    Collections.sort(messages, (a, b) -> {
                        Date ta = a.getTimestamp();
                        Date tb = b.getTimestamp();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;  // pending write -> newest, so last
                        if (tb == null) return -1;
                        return ta.compareTo(tb);
                    });
                    callback.onSuccess(messages);
                });
    }

    /**
     * Live listener on the signed-in user's conversations (both roles use the
     * same participants array). The caller sorts for display. Single-field
     * array-contains query, so no composite index is needed.
     */
    public ListenerRegistration listenToConversations(String uid, ConversationsCallback callback) {
        return db.collection("conversations")
                .whereArrayContains("participants", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(asString(error.getMessage()));
                        return;
                    }
                    if (snapshots == null) return;

                    List<Conversation> conversations = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Conversation conversation =
                                Conversation.fromMap(doc.getId(), doc.getData());
                        if (conversation != null) conversations.add(conversation);
                    }
                    // Newest first; a null timestamp means the server write is
                    // still pending, i.e. the conversation was just updated.
                    Collections.sort(conversations, (a, b) -> {
                        Date ta = a.getLastMessageAt();
                        Date tb = b.getLastMessageAt();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return -1;
                        if (tb == null) return 1;
                        return tb.compareTo(ta);
                    });
                    callback.onSuccess(conversations);
                });
    }

    /**
     * Sends one message and updates the conversation preview in a single
     * batch, so the first message creates both documents atomically.
     *
     * @param conversationExists false on the create path: the batch then
     *                           writes the full conversation document (the
     *                           rules check its identity fields against the
     *                           booking). true on the update path: only the
     *                           last-message and unread fields are written,
     *                           which is exactly what the update rule allows.
     */
    public void sendMessage(ChatContext context, String rawText,
                            boolean conversationExists, SendCallback callback) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            callback.onFailure("Message cannot be empty.");
            return;
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            callback.onFailure("Message is too long (max " + MAX_MESSAGE_LENGTH + " characters).");
            return;
        }

        boolean senderIsClient = context.viewerUid.equals(context.clientId);

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", context.viewerUid);
        message.put("text", text);
        message.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        Map<String, Object> conversation = new HashMap<>();
        if (conversationExists) {
            // Update path: ONLY fields the update rule allows via hasOnly().
            conversation.put("lastMessage", text);
            conversation.put("lastMessageAt",
                    com.google.firebase.firestore.FieldValue.serverTimestamp());
            conversation.put("lastMessageBy", context.viewerUid);
            conversation.put("unreadClient", !senderIsClient);
            conversation.put("unreadOwner", senderIsClient);
        } else {
            // Create path: identity fields are pinned to the booking and salon
            // by the security rules, plus the participants array the inbox
            // query depends on. Merge is safe here - it is still a create when
            // the document does not exist yet.
            conversation.put("bookingId", context.bookingId);
            conversation.put("salonId", context.salonId);
            conversation.put("clientId", context.clientId);
            conversation.put("ownerId", context.ownerId);
            conversation.put("clientName", context.clientName);
            conversation.put("salonName", context.salonName);
            conversation.put("participants",
                    java.util.Arrays.asList(context.clientId, context.ownerId));
            conversation.put("lastMessage", text);
            conversation.put("lastMessageAt",
                    com.google.firebase.firestore.FieldValue.serverTimestamp());
            conversation.put("lastMessageBy", context.viewerUid);
            conversation.put("unreadClient", !senderIsClient);
            conversation.put("unreadOwner", senderIsClient);
        }

        com.google.firebase.firestore.DocumentReference conversationRef =
                db.collection("conversations").document(context.bookingId);
        com.google.firebase.firestore.DocumentReference messageRef =
                conversationRef.collection("messages").document();

        WriteBatch batch = db.batch();
        batch.set(conversationRef, conversation, SetOptions.merge());
        batch.set(messageRef, message);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(asString(e.getMessage())));
    }

    /**
     * Clears the viewer's own unread flag. Only call while messages exist -
     * a conversation document is guaranteed to exist whenever its messages
     * subcollection is non-empty (both are written in the same batch).
     * Failures are ignored: the dot is a convenience, not data.
     */
    public void markRead(ChatContext context) {
        Map<String, Object> update = new HashMap<>();
        update.put(context.viewerIsOwner ? "unreadOwner" : "unreadClient", false);
        db.collection("conversations").document(context.bookingId)
                .update(update)
                .addOnFailureListener(e -> { /* nothing to clear, safe to ignore */ });
    }

    /** Small holder so the batch write reads clearly. */
    private static class DocumentRefPair {
        final com.google.firebase.firestore.DocumentReference conversation;
        final com.google.firebase.firestore.DocumentReference message;

        DocumentRefPair(String bookingId) {
            conversation = FirebaseFirestore.getInstance()
                    .collection("conversations").document(bookingId);
            message = conversation.collection("messages").document();
        }
    }

    private static String asString(String value) {
        return value == null ? "" : value;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
