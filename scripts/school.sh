#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
setup=false
offline=true
for arg in "$@"; do
  case "$arg" in
    --setup) setup=true ;;
    --offline) offline=true ;;
    --online) offline=false ;;
    --mods|--login|--cached-login|--no-login) ;;
    classroom|controller) ;;
    --help|-h) printf 'Usage: sh school.sh [classroom|controller] [--offline|--online] [--setup] [--mods] [--login|--cached-login|--no-login]\n標準はBOT参加・取得済み依存と前回認証した名前・UUIDでオフライン起動。自宅で一度 sh run.sh --login をタイトル画面まで起動してください。--loginはオンラインで再認証。--modsでCurios・武器Modを読み込みます。初回のみRenderの認証キーを入力。\n'; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$arg" >&2; exit 2 ;;
  esac
done
node -e 'if(Number(process.versions.node.split(".")[0])<22)throw new Error("Node.js 22以上が必要です")'
if [[ ! -d "$project_dir/relay/node_modules/ws" ]]; then
  if [[ "$offline" == true ]]; then printf '初回は sh school.sh --online で依存を取得してください。\n' >&2; exit 1; fi
  npm ci --prefix "$project_dir/relay" --no-audit --no-fund
fi
if [[ "$setup" == true || ! -f "$project_dir/.craftlink/school.json" ]]; then
  if [[ -z "${CRAFTLINK_RELAY_SECRET:-}" ]]; then
    if [[ ! -t 0 ]]; then printf '初回はターミナルから起動し、Renderの認証キーを入力してください。\n' >&2; exit 1; fi
    printf '初回のみ: Render → Environment → CRAFTLINK_RELAY_SECRETを貼り付けてEnter（表示されません）> '
    IFS= read -r -s CRAFTLINK_RELAY_SECRET; printf '\n'
    export CRAFTLINK_RELAY_SECRET
  fi
fi
exec node "$project_dir/relay/school.mjs" "$@"
