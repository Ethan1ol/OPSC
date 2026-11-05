# Firebase console setup (do once)

Package name: `com.battlefitness.app`

1. Go to https://console.firebase.google.com and sign in. Click **Create a project**, name it `battle-fitness`, Analytics optional, **Create**.
2. **Project overview > Add app > Android**. Package name: `com.battlefitness.app`. Nickname: Battle Fitness.
3. Add the debug SHA-1 and SHA-256: run `./gradlew :app:signingReport` and copy the values under `Variant: debug`. Each teammate who builds on their own machine must add their own SHA-1. Paste them under **Project settings > Your apps > SHA certificate fingerprints**.
4. Download `google-services.json` and put it in `app/` (NOT the repo root). It is git-ignored; never commit it.
5. **Build > Authentication > Get started**. Enable **Email/Password** and **Google** (set a support email).
6. After enabling Google, re-download `google-services.json` (it now contains the web OAuth client ID that Google sign-in needs).
7. **Build > Firestore Database > Create database**, start in **test mode** for development (we will replace this with proper security rules later).
8. **Build > Storage > Get started**. New projects may require the Blaze plan for Storage; check the console message.
9. **Build > Cloud Messaging**: nothing to enable. It works with the Android app registration.
10. For GitHub Actions: copy the content of `google-services.json`, then in the repo go to **Settings > Secrets and variables > Actions > New repository secret**, name `GOOGLE_SERVICES_JSON`.
