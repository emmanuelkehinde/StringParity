plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.clikt)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "dev.stringparity.MainKt"
    applicationName = "stringparity"
}

tasks.test {
    useJUnitPlatform()
}
