import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.android)
}

val verCode = 301
val verName = "3.0.1"
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use(::load)
    }
}

android {
    namespace = "website.xihan.pbra"
    compileSdk = 35

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("custom") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = verCode
        versionName = verName
        if (keystorePropertiesFile.exists()) {
            signingConfig = signingConfigs.getByName("custom")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions"
            )
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/**", "kotlin-tooling-metadata.json")
    }

    lint.checkReleaseBuilds = false
    dependenciesInfo.includeInApk = false
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(libs.xposed.api)
}

tasks.register<Exec>("restartMiHealth") {
    commandLine("adb", "shell", "am", "force-stop", "com.mi.health")
}

tasks.matching { it.name == "installDebug" }.configureEach {
    finalizedBy("restartMiHealth")
}
