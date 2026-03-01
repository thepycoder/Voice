## Project Overview

Voice is a minimal, user‑focused audiobook player for Android, built for reliability and minimalism.

**This is a greenfield project** — no backward compatibility is required for serialized data or schema changes. If data model changes cause crashes, clearing app data is acceptable.

## Architecture

The project architecture and gradle module structure is defined in [the Architecture Docs](docs/architecture.md)

## Commands

- Assemble the app: `./gradlew :app:assemblePlayDebug`
- Run all tests `./gradlew voiceUnitTest`
- Run tests of a module: `./gradlew :<moduleName>:testDebugUnitTest`
- Create and register a new gradle module: `./scripts/new_module.kts :features:<name>`

## On-Device Debugging

App package: `de.ph1b.audiobook.repo`  
Main activity: `voice.app.features.MainActivity`

**Get crash logs:**
```bash
adb logcat -d | grep -E "audiobook|voice\.app|FATAL|AndroidRuntime" | tail -100
```

**Launch the app:**
```bash
adb shell am start -n de.ph1b.audiobook.repo/voice.app.features.MainActivity
```

**Clear app data (useful for serialization/schema change crashes):**
```bash
adb shell pm clear de.ph1b.audiobook.repo
```

## Testing Conventions

- For Compose view state tests, use Molecule + Turbine. Minimal pattern:
  ```kotlin
  backgroundScope.launchMolecule(RecompositionMode.Immediate) {
    viewModel.viewState()
  }.test {
    awaitItem()
  }
  ```
- Prefer lightweight in-memory fakes (e.g., `MemoryFeatureFlag`, `MemoryDataStore`) over mocks when available

## Information Lookup

- Project Dependencies are declared in `gradle/libs.versions.toml`
- Get an overview of all current modules from the `settings.gradle.kts` file

## Code Style

* Ignore formatting, this is done by ktlint
* Prefer small functions and clear identifiers
* Minimal inline comments

## Minimal Code Documentation

* **Self‑Describing Code**
  * Clear, concise names; one purpose per function/variable.
* **Comment Only “Why”**
  * Don’t explain “what” or “how.” Refactor if unclear.
  * Use comments solely for rationale, workarounds, non‑obvious side‑effects.
* **KDoc Sparingly**
  * Public API: only when names and signatures don’t fully convey behavior or contracts.
* **Tests as Documentation**
  * Write descriptive tests illustrating usage and edge cases.
* **Continuous Pruning**
  * Remove or update any comments that outlive their usefulness.
