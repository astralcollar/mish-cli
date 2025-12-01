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

tasks.test {
    useJUnitPlatform()
}
