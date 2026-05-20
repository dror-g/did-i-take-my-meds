#!/usr/bin/env bash
set -euo pipefail

# Minimal Android SDK setup — works locally AND in CI (GitHub Actions).
# Installs only the components this project needs, with no Android Studio.
#
# Usage:  scripts/setup-android-sdk.sh
#
# On CI: $ANDROID_HOME is already set (ubuntu-latest image),
#        this script installs any missing platform/build-tools.
# Locally: installs cmdline-tools + SDK into $HOME/android-sdk
#          and creates local.properties so Gradle can find it.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---- SDK dir -----------------------------------------------------------
if [ -n "${ANDROID_HOME:-}" ]; then
    SDK_DIR="$ANDROID_HOME"
elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    SDK_DIR="$ANDROID_SDK_ROOT"
else
    SDK_DIR="$HOME/android-sdk"
fi
echo "→ Android SDK: $SDK_DIR"

PLATFORM="platforms;android-34"
BUILD_TOOLS="build-tools;34.0.0"

# ---- Install cmdline-tools if no sdkmanager found -----------------------
if command -v sdkmanager &>/dev/null; then
    SDKMANAGER="sdkmanager"
elif [ -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
else
    echo "Installing cmdline-tools …"

    URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    TMP_ZIP="$(mktemp)"

    if command -v curl &>/dev/null; then
        curl -fsSL -o "$TMP_ZIP" "$URL"
    elif command -v wget &>/dev/null; then
        wget -q -O "$TMP_ZIP" "$URL"
    else
        echo "ERROR: curl or wget required to download cmdline-tools"
        exit 1
    fi

    TMP_DIR="$(mktemp -d)"
    unzip -q -o "$TMP_ZIP" -d "$TMP_DIR"
    rm -f "$TMP_ZIP"

    # Zip contents may be wrapped in cmdline-tools/ or flat (bin/, lib/…)
    mkdir -p "$SDK_DIR/cmdline-tools/latest"
    if [ -d "$TMP_DIR/cmdline-tools" ]; then
        mv "$TMP_DIR/cmdline-tools"/* "$SDK_DIR/cmdline-tools/latest/"
    else
        mv "$TMP_DIR"/* "$SDK_DIR/cmdline-tools/latest/"
    fi
    rm -rf "$TMP_DIR"

    SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
    echo "cmdline-tools installed."
fi

echo "sdkmanager: $SDKMANAGER"

# Accept licenses (harmless if already accepted; sdkmanager ignores repeats)
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

# Install required SDK components
echo "Installing $PLATFORM …"
"$SDKMANAGER" --install "$PLATFORM" 2>&1 | grep -v "^\[=" | grep -v "^$" || true

echo "Installing $BUILD_TOOLS …"
"$SDKMANAGER" --install "$BUILD_TOOLS" 2>&1 | grep -v "^\[=" | grep -v "^$" || true

# ---- local.properties --------------------------------------------------
# Only create if missing — once the user customises it we don't overwrite.
if [ ! -f "$PROJECT_DIR/local.properties" ] && [ -z "${ANDROID_HOME:-}" ]; then
    echo "Creating local.properties"
    echo "sdk.dir=$SDK_DIR" > "$PROJECT_DIR/local.properties"
fi

echo "Done."
