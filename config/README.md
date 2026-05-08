# config

Authors the cluster configuration consumed by the rest of the toolchain. See the [root README](../README.md) for
context.

## Running

### From the command line

Run via the Gradle wrapper:

```sh
./gradlew :config:run --args="--help"
```

Anything you pass inside `--args="…"` is forwarded to picocli.

### From IntelliJ

Create a **Gradle** run configuration:

- **Run**: `:config:run`
- **Arguments** (optional): `--args="--help"`

Alternatively, open `ConfigCommand.kt` and click the gutter icon next to `fun main(...)` — IntelliJ generates an
Application run configuration on the fly. Edit it to set **Program arguments** if you need to pass flags.