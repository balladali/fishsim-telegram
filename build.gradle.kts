plugins {
    kotlin("jvm") version "1.9.21"
    application
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

application {
    // Top-level main() in MainLauncher.kt compiles to MainLauncherKt
    mainClass.set("MainLauncherKt")
}

kotlin {
    jvmToolchain(21)
}
