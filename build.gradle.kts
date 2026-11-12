// Top-level build file
buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin support and pulls in a default Kotlin Gradle
        // plugin (KGP). Declaring KGP here upgrades the Kotlin compiler to the
        // version we want; it must match the Compose compiler plugin below.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
