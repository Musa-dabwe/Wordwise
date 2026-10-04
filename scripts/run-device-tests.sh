#!/usr/bin/env bash
#
# Run the instrumented (androidTest) suite on a connected device.
#
# WHY THIS EXISTS
#
# `./gradlew connectedDebugAndroidTest` drives the tests through Google's
# Unified Test Platform (UTP), which fails on a wireless ADB connection with:
#
#     Failed to install split APK(s): [.../app-debug.apk]
#     com.android.ddmlib.ShellCommandUnresponsiveException
#
# UTP's install step times out talking to the device over WiFi, and it reports
# the whole run as "failed tests" even though zero tests ran. The APK installs
# fine by hand, so this is a harness problem, not an app problem.
#
# This script skips UTP entirely: install both APKs with plain `adb install`,
# then invoke the instrumentation runner with `am instrument`. Those calls are
# single short commands and tolerate a slow link.
#
# Usage:
#   scripts/run-device-tests.sh                 # all tests on the default device
#   scripts/run-device-tests.sh -s <serial>     # pick a specific device
#   scripts/run-device-tests.sh -c <class>      # run one test class
#
# Java: set JAVA_HOME, or have a JDK 17 on PATH.

set -uo pipefail

SERIAL=""
CLASS_FILTER=""

while getopts "s:c:h" opt; do
  case "$opt" in
    s) SERIAL="$OPTARG" ;;
    c) CLASS_FILTER="$OPTARG" ;;
    h) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "unknown option" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/.." || exit 1

ADB=(adb)
if [ -n "$SERIAL" ]; then ADB+=(-s "$SERIAL"); fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH" >&2
  exit 1
fi

# --- pick a device -----------------------------------------------------------
if [ -z "$SERIAL" ]; then
  mapfile -t DEVS < <("${ADB[@]}" devices | awk 'NR>1 && $2=="device" {print $1}')
  if [ "${#DEVS[@]}" -eq 0 ]; then
    echo "No device. Connect over USB, or enable Wireless debugging and run:"
    echo "  adb tcpip 5555 && adb connect <device-ip>:5555" >&2
    exit 1
  fi
  if [ "${#DEVS[@]}" -gt 1 ]; then
    echo "Multiple devices; pick one with -s:" >&2
    printf '  %s\n' "${DEVS[@]}" >&2
    exit 1
  fi
  ADB+=(-s "${DEVS[0]}")
fi

echo "Device: $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')  (${ADB[*]})"

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

# --- build -------------------------------------------------------------------
# Instrumentation runs against the debug APK on purpose: the release build is
# R8-minified, and minification can strip or rename the classes an
# instrumentation test needs to reach.
echo "Building debug + androidTest APKs..."
./gradlew --quiet assembleDebug assembleDebugAndroidTest || exit 1

for apk in "$APP_APK" "$TEST_APK"; do
  [ -f "$apk" ] || { echo "Missing $apk" >&2; exit 1; }
done

# --- install -----------------------------------------------------------------
# -t allows installing test/debuggable packages.
echo "Installing APKs..."
for apk in "$APP_APK" "$TEST_APK"; do
  if ! "${ADB[@]}" install -r -t "$apk" >/dev/null 2>&1; then
    echo "adb install failed for $apk" >&2
    # A signature clash means a release-signed build is already installed.
    echo "If the message is a signature mismatch, uninstall it first:" >&2
    echo "  ${ADB[*]} uninstall com.musa.wordwise" >&2
    exit 1
  fi
done

# --- run ---------------------------------------------------------------------
RUNNER="com.musa.wordwise.test/androidx.test.runner.AndroidJUnitRunner"
ARGS=(-w -r)
[ -n "$CLASS_FILTER" ] && ARGS+=(-e class "$CLASS_FILTER")

echo "Running instrumented tests..."
"${ADB[@]}" shell am instrument "${ARGS[@]}" "$RUNNER"
STATUS=$?

# am instrument exits 0 on success, non-zero on test failure.
exit $STATUS