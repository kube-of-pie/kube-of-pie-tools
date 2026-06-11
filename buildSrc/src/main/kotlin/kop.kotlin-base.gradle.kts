plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // sqlite-jdbc loads its native lib via System::load, which JDK 24+ flags as a restricted
    // operation unless the caller is explicitly granted native access.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}