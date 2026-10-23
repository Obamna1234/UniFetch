#!/usr/bin/env bash
# Installs a UniFetch APK on the running emulator, opens it, and checks it is still running
# 25 seconds later. Prints the crash log if it isn't. Usage: smoke-test.sh <apk> <package>
set -u
APK="$1"
PKG="$2"
OUT="smoke-test-logs"
mkdir -p "$OUT"

adb wait-for-device
adb logcat -c || true
echo "Installing $(basename "$APK")"
adb install -r -g "$APK"

echo "Opening $PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 25

adb logcat -d > "$OUT/logcat.txt" || true
adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png "$OUT/screen.png" || true

# Crash lines for this app (Java crashes and native crashes).
grep -n -A60 -E "FATAL EXCEPTION|Process: $PKG|>>> $PKG <<<" "$OUT/logcat.txt" > "$OUT/crash.txt" || true

if adb shell pidof "$PKG" > /dev/null 2>&1 && [ ! -s "$OUT/crash.txt" ]; then
  echo "PASS: $PKG is running and no crash was logged."
  exit 0
fi

echo "FAIL: $PKG crashed or is not running."
echo "----- crash log -----"
cat "$OUT/crash.txt"
echo "----- UniFetch log lines -----"
grep -E "AndroidRuntime|$PKG|UniFetch|unifetch" "$OUT/logcat.txt" | tail -n 120
exit 1
