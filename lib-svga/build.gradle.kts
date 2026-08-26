import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("kapt")
    id("maven-publish")
}

android {
    namespace = "com.svga.glide"
    compileSdk = 34

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api("com.squareup.wire:wire-runtime:4.4.1")
    api("com.github.bumptech.glide:glide:5.0.5")
    kapt("com.github.bumptech.glide:compiler:5.0.5")
    kapt("com.github.bumptech.glide:annotations:5.0.5")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.github.Frank97129.GlideSvga"
                artifactId = "lib-svga"
                version = (findProperty("version") as? String)
                    ?.takeIf { it.isNotBlank() && it != "unspecified" }
                    ?: System.getenv("VERSION")
                    ?: "1.0.0"
                from(components["release"])

                pom {
                    name.set("GlideSvga")
                    description.set("Load and control SVGA animations with Glide.")
                    url.set("https://github.com/Frank97129/GlideSvga")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/Frank97129/GlideSvga.git")
                        developerConnection.set("scm:git:https://github.com/Frank97129/GlideSvga.git")
                        url.set("https://github.com/Frank97129/GlideSvga")
                    }
                }
            }
        }
    }
}
