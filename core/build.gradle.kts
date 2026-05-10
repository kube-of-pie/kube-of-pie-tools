plugins {
    id("kop.core-library")
}

dependencies {
    api(libs.micronaut.data.jdbc)
    ksp(libs.micronaut.data.processor)
    implementation(libs.flyway.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.micronaut.serde.jackson)
    ksp(libs.micronaut.serde.processor)
    implementation(libs.snakeyaml)
}