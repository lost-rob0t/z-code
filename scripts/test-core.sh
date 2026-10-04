#!/usr/bin/env bash
set -euo pipefail
root="$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
out="$(mktemp -d)"
trap 'rm -rf -- "$out"' EXIT
src="$root/android/app/src/main/java/actor/starintel/zcode/remote"
javac --release 17 -Xlint:all -Werror -d "$out" \
  "$src/LinkPolicy.java" "$src/SessionState.java" "$src/Profile.java" "$src/VaultCipher.java" \
  "$root/tests/java/actor/starintel/zcode/remote/CoreSpec.java"
java -cp "$out" actor.starintel.zcode.remote.CoreSpec
python3 "$root/scripts/check-android-boundary.py"
