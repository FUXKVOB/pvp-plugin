plugins {
    kotlin("jvm") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.pvpkits"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
    
    // Adventure API (MiniMessage support)
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
    
    // MCCoroutine for Paper
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.20.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.20.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("kotlin", "com.pvpkits.kotlin")
        relocate("kotlinx.coroutines", "com.pvpkits.kotlinx.coroutines")
        relocate("net.kyori", "com.pvpkits.kyori")
        relocate("com.github.shynixn.mccoroutine", "com.pvpkits.mccoroutine")
    }
    
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    
    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf(
                "-Xjvm-default=all",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}
