plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "llc.berserkr.gammalib"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++11"
//                arguments += listOf(
//                    "-DANDROID_STL=c++_shared"
//                )
            }
        }

//        ndk {
//            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
//        }

    }

    buildTypes {

        getByName("debug") {
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_BUILD=ON"
                }
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_BUILD=OFF"
                }
            }
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    // Link to your CMakeLists.txt
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.2.1"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "llc.berserkr"
            artifactId = "gammalib"
            version = "2.0.0-SNAPSHOT"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("com.alphacephei:vosk-android:0.3.75")
    androidTestImplementation("net.java.dev.jna:jna:5.18.1@aar")

    implementation("org.slf4j:slf4j-api:2.0.17")


}
