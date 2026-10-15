#!/bin/sh
# Android USB起動（このファイルのあるフォルダーで実行）
# sh run-usb.sh                # Wi-Fiを使わずUSB経由で接続
# sh run-usb.sh --offline      # 取得済み依存だけで起動
# sh run-usb.sh --port 9876    # 接続ポートを指定
# AndroidのUSBデバッグ許可とadbが必要です。iPhoneには対応しません。
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec bash "$project_dir/scripts/run.sh" usb "$@"
