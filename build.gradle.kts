import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.ququla89"
version = "1.0.1"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        javaParameters.set(true)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
}

ktlint {
    version.set("1.7.1")
    verbose.set(true)
}
