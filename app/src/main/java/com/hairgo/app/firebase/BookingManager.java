package com.hairgo.app.firebase;

import android.text.TextUtils;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class BookingManager {
    /**
     * How far ahead a booking (or reschedule) must start, in minutes.
     * The single place to change the notice period: this class's write
     * methods and the booking screen's pickers/confirm check all read it.
     */
    public static final int MIN_NOTICE_MINUTES = 60;

    /** First bookable hour of the day (same grid the booking screen shows). */
    public static final int SLOT_START_HOUR = 9;
    /** End of the bookable day, exclusive — the last slot is 17:30. */
    public static final int SLOT_END_HOUR = 18;

    /** Collection of per-slot lock docs that serialise concurrent bookings. */
    private static final String SLOT_LOCKS = "slotLocks";

    /** Shown when another client claims the slot between the picker and confirm. */
    private static final String SLOT_TAKEN_MESSAGE =
            "That time was just booked by someone else. Please choose another slot.";

    private FirebaseFirestore db;

    public BookingManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface BookingCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    /**
     * Used when creating a booking, so the caller learns the id Firestore
     * generated for the new document.
     */
    public interface BookingCreatedCallback {
        void onSuccess(String bookingId);
        void onFailure(String errorMessage);
    }

    public interface BookingDataCallback {
        void onSuccess(Map<String, Object> booking);
        void onFailure(String errorMessage);
    }

    public interface BookingListCallback {
        void onSuccess(List<Map<String, Object>> bookings);
        void onFailure(String errorMessage);
    }

    /** Used by the account-deletion flow, so the caller learns which reminders to cancel. */
    public interface CancelledBookingsCallback {
        void onSuccess(List<String> cancelledBookingIds);
        void onFailure(String errorMessage);
    }

    /**
     * Generates the default 30-minute "HH:mm" slot labels for the bookable
     * day (09:00–18:00). Shared so the booking screen's picker and the
     * "next available" message can never disagree about which slots exist.
     */
    public static List<String> generateTimeSlots() {
        return generateTimeSlots(SLOT_START_HOUR * 60, SLOT_END_HOUR * 60);
    }

    /**
     * The same grid for a salon's custom opening hours: 30-minute slots from
     * openMinutes, each fully inside the window (a slot starts only when its
     * end, start + 30, still fits before closeMinutes).
     */
    public static List<String> generateTimeSlots(int openMinutes, int closeMinutes) {
        List<String> slots = new ArrayList<>();
        for (int minutes = openMinutes; minutes + 30 <= closeMinutes; minutes += 30) {
            slots.add(String.format(Locale.getDefault(), "%02d:%02d",
                    minutes / 60, minutes % 60));
        }
        return slots;
    }

    /** "yyyyMMddHHmm" of a booking time — minute-accurate like the slot grid. */
    private static String minuteKey(Date time) {
        return new SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(time);
    }

    /**
     * Deterministic lock document id for one salon's slot. Locale.US so every
     * device derives the same id for the same slot, whatever its own locale.
     */
    private static String slotLockKey(String salonId, Date time) {
        return salonId + "_" + minuteKey(time);
    }

    private static Map<String, Object> lockData(String bookingId) {
        Map<String, Object> lock = new HashMap<>();
        lock.put("bookingID", bookingId);
        return lock;
    }

    /**
     * Transaction-side availability check for one slot.
     *
     * <p>The lock doc names the booking that last claimed the slot; that
     * booking is then read live, so a cancelled/expired one no longer blocks
     * and a rescheduled one (whose time moved away) no longer matches the
     * minute. The Android SDK cannot query inside a transaction, which is why
     * the lock document exists at all: two racing clients contend on the same
     * lock doc, so only one can ever pass this check.
     */
    private boolean slotLockedByOther(Transaction tx, String lockKey, Date wanted,
                                      String myBookingId) throws FirebaseFirestoreException {
        DocumentSnapshot lock = tx.get(db.collection(SLOT_LOCKS).document(lockKey));
        if (!lock.exists()) return false;

        String holderId = lock.getString("bookingID");
        if (holderId == null || holderId.isEmpty()) return false;
        // A reschedule (or upsert) targeting its own slot is not a conflict.
        if (holderId.equals(myBookingId)) return false;

        DocumentSnapshot holder = tx.get(db.collection("bookings").document(holderId));
        if (!holder.exists()) return false;
        if (Boolean.TRUE.equals(holder.getBoolean("isDeleted"))) return false;
        String status = holder.getString("status");
        if ("cancelled".equals(status) || "expired".equals(status)) return false;

        com.google.firebase.Timestamp heldAt = holder.getTimestamp("dateTime");
        return heldAt != null && minuteKey(heldAt.toDate()).equals(minuteKey(wanted));
    }

    /**
     * Creates a booking for the logged-in client.
     *
     * <p>The salon and service names are stored on the booking as well as their
     * ids. Salon services are plain strings rather than documents, so the names
     * are the only way to label a booking, and keeping a copy here means the
     * bookings list can render in a single read.
     *
     * <p>A booking can cover any number of services. Both a serviceNames array
     * (the source of truth) and a comma-joined serviceName string are written:
     * the array is what the app reads, the string keeps older documents and any
     * external tooling that only knows about serviceName working.
     */
    public void createBooking(String clientId, String salonId, String salonName,
                              List<String> serviceNames, Date dateTime, BookingCreatedCallback callback) {
        createBooking(null, clientId, salonId, salonName, serviceNames, dateTime, callback);
    }

    /** Convenience overload for a single service. */
    public void createBooking(String clientId, String salonId, String salonName,
                              String serviceName, Date dateTime, BookingCreatedCallback callback) {
        createBooking(null, clientId, salonId, salonName, singleton(serviceName), dateTime, callback);
    }

    /**
     * Creates a booking, optionally writing to a known document id.
     *
     * <p>Passing a documentId turns this into an upsert: the named document is
     * replaced instead of a new one being created. The development seeder relies
     * on that, and on the replacement resetting status and isDeleted, so a
     * cancelled test booking comes back to life on the next run.
     */
    public void createBooking(String documentId, String clientId, String salonId, String salonName,
                              String serviceName, Date dateTime, BookingCreatedCallback callback) {
        createBooking(documentId, clientId, salonId, salonName, singleton(serviceName), dateTime, callback);
    }

    public void createBooking(String documentId, String clientId, String salonId, String salonName,
                              List<String> serviceNames, Date dateTime, BookingCreatedCallback callback) {
        // Re-check the rule here, not only in the UI: nothing before now +
        // the notice period may be written.
        if (dateTime == null
                || dateTime.getTime() < System.currentTimeMillis() + MIN_NOTICE_MINUTES * 60_000L) {
            callback.onFailure("Bookings must be made at least " + MIN_NOTICE_MINUTES
                    + " minutes in advance.");
            return;
        }

        DocumentReference ref = documentId == null
                ? db.collection("bookings").document()
                : db.collection("bookings").document(documentId);

        List<String> names = serviceNames == null ? new ArrayList<>() : serviceNames;

        // Look up the client's own name first so the salon owner can see who booked.
        // A client may read their own users document. If the lookup fails for any
        // reason (offline, a seeded client with no profile), the booking is still
        // created, just without a name.
        if (clientId == null || clientId.isEmpty()) {
            writeBooking(ref, clientId, "", salonId, salonName, names, dateTime, callback);
            return;
        }

        db.collection("users").document(clientId).get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("name");
                    String last = doc.getString("surname");
                    String clientName = ((first == null ? "" : first) + " "
                            + (last == null ? "" : last)).trim();
                    writeBooking(ref, clientId, clientName, salonId, salonName, names, dateTime, callback);
                })
                .addOnFailureListener(e ->
                        writeBooking(ref, clientId, "", salonId, salonName, names, dateTime, callback));
    }

    /** Writes the booking document once the client's name (possibly empty) is known. */
    private void writeBooking(DocumentReference ref, String clientId, String clientName,
                              String salonId, String salonName, List<String> names,
                              Date dateTime, BookingCreatedCallback callback) {
        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingID", ref.getId());
        booking.put("clientID", clientId);
        booking.put("clientName", clientName);
        booking.put("salonID", salonId);
        booking.put("salonName", salonName);
        booking.put("serviceNames", names);
        booking.put("serviceName", TextUtils.join(", ", names));
        booking.put("dateTime", dateTime);
        booking.put("status", "pending");
        booking.put("isDeleted", false);

        // Claim the slot and write the booking in one transaction. A competing
        // client contending for the same lock document makes this retry, see
        // the holder, and fail with the friendly message instead of
        // double-booking. All reads happen above; both writes below.
        String lockKey = slotLockKey(salonId, dateTime);
        AtomicBoolean slotTaken = new AtomicBoolean(false);

        db.runTransaction(tx -> {
            if (slotLockedByOther(tx, lockKey, dateTime, ref.getId())) {
                slotTaken.set(true);
                throw new IllegalStateException("Slot already taken");
            }
            tx.set(ref, booking);
            tx.set(db.collection(SLOT_LOCKS).document(lockKey), lockData(ref.getId()));
            return null;
        })
                .addOnSuccessListener(unused -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(
                        slotTaken.get() ? SLOT_TAKEN_MESSAGE : e.getMessage()));
    }

    /**
     * Reads one booking by id.
     *
     * <p>A soft-deleted booking is reported as not found. Cancelling flips
     * isDeleted rather than removing the document, so without this check a
     * cancelled booking can still be opened from recents and cancelled again.
     */
    public void getBookingById(String bookingId, BookingDataCallback callback) {
        db.collection("bookings").document(bookingId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onFailure("Booking not found.");
                        return;
                    }
                    Map<String, Object> booking = doc.getData();
                    Boolean deleted = booking == null ? null : (Boolean) booking.get("isDeleted");
                    if (Boolean.TRUE.equals(deleted)) {
                        callback.onFailure("Booking not found.");
                        return;
                    }
                    callback.onSuccess(booking);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getBookingsForClient(String clientId, BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("clientID", clientId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) bookings.add(doc.getData());
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Real-time version of {@link #getBookingsForClient}: fires the callback now
     * and again on every change to the client's bookings, so a status update made
     * anywhere shows up without the user reloading.
     *
     * <p>Returns the {@link ListenerRegistration} so the caller can stop listening
     * (for example in the fragment's onStop) and avoid background reads.
     */
    public ListenerRegistration listenToBookingsForClient(String clientId, BookingListCallback callback) {
        return db.collection("bookings")
                .whereEqualTo("clientID", clientId)
                .whereEqualTo("isDeleted", false)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) bookings.add(doc.getData());
                    }
                    callback.onSuccess(bookings);
                });
    }

    public void getBookingsForSalon(String salonId, BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) bookings.add(doc.getData());
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Real-time version of {@link #getBookingsForSalon}: fires the callback now
     * and again on every change to the salon's bookings, so a new client booking
     * (or a status change) shows up without the owner refreshing.
     *
     * <p>Returns the {@link ListenerRegistration} so the caller can stop
     * listening (for example in the fragment's onStop) and avoid background reads.
     */
    public ListenerRegistration listenToBookingsForSalon(String salonId, BookingListCallback callback) {
        return db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) bookings.add(doc.getData());
                    }
                    callback.onSuccess(bookings);
                });
    }

    /**
     * Returns the bookings a salon has for one specific day. Used to work out
     * which time slots are already taken before showing the time picker.
     *
     * <p>Requires a composite index on salonID + isDeleted + dateTime. Create it
     * in the Firebase console when the console reports a FAILED_PRECONDITION.
     */
    public void getBookingsForSalonOnDate(String salonId, Date dayStart, Date dayEnd,
                                          BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("dateTime", dayStart)
                .whereLessThan("dateTime", dayEnd)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        if (holdsTimeSlot(doc)) bookings.add(doc.getData());
                    }
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Returns every booking a salon has from the given moment onwards, so a
     * screen can work out which coming days still have room.
     *
     * <p>Shares the composite index on salonID + isDeleted + dateTime with
     * getBookingsForSalonOnDate.
     */
    public void getFutureBookingsForSalon(String salonId, Date fromDate, BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("dateTime", fromDate)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        if (holdsTimeSlot(doc)) bookings.add(doc.getData());
                    }
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void updateBookingStatus(String bookingId, String newStatus, BookingCallback callback) {
        db.collection("bookings").document(bookingId).update("status", newStatus)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Edits an existing booking's service, date and time, and returns it to the
     * pending state so the salon re-confirms it.
     *
     * <p>Editing a pending booking keeps it pending; rescheduling a confirmed one
     * deliberately drops it back to pending rather than changing the time behind
     * the salon's back. update() is used instead of the createBooking upsert so
     * unrelated fields on the document survive the change.
     */
    public void updateBookingDetails(String bookingId, List<String> serviceNames, Date dateTime,
                                     BookingCallback callback) {
        // Reschedules obey the same notice rule as new bookings.
        if (dateTime == null
                || dateTime.getTime() < System.currentTimeMillis() + MIN_NOTICE_MINUTES * 60_000L) {
            callback.onFailure("Bookings must be made at least " + MIN_NOTICE_MINUTES
                    + " minutes in advance.");
            return;
        }

        List<String> names = serviceNames == null ? new ArrayList<>() : serviceNames;

        Map<String, Object> update = new HashMap<>();
        update.put("serviceNames", names);
        update.put("serviceName", TextUtils.join(", ", names));
        update.put("dateTime", dateTime);
        update.put("status", "pending");

        // Same claim-and-write transaction as createBooking, so a reschedule
        // cannot race another client for the target slot. The old slot's lock
        // self-heals: its holder's time no longer matches, so the slot reads
        // as free on the next claim.
        DocumentReference bookingRef = db.collection("bookings").document(bookingId);
        AtomicBoolean slotTaken = new AtomicBoolean(false);

        db.runTransaction(tx -> {
            DocumentSnapshot current = tx.get(bookingRef);
            if (!current.exists()) {
                throw new IllegalStateException("Booking not found.");
            }

            String salonId = current.getString("salonID");
            if (salonId != null) {
                String lockKey = slotLockKey(salonId, dateTime);
                if (slotLockedByOther(tx, lockKey, dateTime, bookingId)) {
                    slotTaken.set(true);
                    throw new IllegalStateException("Slot already taken");
                }
                tx.set(db.collection(SLOT_LOCKS).document(lockKey), lockData(bookingId));
            }

            tx.update(bookingRef, update);
            return null;
        })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(
                        slotTaken.get() ? SLOT_TAKEN_MESSAGE : e.getMessage()));
    }

    /** Convenience overload for a single service. */
    public void updateBookingDetails(String bookingId, String serviceName, Date dateTime,
                                     BookingCallback callback) {
        updateBookingDetails(bookingId, singleton(serviceName), dateTime, callback);
    }

    private static List<String> singleton(String serviceName) {
        List<String> list = new ArrayList<>();
        if (serviceName != null && !serviceName.isEmpty()) {
            list.add(serviceName);
        }
        return list;
    }

    /**
     * Marks a batch of stale pending bookings as expired in one round-trip.
     *
     * <p>A booking expires once its day has passed while still pending, meaning
     * the salon never accepted or cancelled it. The whole set is written with a
     * single WriteBatch so the client pays one network cost instead of one per
     * booking, and so the set either lands together or not at all.
     */
    public void expireBookings(List<String> bookingIds, BookingCallback callback) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            if (callback != null) callback.onSuccess();
            return;
        }

        WriteBatch batch = db.batch();
        for (String bookingId : bookingIds) {
            if (bookingId == null || bookingId.isEmpty()) continue;
            batch.update(db.collection("bookings").document(bookingId), "status", "expired");
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    /**
     * Cancels every upcoming booking a client still has, used when the client
     * deletes their account.
     *
     * <p>Only future pending or confirmed bookings are touched: anything already
     * cancelled, expired or completed is left as it is. Each one is written with
     * the same status + isDeleted pair as {@link #cancelBooking}, in a single
     * WriteBatch so the whole set lands together. The ids of the bookings that
     * were cancelled are returned so the caller can cancel their reminders.
     *
     * <p>Uses the same single-field-equality query as {@link #getBookingsForClient},
     * so no composite index is needed.
     */
    public void cancelUpcomingBookingsForClient(String clientId, CancelledBookingsCallback callback) {

        if (callback == null) return;

        if (clientId == null || clientId.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        db.collection("bookings")
                .whereEqualTo("clientID", clientId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    long now = System.currentTimeMillis();
                    List<String> cancelledIds = new ArrayList<>();
                    WriteBatch batch = db.batch();

                    for (QueryDocumentSnapshot doc : query) {
                        String status = doc.getString("status");
                        boolean upcoming = !"cancelled".equals(status)
                                && !"expired".equals(status)
                                && !"completed".equals(status);

                        com.google.firebase.Timestamp dateTime = doc.getTimestamp("dateTime");
                        if (dateTime == null || dateTime.toDate().getTime() < now) {
                            upcoming = false;
                        }

                        if (!upcoming) continue;

                        Map<String, Object> update = new HashMap<>();
                        update.put("status", "cancelled");
                        update.put("isDeleted", true);

                        batch.update(doc.getReference(), update);
                        cancelledIds.add(doc.getId());
                    }

                    if (cancelledIds.isEmpty()) {
                        callback.onSuccess(cancelledIds);
                        return;
                    }

                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess(cancelledIds))
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void softDeleteBooking(String bookingId, BookingCallback callback) {
        db.collection("bookings").document(bookingId).update("isDeleted", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Cancels a booking by writing the cancelled status and the soft-delete flag
     * in a single update.
     *
     * <p>Both fields go in one write so a booking can never end up hidden from
     * the client's list while still reading as pending to the owner, and so a
     * failure part way through cannot leave it half cancelled.
     */
    public void cancelBooking(String bookingId, BookingCallback callback) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "cancelled");
        update.put("isDeleted", true);

        db.collection("bookings").document(bookingId).update(update)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * A cancelled or expired booking no longer holds its time slot. The salon-wide
     * availability queries use this so a declined booking can stay visible to the
     * client as "cancelled" without blocking that time for anyone else.
     */
    private static boolean holdsTimeSlot(QueryDocumentSnapshot doc) {
        String status = doc.getString("status");
        return !"cancelled".equals(status) && !"expired".equals(status);
    }
}