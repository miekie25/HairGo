package com.hairgo.app.models;

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.Map;

/**
 * One message inside a conversation's messages subcollection.
 * Parsed defensively: a document may be missing any field, and the
 * timestamp is briefly null while the server write is still pending.
 */
public class ChatMessage {

    private final String id;
    private final String senderId;
    private final String text;
    private final Date timestamp; // null until the server confirms the write

    public ChatMessage(String id, String senderId, String text, Date timestamp) {
        this.id = id;
        this.senderId = senderId == null ? "" : senderId;
        this.text = text == null ? "" : text;
        this.timestamp = timestamp;
    }

    /** Builds a ChatMessage from a raw Firestore map. Returns null if there is no text. */
    public static ChatMessage fromMap(String id, Map<String, Object> raw) {
        if (raw == null) return null;

        Object textValue = raw.get("text");
        String text = textValue == null ? null : textValue.toString();
        if (text == null || text.isEmpty()) return null;

        Object when = raw.get("timestamp");
        Date date = when instanceof Timestamp ? ((Timestamp) when).toDate() : null;

        Object sender = raw.get("senderId");
        return new ChatMessage(id, sender == null ? "" : sender.toString(), text, date);
    }

    public String getId() { return id; }
    public String getSenderId() { return senderId; }
    public String getText() { return text; }
    public Date getTimestamp() { return timestamp; }
}
