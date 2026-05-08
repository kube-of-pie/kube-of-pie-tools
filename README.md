# kube-of-pie-tools

A toolchain of command-line tools that helps you build and operate a home Kubernetes cluster running on Raspberry Pi.

The toolchain is built with [Micronaut](https://micronaut.io/) in Kotlin and compiled to native binaries via GraalVM
`native-image`.

## Core library

A shared core library sits underneath the CLI tools and exposes:

- the model and accessors for the user-supplied cluster configuration (read, validate, query),
- a static catalogue of per-Raspberry-Pi-model data (board capabilities, default boot args, network interfaces, …),
- a static catalogue of per-Alpine-version data (release URLs, package names, defaults, …).

Each tool depends on this library rather than reimplementing configuration parsing or hardcoding model/version
specifics.

See [`core/README.md`](core/README.md) for the full description of the library's responsibilities and storage model.

## Tools

The toolchain is split into three independent commands. Each one consumes (and/or produces) the cluster configuration so
they can be chained.

### `config`

Authors the cluster configuration. Provides both a CLI and an embedded web UI to describe:

- which Raspberry Pi hardware will be used,
- which storage layout to apply,
- how each Pi connects to the network — wired or Wi-Fi, DHCP or static IP.

The output is the configuration file consumed by the other tools.

### `imagegenerator`

Produces a ready-to-flash SD card image based on the configuration. The image bundles the Alpine Linux installer
together with first-boot scripts that apply the chosen settings (hostname, network, users, SSH, boot args, …) on the
target Pi.

### `inventorygenerator`

Generates an Ansible inventory used to install the cluster's system software (Kubernetes, cri-o, …) onto the
freshly-booted Pis. The inventory carries the version pins (Kubernetes, Alpine, …) and other variables consumed by the
playbooks.

> The Ansible playbooks themselves are **not** kept in this repository.

## Stack

- [Kotlin](https://kotlinlang.org/) on [Micronaut](https://micronaut.io/)
- [GraalVM `native-image`](https://www.graalvm.org/latest/reference-manual/native-image/) for distribution as standalone
  binaries
