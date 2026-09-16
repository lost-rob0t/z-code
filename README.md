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
