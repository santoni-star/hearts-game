#!/bin/bash
# Termux build script for Hearts Game
# NOTE: Due to aapt2 architecture mismatch (Gradle downloads x86_64 aapt2 but Termux is ARM64),
# local builds on Termux are NOT reliable. Use GitHub Actions instead.
# This script applies workarounds but may still fail.

set -euo pipefail

echo "=== Hearts Game Termux Build (Experimental) ==="
echo "WARNING: ARM64 Termux builds have known aapt2 issues."
echo "For reliable builds, use GitHub Actions (x86_64)."
echo ""

echo "Setting up environment..."

# Android SDK setup (if not already done)
if [ ! -d "$HOME/.android" ]; then
    echo "Setting up Android SDK..."
    mkdir -p ~/.android
    cd ~/.android
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    unzip -q cmdline-tools.zip
    mkdir -p cmdline-tools/latest
    mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
    rm cmdline-tools.zip
fi

export ANDROID_HOME="$HOME/.android"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# Install required SDK components
echo "Installing SDK components..."
yes | sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools" >/dev/null 2>&1

# Install aapt2 from Termux (ARM64 native)
echo "Installing aapt2..."
pkg install -y aapt2 >/dev/null 2>&1

# Verify aapt2
AAPT2_PATH="/data/data/com.termux/files/usr/bin/aapt2"
if [ ! -f "$AAPT2_PATH" ]; then
    echo "ERROR: aapt2 not found at $AAPT2_PATH"
    exit 1
fi

# Replace ALL cached aapt2 binaries with ARM64 version
echo "Replacing cached aapt2 binaries..."
find "$HOME/.gradle/caches" -name "aapt2" -type f -exec sh -c 'cp -f "$AAPT2_PATH" "$1" && chmod +x "$1"' _ {} \; 2>/dev/null || true

# Update local.properties with aapt2 override
echo "Configuring local.properties..."
cat > local.properties <<EOF
sdk.dir=$ANDROID_HOME
android.aapt2FromMavenOverride=$AAPT2_PATH
EOF

# Make gradlew executable
chmod +x gradlew

echo "Building Debug APK..."
./gradlew assembleDebug --no-daemon -Dorg.gradle.jvmargs="-Xmx2048m"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=== BUILD SUCCESSFUL ==="
    echo "APK location: $APK_PATH"
    echo "Size: $(du -h $APK_PATH | cut -f1)"
    echo ""
    echo "To install on device:"
    echo "  cp $APK_PATH /sdcard/Download/hearts-debug.apk"
    echo "  Then install via file manager or:"
    echo "  su -c \"pm install -r /sdcard/Download/hearts-debug.apk\""
else
    echo ""
    echo "=== BUILD FAILED ==="
    echo "This is expected on ARM64 Termux due to aapt2 architecture mismatch."
    echo "Please use GitHub Actions for reliable builds:"
    echo "  1. git init && git add . && git commit -m 'Initial commit'"
    echo "  2. gh repo create hearts-game --public --source=. --push"
    echo "  3. Go to GitHub Actions tab and run 'Build APK' workflow"
    exit 1
fi