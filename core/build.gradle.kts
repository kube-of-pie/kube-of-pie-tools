plugins {
    id("kop.core-library")
}

dependencies {
    api(libs.micronaut.data.jdbc)
    ksp(libs.micronaut.data.processor)
    implementation(libs.flyway.core)
    implementation(libs.sqlite.jdbc)
}