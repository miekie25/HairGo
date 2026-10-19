# HairGo

HairGo is a mobile appointment booking and client management app for hair salons and
barbershops, built as a final-year project. Clients discover salons and book services,
salon owners manage their bookings and salon details, and administrators moderate
users, salons and problem reports. All data is stored in Google Cloud Firestore.

- Native Android app (Java + Kotlin), package `com.hairgo.app`
- Backend: Firebase Authentication, Cloud Firestore, Firebase Cloud Messaging
- Min SDK 26 (Android 8.0), target/compile SDK 36

## Features

### Client
- Register (with phone OTP verification and terms acceptance) and log in
- Browse salons with search/filter, view salon details, services and average rating
- Book one or more services with date and time slot availability (free slots are
  computed from existing bookings at that salon)
- View bookings in sections (upcoming/completed/cancelled), reschedule, cancel,
  and leave a review on a completed booking
- Report an app problem (category, severity, description, screenshots) and track
  or edit the report while it is still pending
- In-app notification history, push notifications, light/dark theme toggle,
  profile editing, forgot password (Firebase password reset email)

### Owner
- Register as a salon owner and create one or more salons (name, location, services)
- Live bookings feed: confirm, complete or decline incoming booking requests
- Home dashboard with pending/confirmed/completed booking stats
- Switch between salons from a salon picker; profile and logout

### Admin
- Separate dashboard with Overview, Users, Salons and Reports tabs
- Open a report detail, change its status, suspend a user account and send a
  message to a user
- Note: the admin tab lists currently display sample data from `MockDataStore.kt`
  while the report detail screen reads/writes real Firestore data. This is marked
  as a TODO in the code.

## Tech stack

| Layer | Choice |
|---|---|
| UI | Native Android views, ViewBinding, Material 3, `BottomNavigationView` |
| Languages | Java (57 files) and Kotlin (21 files) in the same module |
| Auth | Firebase Authentication (email/password + phone OTP) |
| Database | Cloud Firestore (soft deletes via an `isDeleted` flag) |
| Push | Firebase Cloud Messaging + local notifications |
| Background work | WorkManager (appointment reminder) |
| Build | Gradle 8.13, Android Gradle Plugin 8.12.3, Kotlin 2.4.0, JDK 11 target |

## Project structure

```
HairGo/
├── firestore.rules                 # Firestore security rules (see "Firestore setup")
├── gradle/libs.versions.toml       # Dependency versions
└── app/
    ├── google-services.json        # Firebase project config (committed)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/hairgo/app/
        │   ├── HairGoApp.java          # App init: theme, notification channels, FCM token
        │   ├── activities/             # All screens (activities)
        │   ├── fragments/              # Dashboard tabs, bottom sheets
        │   ├── adapters/               # RecyclerView adapters
        │   ├── firebase/               # Data layer: AuthManager and *Manager classes
        │   ├── models/                 # Data models
        │   ├── notifications/          # FCM service + reminder worker
        │   └── utils/                  # Notifications, session, theme, helpers
        └── res/                        # Layouts, drawables, menus, strings, themes
```

All Firestore access goes through the classes in `firebase/`
(`AuthManager`, `BookingManager`, `SalonManager`, `OwnerManager`, `ReviewManager`,
`ReportManager`). Screens never talk to Firestore directly except for a few
documented cases.

## Getting started

### Requirements
- Android Studio (recent version; the project uses AGP 8.12.3)
- JDK 17 or newer (AGP 8.12 requirement). The project was last built with
  Temurin JDK 21, which Android Studio bundles by default.
- An Android device or emulator running API 26 or newer
- Internet connection (the app needs Firebase)

### Run the app
1. Clone the repository.
2. Open the project root in Android Studio and let Gradle sync
   (the Gradle wrapper and all dependencies resolve automatically).
3. Create/start an emulator (API 26+), or connect a physical device with
   USB debugging enabled.
