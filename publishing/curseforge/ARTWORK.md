# 指定されたアイコンとロゴ

ユーザー指定のPNGを変更せずに採用しています。画像生成・描き直し・色変更は行っていません。

| ファイル | サイズ | 用途 |
| --- | --- | --- |
| `icon.png` | 512×512 | CurseForgeのProject Logo |
| `logo.png` | 640×128 | ForgeのMOD一覧用ロゴ・説明欄のバナー |
| `icon-32.png` | 32×32 | アイコンの元のドット配列 |

`icon.png` は添付の正方形アイコン、`logo.png` は「CraftLink / PHONE CONTROLLER」の横長ロゴです。512×512画像は32×32の各ピクセルが16×16に拡大されたものです。

```sh
java scripts/PixelIcon.java
```

このコマンドは `icon.png` の各16×16領域が単色であることを確認して `icon-32.png` だけを抽出します。指定アイコンやロゴを別の絵で上書きしません。

MOD本体用の実ファイルは `src/main/resources/assets/craftlink/icon.png` と `logo.png` に配置しています。通常のForgeリソースとしてJARの `assets/craftlink/` に同梱します。`META-INF/mods.toml` の `logoFile` は横長の `assets/craftlink/logo.png` を参照します。

画像を差し替えるときは、CurseForge用のこのフォルダーと、MOD本体用の `src/main/resources/assets/craftlink/` の両方を更新してください。
