# imagegenerator

Stages a per-node Raspberry Pi image directory from the cluster configuration. See the [root README](../README.md) for
context, and [`core/README.md`](../core/README.md) for the configuration model this tool consumes.

`imagegenerator` opens the cluster database **read-only**: it never mutates user state and never generates secrets. If a
required value is missing (no `version.alpine`, no model on the node, an unset SSH key on an enabled user…) it fails
with a pointer to `config` rather than filling in for it.

## What it produces

`generate --node <id> --out <dir>` populates `<dir>` with everything needed to bring up the chosen Pi:

- The extracted Alpine Linux `rpi` rootfs for the version pinned in `version.alpine`.
- The `headless.apkovl.tar.gz` bootstrap overlay published alongside that Alpine release.
- A templated `unattended.sh` (mode `0755`) carrying the hostname, network, users, SSH keys, kernel cmdline additions,
  timezone, NTP, and SSHD toggle resolved from the configuration for this specific node.

The output is a **staging directory**, not a flashed SD card. Copy its contents onto a pre-formatted FAT32 SD card
yourself; partitioning and `dd`-style writes are out of scope.

## CLI

```sh
imagegenerator generate --node <id> --out <dir> [--db <path>] [--cache-dir <dir>] [--offline] [--force]
```

- `--node <id>` (required) — the node to generate for, as registered with `config add nodes <id>`.
- `--out <dir>` (required) — the staging directory. Refuses to overwrite a non-empty directory unless `--force` is also
  passed.
- `--db <path>` — same precedence as the other tools: flag wins, then `KUBE_OF_PIE_DB`, then `./kube-of-pie.db`. A
  missing database is reported as `no database at <path>` rather than created — only `config` creates databases.
- `--cache-dir <dir>` — overrides where the Alpine tarball and overlay are cached. Falls back to
  `$KUBE_OF_PIE_CACHE`, then to the OS-native user cache directory (`~/Library/Caches/kube-of-pie` on macOS,
  `$XDG_CACHE_HOME/kube-of-pie` on Linux, `%LOCALAPPDATA%\kube-of-pie\Cache` on Windows).
- `--offline` — fail if either asset is missing from the cache instead of attempting a download. Useful for reproducible
  runs and air-gapped machines.
- `--force` — clear the contents of `--out` before extracting.

Exit codes match the rest of the toolchain: `0` on success, `2` for user / configuration errors (missing database,
unregistered node, validation failure, offline cache miss), `1` for unexpected I/O or HTTP errors.

## Asset cache

The Alpine `rpi` tarball and the headless bootstrap overlay are downloaded on first use and cached on disk under the
cache directory. Subsequent runs reuse the cached files without contacting the network — the Alpine CDN URLs and the
pinned overlay URL are immutable, so there is no TTL. Clear the cache directory by hand to force a re-download.

The overlay URL is part of the Alpine catalogue: each entry in `core/src/main/resources/alpine/<version>.yaml` carries
its own `overlay_url`, so different Alpine releases can ship different overlay versions.

## First-boot behaviour

The rendered `unattended.sh` runs on the Pi at first boot and applies the configuration end-to-end: `setup-keymap`,
`setup-hostname`, optional Wi-Fi, `setup-interfaces`, `setup-dns`, `setup-timezone`, user creation with passwords and
SSH keys, `setup-sshd`, `setup-ntp`, `setup-apkrepos`, `setup-disk`, plus any per-model kernel cmdline additions
(typically `cgroup_memory=1 cgroup_enable=memory` for Kubernetes). The Pi reboots into the installed system once
`setup-disk` finishes.

## Running

### From the command line

Run via the Gradle wrapper:

```sh
./gradlew :imagegenerator:run -q --console=plain --args="generate --node master --out /tmp/master-image --db /tmp/kop.db"
```

`-q --console=plain` silences Gradle's lifecycle output and progress bar so only the tool's own output is shown.
Anything you pass inside `--args="…"` is forwarded to picocli.

### From IntelliJ

Create a **Gradle** run configuration:

- **Run**: `:imagegenerator:run`
- **Arguments**: `--args="generate --node master --out /tmp/master-image --db /tmp/kop.db"`

Alternatively, open `ImageGeneratorCommand.kt` and click the gutter icon next to `fun main(...)` — IntelliJ generates an
Application run configuration on the fly. Edit it to set **Program arguments** if you need to pass flags.