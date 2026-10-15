#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  printf 'Usage: sh relay.sh controller|classroom https://your-relay.onrender.com\n認証キーは環境変数CRAFTLINK_RELAY_SECRETか非表示プロンプトで入力します。\n'; exit 0
fi
if [[ $# != 2 || ( "$1" != controller && "$1" != classroom ) ]]; then
  printf 'Usage: sh relay.sh controller|classroom https://your-relay.onrender.com\n' >&2; exit 2
fi
node -e 'if(Number(process.versions.node.split(".")[0])<22)throw new Error("Node.js 22以上が必要です")'
if [[ ! -d "$project_dir/relay/node_modules/ws" ]]; then npm ci --prefix "$project_dir/relay" --no-audit --no-fund; fi
if [[ -z "${CRAFTLINK_RELAY_SECRET:-}" ]]; then
  if [[ ! -t 0 ]]; then printf 'CRAFTLINK_RELAY_SECRETを設定してください。\n' >&2; exit 1; fi
  printf 'RenderのEnvironmentにあるCRAFTLINK_RELAY_SECRETを貼り付けてEnter（表示されません）> '
  IFS= read -r -s CRAFTLINK_RELAY_SECRET
  printf '\n'
  export CRAFTLINK_RELAY_SECRET
fi
exec node "$project_dir/relay/bridge.mjs" "$@"
