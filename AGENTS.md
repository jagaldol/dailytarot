# Repository Guidelines

## Project Structure & Modules
- `:app`: Single Android app module using Jetpack Compose and Glance.
- Code: `app/src/main/java/com/jagaldol/dailytarot/...`
- UI/Widget: Compose screens under `ui/`, Glance widget under `widget/`.
- Data: DataStore helpers in `data/`, models in `model/`.
- Resources: `app/src/main/res` (drawable, values, xml).
- Tests: Unit tests `app/src/test`, instrumented tests `app/src/androidTest`.
- Versions: `gradle/libs.versions.toml` (AGP, Kotlin, libraries).

## Build, Test, and Development Commands
- Build debug APK: `./gradlew :app:assembleDebug`
- Install & run on device/emulator: `./gradlew :app:installDebug` then launch from launcher.
- Unit tests (JVM): `./gradlew :app:testDebugUnitTest`
- Instrumented tests (device): `./gradlew :app:connectedDebugAndroidTest`
- Lint & static checks: `./gradlew :app:lint`
- Clean build outputs: `./gradlew clean`

## Coding Style & Naming Conventions
- Kotlin style (official): 4‑space indent, no wildcard imports, trailing commas allowed.
- Files/classes: `PascalCase` (e.g., `MainActivity.kt`, `TodayScreen`). Functions/vars: `camelCase`.
- Packages: lowercase dot‑separated (e.g., `com.jagaldol.dailytarot.ui`).
- Android resources: lowercase `snake_case` (e.g., `tarot_mj_01_the_magician`).
- Compose: stateless first; hoist state; keep previews lightweight.

## Testing Guidelines
- Frameworks: JUnit4 for unit tests, Espresso/Compose Test for instrumented UI.
- Place unit tests mirroring package under `app/src/test/...` and Android tests under `app/src/androidTest/...`.
- Name tests with `ClassNameTest` and methods with intent-revealing names.
- Cover new logic (models, DataStore helpers, widget actions). Prefer fast unit tests; add UI tests for critical flows.

## Commit & Pull Request Guidelines
- Commits: small, focused messages in imperative mood (e.g., "Add widget state sync").
- Prefer Conventional Commits when practical: `feat:`, `fix:`, `refactor:`, `test:`.
- PRs: include summary, screenshots or screen recordings for UI/widget changes, steps to verify, and linked issues.
- Ensure CI‑green locally: build, unit tests, lint before requesting review.

## Tips & Configuration
- SDK/NDK paths live in `local.properties`; do not commit secrets/keystores.
- App targets SDK 36, min SDK 24; test on a 24+ emulator.
- Widget uses Glance + DataStore; after data changes, call `updateAll` to refresh.
