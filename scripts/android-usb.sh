#!/usr/bin/env bash
set -euo pipefail
port="${1:-8765}"
if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1024 || 10#$port > 65535 )); then
  printf 'Usage: bash scripts/android-usb.sh [port: 1024..65535]\n' >&2; exit 2
fi
if ! command -v adb >/dev/null 2>&1; then
  printf 'Install Android SDK Platform-Tools (adb), enable USB debugging, and authorize this PC on your phone.\n' >&2; exit 1
fi
# -d selects a USB device and refuses ambiguous multiple-device connections.
adb -d reverse "tcp:$port" "tcp:$port"
printf 'USB forwarding ready. In CraftLink, select 127.0.0.1, scan that QR, then approve on the PC.\n'
printf 'Remove forwarding after use: adb -d reverse --remove tcp:%s\n' "$port"
