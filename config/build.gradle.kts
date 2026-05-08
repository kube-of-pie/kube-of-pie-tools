plugins {
    id("kop.micronaut-cli")
}

application {
    mainClass = "kubeofpie.config.ConfigCommandKt"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.micronaut.picocli)
    implementation(libs.picocli)
}