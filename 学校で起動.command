#!/bin/sh
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1
sh "$project_dir/school.sh"
result=$?
if [ "$result" -ne 0 ]; then printf '\n起動を終了しました。上のメッセージを確認してください。Enterで閉じます。'; read -r reply; fi
exit "$result"
