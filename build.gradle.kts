plugins {
    kotlin("jvm") version "1.9.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

allprojects {
    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlin.plugin.serialization")
        plugin("com.github.johnrengelman.shadow")
    }

    group = "xyz.acrylicstyle"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val dockerJavaVersion = "3.3.4"

dependencies {
    implementation("dev.kord:kord-core:0.12.0")
    implementation("org.slf4j:slf4j-simple:2.0.1")
    implementation("com.charleskorn.kaml:kaml:0.55.0") // YAML support for kotlinx.serialization
    implementation("com.aallam.openai:openai-client:3.6.0")
    implementation("com.spotify:github-client:0.2.0")
    implementation("it.skrape:skrapeit:1.3.0-alpha.1")
    implementation("io.github.furstenheim:copy_down:1.1")   // Convert HTML to Markdown
    implementation("com.github.jelmerk:hnswlib-core:1.1.0")
    implementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerJavaVersion")
    implementation("com.google.cloud:google-cloud-aiplatform:3.33.0")
    implementation("com.google.cloud:google-cloud-vertexai:0.1.0")
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
