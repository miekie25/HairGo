# HairGo

HairGo is an Android app for booking hair salon and barbershop appointments.
Clients find salons and book services, salon owners run their bookings and
salon pages, and an admin moderates users, salons and reports. Built as a
final-year project on Firebase.

## Features

### Client
- Sign up with phone OTP, log in with email, reset password by email
- Search salons by name or location; view services, rating and photo
- Book one or more services; only free times are offered (30-minute slots,
  60-minute minimum notice)
- View bookings (Upcoming / Past), reschedule, cancel
- Use the mock payment system to demonstrate the booking payment workflow
- Chat with the salon about a booking
- Leave a review on a completed booking
- Report an app problem or report a salon; track or edit it while pending
- Notification history, reminders 1 day and 1 hour before, dark mode,
  profile editing, delete account

### Owner
- Sign up and create one or more salons
- Live bookings feed: confirm, decline, or mark completed (you decide when)
- Booking detail with the client's phone (tap to call) and their note
- Dashboard with pending / confirmed / completed / today counts
- Edit salon services, operating hours and photo; switch between salons
- Chat with the client about a booking

### Admin
- Dashboard with Overview, Users, Salons and Reports tabs
- Open a report, change its status, message a user
- Still being wired to Firebase: the tab lists show sample data, and only the
  report detail screen reads and writes real data

## Tech stack
- Native Android (Java + Kotlin), ViewBinding, Material 3
- Firebase Authentication (email/password + phone OTP)
- Cloud Firestore, Firebase Cloud Messaging
- WorkManager for appointment reminders
- Min SDK 26, target/compile SDK 36, AGP 8.12.3, Kotlin 2.4.0, JDK 11 target

## How to run it
1. Install Android Studio (a recent version).
2. Use JDK 17 or newer (the bundled one is fine). The project itself targets JDK 11.
3. Open the project root and let Gradle sync.
4. `app/google-services.json` is already committed, so a fresh clone builds
   as-is. To use your own Firebase project, create one with package
   `com.hairgo.app` and replace that file.
5. Start an emulator (API 26 or newer) or connect a phone with USB debugging,
   then press Run.

## Firebase setup

### Collections
Nine top-level collections. These field lists are the de facto schema:

- `users` - name, surname, email, phoneNumber, role (client/owner/admin),
  createdAt, isDeleted, fcmToken
- `salons` - salonID, ownerID, name, location, services[], avgRating,
  hours (per-day open/close map), isDeleted
- `bookings` - bookingID, clientID, clientName, salonID, salonName,
  serviceNames[], dateTime, status (pending/confirmed/completed/cancelled/
  expired), notes, isDeleted, paymentType (deposit/full/cash), paymentStatus
  (partial/paid/due), amountPaid, amountOutstanding (payment fields are added
  only to new bookings; older bookings keep working without them)
- `reviews` - reviewID, bookingID, clientID, salonID, rating, comment,
  createdAt, isDeleted
- `reports` - reportID, userID, type (app/salon), salonID, bookingID, category,
  severity, description, status, screenshots[], createdAt, updatedAt
- `messages` - senderId, recipientUid, message, timestamp (admin to user)
- `conversations` - one per booking (doc ID = booking ID); participants[],
  lastMessage, unread flags; chat messages live in a subcollection
- `salonPhotos` - one per salon (doc ID = salon ID); a single base64 JPEG,
  about 225 KB limit
- `slotLocks` - one per salon slot (doc ID = salon + date/time); claims the
  slot so two clients cannot book it at once

Nothing is ever deleted: documents are flagged `isDeleted` instead, and the
security rules deny all hard deletes.

### Rules and indexes
- Rules live in `firestore.rules`. Publish them from the Firebase console
  (Firestore > Rules, paste the file, Publish), or with the CLI:
  `firebase init firestore` then `firebase deploy --only firestore:rules`.
- One composite index is required on `bookings`: salonID Ascending,
  isDeleted Ascending, dateTime Ascending. Easiest way to create it: run the
  app, open a salon and tap Book; the log then shows a FAILED_PRECONDITION
  error containing a link that builds the index for you.
- Phone OTP numbers are normalised to +27 (South Africa). Add test numbers
  under Authentication > Phone to avoid sending real SMS.

### Email enumeration
The sign-up screens warn early when an email is already taken. That early
warning needs Email Enumeration Protection turned OFF (Firebase console >
Authentication > Sign-in method > Email/Password). Leave it ON and the app
still refuses duplicate accounts, just without the early warning.

## Test accounts

No demo credentials ship with the repo. Create a client or owner account
through the app. There is no admin sign-up: register any user, then in the
Firebase console open `users/{uid}` and set `role` to `admin`. Log in with
that account and the app routes you to the admin dashboard.

## Design summary

Three layers. Screens (activities and fragments) call the classes in
`firebase/`, which talk to Firebase Auth and Firestore. Screens never touch
Firestore directly; they pass a callback and the manager reports back.
Live screens attach snapshot listeners, so changes appear without a refresh.

Data model:
- An owner owns many salons
- A client creates many bookings; a booking belongs to one salon
- A booking may have one review, and updates the salon's average rating
- A booking has one chat conversation and may be the subject of a report
- Bookings store the client name and salon name, so lists need no extra reads

## Payments

HairGo includes a **mock payment system** to demonstrate the booking payment
workflow. Payments are simulated and do not process real financial
transactions through a live payment gateway.

## Notifications

The application stores device tokens for messaging infrastructure. Sending
push notifications requires a configured message-sending service; storing
a device token alone does not send notifications.

## Demonstration Video

The HairGo demonstration video showcases the application's implemented
features and client and salon-owner workflows.

**Watch the HairGo demonstration:**
[HairGo Application Demonstration](https://drive.google.com/file/d/1NaLTKK7TcePOhsIF6tFPdzChTrW827_b/view?usp=sharing)

The video is hosted on Google Drive. Playback may be unavailable temporarily
while Google Drive processes the uploaded video.

## Screenshots and User Stories

Screenshots of the running application and brief user stories will be included
in the Deliverable 4 submission PDF. They will demonstrate key features and
explain how clients, salon owners, and administrators interact with HairGo.
