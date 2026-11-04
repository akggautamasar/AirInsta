#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
airinsta_apk="$(printenv AIRINSTA_APK_PATH || true)"
if [[ -z "$airinsta_apk" ]]; then airinsta_apk="android/app/build/outputs/apk/debug/app-debug.apk"; fi

"$ANDROID_HOME/emulator/emulator" -avd airinsta_smoke -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > /tmp/airinsta-emulator.log 2>&1 &
if ! timeout 180 adb wait-for-device; then cat /tmp/airinsta-emulator.log; exit 1; fi
timeout 240 bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == "1" ]]; do sleep 3; done'
adb shell input keyevent 82
adb shell wm size 1080x1920
adb shell wm density 420
adb install -r "$airinsta_apk"
adb logcat -c

dump_ui() {
  adb shell uiautomator dump /sdcard/airinsta-window.xml >/dev/null
  adb exec-out cat /sdcard/airinsta-window.xml > android-window.xml
}
capture() {
  timeout 10 adb exec-out screencap -p > android-smoke.png || true
  timeout 10 adb logcat -d -s AndroidRuntime:E || true
}
trap capture EXIT
click_if_visible() {
  python3 - "$1" <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as ET
try: root=ET.parse("android-window.xml").getroot()
except Exception: raise SystemExit(1)
label=sys.argv[1]
for n in root.iter("node"):
    if (n.get("text","").startswith(label) or n.get("content-desc","")==label) and n.get("enabled")=="true":
        b=list(map(int,re.findall(r"\d+",n.get("bounds",""))))
        if len(b)==4 and b[2]>b[0] and b[3]>b[1] and b[1]<1840 and b[3]>80:
            subprocess.run(["adb","shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)],check=True)
            raise SystemExit(0)
raise SystemExit(1)
PY
}
click_scroll() {
  for attempt in $(seq 1 6); do
    dump_ui
    if click_if_visible "$1"; then return 0; fi
    adb shell input swipe 500 1600 500 600 350
  done
  cat android-window.xml
  return 1
}
assert_playing() {
  for attempt in $(seq 1 18); do
    sleep 3
    dump_ui
    if python3 - <<'PY'
import re,xml.etree.ElementTree as ET
texts=[n.get("text","") for n in ET.parse("android-window.xml").getroot().iter("node")]
for text in texts:
    m=re.search(r"Playing · (\d+):(\d+)",text)
    if m and int(m.group(1))*60+int(m.group(2))>0: raise SystemExit(0)
raise SystemExit(1)
PY
    then return 0; fi
  done
  cat android-window.xml
  return 1
}

adb shell am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'https://www.instagram.com/reel/DfP7epuS9s1/' -n com.worksbeyondworks.airinsta/.MainActivity
resolved=0
for attempt in $(seq 1 15); do
  sleep 4
  dump_ui
  if grep -q '@hustlersability' android-window.xml; then resolved=1; break; fi
done
if [[ "$resolved" != 1 ]]; then cat android-window.xml; exit 1; fi
click_scroll 'Watch fullscreen'
assert_playing
adb exec-out screencap -p > android-player.png
adb shell input keyevent 4
click_scroll 'Save link'
dump_ui
click_if_visible 'Library tab'
dump_ui
grep -q '@hustlersability' android-window.xml
adb exec-out screencap -p > android-library.png

# Verify saved links survive process restart.
adb shell am force-stop com.worksbeyondworks.airinsta
adb shell am start -W -n com.worksbeyondworks.airinsta/.MainActivity
sleep 2
dump_ui
click_if_visible 'Library tab'
dump_ui
grep -q '@hustlersability' android-window.xml
click_scroll 'Open'
sleep 5
click_scroll 'Download this item'
dump_ui
click_if_visible 'Downloads tab'
complete=0
for attempt in $(seq 1 24); do
  sleep 4
  dump_ui
  if grep -q 'Complete' android-window.xml; then complete=1; break; fi
  if grep -q 'Download failed' android-window.xml; then cat android-window.xml; exit 1; fi
done
if [[ "$complete" != 1 ]]; then cat android-window.xml; exit 1; fi
adb exec-out screencap -p > android-downloads.png
click_if_visible 'Open'
assert_playing
adb exec-out screencap -p > android-offline-player.png
echo 'PASS: shared link, video playback, saved link persistence, download completion and downloaded-file playback'
