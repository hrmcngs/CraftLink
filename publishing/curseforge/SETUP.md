# CurseForge入力用まとめ

説明文は [description.md](description.md)、英語Summaryは [summary.txt](summary.txt) をそのまま使えます。登録・アップロード・審査依頼はまだ実行していません。

| 入力欄 | 設定する内容 |
| --- | --- |
| Project name | **CraftLink** |
| Logo | このフォルダーの **icon.png**（1254×1254 PNG） |
| Summary | `Turn your phone into a touch controller and let friends control their own bots over your local network.` |
| Class | **Mods** |
| Main category | **Utility & QoL** |
| Additional categories | **空欄**。無関係なカテゴリを追加しない |
| Allow Comments | **ON推奨**：不具合報告を受け付ける |
| Unlisted project | 一般公開して検索対象にするなら **OFF** |
| Social Links | 必須ではないため空欄可。公開済みの自分のサイト・SNSだけ登録 |
| License | **MIT License** |
| Description | [description.md](description.md) の英語本文 |

プロジェクト名の空きは登録画面で確認してください。まだ存在しないDiscord・SNS・サポートURLは掲載しません。GitHubの候補URLは `https://github.com/hrmcngs/CraftLink` ですが、非公開のままなら一般利用者は閲覧できません。公開範囲を確認してからSource・Issues・Social Linksに設定してください。この作業でリポジトリの公開範囲は変更しません。

## Files画面

| 入力欄 | 設定する内容 |
| --- | --- |
| Upload file | [craftlink-forge-1.20.1-0.1.0.jar](../../releases/craftlink-forge-1.20.1-0.1.0.jar) |
| Display name | CraftLink 0.1.0 — Forge 1.20.1 |
| Game version | **1.20.1** |
| Mod loader | **Forge** |
| Release type | 初期公開は **Beta推奨**。安定性を確認してからReleaseへ変更 |
| Dependencies | Forgeは対応ローダーとして指定。Curios・武器・バックパックMODは必須依存にしない |
| Changelog | [changelog.md](changelog.md) |

`-dev.jar`、`-thin.jar`、主催者用ZIP、開発フォルダーはアップロードしません。公開JARはローカルネットワーク用です。hrmcngs専用Render中継を、誰でも使える配布機能として説明しないでください。

## ロゴ

オリジナルの「スマホの方向ボタン＋リンク＋幾何学的な立方体」です。ゲームのロゴ・キャラクター・草ブロック素材・既存の商標アセットは使用していません。生成画像をそのままPNGで保存し、元の解像度を維持しています。使用した生成方法とプロンプトは [ICON_PROMPT.md](ICON_PROMPT.md) に記載しています。

## 参考

- [CurseForgeの提出ガイド：名前・ロゴ・Summary・カテゴリ](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips)
- [プロジェクト作成・ファイル提出](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project)
- [JARなどの提出形式](https://blog.curseforge.com/how-to-pass-moderation-review-on-curseforge-2/)

公式ガイドではロゴは正方形PNGで最低400×400、追加カテゴリは空欄可とされています。文章は英語で用意しています。採否はCurseForge側の審査によります。
