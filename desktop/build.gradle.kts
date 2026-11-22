plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    implementation("org.openjfx:javafx-swing:17.0.12:linux")
    implementation("org.openjfx:javafx-web:17.0.12:linux")
    implementation("org.openjfx:javafx-controls:17.0.12:linux")
    implementation("org.openjfx:javafx-graphics:17.0.12:linux")
    implementation("org.openjfx:javafx-base:17.0.12:linux")
    implementation("org.openjfx:javafx-media:17.0.12:linux")
}
compose.desktop {
    application {
        mainClass = "org.youschool.desktop.MainKt"
        nativeDistributions {
            packageName = "youschool"
            packageVersion = "0.3.11"
            modules("java.sql", "jdk.unsupported", "java.net.http", "jdk.jsobject", "jdk.crypto.ec", "java.xml", "java.scripting", "jdk.xml.dom", "java.management")
        }
    }
}

kotlin.sourceSets.getByName("main").kotlin.srcDir("../shared-ui/src/main/kotlin")

// Keep AWT resize events correct under tiling Wayland/XWayland window managers.
tasks.withType<JavaExec>().configureEach { environment("_JAVA_AWT_WM_NONREPARENTING", "1") }
