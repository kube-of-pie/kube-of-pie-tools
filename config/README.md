# config

Authors the cluster configuration consumed by the rest of the toolchain. See the [root README](../README.md) for
context, and [`core/README.md`](../core/README.md) for the variable model `config` exposes.

`config` is the only tool that writes to the cluster database. It populates generated secrets (SSH keys, passwords) on
first need; the read-only tools (`imagegenerator`, `inventorygenerator`) expect those to already exist.

## Surfaces

`config` offers two surfaces over the same variable registry:

- a **CLI** for scripting and one-off changes,
- an embedded **web UI** for browsing and editing the configuration interactively.

Both go through the same `core` registry, so anything one can do, the other can too.

## CLI

The CLI is a thin wrapper over the registry:

```sh
config list [--prefix <p>] [--writable-only]
config get <key> [--reveal]
config set <key> <value>
config add <key> [<id>]
config remove <key> <id>
config ui
```

- `list` enumerates variables; metadata (description, writable, allowed values, sensitive) is shown alongside. Listable
  family heads (`users`, `nodes`) print one identifier per line instead of a single value.
- `get` prints a single variable's current value and metadata. Sensitive values are redacted unless `--reveal` is
  passed. For listable heads, the identifiers are printed one per line.
- `set` writes a value. The registry validates against the allowed-values list and the writable flag; non-writable keys
  and out-of-range values are rejected with the variable's description as context. Listable family heads are not
  user-writable — use `add` / `remove` instead.
- `add` adds an identifier to a listable family. `<id>` is required for name-keyed families (`config add users root`)
  and optional for index-keyed families (`config add nodes` auto-assigns the next index).
- `remove` removes an identifier from a listable family (`config remove users root`, `config remove nodes 1`). For
  index-keyed families only the highest current index can be removed.
- `ui` launches the embedded web UI.

All commands take a global `--db <path>` flag and respect `KUBE_OF_PIE_DB`; see
[`core/README.md`](../core/README.md) for the full path-resolution rules.

## Web UI

`config ui` launches an embedded web server backed by the same variable registry as the CLI:

- Binds to `127.0.0.1` only — the UI assumes a trusted local user and runs without authentication.
- Listens on port `8080` by default; override with `--port <n>`. On a port collision the command fails with a clear
  error rather than silently picking another port.
- Runs in the foreground; Ctrl-C stops it.
- Renders the registry directly: each variable is shown with its description, current value, and (where applicable) the
  allowed-values picker. Sensitive variables are redacted by default, with the same reveal opt-in as the CLI.
- Validation runs through the same registry write path the CLI uses, so error messages are identical across surfaces.

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
