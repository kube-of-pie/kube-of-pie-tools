plugins {
    id("kop.micronaut-cli")
}

application {
    mainClass = "kubeofpie.imagegenerator.ImageGeneratorCommandKt"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.micronaut.picocli)
    implementation(libs.picocli)
    implementation(libs.commons.compress)
    implementation(libs.freemarker)
    implementation(libs.appdirs)

    testImplementation(project(":config"))
}
