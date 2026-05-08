plugins {
    id("kop.micronaut-cli")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.micronaut.picocli)
    implementation(libs.picocli)
}