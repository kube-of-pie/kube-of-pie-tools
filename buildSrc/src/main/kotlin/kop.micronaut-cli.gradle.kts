plugins {
    id("kop.kotlin-base")
    id("io.micronaut.application")
    id("com.google.devtools.ksp")
}

micronaut {
    runtime("none")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("kubeofpie.*")
    }
}