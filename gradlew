#!/usr/bin/env sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROP="$DIR/gradle/wrapper/gradle-wrapper.properties"
URL=$(sed -n 's/^distributionUrl=//p' "$PROP" | sed 's#\\:#:#g')
VER=$(printf '%s' "$URL" | sed -n 's#.*gradle-\([0-9][0-9.]*\)-bin.zip#\1#p')
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/reconstructed-wrapper/gradle-$VER"
BIN="$CACHE/gradle-$VER/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$CACHE"
  ZIP="$CACHE/gradle-$VER-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ZIP";
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL";
  else echo "curl or wget is required" >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$CACHE"
fi
exec "$BIN" "$@"
