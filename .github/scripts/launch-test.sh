#!/bin/bash
# Installs the built APK, adds a few test songs, then runs the smoke test (smoke.py).
set -x
mkdir -p launch-logs
APK=$(ls apk/*.apk | head -1)
adb uninstall com.nobodybuilds.listens >/dev/null 2>&1
adb install -r -g "$APK" || { echo "INSTALL FAILED"; exit 1; }
# Test songs (generated tones with tags) so the player screens can be exercised.
command -v ffmpeg >/dev/null || { echo "ffmpeg missing: cannot create test songs"; exit 1; }
if true; then
  for i in 1 2 3; do
    ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=$((220*i)):duration=40" -c:a libmp3lame -b:a 128k \
      -metadata title="LMusic Test $i" -metadata artist="Test Artist $i" -metadata album="Test Album" "song$i.mp3"
    adb push "song$i.mp3" /sdcard/Music/ >/dev/null
  done
  adb shell content call --method scan_volume --uri content://media --arg external_primary >/dev/null 2>&1 || true
  sleep 5
fi
python3 .github/scripts/smoke.py
