plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.0.13"
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
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
    implementation("org.openjfx:javafx-graphics:21")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    // Cambia esta clase por tu clase principal real
    mainClass.set("com.trafico.Main")
}

tasks.test {
    useJUnitPlatform()
}
