#!/bin/sh
# 指定を省略すると前回の選択を復元（通常起動・学校用起動は別々に保存）。--previous も同じ。
# 起動時の状態を選択: --dev（同じWi-Fi・LAN専用） / --login（Microsoft認証） / --cached-login（保存済み本人情報）
# 例: sh run.sh --dev / sh run.sh --login
# CraftLink 開発用Minecraft起動（このファイルのあるフォルダーで実行）
# sh run.sh                      # 初回・通常起動（LAN / Wi-Fi）
# sh run.sh --offline            # 取得済み依存だけで起動
# sh run.sh --prepare            # 依存取得・起動準備だけ行う
# sh run.sh lan --port 9876      # 接続ポートを指定
# sh run.sh usb --offline        # Android USB接続で起動
# 別のWi-Fi / モバイル通信からBOT参加: sh school.sh
# 通常起動後は F8 から接続を開始してQRコードを表示します。
# Macの「Failed to locate a primary monitor」対策は起動時に自動適用します。
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec bash "$project_dir/scripts/run.sh" "$@"
