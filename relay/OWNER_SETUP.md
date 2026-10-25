# hrmcngs専用の主催者登録

中継の主催者接続（`/_bridge`）は、従来の `CRAFTLINK_RELAY_SECRET` に加え、登録した hrmcngs のMacのEd25519署名を要求します。招待されたスマホの参加方法は同じです。名前だけを名乗る接続・登録されていない鍵・期限切れ署名・署名の再利用は拒否します。公開鍵が未設定なら中継は起動しません。

1. Macで hrmcngs として `sh run.sh --offline --login` を一度タイトル画面まで起動します。
2. `node relay/owner-auth.mjs --setup` を実行します。秘密鍵を `.craftlink/owner-key.pem`（権限600）へ保存し、公開鍵だけを表示します。再実行しても既存の秘密鍵を変更しません。
3. Renderの該当サービスのEnvironmentで `CRAFTLINK_OWNER_PUBLIC_KEY` に、表示された公開鍵を登録します。既存の `CRAFTLINK_RELAY_SECRET` も維持します。
4. この変更をRenderへデプロイしてから、Macで `sh school.sh --mods` を実行します。

秘密鍵と認証キーは共有しないでください。`.craftlink/` はGitや配布ZIPに含めません。公開鍵の登録・デプロイが完了するまで公開サービスの制限は変更されません。

起動状態はVS Codeの **⌘ShiftB**、または `sh menu.sh` の **11** で選べます。`--dev` はゲスト、`--login` はMicrosoft認証、`--cached-login` は保存済みの名前・UUIDによるローカル起動です。初回の主催者登録には手順1の `--login` が必要です。devを選んでも中継の主催者登録・署名は省略しません。

この方式が中継側で証明するのは、管理者がhrmcngs用として登録したMacの秘密鍵を持つことです。毎回Microsoftに照会して現在のアカウントを証明する仕組みではありません。学校ではMicrosoftへの再認証なしで署名できます。Renderの登録権限を持つ本人が初回登録してください。Macを交換したときや秘密鍵を失ったときは、別の鍵を生成してRenderの公開鍵を更新します。
