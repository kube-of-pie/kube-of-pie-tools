plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.micronaut.gradle.plugin)
}