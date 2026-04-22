# Skill: Gradle Multi-Module Build System

> Specialized skill for configuring and maintaining the Gradle Kotlin DSL multi-module build for this Android SDK project.

## When This Skill Applies

- Creating or modifying any `build.gradle.kts` file
- Updating `settings.gradle.kts` or `gradle.properties`
- Managing the version catalog (`gradle/libs.versions.toml`)
- Adding or removing dependencies
- Configuring ProGuard rules
- Setting up publishing (Maven Central, JitPack, GitHub Packages)
- Fixing build errors

## Project Module Graph

```
:core ────────────────────────────┐
  ↑                               │
  ├── :compose-ui ─── :sample-compose
  ├── :cli
  └── :sample-xml
```

## Version Catalog (`gradle/libs.versions.toml`)

This is the **single source of truth** for all dependency versions. Never hardcode versions elsewhere.

```toml
[versions]
kotlin = "2.0.21"
agp = "8.5.2"
coroutines = "1.8.1"
ktor = "2.3.12"
serialization = "1.7.3"
compose-bom = "2024.12.01"
material3 = "1.3.1"
junit5 = "5.10.3"
mockk = "1.13.12"
turbine = "1.1.0"
kotlinx-cli = "0.3.6"

[libraries]
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }
junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
kotlinx-cli = { module = "org.jetbrains.kotlinx:kotlinx-cli", version.ref = "kotlinx-cli" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

## Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

## Module Build Files

### `:core/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.openrouter.android.auto"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.withType<Test> { useJUnitPlatform() }
```

### `:compose-ui/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.openrouter.android.auto.compose"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
}
```

## ProGuard Rules (`core/consumer-rules.pro`)

```proguard
# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes in the SDK package
-keep,includedescriptorclasses class io.openrouter.android.auto.**$$serializer { *; }
-keepclassmembers class io.openrouter.android.auto.** {
    *** Companion;
}
-keepclasseswithmembers class io.openrouter.android.auto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
```

## Common Build Issues

1. **Version conflicts**: Always use the version catalog, never mix catalog + hardcoded versions.
2. **Compose compiler version**: Must match Kotlin version exactly — use `compose-compiler` plugin.
3. **JVM target mismatch**: All modules must use `jvmTarget = "17"`.
4. **R8/ProGuard stripping serializers**: Consumer rules must keep `$$serializer` classes.
5. **Test runner**: Use `useJUnitPlatform()` for JUnit 5, not JUnit 4.
