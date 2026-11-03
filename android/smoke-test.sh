#!/usr/bin/env bash
set -euo pipefail

"$ANDROID_HOME/emulator/emulator" -avd airinsta_smoke -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > /tmp/airinsta-emulator.log 2>&1 &
adb wait-for-device
timeout 240 bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == "1" ]]; do sleep 3; done'
adb shell input keyevent 82
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.worksbeyondworks.airinsta/.MainActivity
sleep 5
adb shell pidof com.worksbeyondworks.airinsta

dump_ui() {
  adb shell uiautomator dump /sdcard/airinsta-window.xml >/dev/null
  adb exec-out cat /sdcard/airinsta-window.xml > android-window.xml
}
tap_node() {
  python3 - "$1" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
root = ET.parse("android-window.xml").getroot()
kind = sys.argv[1]
nodes = [n for n in root.iter("node") if (n.get("class") == "android.widget.EditText" if kind == "input" else n.get("text", "").startswith("Get media"))]
if not nodes:
    raise SystemExit("Missing UI element: " + kind)
bounds = list(map(int, re.findall(r"\d+", nodes[0].get("bounds", ""))))
subprocess.run(["adb", "shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2)], check=True)
PY
}

dump_ui
tap_node input
adb shell input text 'https://www.instagram.com/reel/DfP7epuS9s1/'
adb shell input keyevent 4
dump_ui
tap_node submit
success=0
for attempt in $(seq 1 12); do
  sleep 5
  dump_ui
  if grep -q 'Link resolved' android-window.xml; then success=1; break; fi
done
adb exec-out screencap -p > android-smoke.png
if [[ "$success" != 1 ]]; then
  cat android-window.xml
  adb logcat -d -s AndroidRuntime:E
  exit 1
fi
