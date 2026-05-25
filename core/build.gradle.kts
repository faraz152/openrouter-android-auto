plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.publish)
}

android {
    namespace = "io.openrouter.android.auto"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
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
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── Maven publishing ──────────────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("io.openrouter.android", "auto-core", "1.0.0")
    pom {
        name.set("OpenRouter Android Auto — Core")
        description.set("Pure-Kotlin Android SDK for OpenRouter: model discovery, chat, streaming, cost estimation.")
        url.set("https://github.com/faraz152/openrouter-android-auto")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("faraz152")
                name.set("Faraz Ahmed")
                email.set("faraz152@users.noreply.github.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/faraz152/openrouter-android-auto.git")
            developerConnection.set("scm:git:ssh://github.com/faraz152/openrouter-android-auto.git")
            url.set("https://github.com/faraz152/openrouter-android-auto")
        }
    }
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/faraz152/openrouter-android-auto")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: "token"
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
