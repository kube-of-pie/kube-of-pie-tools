plugins {
    id("kop.kotlin-base")
    id("io.micronaut.application")
}

micronaut {
    runtime("none")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("kubeofpie.*")
    }
}