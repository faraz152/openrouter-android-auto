plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "io.openrouter.android.auto.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.serialization.json)
}

// ── Maven publishing ──────────────────────────────────────────────────────────

afterEvaluate {
    val groupId = project.findProperty("MAVEN_GROUP_ID") as String? ?: "io.openrouter.android"
    val version  = project.findProperty("VERSION_NAME")   as String? ?: "1.0.0"
    val artifactId = "auto-compose"

    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                this.groupId    = groupId
                this.artifactId = artifactId
                this.version    = version

                pom {
                    name.set("OpenRouter Android Auto — Compose UI")
                    description.set("Optional Material3 Compose components for the OpenRouter Android SDK.")
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
                            name.set("Faraz")
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
        }

        repositories {
            maven {
                name = "MavenCentral"
                url = uri(
                    if (version.endsWith("SNAPSHOT"))
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    else
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                )
                credentials {
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
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

    signing {
        val signingKey      = System.getenv("SIGNING_KEY")
        val signingPassword = System.getenv("SIGNING_PASSWORD")
        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications["release"])
        }
    }
}
