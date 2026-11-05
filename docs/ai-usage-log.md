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
