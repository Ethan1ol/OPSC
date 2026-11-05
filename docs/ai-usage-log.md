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

## 2026-11-05 – Piece 3: Settings, languages and profile
- **Generated:** `LanguageManager.kt`, `ProfileRepository.kt`, new `SettingsActivity.kt` and `activity_settings.xml`, shared widget styles, height/weight validators plus 17 more unit tests, `locales_config.xml`, manifest changes, English strings, and **Afrikaans (`values-af`) and isiZulu (`values-zu`) translations of every string**.
- **Reviewed/changed by students:** (to be completed) the Afrikaans and isiZulu text was written by the AI and must be checked by a first-language speaker before submission.
- **Bugs debugged:** AAPT failure because style names with a dot (`Battle.Label`) implied a missing parent style; fixed by renaming. Switched theme from DayNight to Light because layouts use fixed black text on white.
- **Images/text generated:** all translated UI text (Afrikaans, isiZulu).

## 2026-11-05 – UI fix: sign-up form and dashboard (user feedback from phone testing)
- **Generated:** rewrote `activity_sign_up_details.xml` as a clean scrolling card layout; new dashboard `activity_home.xml` with feature tiles; `FeaturePlaceholderActivity` ("Coming soon" page); tile styles and `card_background.xml`; extra strings in English, Afrikaans and isiZulu.
- **Reviewed/changed by students:** Ethan found these problems while testing on the phone (labels not matching boxes, surname hidden like a password, Home only showing Settings and Log out) and requested the fix.
- **Bugs debugged:** surname field had `inputType="textPassword"`; labels and boxes drifted apart because of fragile ConstraintLayout bias offsets; an apostrophe escape lost in the Afrikaans string caused a resource compile error.
- **Images/text generated:** new UI text and translations; dashboard tile icons are Unicode emoji characters, not generated images.

## 2026-11-05 – Step A: Design system and app shell
- **Generated:** colour tokens (light and dark), Material 3 theme, type scale, shapes and spacing; component styles; `StateLayout`, `UiMessages`, `ConfirmDialog`, `ConnectivityMonitor`; custom stroke icons; `AppActivity` with Navigation component and bottom navigation; four tab fragments; debug-only Design Gallery; `DesignContrastTest`; `docs/design-system.md`; translations of the new strings (Afrikaans, isiZulu).
- **Reviewed/changed by students:** (to be completed) Ethan asked for the rebuild and set the design requirements (orange primary, Material 3, bottom navigation, light and dark mode).
- **Bugs debugged:** (1) lint errors for `lineHeight` and `textFontWeight` needing API 28; (2) gallery content touching the screen edge because `fitsSystemWindows` replaced the padding; (3) all text weights rendering as Regular and chips as Roboto. Found by installing on the emulator, screenshotting, and measuring stroke thickness against the Poppins font files; root cause was a theme-level `android:fontFamily` overriding the text styles and Material's parent styles setting the app-namespace `fontFamily`. (4) a missing `style` parent causing an AAPT link error.
- **Images/text generated:** no images. Icons are hand-written vector paths. Screenshots in `docs/images` are real emulator screenshots of the app, not generated.


## 2026-11-05 – Design tweaks after review
- **Generated:** `ThemeMode`/`ThemeManager`/`BattleFitnessApp` (dark by default, saved choice), Dark/Light/System switch on Profile, tonal secondary button style, translations of the four new labels.
- **Reviewed/changed by students:** Ethan asked for dark mode as the standard with an option to change back, and for the thin-bordered Profile buttons to be a different, clearer colour.
- **Bugs debugged:** none.
- **Images/text generated:** none (screenshot is a real emulator capture).

