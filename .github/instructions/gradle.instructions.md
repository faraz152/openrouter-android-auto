---
applyTo: "**/*.gradle.kts"
---

# Build System Instructions — OpenRouter Android Auto SDK

## Gradle Kotlin DSL

- All build files use `.gradle.kts` (Kotlin DSL), never Groovy `.gradle`.
- Use the version catalog at `gradle/libs.versions.toml` as the single source of truth for all dependency versions.
- Reference dependencies as `libs.coroutines.core`, `libs.ktor.client.core`, etc.
- Reference plugins as `libs.plugins.android.library`, `libs.plugins.kotlin.android`, etc.

## Module Configuration

### :core (Android Library)

- `com.android.library` + `kotlin-android` + `kotlinx-serialization`
- `minSdk = 24`, `compileSdk = 35`
- Embed registry JSONs in `src/main/res/raw/`
- ProGuard keep rules for `@Serializable` classes

### :compose-ui (Android Library)

- `com.android.library` + `kotlin-android` + `compose-compiler`
- Depends on `:core`
- Compose BOM for version alignment

### :cli (JVM Application)

- `kotlin-jvm` + `application` plugin
- Depends on `:core` (JVM-compatible subset)
- `kotlinx-cli` for argument parsing

### :sample-compose (Android Application)

- `com.android.application` + `kotlin-android` + `compose-compiler`
- Depends on `:core` + `:compose-ui`

### :sample-xml (Android Application)

- `com.android.application` + `kotlin-android`
- Depends on `:core`

## Version Catalog

All versions must be defined in `gradle/libs.versions.toml`. Never hardcode version strings in build files.

## Publishing

Maven coordinates:

- `io.openrouter.android:auto-core`
- `io.openrouter.android:auto-compose`
- `io.openrouter.android:auto-cli`
