plugins {
    kotlin("jvm") version "1.9.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.acrylicstyle"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.kord:kord-core:0.12.0")
    implementation("org.slf4j:slf4j-simple:2.0.1")
    implementation("com.charleskorn.kaml:kaml:0.55.0") // YAML support for kotlinx.serialization
    implementation("com.aallam.openai:openai-client:3.6.0")
    implementation("com.spotify:github-client:0.2.0")
    implementation("it.skrape:skrapeit:1.3.0-alpha.1")
    implementation("io.github.furstenheim:copy_down:1.1")   // Convert HTML to Markdown
    implementation("com.github.jelmerk:hnswlib-core:1.1.0")
}

tasks {
    shadowJar {
        manifest {
            attributes(
                "Main-Class" to "xyz.acrylicstyle.gptxbot.MainKt",
            )
        }
        archiveFileName.set("GPTxBot.jar")
    }
}

kotlin {
    jvmToolchain(8)
}
