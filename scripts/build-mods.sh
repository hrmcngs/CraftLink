#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
weapons_dir="${CRAFTLINK_WEAPONS_PROJECT:-$HOME/The-four-primitives-and-Weapons}"
offline=false
while (( $# )); do
  case "$1" in
    --offline|offline) offline=true; shift ;;
    --project) weapons_dir="${2:?--project にModのフォルダーを指定してください}"; shift 2 ;;
    *) printf 'Usage: sh build-mods.sh [--offline] [--project PATH]\n' >&2; exit 2 ;;
  esac
done
[[ -f "$weapons_dir/gradlew" ]] || { printf 'Modプロジェクトが見つかりません: %s\n' "$weapons_dir" >&2; exit 1; }
mkdir -p "$project_dir/.craftlink/modpack"
stage_dir="$project_dir/.craftlink/modpack"
external_args=(build craftlinkStage --console=plain -Pmod_version_override=7-test -PwithExternalMods=false -I "$project_dir/scripts/modpack.init.gradle" "-PcraftlinkStageDirectory=$stage_dir")
local_args=(modpack --console=plain -PcraftlinkModpack=true -PcraftlinkRemote=false -PcraftlinkLogin=false)
if [[ "$offline" == true ]]; then
  external_args+=(--offline)
  local_args+=(--offline -PwebOffline=true -Dnet.minecraftforge.gradle.check.certs=false)
fi
(cd "$weapons_dir"; bash ./gradlew "${external_args[@]}")
cd "$project_dir"
bash ./gradlew "${local_args[@]}"
printf '\nModセット: %s/build/modpack/mods\n起動: sh run.sh --mods%s\n' "$project_dir" "$([[ "$offline" == true ]] && printf ' --offline' || true)"
