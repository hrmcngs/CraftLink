#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
transport=lan
offline=false
prepare=false
port=""
modpack=false
login=true
usage() { printf 'Usage: sh run.sh [lan|usb] [--offline] [--mods] [--login|--no-login] [--port 1024..65535] [--prepare]\n'; }
if [[ "${1:-}" == lan || "${1:-}" == usb ]]; then transport="$1"; shift; fi
while (( $# )); do
  case "$1" in
    --offline|offline) offline=true; shift ;;
    --prepare) prepare=true; shift ;;
    --mods) modpack=true; shift ;;
    --login) login=true; shift ;;
    --no-login) login=false; shift ;;
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
if [[ "$modpack" == true ]]; then args+=(-PcraftlinkModpack=true); fi
if [[ -n "$port" ]]; then args+=("-PcraftlinkPort=$port"); fi
if [[ "$offline" == true ]]; then
  if [[ ! -d web/node_modules ]]; then printf 'Offline dependencies missing. First run: sh run.sh --prepare\n' >&2; exit 1; fi
  # ForgeGradle otherwise probes its Maven certificate even with cached dependencies.
  # Offline forbids downloads; skip only that remote preflight, retaining TLS validation.
  args+=(--offline -PwebOffline=true -Dnet.minecraftforge.gradle.check.certs=false)
fi
if [[ "$modpack" == true ]]; then
  printf '自作MODをビルドしてローカルのMODセットを更新します…\n'
  update_args=(bash "$project_dir/scripts/build-mods.sh")
  if [[ "$offline" == true ]]; then update_args+=(--offline); fi
  if "${update_args[@]}"; then
    printf 'MODセットの更新が完了しました。最新版で起動準備を続けます。\n'
  else
    result=$?
    printf '自作MODの更新に失敗したため、古いJARでの起動を中止しました。\n' >&2
    exit "$result"
  fi
fi
if [[ "$login" == true ]]; then
  umask 077
  mkdir -p .craftlink/dev-auth
  chmod 700 .craftlink/dev-auth
  args+=(-PcraftlinkLogin=true)
  if [[ "$prepare" == true ]]; then args+=(verifyDevLogin); fi
  printf 'Microsoftログインで起動します。初回は表示された公式ログインページをブラウザで開いて認証してください。\n'
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
if bash ./gradlew "${args[@]}"; then
  exit 0
else
  result=$?
  printf '\nMinecraftの起動に失敗しました（終了コード %s）。\nログ: %s/run/logs/latest.log\n' "$result" "$project_dir" >&2
  shopt -s nullglob
  reports=(run/crash-reports/crash-*.txt)
  if (( ${#reports[@]} > 0 )); then printf 'クラッシュレポート: %s/%s\n' "$project_dir" "${reports[${#reports[@]}-1]}" >&2; fi
  exit "$result"
fi
