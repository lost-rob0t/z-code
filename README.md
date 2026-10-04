# ZCode for NixOS

This flake packages the official ZCode Linux x64 AppImage. It is pinned to
ZCode 3.11.2 and a fixed Nixpkgs revision.

## Run

```sh
nix run github:lost-rob0t/z-code
```

## Install

```sh
nix profile install github:lost-rob0t/z-code
zcode
```

From a local checkout, use `nix run .`.

The upstream Linux build is beta and currently supports `x86_64-linux`. Its
own desktop launcher disables Chromium's process sandbox, so this package uses
the same `--no-sandbox` launch flag.

## Android remote companion (alpha)

`android/` adds **ZCode Remote**, an Android 8+ connection manager that opens
ZCode's existing mobile remote interface. Agent execution, repositories, tools
and model configuration remain on the desktop. The Nix desktop package is unchanged.

Scan the desktop's remote-control QR or paste/share its complete HTTPS link,
review the destination, save it, and open the connection. QR uses Google Play
services; paste/share remains available without it.

This first slice is a native Android shell around the **host-served** upstream UI,
not a bundled React/Capacitor port or an independent protocol implementation.
A separately configured HTTPS web server can also be saved; its sessions are not
presented as the running Electron window's sessions.

See [setup, security boundaries and build instructions](docs/android-remote.md)
and the [remaining implementation and device acceptance gates](docs/android-roadmap.md).

```sh
# No Android SDK or network needed; requires JDK 17+ and Python 3.
bash scripts/test-core.sh

# Requires Gradle 8.13, JDK 17 and Android SDK 36.
gradle -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The Android Remote workflow publishes a debug APK and checksum **only after**
its build succeeds. Alpha/debug signing is not a production release identity.