## 2026-11-05 – Feature 1: Register and log in
- **Generated:** `PasswordStrengthRater` and 10 unit tests; `AuthViewModel`; `AuthActivity` with a navigation graph; `LoginFragment`, `SignUpAccountFragment`, `SignUpProfileFragment`, `ForgotPasswordFragment` and their layouts; calendar icon; dropdown style; English, Afrikaans and isiZulu text for every new string. The old login and sign-up activities and layouts were deleted.
- **Reviewed/changed by students:** (to be completed) Ethan specified the two-step sign-up, the fields, the minimum age of 13, the strength hint and the show/hide toggle.
- **Bugs debugged:** unit labels (cm, kg) only showed once a field was focused, so they were moved into the field labels; a typo in the terms text ("Battles"); an unused-string clean-up that would have removed strings the Settings screen still needs (caught before building).
- **How it was tested:** installed on the emulator and driven with adb: session persistence (opened straight into the app), log out with confirmation, login screen, sign-up step 1 validation errors and strength bar, step 2 form, date picker limits, back navigation. Not tested: real registration, login, password-reset email and Google sign-in against Firebase.
- **Images/text generated:** no images. Screenshots in `docs/images/f1-*.png` are real emulator captures.

## 2026-11-05 – Feature 2: Google single sign-on
- **Generated:** `FinishProfileFragment` and layout; `ProfileGate.kt` (profile status, the routing decision and a per-phone "profile confirmed" flag) with 3 unit tests; changes to `AuthRepository` (profile check, awaited profile save, suggested profile from the Google account; removed the automatic minimal profile); `AuthViewModel`, `AuthNavigation.kt` (shared Google button and sign-in handling), a launch gate in `AuthActivity`, a Google button on sign-up step 1; English, Afrikaans and isiZulu strings.
- **Reviewed/changed by students:** (to be completed) Ethan specified the finish-profile fields and that returning Google users go straight to Home.
- **Design decision:** the old code silently created a profile with a guessed username for Google users. That was replaced by an explicit finish-profile screen so the user picks their own username, and the app now also resumes that screen on the next launch if the user quits halfway.
- **How it was tested:** build, lint and 79 unit tests; emulator launch with an existing session (the launch gate opened the app without errors). Not tested: the Google account picker, first-time Google sign-in, the finish-profile screen on a device, cancel and no-account paths, because they need a real Google account on a device.
- **Bugs debugged:** none found in testing yet.
- **Images/text generated:** none.

## 2026-11-05 – Feature 3: Settings and languages
- **Generated:** the Settings screen (`SettingsFragment`, `SettingsViewModel`, layout) with Account, Preferences and About cards; `ChangePasswordFragment`; `PasswordPromptDialog`; `Units.kt` (metric/imperial conversion and unit-aware validation) with 12 unit tests; new validator for "new password must differ" with tests; repository functions for re-authentication, password change and account deletion; a refactor of the Google helper to separate "get a token" from "sign in"; `docs/privacy-policy.md` (a draft); `docs/translation-review.md` (generated from the string files); English, Afrikaans and isiZulu text for every new string. The old Settings activity, legacy theme, styles and drawables were removed.
- **Reviewed/changed by students:** (to be completed) Ethan specified the sections, the languages, and that settings must save to Firestore.
- **Translations:** all Afrikaans and isiZulu text is AI-written and unchecked. `docs/translation-review.md` lists every string and flags the ones the AI is least sure of. This must be disclosed in the AI declaration.
- **Design decisions:** deleting an account always re-checks the user first (password, or the same Google account), and deletes the data before the sign-in so a failure never leaves a profile-less account; the Dark/Light/System switch moved from Profile to Settings.
- **Bugs debugged:** a script step failed halfway because it expected different manifest text; found by reading the error, fixed by finishing the remaining steps separately. A hack that reused an unrelated error code for "same password" was replaced with a proper validator.
- **How it was tested:** build, lint (no errors) and 94 unit tests. Not tested: the Settings screens on a device. The emulator froze with "system not responding" dialogs while testing, so no Settings screenshot was captured. Not tested: language switching, saving to Firestore, change password, delete account, the privacy link.
- **Images/text generated:** all Afrikaans and isiZulu text; the privacy policy draft. No images.

