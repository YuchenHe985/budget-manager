plugins {
    application
    jacoco
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.yuchenhe"
version = "2.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.yuchenhe.budget.Launcher")
    applicationName = "budget-manager"
}

tasks.test {
    useJUnitPlatform()
    // ./gradlew test -Pscreenshots=docs/screenshots renders every tab of the window to PNG.
    providers.gradleProperty("screenshots").orNull?.let {
        systemProperty("screenshots", file(it).absolutePath)
    }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}

spotless {
    java {
        googleJavaFormat("1.23.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Native app image for the current OS (macOS .app, Windows/Linux folder) with the JDK's jpackage.
tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Builds a self-contained application image with jpackage."
    dependsOn(tasks.installDist)
    val lib = layout.buildDirectory.dir("install/budget-manager/lib")
    val out = layout.buildDirectory.dir("package")
    doFirst { delete(out) }
    commandLine(
        "jpackage", "--type", "app-image", "--name", "BudgetManager",
        "--app-version", "2.0.0",
        "--input", lib.get().asFile.path,
        "--main-jar", "budget-manager-2.0.0.jar",
        "--main-class", "com.yuchenhe.budget.Launcher",
        "--dest", out.get().asFile.path,
    )
}
