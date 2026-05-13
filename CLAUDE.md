# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, run, test

Toolchain: Kotlin on JVM toolchain 25, Gradle wrapper, Micronaut application/library plugins, KSP. Targets native
binaries via GraalVM `native-image` (not yet wired — see TODO in `SqliteDataSourceFactory`).

```sh
./gradlew build                # compile + test everything
./gradlew :core:test           # tests for one module
./gradlew :core:test --tests 'kubeofpie.core.business.nodes.NodeManagerTest'   # single test class
./gradlew :core:test --tests '*NodeManager*.kernelArgs joins*'                  # single test method (pattern)

# Run the config CLI from source. -q --console=plain hides Gradle noise.
./gradlew :config:run -q --console=plain --args="--help"
./gradlew :config:run -q --console=plain --args="set version.alpine 3.21 --db /tmp/kop.db"
```

`inventorygenerator` is scaffolded but currently empty (`.gitkeep` only) — it has a build file and applies
`kop.micronaut-cli` but no sources yet.

```sh
# Stage a per-node Raspberry Pi image directory (Alpine rootfs + headless overlay + templated unattended.sh).
./gradlew :imagegenerator:run -q --console=plain --args="generate --node master --out /tmp/master-image --db /tmp/kop.db"
```

## High-level architecture

This is a Gradle multi-module build with three CLI tools (`config`, `imagegenerator`, `inventorygenerator`) sitting on
top of a shared `:core` library. The shared library owns *all* domain logic; CLI modules are thin Picocli + Micronaut
wrappers.

### Module wiring

- `buildSrc/src/main/kotlin/` holds three convention plugins applied by every module: `kop.kotlin-base` (Kotlin JVM +
  JUnit5), `kop.core-library` (adds `io.micronaut.library` + KSP, processing annotations `kubeofpie.*`), and
  `kop.micronaut-cli` (adds `io.micronaut.application` with `runtime("none")` so picocli drives the lifecycle, plus
  KSP). **KSP must be applied alongside the Micronaut plugins** — Micronaut Data and Serde rely on KSP-generated code;
  new modules using `@Singleton`/`@Serdeable`/`@JdbcRepository` need both.
- Versions are centralized in `gradle/libs.versions.toml`. The Micronaut platform BOM resolves most library versions
  transitively, so libraries without `version.ref` are intentional.

### Configuration model (the heart of `:core`)

The cluster configuration is exposed to callers as a **flat namespace of dotted-key variables** (`version.alpine`,
`nodes.master.network.hostname`, `users.root.password`). The implementation has two distinct layers:

1. **Typed entity layer** — `core/src/main/kotlin/kubeofpie/core/business/{nodes,users,versions}/` defines
   `NodeManager`, `UserManager`, `AlpineVersionManager`. These are the real domain APIs: they validate IDs, enforce
   uniqueness, check catalogue membership, and derive computed fields (e.g. `NodeManager.kernelArgs` joins catalogue
   args from the configured `version.alpine`). Each manager talks to a Micronaut Data JDBC `*Repository` in
   `core/src/main/kotlin/kubeofpie/core/data/*/`, backed by its own SQLite table (`nodes`, `users`, `versions`). **The
   future web UI calls managers directly.**

2. **Variable registry layer** — `core/src/main/kotlin/kubeofpie/core/registry/` adapts the typed layer (and a generic
   key/value `variables` table via `VariableStorage`) into the dotted-key surface the CLI uses. `Variable` is an
   interface with `key`/`description`/`writable`/`sensitive`/`allowedValues()`/`read()`/`write()`. Static variables are
   `@Singleton Variable` beans (e.g. `SetupTimezoneVariable`, `VersionAlpineVariable`); dynamic ones (per-node,
   per-user) are produced by `@Singleton VariableFamily` beans that enumerate keys based on current state.
   `VariableRegistry` collects every `Variable` and `VariableFamily` bean via constructor injection, validates writes
   against `writable` + `allowedValues`, then delegates.

When designing new variables: a registry adapter (the `Variable`/`VariableFamily`) is *not* a data class — it's an
interface with a Micronaut `@Singleton` implementation. `Variable.read()` returns the consumer-ready shape (e.g.
space-separated cmdline args), not a JSON envelope. The typed manager owns the data and validation; the adapter formats
it for the dotted-key surface.

### Storage (`:core`'s `storage` package)

- SQLite via `micronaut-data-jdbc`. Schema is owned by `:core` under `core/src/main/resources/db/migration/V*.sql` and
  applied by **Flyway** on first open in `READ_WRITE` mode.
- `ConfigDatabase` is the single bean holding the open SQLite connection. The DB path is dynamic (CLI parses `--db`, env
  `KUBE_OF_PIE_DB`, or default `./kube-of-pie.db` — precedence resolved by `DatabasePath`), so the bean starts *
  *unopened**. The CLI calls `ConfigDatabase.open(path, OpenMode)` *exactly once* before any registry read/write.
  `OpenMode.READ_ONLY` refuses to create the file (raises `NoDatabaseException`) and skips Flyway; only `config` ever
  uses `READ_WRITE`.
- **Constructor contract**: never call `database.connection()` or `database.dataSource()` from a bean constructor —
  Micronaut builds the context before `open()` is called. Read from inside `read()`/`write()` methods instead. Managers
  that need the repository inject `Provider<XxxRepository>` (lazy resolution) for the same reason — see `NodeManager`,
  `AlpineVersionManager`.
- `ReadOnlyTolerantDataSource` exists because SQLite forbids changing the read-only flag after connection, but Micronaut
  Data calls `setReadOnly(true)` on every read query. The wrapper silently swallows that call.

### Static catalogues

- `AlpineCatalogue` enumerates supported Alpine versio
- ns by scanning `classpath:alpine/<version>.yaml` (works both from
  a directory and inside a jar). Adding a new Alpine release = dropping a new YAML file. YAML is parsed by SnakeYAML,
  then bound to `@Serdeable` types via `JsonNode.from(...)` + `ObjectMapper.readValueFromTree(...)` — this round-trip
  keeps deserialization reflection-free under `native-image`.
- `RaspberryPiCatalogue` follows the same pattern under `classpath:raspberrypi/`.

### CLI (`:config`)

- Entry point: `config/src/main/kotlin/kubeofpie/config/ConfigCommand.kt` (
  `mainClass = "kubeofpie.config.ConfigCommandKt"`). Subcommands (`list`/`get`/`set`/`add`/`remove`) are each
  `@Singleton Callable<Int>` so picocli can inject `VariableRegistry`, `ConfigDatabase`, etc. via the Micronaut context.
- Every subcommand applies the `DatabaseOptions` mixin for the global `--db` flag, then calls `ConfigDatabase.open(...)`
  before touching the registry. Read-only commands open `READ_ONLY` (a missing DB is an error); write commands open
  `READ_WRITE` (a missing DB is created and reported as `created new database at <path>`).

## Testing conventions

- Tests are JUnit 5. Manager and registry tests boot an `ApplicationContext`, open a `ConfigDatabase` against a
  `@TempDir`-scoped path, then resolve the bean under test from the context.
- **Respect layer boundaries**: when testing an adapter (variable/family), set up state via the layer below — the typed
  manager — not via the adapter itself. Example: a `NodesModelFamilyTest` should add nodes through
  `NodeManager.add(...)` and read via the family, not write through the family adapter to seed state.
