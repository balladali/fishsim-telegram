plugins {
    kotlin("jvm") version "1.9.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"
    application
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")
}

application {
    // Top-level main() in MainLauncher.kt compiles to MainLauncherKt
    mainClass.set("MainLauncherKt")
}

kotlin {
    jvmToolchain(21)
}
