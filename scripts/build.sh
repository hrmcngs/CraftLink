#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
mode="${1:-online}"
args=(build --console=plain)
case "$mode" in
  online) ;;
  offline|--offline) args+=(--offline -PwebOffline=true) ;;
  *) printf 'Usage: bash scripts/build.sh [online|offline]\n' >&2; exit 2 ;;
esac
for tool in java node npm; do
  if ! command -v "$tool" >/dev/null 2>&1; then printf 'Required tool missing: %s (JDK 17 / Node.js 22 / npm)\n' "$tool" >&2; exit 1; fi
done
if [[ "$mode" != online ]]; then
  if [[ ! -d web/node_modules ]]; then
    printf 'Offline cache missing. First run: bash scripts/build.sh online\n' >&2; exit 1
  fi
fi
bash ./gradlew "${args[@]}"
printf '\nBuilt: %s/build/libs/craftlink-forge-1.20.1-0.1.0.jar\n' "$project_dir"

printf "Host kit: %s/build/distributions/craftlink-host-forge-1.20.1-0.1.0.zip\n" "$project_dir"
