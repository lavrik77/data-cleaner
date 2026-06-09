plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.rit.crossdev.jaga"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("io.minio:minio:8.5.7")
    implementation("com.jcraft:jsch:0.1.55")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.4.11")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.rit.crossdev.jaga.minio.cleaner.MainKt")
}

tasks.shadowJar {
    // Указываем mainClassName явно (устаревшее, но shadow plugin его требует)
    manifest.attributes["Main-Class"] = application.mainClass.get()
}