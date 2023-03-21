plugins {
    kotlin("jvm") version "1.8.10"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.8.10"
}

dependencies {
    implementation("org.postgresql:postgresql:42.5.4")
    implementation("org.hibernate:hibernate-core:6.1.7.Final")
}

val jarTask = tasks.withType<Jar>()

tasks.register<JavaExec>("runJar") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("github.fatalcatharsis.MainKt")
    enableAssertions = true
    dependsOn(jarTask)
}

allOpen {
    annotation("jakarta.persistence.Entity")
}