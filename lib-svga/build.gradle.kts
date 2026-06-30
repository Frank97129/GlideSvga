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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(group = "com.squareup.wire", name = "wire-runtime", version = "4.4.1")
    api(group = "com.github.bumptech.glide", name = "glide", version = "4.16.0")
    kapt(group = "com.github.bumptech.glide", name = "compiler", version = "4.16.0")
    kapt(group = "com.github.bumptech.glide", name = "annotations", version = "4.16.0")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.github.Frank97129.GlideSvga"
                artifactId = "lib-svga"
                version = System.getenv("VERSION") ?: "1.0.0"
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
