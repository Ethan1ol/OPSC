# AI usage log

Source material for the 500-word AI declaration. Tool: Claude Code (Claude Sonnet 5.5, Anthropic).
Entries are appended after every piece.

## 2026-11-05 – Piece 0: Recon and plan
- **Generated:** read-only review of the repo; list of bugs and risks; piece-by-piece plan and estimates.
- **Reviewed/changed by students:** Ethan confirmed the plan, UD4/UD5 assumption, package name `za.co.battlefitness`, repo `Saafie/OPSC`, Spark plan.
- **Bugs debugged:** found CRLF line endings in `gradlew` (would break Linux CI); found PR #1 branch already merged.
- **Images/text generated:** none.

## 2026-11-05 – Piece 1: Build foundation
- **Generated:** `.gitattributes`, `.gitignore` additions, `gradle/libs.versions.toml`, Gradle build files, `docs/` files, Firebase console walkthrough.
- **Reviewed/changed by students:** (to be completed by Ethan after review)
- **Bugs debugged:** missing `google-gms-google-services` catalog entry; Kotlin metadata mismatch (Coil 3.6.x, Firebase BoM 35.x and googleid 1.2.1 are built with Kotlin 2.4, AGP's built-in compiler is 2.2), solved by pinning Coil 3.3.0, BoM 34.19.0, googleid 1.2.0.
- **Images/text generated:** none.

## 2026-11-05 – Extra: firestore.rules
- **Generated:** `firestore.rules` (security rules and the Firestore data model they assume).
- **Reviewed/changed by students:** (to be completed)
- **Bugs debugged:** none. Rules are not yet tested in the Firebase Rules Playground.
- **Images/text generated:** none.

## 2026-11-05 – Piece 2: Authentication and SSO
- **Generated:** `Validators.kt` and its 42 unit tests, `AuthRepository.kt`, `GoogleSignInHelper.kt`, `FieldErrorMessages.kt`, rewrites of `MainActivity`, `signUpSection`, `signUpDetailsActivity`, `HomeActivity`, `activity_home.xml`, new validation/auth strings, INTERNET permission.
- **Reviewed/changed by students:** (to be completed) Ethan chose to keep the Firebase package `com.battlefitness.app` instead of `za.co.battlefitness`.
- **Bugs debugged:** duplicate name check, date picker never shown, wrong birth-date error text, password/username messages not matching their rules; a resource-compiler error from an apostrophe in a string; Firebase config package mismatch found by reading `google-services.json`.
- **Images/text generated:** English error and message strings.


## 2026-11-05 – Extra: storage.rules
- **Generated:** `storage.rules` (Firebase Storage security rules for avatars, banners and proof media).
- **Reviewed/changed by students:** (to be completed)
- **Bugs debugged:** none. Not yet tested in the Firebase console.
- **Images/text generated:** none.
