plugins {
    id("kop.kotlin-base")
    id("io.micronaut.library")
    id("com.google.devtools.ksp")
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("kubeofpie.*")
    }
}
