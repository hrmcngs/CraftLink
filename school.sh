#!/bin/sh
# CraftLink インターネット中継付き起動（このファイルのあるフォルダーで実行）
# sh school.sh             # 保存済み設定でMinecraftと中継をまとめて起動
# sh school.sh --online    # 初回・依存キャッシュ不足時（依存取得を許可）
# sh school.sh --mods      # Curios・武器Mod入りで起動
# sh school.sh --setup     # 中継の接続設定をやり直す
# sh school.sh controller  # BOT参加ではなく自分のプレイヤーを操作
# 初回のみ中継の秘密キーを非表示入力します。このファイルに記載しないでください。
# ワールドを開くと参加用QRを表示します。名前・スキンはスマホの設定から変更できます。
# 学校などのネットワーク側が中継への通信を禁止している場合は接続できません。
# Macの「Failed to locate a primary monitor」対策は起動時に自動適用します。
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec bash "$project_dir/scripts/school.sh" "$@"
