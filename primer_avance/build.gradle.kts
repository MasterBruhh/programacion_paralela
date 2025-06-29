plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("jacoco")
    // SpotBugs temporalmente removido - no compatible con Java 21
    // id("com.github.spotbugs") version "5.0.14"
}

group = "edu.pucmm"
version = "1.0-snapshot"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // JavaFX
    implementation("org.openjfx:javafx-controls:22")
    implementation("org.openjfx:javafx-fxml:22")
    implementation("org.openjfx:javafx-graphics:22")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testfx:testfx-core:4.0.16-alpha")
    testImplementation("org.testfx:testfx-junit5:4.0.16-alpha")
}

javafx {
    version = "22"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("edu.pucmm.Main")
    mainModule.set("edu.pucmm")
}

tasks.test {
    useJUnitPlatform()
}

// Jacoco coverage
tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Fat JAR task
tasks.register<Jar>("fatJar") {
    dependsOn.addAll(listOf("compileJava", "processResources"))
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "edu.pucmm.Main"
    }
    val sourcesMain = sourceSets.main.get()
    val contents = configurations.runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) } +
            sourcesMain.output
    from(contents)
}

// JPackage task placeholder (requires additional configuration)
tasks.register<Exec>("jpackage") {
    dependsOn("fatJar")
    group = "distribution"
    description = "Create native installer"
    // This would need platform-specific configuration
}
