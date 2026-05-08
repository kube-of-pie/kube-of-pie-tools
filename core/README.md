Li# core

Shared library consumed by every CLI in the toolchain (`config`, `imagegenerator`, `inventorygenerator`).

The library is the single source of truth for two kinds of data:

1. The **cluster configuration** — user-supplied + generated values describing _this_ cluster. Persisted to a SQLite file.
2. **Static catalogues** — facts about the Raspberry Pi hardware and Alpine Linux releases that the toolchain can target. Compiled into the library.

CLI modules depend on `core` instead of reimplementing parsing, validation, or hardcoding model/version specifics.

## Cluster configuration

The configuration is stored in a single SQLite database file. Going binary keeps generated secrets (SSH key pairs, passwords) bundled with the rest of the configuration without scattering files around the filesystem, and the format is universally inspectable with the `sqlite3` CLI or DB Browser if you ever need to peek inside.

### Persistence

- Backed by **SQLite** through [Micronaut Data JDBC](https://micronaut-projects.github.io/micronaut-data/latest/guide/#dbc).
- Schema is owned by `core` and **versioned with migrations** (Flyway), so an existing config file can be upgraded as the schema evolves rather than forcing a recreation.
- Only the `config` tool writes to the database. `imagegenerator` and `inventorygenerator` open it read-only — they never mutate user state, never generate secrets, never run migrations.

### What is stored

| Area                    | Content                                                                                       |
|-------------------------|-----------------------------------------------------------------------------------------------|
| Cluster topology        | Number of nodes, role per node (control plane / worker), Raspberry Pi model assigned to each. |
| Per-node network config | Hostname, wired vs Wi-Fi, static IP or DHCP, Wi-Fi SSID + passphrase, DNS.                    |
| Storage layout          | SD card disk identifier, partition names, additional storage attached to the node.            |
| Generated secrets       | SSH key pairs, user passwords (or hashes) — generated on demand by `config` and reused later. |

### Public API

The library exposes typed Kotlin models for each of these areas plus repository interfaces driven by Micronaut Data. CLI modules consume those repositories rather than building SQL by hand.

## Static catalogues

Catalogues are read-only data baked into the library. They carry the facts the toolchain needs to know about each supported piece of hardware and each supported Alpine release.

### Raspberry Pi models

Supported initially:

- Raspberry Pi 4
- Raspberry Pi 5

For each model, the catalogue carries:

- **Boot config defaults** — required kernel cmdline / `config.txt` entries (for example `cgroup_memory=1`, `cgroup_enable=memory`) that Kubernetes needs to come up cleanly on that board.
- **Alpine compatibility** — which Alpine versions and image variants (`rpi`, `rpi4`, `rpi5`, `aarch64`, …) are valid for this model.

### Alpine releases

For each supported Alpine version, the catalogue carries:

- **Release URLs / mirror info** — where to download the SD-card-ready archive for a given version and architecture.
- **Checksums** — SHA-256 of the official archive, used by `imagegenerator` to verify downloads.
- **Package set / repository URLs** — which apk repositories to enable, baseline package names that may differ across versions.
- **Version-specific defaults** — settings that vary release-to-release (for example cgroup v1 vs v2, kernel modules required by the targeted Kubernetes version).

## Module layout

```
core/
├── build.gradle.kts
└── src/
    ├── main/kotlin/kubeofpie/core/...
    └── test/kotlin/kubeofpie/core/...
```

The library applies the `kop.core-library` convention plugin (see `buildSrc/`) and is published into the multi-module build under the `:core` project coordinate.
