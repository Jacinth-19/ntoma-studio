#!/usr/bin/env bash
# Provisions JDK 17 + Android SDK + Gradle in the sandbox.
set -euo pipefail
TOOLS=/var/tmp/tools
mkdir -p "$TOOLS"
cd "$TOOLS"

echo "### [1/4] JDK 17 (Temurin)"
if [ ! -x "$TOOLS/jdk17/bin/java" ]; then
  curl -fsSL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p jdk17
  tar -xzf jdk17.tar.gz -C jdk17 --strip-components=1
  rm -f jdk17.tar.gz
fi
"$TOOLS/jdk17/bin/java" -version 2>&1 | head -1

echo "### [2/4] Gradle 8.9"
if [ ! -x "$TOOLS/gradle-8.9/bin/gradle" ]; then
  curl -fsSL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  unzip -q -o gradle.zip -d "$TOOLS"
  rm -f gradle.zip
fi
"$TOOLS/gradle-8.9/bin/gradle" --version 2>&1 | grep -E "^Gradle"

echo "### [3/4] Android command-line tools"
if [ ! -d "$TOOLS/cmdline-tools/latest" ]; then
  curl -fsSL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$TOOLS/cmdline-tools"
  unzip -q -o cmdtools.zip -d "$TOOLS/cmdline-tools-tmp"
  mv "$TOOLS/cmdline-tools-tmp/cmdline-tools" "$TOOLS/cmdline-tools/latest"
  rm -rf cmdtools.zip "$TOOLS/cmdline-tools-tmp"
fi

export JAVA_HOME="$TOOLS/jdk17"
export ANDROID_HOME=/var/tmp/android-sdk
export ANDROID_SDK_ROOT=/var/tmp/android-sdk
mkdir -p "$ANDROID_HOME"

echo "### [4/4] SDK packages"
yes | "$TOOLS/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > /dev/null 2>&1 || true
"$TOOLS/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 | tail -5

echo "### DONE"
ls "$ANDROID_HOME"

# --- memory headroom for Gradle/Kotlin on the 2GB sandbox ---
if ! grep -q '^/swapfile' /proc/swaps; then
  sudo fallocate -l 4G /swapfile 2>/dev/null || sudo dd if=/dev/zero of=/swapfile bs=1M count=4096 status=none
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  echo "swapfile enabled"
fi