4. Press Run, or build from the terminal:

   ```
   ./gradlew assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Firebase configuration
`app/google-services.json` for Firebase project `hairgo-32fc0` is already
committed, so a fresh clone builds and runs without any extra configuration.

If you want to use your own Firebase project instead:
1. Create a Firebase project and add an Android app with package name
   `com.hairgo.app`.
2. Download its `google-services.json` and replace `app/google-services.json`.
3. Enable the providers described in the next section.

## Firestore setup

### Collections

The app uses six top-level collections. Field lists are the de facto schema,
written by the `firebase/*Manager` classes.

| Collection | Key fields |
|---|---|
| `users` | `name, surname, email, phoneNumber, role` (`client`/`owner`/`admin`), `createdAt`, `isDeleted`, `fcmToken`, `fcmUpdatedAt` |
| `salons` | `salonID, ownerID, name, location, services` (list of strings), `avgRating`, `isDeleted` |
| `bookings` | `bookingID, clientID, clientName, salonID, salonName, serviceNames[]`, `serviceName`, `dateTime`, `status` (`pending`/`confirmed`/`completed`/`cancelled`/`expired`), `isDeleted` |
| `reviews` | `reviewID, bookingID, clientID, salonID, rating, comment, createdAt, isDeleted` |
| `reports` | `reportID, userID, userName, userEmail, userRole, category, severity, description, status` (`pending`/`in_review`/`resolved`/`closed`), `screenshots[]`, `appVersion`, `deviceModel`, `osVersion`, `isDeleted`, `isEdited`, `createdAt`, `updatedAt` |
| `messages` | `senderId, recipientUid, recipientEmail, recipientName, message, timestamp` |

Notes:
- Deletes are soft deletes: documents are never removed, they are updated with
  `isDeleted = true` (and `bookings` are cancelled at the same time). This matches
  the security rules, which deny all hard deletes.
- `bookings.dateTime` and the `salons.location`/`services` fields are set from
  the client. There is no `createdAt` on bookings by design.

### Authentication providers

1. **Email/Password** - required. Enable it under Firebase Console ->
   Authentication -> Sign-in method.
2. **Phone** - required for the registration OTP step
   (`OtpVerificationActivity`). Enable it and note:
   - Phone numbers are normalised to E.164 with a hard-coded `+27` (South Africa)
     prefix, so enter South African numbers during testing.
   - On a physical device, add your SHA-1/SHA-256 fingerprints
     (`./gradlew signingReport`) to the Firebase project.
   - For testing without SMS, add test phone numbers under
     Authentication -> Phone -> Phone numbers for testing.

### Composite indexes

One composite index is required at runtime:

| Collection | Fields |
|---|---|
| `bookings` | `salonID` Ascending, `isDeleted` Ascending, `dateTime` Ascending |

This supports the availability queries in
`BookingManager.getBookingsForSalonOnDate()` and
`BookingManager.getFutureBookingsForSalon()` (used by the booking time picker
and the salon profile screen).

Easiest way to create it: run the app, open a salon and tap Book; when Firestore
logs a `FAILED_PRECONDITION` error, the log message contains a direct link that
creates the index for you. Alternatively create it manually in
Firebase Console -> Firestore -> Indexes -> Add index (collection `bookings`,
fields as above).

Optional index (only needed by `ReportManager.getAllReports()`, which is not yet
called from any screen):

| Collection | Fields |
|---|---|
| `reports` | `isDeleted` Ascending, `createdAt` Descending |

### Security rules

The rules live in `firestore.rules` at the repository root. They require a signed
in user for every read/write, restrict writes to the resource owner (or an
admin), restrict booking creation to the booking's own client, and deny all hard
deletes.

There is no `firebase.json` in the repo, so to deploy:

**Option A (console, simplest):**
1. Open Firebase Console -> Firestore Database -> Rules.
2. Paste the entire contents of `firestore.rules`.
3. Click Publish.

**Option B (Firebase CLI):**
1. `npm install -g firebase-tools` and `firebase login`.
2. Run `firebase init firestore` in the project root and select the
   `hairgo-32fc0` project when prompted (accept the default to use the existing
   `firestore.rules` file).
3. `firebase deploy --only firestore:rules`.

## Creating an admin account

There is no admin sign-up flow inside the app; the admin dashboard only checks
that the signed-in user's profile has the right role. To create one:

1. Register normally in the app as any user (client or owner), or create the user
   directly in Firebase Console -> Authentication -> Add user (email + password).
2. In Firebase Console -> Firestore -> `users`, open the document whose ID equals
   that user's UID (Authentication tab shows the UID).
3. Edit the document and set the field `role` to `admin` (type: string).
4. Log in with that account in the app. `SplashActivity`/`LoginActivity` route
   `role == "admin"` to `AdminDashboardActivity`.

## Test accounts

No demo or test credentials are shipped with this repository. Create accounts
through the app:

- **Client:** Welcome -> Sign Up -> Client -> fill in the form -> OTP ->
  accept terms -> dashboard.
- **Owner:** the same flow choosing Owner; after first login the app prompts you
  to create your first salon.

For quicker manual testing you can pre-create users in the Firebase console and
skip registration.

## Design summary

### Architecture

The app is a single-module, three-layer Android app:

```
Screens (activities/fragments, ViewBinding)
        |
        |  callbacks (small listener interfaces per manager)
        v
Data layer (firebase/*Manager)  --  Auth, Firestore reads/writes, FCM token
        |
        v
Firebase (Auth + Firestore)     --  rules enforce ownership server-side
```

- **Navigation** is explicit-Intents based: `SplashActivity` (launcher) checks the
  Firebase session and the `role` field on `users/{uid}`, then routes to the
  client, owner or admin dashboard. Each dashboard uses a
  `BottomNavigationView` with fragments (client: Home/Bookings/Profile; owner:
  Home/Bookings/Profile; admin: Overview/Users/Salons/Reports). Secondary screens
  (salon profile, booking form, booking details, reports) are launched as
  activities and finish themselves to return.
- **Data access** is centralised in the `firebase/` package. Screens pass a
  callback interface into a manager method; the manager performs the Firestore
  operation and invokes the callback on success/failure. Live screens
  (client bookings, owner bookings, owner stats) attach Firestore snapshot
  listeners in `onStart` and remove them in `onStop`.
- **Soft delete model**: nothing is ever deleted from Firestore. Queries filter
  `isDeleted == false`; cancelling a booking sets `status = "cancelled"` and
  `isDeleted = true` in one update, which also frees the time slot.
- **Roles** are stored on `users.role` and checked at routing time and, for the
  admin area, again in `AdminDashboardActivity`. The `firestore.rules` file
  enforces the same ownership rules server-side.
- **Notifications**: booking creation schedules a WorkManager reminder
  (`ReminderScheduler`/`ReminderWorker`); the FCM token is stored on the user
  document so an external sender can reach the device; status changes surfacing
  while the bookings tab is open are bridged to local notifications by
  `BookingStatusNotifier`.
- **Offline/state**: theme choice and notification history are stored in
  `SharedPreferences` (`ThemeManager`, `NotificationStore`); the owner's
  selected salon is cached in `OwnerSession`.

### Data model (entity relationships)

```
users (1)  --owns-->  salons (N)
users (1)  --creates--> bookings (N)   bookings (N)  --belong to-->  salons (1)
bookings (1) --may have--> reviews (1)
users (1)  --creates--> reports (N)    users  --send/receive--> messages (N)
```

- A booking stores denormalised `clientName` and `salonName` so list screens do
  not need extra reads.
- A review triggers `avgRating` recalculation on the parent salon
  (`ReviewManager.recalculateSalonRating`).
- Bookings are considered expired client-side by comparing `dateTime` with now;
  stale bookings are batch-updated to `status = "expired"` when the client opens
  their bookings tab.
