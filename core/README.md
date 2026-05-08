# core

Shared library consumed by every CLI in the toolchain (`config`, `imagegenerator`, `inventorygenerator`).

The library is the single source of truth for two kinds of data:

1. The **cluster configuration** — user-supplied + generated values describing _this_ cluster. Persisted to a SQLite
   file.
2. **Static catalogues** — facts about the Raspberry Pi hardware and Alpine Linux releases that the toolchain can
   target. Compiled into the library.

CLI modules depend on `core` instead of reimplementing parsing, validation, or hardcoding model/version specifics.

## Cluster configuration

The configuration is stored in a single SQLite database file. Going binary keeps generated secrets (SSH key pairs,
passwords) bundled with the rest of the configuration without scattering files around the filesystem, and the format is
universally inspectable with the `sqlite3` CLI or DB Browser if you ever need to peek inside.

### Persistence

- Backed by **SQLite**
  through [Micronaut Data JDBC](https://micronaut-projects.github.io/micronaut-data/latest/guide/#dbc).
- Schema is owned by `core` and **versioned with migrations** (Flyway), so an existing config file can be upgraded as
  the schema evolves rather than forcing a recreation.
- Only the `config` tool writes to the database. `imagegenerator` and `inventorygenerator` open it read-only — they
  never mutate user state, never generate secrets, never run migrations.
- **File location** — by default the database is `./kube-of-pie.db` in the current working directory. All CLI tools
  resolve the path the same way: the `--db <path>` flag wins, then the `KUBE_OF_PIE_DB` environment variable, then the
  default. The resolution logic lives in `core` so the tools cannot drift.
- **First-run creation** — `config set` and `config ui` create the file (and run migrations) if it does not exist. The
  read-only commands `config list` and `config get` fail with `no database at <path>` rather than create one, so typos
  on those paths are caught instead of silently producing an empty DB.
- **Write feedback** — every successful write prints a confirmation naming the database it modified (e.g.,
  `updated <path>`). First-time creation prints `created new database at <path>` instead, so an accidental DB conjured
  by a typo'd `--db` is visible at the moment it happens.

### What is stored

| Area                    | Content                                                                                       |
|-------------------------|-----------------------------------------------------------------------------------------------|
| Cluster topology        | Number of nodes, role per node (control plane / worker), Raspberry Pi model assigned to each. |
| Per-node network config | Hostname, wired vs Wi-Fi, static IP or DHCP, Wi-Fi SSID + passphrase, DNS.                    |
| Storage layout          | SD card disk identifier, partition names, additional storage attached to the node.            |
| Generated secrets       | SSH key pairs, user passwords (or hashes) — generated on demand by `config` and reused later. |

Secret variables are not user-writable: `config` populates them automatically the first time the surrounding
configuration calls for them, and subsequent runs reuse the stored values. `imagegenerator` and `inventorygenerator`
only ever read them; if a required secret is missing they fail with a pointer to re-run `config` rather than generating
one themselves.

### Variable model

Configuration entries are exposed as a flat namespace of **dotted-key variables** — for example `version.alpine`,
`version.kubernetes`, or `kubernetes.network.pod_subnet`. Per-node settings live under `nodes.<i>.…`, where `<i>` is the
node's positional index in the cluster (`nodes.0.hostname`, `nodes.0.network.mode`, `nodes.1.model`, …); the node count
is itself a variable, `cluster.nodes.count`.

The library does not just hand callers raw values; each variable carries metadata that the CLI and web UI drive their UX
from:

- **Description** — a human-readable explanation suitable for showing next to a form field or in `config get --help`.
- **User-writable flag** — whether the user can set this variable directly, or whether it is computed/derived (catalogue
  lookups, generated secrets, values pinned by another choice).
- **Allowed values** — for variables whose domain is bounded, the library returns the list of admissible values. That
  list can depend on other variables: for example, `version.kubernetes` is constrained to the Kubernetes packages
  available in the apk repositories of the currently selected `version.alpine`.
- **Sensitive flag** — marks values that must not be printed in the clear by default (generated SSH private keys, user
  passwords, Wi-Fi passphrases, …). Sensitive variables are redacted in listings and bulk dumps; revealing them requires
  an explicit opt-in (`config get --reveal <key>`).

CLI modules discover variables and their constraints through this metadata layer rather than hardcoding the schema.
Adding a new variable, or a new dependency between variables, is a change confined to `core`.

### Public API

The library's public surface is the **variable registry**. CLI modules read and write configuration exclusively through
it:

- **list** — iterate the namespace, optionally filtered by prefix or writability;
- **read** — fetch a variable's current value together with its metadata (description, writable flag, allowed values,
  sensitive flag);
- **write** — validate against the allowed-values list and the writable flag before persisting; reject changes to
  non-writable keys.

SQLite and the storage schema are implementation details behind the registry — callers never see entities, repositories,
or SQL.

## Static catalogues

Catalogues are read-only data baked into the library. They carry the facts the toolchain needs to know about each
supported piece of hardware and each supported Alpine release.

The variable registry consumes them to compute allowed values for keys whose domain is fixed by the catalogue — for
example `nodes.<i>.model` (drawn from the Pi catalogue), `version.alpine` (drawn from the Alpine catalogue), or
`version.kubernetes` (intersected with the apk repositories of the selected `version.alpine`).

### Raspberry Pi models

Supported initially:

- Raspberry Pi 4
- Raspberry Pi 5

For each model, the catalogue carries:

- **Boot config defaults** — required kernel cmdline / `config.txt` entries (for example `cgroup_memory=1`,
  `cgroup_enable=memory`) that Kubernetes needs to come up cleanly on that board.
- **Alpine compatibility** — which Alpine versions and image variants (`rpi`, `rpi4`, `rpi5`, `aarch64`, …) are valid
  for this model.

### Alpine releases

For each supported Alpine version, the catalogue carries:

- **Release URLs / mirror info** — where to download the SD-card-ready archive for a given version and architecture.
- **Checksums** — SHA-256 of the official archive, used by `imagegenerator` to verify downloads.
- **Package set / repository URLs** — which apk repositories to enable, baseline package names that may differ across
  versions.
- **Version-specific defaults** — settings that vary release-to-release (for example cgroup v1 vs v2, kernel modules
  required by the targeted Kubernetes version).

## Module layout

```
core/
├── build.gradle.kts
└── src/
    ├── main/kotlin/kubeofpie/core/...
    └── test/kotlin/kubeofpie/core/...
```

The library applies the `kop.core-library` convention plugin (see `buildSrc/`) and is published into the multi-module
build under the `:core` project coordinate.
