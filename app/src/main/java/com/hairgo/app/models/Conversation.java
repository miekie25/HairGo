package com.hairgo.app.models;

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.Map;

/**
 * A conversation between one client and one salon owner about one booking.
 * The Firestore document id is the booking id, so there can never be two
 * conversations for the same booking.
 *
 * <p>Documents created before a field existed (there should be none, but old
 * data is always tolerated) simply fall back to empty values.
 */
public class Conversation {

    private final String bookingId;
    private final String salonId;
    private final String clientId;
    private final String ownerId;
    private final String clientName;
    private final String salonName;
    private final String lastMessage;
    private final Date lastMessageAt; // null while the server write is pending
    private final String lastMessageBy;
    private final boolean unreadClient;
    private final boolean unreadOwner;

    public Conversation(String bookingId, String salonId, String clientId, String ownerId,
                        String clientName, String salonName, String lastMessage,
                        Date lastMessageAt, String lastMessageBy,
                        boolean unreadClient, boolean unreadOwner) {
        this.bookingId = bookingId;
        this.salonId = salonId;
        this.clientId = clientId;
        this.ownerId = ownerId;
        this.clientName = clientName == null ? "" : clientName;
        this.salonName = salonName == null ? "" : salonName;
        this.lastMessage = lastMessage == null ? "" : lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.lastMessageBy = lastMessageBy == null ? "" : lastMessageBy;
        this.unreadClient = unreadClient;
        this.unreadOwner = unreadOwner;
    }

    /** Builds a Conversation from a raw Firestore map. Returns null if it has no booking id. */
    public static Conversation fromMap(String docId, Map<String, Object> raw) {
        if (raw == null) return null;

        String bookingId = asString(raw.get("bookingId"));
        if (bookingId == null || bookingId.isEmpty()) bookingId = docId;
        if (bookingId == null || bookingId.isEmpty()) return null;

        Object when = raw.get("lastMessageAt");
        Date date = when instanceof Timestamp ? ((Timestamp) when).toDate() : null;

        return new Conversation(
                bookingId,
                asString(raw.get("salonId")),
                asString(raw.get("clientId")),
                asString(raw.get("ownerId")),
                asString(raw.get("clientName")),
                asString(raw.get("salonName")),
                asString(raw.get("lastMessage")),
                date,
                asString(raw.get("lastMessageBy")),
                Boolean.TRUE.equals(raw.get("unreadClient")),
                Boolean.TRUE.equals(raw.get("unreadOwner")));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** The name to show this viewer: clients see the salon, owners see the client. */
    public String displayNameFor(boolean viewerIsOwner) {
        if (viewerIsOwner) return clientName.isEmpty() ? "Client" : clientName;
        return salonName.isEmpty() ? "Salon" : salonName;
    }

    /** True if the given user has an unread message in this conversation. */
    public boolean isUnreadFor(String uid) {
        return uid != null && uid.equals(clientId) ? unreadClient
                : uid != null && uid.equals(ownerId) ? unreadOwner
                : false;
    }

    public String getBookingId() { return bookingId; }
    public String getSalonId() { return salonId; }
    public String getClientId() { return clientId; }
    public String getOwnerId() { return ownerId; }
    public String getClientName() { return clientName; }
    public String getSalonName() { return salonName; }
    public String getLastMessage() { return lastMessage; }
    public Date getLastMessageAt() { return lastMessageAt; }
    public String getLastMessageBy() { return lastMessageBy; }
}
