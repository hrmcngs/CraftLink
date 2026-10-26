# Shared by the regular and school launchers. Stores mode names, never tokens.
craftlink_login_mode() {
  local profile="$1" selected="$2" fallback="$3" directory file saved temporary
  case "$profile" in run|school) ;; *) printf 'Invalid login profile\n' >&2; return 2 ;; esac
  directory="$project_dir/.craftlink"
  file="$directory/login-$profile"
  if [[ -L "$directory" || -L "$file" ]]; then
    printf 'ログイン設定の保存先にシンボリックリンクは使えません。\n' >&2; return 1
  fi
  if [[ -z "$selected" ]]; then
    selected="$fallback"
    if [[ -e "$file" ]]; then
      if [[ ! -f "$file" ]]; then printf 'ログイン設定が不正です。\n' >&2; return 1; fi
      IFS= read -r saved < "$file" || true
      case "$saved" in online|cached|guest) selected="$saved" ;;
        *) printf 'ログイン設定が不正です。--login / --cached-login / --dev で選び直してください。\n' >&2; return 1 ;; esac
    fi
  fi
  case "$selected" in online|cached|guest) ;; *) return 2 ;; esac
  if [[ "$profile" == school && "$selected" == guest ]]; then
    printf 'devは同じWi-Fi・LANのローカル起動専用です。sh craftlink play --dev を使ってください。学校用中継では --login または --cached-login を選んでください。\n' >&2
    return 2
  fi
  mkdir -p "$directory"
  temporary=$(mktemp "$directory/login-mode.XXXXXX") || return 1
  chmod 600 "$temporary"
  printf '%s\n' "$selected" > "$temporary"
  mv -f "$temporary" "$file"
  login_mode="$selected"
}
