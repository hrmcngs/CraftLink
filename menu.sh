#!/bin/sh
# sh menu.sh  # 対話式でビルド・起動を選択（このファイルのあるフォルダーで実行）
# 最初の画面: 1/EnterでMinecraft起動、2で起動方法、3で設定、4で詳細。
# 直接実行: sh build.sh / sh run.sh / sh run-usb.sh / sh school.sh
# Interactive entry point; the existing scripts own builds and USB cleanup.
set -u
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1
case "${1:-}" in
  '') ;;
  -h|--help) printf 'Usage: sh menu.sh\n対話式メニューでビルド・LAN/USB/学校用起動・ログイン状態・追加MOD・中継設定を選択します。\n'; exit 0 ;;
  *) printf 'Usage: sh menu.sh\n' >&2; exit 2 ;;
esac
mode=online
if [ -d "$project_dir/web/node_modules" ] && [ -d "$project_dir/relay/node_modules/ws" ]; then mode=offline; fi
screen=main
launch_choice=10
launch_label=学校用・スマホでBOTを操作
mods=false
port=
auth_flag=
auth_label=前回の状態（未設定なら標準）
trap 'exit 130' INT
trap 'exit 143' TERM
while :; do
  if [ "$mods" = true ]; then mods_label=あり; else mods_label=なし; fi
  if [ "$mode" = online ]; then download_label=許可（初回準備向け）; else download_label=しない（準備済み）; fi
  if [ "$screen" = main ]; then
    printf '\n━━━━━━━━ CraftLink ━━━━━━━━\n\n'
    printf '  1) ▶ Minecraftを起動（Enterでも起動）\n     %s\n\n' "$launch_label"
    printf '  2) 起動方法を変更（学校 / 同じWi-Fi / USB）\n'
    printf '  3) ログイン・追加MODの設定\n'
    printf '  4) 詳細メニュー（ビルド・中継・開発用）\n'
    printf '  5) 使い方を見る\n  0) 終了\n\n'
    printf 'ログイン: %s\n番号を入力 > ' "$auth_label"
    IFS= read -r choice || { printf '\n'; exit 0; }
    case "$choice" in
      1|'') choice="$launch_choice" ;;
      2)
        printf '\n【起動方法】\n  1) 学校用：スマホでBOTを操作（中継付き）\n  2) 同じWi-Fi / LANで遊ぶ\n  3) AndroidをUSBケーブルで接続\n  4) 学校用：自分のプレイヤーを操作（中継付き）\n  0) 戻る\n選択 > '
        IFS= read -r selection || exit 0
        case "$selection" in
          1) launch_choice=10; launch_label=学校用・スマホでBOTを操作 ;;
          2) launch_choice=2; launch_label=同じWi-Fi・LAN ;;
          3) launch_choice=3; launch_label=Android・USBケーブル ;;
          4) launch_choice=12; launch_label=学校用・自分のプレイヤーを操作 ;;
          0) ;;
          *) printf '0〜4を選んでください。\n' ;;
        esac
        continue ;;
      3)
        printf '\n【設定】\n  1) ログイン状態を変更\n  2) 追加MODあり / なし（現在: %s）\n  3) 依存ダウンロード（現在: %s）\n  4) LAN・USBポート\n  0) 戻る\n選択 > ' "$mods_label" "$download_label"
        IFS= read -r selection || exit 0
        case "$selection" in 1) choice=11 ;; 2) choice=14 ;; 3) choice=5 ;; 4) choice=6 ;; 0) continue ;; *) printf '0〜4を選んでください。\n'; continue ;; esac ;;
      4) screen=advanced; continue ;;
      5) printf '\n1 またはEnterでMinecraftを起動します。ワールドを開き、スマホでQRを読んでください。\n起動方法は2、ログイン設定は3で変更できます。\n学校用は登録済み中継が必要です。初回準備は詳細メニューの4、認証キー再設定は13です。\n'; continue ;;
      0|q|quit) exit 0 ;;
      *) printf 'メニューの番号を入力してください。\n'; continue ;;
    esac
  else
  printf '\nCraftLink 詳細メニュー（0で最初の画面へ戻る）\n'
  printf 'ログイン状態: %s（11で変更）\n' "$auth_label"
  printf '依存取得: %s / LAN・USBポート: %s / 追加MOD: %s\n' "$mode" "${port:-標準}" "$mods"
  printf '  1) MODをビルド\n  2) Minecraftを起動（LAN / Wi-Fi）\n  3) Minecraftを起動（Android USB / Wi-Fi不要）\n  4) 起動用データを準備（起動しない・スマホ不要）\n  5) オンライン / オフライン切り替え\n  6) 起動ポートを変更\n  7) 接続方法を表示\n  8) 別のWi-Fi用HTTPS中継を起動\n  9) 外部中継サーバーに接続（HTTPS・443番）\n  10) 学校用まとめて起動（初回のみ設定）\n  11) dev / ログイン状態を選択\n  12) 学校用まとめて起動（自分のプレイヤー）\n  13) 学校用の中継認証キーを再設定して起動\n  14) 追加MODあり / なしを切り替え\n  15) 追加MODセットだけをビルド\n  16) コマンド・shellの詳しい説明\n  0) 終了\n選択 > '
  IFS= read -r choice || { printf '\n'; exit 0; }
    if [ "$choice" = 0 ]; then screen=main; continue; fi
  fi
  case "$choice" in
    0|q|quit) exit 0 ;;
    16|help|'?')
      printf '\n説明する項目: school / play / usb / build / prepare / mods / tunnel / relay / login / terminal\n空欄で全体の説明 > '
      IFS= read -r topic || exit 0
      sh "$project_dir/craftlink" help "${topic:-overview}"
      continue ;;
    11)
      printf '1) dev（ゲスト）  2) Microsoftログイン  3) 前回の名前・UUID（再認証なし）  4) 保存した起動状態を復元  0) 戻る > '
      IFS= read -r auth_choice || exit 0
      case "$auth_choice" in
        1) auth_flag=--dev; auth_label=dev（ゲスト） ;;
        2) auth_flag=--login; auth_label=Microsoftログイン ;;
        3) auth_flag=--cached-login; auth_label=保存済み本人情報 ;;
        4) auth_flag=--previous; auth_label=前回の状態（未設定なら標準） ;;
        0) ;;
        *) printf '0〜4を選んでください。\n' ;;
      esac
      continue ;;
    14)
      if [ "$mods" = true ]; then mods=false; else mods=true; fi
      continue ;;
    5)
      if [ "$mode" = online ]; then mode=offline; else mode=online; fi
      printf '初回はオンラインで依存を取得してください。オフラインは取得済みキャッシュを使います。\n'
      continue ;;
    6)
      printf 'ポート（1024〜65535、空欄で標準に戻す）> '
      IFS= read -r candidate || { printf '\n'; exit 0; }
      case "$candidate" in
        '') port= ;;
        *[!0-9]*|??????*) printf '1024〜65535の整数を入力してください。\n'; continue ;;
        *)
          # Strip leading zeroes before POSIX numeric comparisons.
          while [ "${candidate#0}" != "$candidate" ]; do candidate=${candidate#0}; done
          if [ -n "$candidate" ] && [ "$candidate" -ge 1024 ] && [ "$candidate" -le 65535 ]; then
            port=$candidate
          else printf '1024〜65535の整数を入力してください。\n'; fi ;;
      esac
      continue ;;
    7)
      printf '\n起動後、F8 →「接続を開始」→ スマホでQRを読む → PCで接続を許可。\nLAN: PCとスマホが通信できる同じネットワークを使用します。\nUSB: AndroidのUSBデバッグを有効にし、PCを許可してケーブル接続します。adbが必要です。\niPhoneはこのAndroid USBモードには対応していません。\n依存取得のオンライン/オフライン設定は、スマホの接続方式とは別です。\n参加モード: LANで起動し、シングルプレイ → F8 →「みんなで参加」。F9で全員停止できます。\n詳細: %s/docs/USER_GUIDE.md\n' "$project_dir"
      continue ;;
    8)
      printf 'この中継を動かしたままMinecraftを使うため、別のターミナルで開いてください。\n1) 通常コントローラー  2) みんなで参加 > '
      IFS= read -r tunnel_choice || { printf '\n'; exit 0; }
      case "$tunnel_choice" in
        1) set -- sh "$project_dir/tunnel.sh" controller; if [ -n "$port" ]; then set -- "$@" --port "$port"; fi ;;
        2) set -- sh "$project_dir/tunnel.sh" classroom ;;
        *) printf '1または2を選んでください。\n'; continue ;;
      esac ;;
    10|12|13)
      set -- sh "$project_dir/school.sh"
      if [ "$choice" = 12 ]; then set -- "$@" controller; fi
      if [ "$choice" = 13 ]; then set -- "$@" --setup; fi
      if [ "$mode" = online ]; then set -- "$@" --online; else set -- "$@" --offline; fi ;;
    15)
      set -- bash "$project_dir/scripts/build-mods.sh"
      if [ "$mode" = offline ]; then set -- "$@" --offline; fi ;;
    9)
  printf '別ターミナルで実行してください。公開済み中継のHTTPS URL > '
  IFS= read -r relay_url || exit 0
  printf '1) 通常コントローラー  2) みんなで参加 > '
  IFS= read -r relay_choice || exit 0
  case "$relay_choice" in
    1) set -- sh "$project_dir/relay.sh" controller "$relay_url" ;;
    2) set -- sh "$project_dir/relay.sh" classroom "$relay_url" ;;
    *) printf '1または2を選んでください。\n'; continue ;;
  esac ;;
    1) set -- sh "$project_dir/build.sh" "$mode" ;;
    2|3|4)
      set -- sh "$project_dir/run.sh"
      if [ "$choice" = 3 ]; then set -- "$@" usb; else set -- "$@" lan; fi
      if [ "$choice" = 4 ]; then set -- "$@" --prepare; fi
      if [ "$mode" = offline ]; then set -- "$@" --offline; fi
      if [ -n "$port" ]; then set -- "$@" --port "$port"; fi ;;
    *) printf 'メニューの番号を入力してください。\n'; continue ;;
  esac
  case "$choice" in
    2|3|4|10|12|13)
      if [ -n "$auth_flag" ]; then set -- "$@" "$auth_flag"; fi
      if [ "$mods" = true ]; then set -- "$@" --mods; fi ;;
  esac
  case "$choice" in
    2|3|10|12|13) printf '\n▶ Minecraftを起動しています。初回は準備に時間がかかります。\n' ;;
    *) printf '\n選択した処理を実行しています。\n' ;;
  esac
  if [ "$screen" = advanced ]; then printf '実行するshell: %s\n' "$2"; fi
  printf '処理終了後はメニューへ戻ります。中断はCtrl+Cです。\n'
  if "$@"; then
    printf '\n処理が完了しました。\n'
  else
    result=$?
    printf '\n処理に失敗しました（終了コード: %s）。上のエラーを確認して再実行できます。\n' "$result" >&2
  fi
done
