# source scripts/terminal.zsh — 現在のzshへCraftLinkコマンドとTab補完を追加。
# .zshrc・認証データ・グローバルPATHは変更しません。
typeset -g CRAFTLINK_TERMINAL_ROOT="${${(%):-%x}:A:h:h}"
craftlink() { command sh "$CRAFTLINK_TERMINAL_ROOT/craftlink" "$@"; }
_craftlink() {
  local -a commands topics
  commands=('menu:対話式メニュー' 'school:中継付き学校用起動' 'play:LAN起動' 'usb:Android_USB起動' 'build:MODビルド' 'prepare:起動準備' 'mods:追加MOD更新' 'tunnel:Cloudflare中継' 'relay:公開中継接続' 'help:使い方・実行内容')
  topics=(school play usb build prepare mods tunnel relay login terminal)
  if (( CURRENT == 2 )); then
    _describe 'やりたいこと' commands
  elif [[ "$words[2]" == help ]]; then
    compadd -- "${topics[@]}"
  else
    case "$words[2]" in
      school) compadd -- controller classroom --online --offline --login --cached-login --previous --mods --setup ;;
      usb) compadd -- --offline --login --cached-login --previous --mods --port ;;
      play|prepare) compadd -- --offline --login --dev --cached-login --previous --mods --port ;;
      build) compadd -- online offline ;;
      mods) compadd -- --offline --project ;;
      tunnel|relay) compadd -- controller classroom ;;
    esac
  fi
}
if ! (( $+functions[compdef] )); then
  autoload -Uz compinit
  compinit -D
fi
compdef _craftlink craftlink
printf 'CraftLinkコマンドを追加しました。craftlink でメニュー、craftlink help で説明、Tabで候補を表示します。\n'
