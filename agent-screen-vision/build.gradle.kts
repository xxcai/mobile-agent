plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.screenvision.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++17")
            }
        }
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"1.0.0\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }

    ndkVersion = "26.3.11579264"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    publishing {
        singleVariant("debug") {
            withSourcesJar()
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    api(project(":agent-android"))
    implementation("androidx.core:core:1.12.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("debug") {
                from(components["debug"])
                groupId = rootProject.extra["mobileAgentMavenGroup"] as String
                artifactId = "agent-screen-vision"
                version = rootProject.extra["mobileAgentMavenVersion"] as String
            }
        }
        repositories {
            maven {
                name = "offlineBundle"
                url = uri(rootProject.layout.buildDirectory.dir("local-maven-repo"))
            }
        }
    }
}

