# Push notifications: what is automatic and how to demo it

The project is on the **free Firebase (Spark) plan**, which has no Cloud Functions. That decides what the app can do on its own.

## What the app does by itself
| Part | Status |
|---|---|
| Saves this phone's FCM token to `users/{uid}.fcmToken` (at launch, and when Firebase changes it) | Automatic |
| Turning notifications off in **Settings** deletes the token, so the phone stops receiving pushes | Automatic |
| Asks for the Android 13+ permission with an explanation card on Home, and again from the Settings switch | Automatic |
| Three channels: Fixtures, Referee requests, Leaderboard | Automatic |
| Shows a push that arrives while the app is open (its own notification, on the right channel) | Automatic |
| Tapping a notification opens the club (if the message carries `clubId`) or the notification list | Automatic |
| **In-app notifications** (bell icon with an unread badge, list screen) for: league started, you were chosen as referee, result confirmed, leaderboard changed | Automatic and real time (Firestore listener) |

The in-app notifications are written to the other members' `users/{uid}/notifications` by the device of the person who did the action (the Firestore rules allow this when `fromUid` is the sender). Only the *type and values* are stored, so each phone shows the text in its own language.

## What is NOT automatic
A real **push to another phone while the app is closed** needs a server to call the FCM API with a secret credential. That needs a Cloud Function (Blaze plan) or another server, and a secret must never be put inside the app. So on the free plan:

- The in-app bell updates instantly **while the app is open or after it is opened again**.
- A push that arrives with the app in the background has to be **sent from the Firebase console**.

## Demo steps for the video (real phone)
1. Install a **debug** build (the "Copy push token" button only exists in debug builds).
2. Sign in, allow notifications when asked, then open **Profile** and tap **Copy push token (debug)**.
3. Put the phone's app in the background (press Home).
4. Firebase console, **Messaging**, create a campaign of type **Firebase Notification messages**, enter a title and text.
5. Click **Send test message**, paste the token, press **Test**. The notification appears on the phone.
6. To show tap-to-open: before sending, open *Additional options*, then *Custom data*, and add `clubId` = a real club id (copy it from the Firestore console) and `type` = `result_verified`. Tapping the notification opens that club.
7. Show the in-app bell: with two accounts in a club, start the league or confirm a result with one account. The other account's bell badge updates in real time.

## Notes
- Messages sent from the console have fixed text (the console does not translate it). The in-app notifications are translated.
- If notifications are blocked by the phone, nothing is shown (the app respects the Settings switch and the system setting).
