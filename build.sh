#!/bin/sh
# CraftLink ビルド（このファイルのあるフォルダーで実行）
# sh build.sh          # 初回・通常ビルド：依存をダウンロード可能
# sh build.sh offline  # 取得済み依存だけでビルド
# 出力: build/libs/craftlink-forge-1.20.1-0.1.0.jar
# ビルド後の起動: sh school.sh または sh run.sh --offline
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec bash "$project_dir/scripts/build.sh" "$@"
