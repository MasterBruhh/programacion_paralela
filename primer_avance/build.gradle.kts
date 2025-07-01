plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "edu.pucmm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.openjfx:javafx-controls:22")
    implementation("org.openjfx:javafx-fxml:22")
    implementation("org.openjfx:javafx-graphics:22")
    
    // logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testfx:testfx-junit5:4.0.16-alpha")
}

javafx {
    version = "22"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    // Cambia esta clase por tu clase principal real
    mainClass.set("edu.pucmm.Main")
}

tasks.test {
    useJUnitPlatform()
}
