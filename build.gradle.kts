plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.zolitatek.mish"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jline:jline:3.25.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("MainKt")
}

// Configure Java and Kotlin target compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.test {
    useJUnitPlatform()
}
