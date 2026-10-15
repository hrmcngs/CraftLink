#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
transport=lan
offline=false
prepare=false
port=""
usage() { printf 'Usage: sh run.sh [lan|usb] [--offline] [--port 1024..65535] [--prepare]\n'; }
if [[ "${1:-}" == lan || "${1:-}" == usb ]]; then transport="$1"; shift; fi
while (( $# )); do
  case "$1" in
    --offline|offline) offline=true; shift ;;
    --prepare) prepare=true; shift ;;
    --port) if (( $# < 2 )) || [[ -z "$2" ]]; then usage >&2; exit 2; fi; port="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done
if [[ -n "$port" ]]; then
  if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1024 || 10#$port > 65535 )); then usage >&2; exit 2; fi
  port="$((10#$port))"
fi
if [[ "$transport" == usb && -z "$port" ]]; then port=8765; fi
for tool in java node npm; do
  if ! command -v "$tool" >/dev/null 2>&1; then printf 'Required tool missing: %s (JDK 17 / Node.js 22 / npm)\n' "$tool" >&2; exit 1; fi
done
args=(runClient --console=plain "-PcraftlinkTransport=$transport")
if [[ "$prepare" == true ]]; then args=(classes prepareRunClient --console=plain "-PcraftlinkTransport=$transport"); fi
if [[ -n "$port" ]]; then args+=("-PcraftlinkPort=$port"); fi
if [[ "$offline" == true ]]; then
  if [[ ! -d web/node_modules ]]; then printf 'Offline dependencies missing. First run: sh run.sh --prepare\n' >&2; exit 1; fi
  # ForgeGradle otherwise probes its Maven certificate even with cached dependencies.
  # Offline forbids downloads; skip only that remote preflight, retaining TLS validation.
  args+=(--offline -PwebOffline=true -Dnet.minecraftforge.gradle.check.certs=false)
fi
created_forward=false
serial=""
cleanup() {
  if [[ "$created_forward" == true ]]; then adb -s "$serial" reverse --remove "tcp:$port" || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if [[ "$transport" == usb && "$prepare" == false ]]; then
  if ! command -v adb >/dev/null 2>&1; then printf 'USB mode needs Android SDK Platform-Tools (adb) and an authorized USB-debugging device.\n' >&2; exit 1; fi
  serial="$(adb -d get-serialno)"
  if [[ -z "$serial" || "$serial" == unknown ]]; then printf 'Connect one authorized Android USB device.\n' >&2; exit 1; fi
  mappings="$(adb -s "$serial" reverse --list)"
  reuse=false
  while read -r mapping_device remote_endpoint local_endpoint rest; do
    if [[ "$remote_endpoint" == "tcp:$port" ]]; then
      if [[ "$local_endpoint" != "tcp:$port" ]]; then printf 'USB port %s already forwards elsewhere. Choose --port or remove that mapping explicitly.\n' "$port" >&2; exit 1; fi
      reuse=true
    fi
  done <<< "$mappings"
  if [[ "$reuse" == false ]]; then
    adb -s "$serial" reverse --no-rebind "tcp:$port" "tcp:$port"
    created_forward=true
  fi
  printf 'Android USB mode: Wi-Fi is not used. Press F8, start the connection, scan the 127.0.0.1 QR, and approve on PC.\n'
fi
printf 'CraftLink development client: %s (server starts only when you press Start in F8).\n' "$transport"
bash ./gradlew "${args[@]}"
