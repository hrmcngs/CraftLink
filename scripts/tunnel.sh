#!/usr/bin/env bash
set -euo pipefail
kind="${1:-controller}"
port=""
usage() { printf 'Usage: sh tunnel.sh [controller|classroom] [--port 1024..65535]\n'; }
if [[ "$kind" == -h || "$kind" == --help ]]; then usage; exit 0; fi
if [[ "$kind" == controller || "$kind" == classroom ]]; then
  if (( $# )); then shift; fi
else usage >&2; exit 2; fi
while (( $# )); do
  case "$1" in
    --port) if (( $# < 2 )); then usage >&2; exit 2; fi; port="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done
if [[ -z "$port" ]]; then if [[ "$kind" == classroom ]]; then port=8766; else port=8765; fi; fi
if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1024 || 10#$port > 65535 )); then usage >&2; exit 2; fi
port="$((10#$port))"
if ! command -v cloudflared >/dev/null 2>&1; then
  printf 'cloudflaredが必要です。Macでは brew install cloudflared を実行してください。\n公式手順: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/\n' >&2
  exit 1
fi
printf 'CraftLink HTTPS中継: %s / ローカルポート %s\n' "$kind" "$port"
printf '中継方式: HTTP/2（TCP）。QUIC/UDPが制限された回線でも接続を試みます。\n'
printf 'Cloudflareを経由します。Mac・スマホのインターネット接続が必要です。\n'
printf '表示される https://...trycloudflare.com を、Minecraftの対象画面 →「別のWi-Fi」に貼り付けて保存してください。\n'
printf '通常: F8 →「別のWi-Fi」。参加モード: F8 →「みんなで参加」→「別のWi-Fi」。\n'
printf 'その後Minecraftで接続を開始し、新しいQRを読みます。終了はこのターミナルでCtrl+C。\n'
# Foreground process: no detached listener or permanent service; Ctrl+C stops the tunnel.
exec cloudflared tunnel --no-autoupdate --protocol http2 --url "http://127.0.0.1:$port"
