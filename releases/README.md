# CraftLink 配布用JAR

`craftlink-forge-1.20.1-0.1.0.jar` をダウンロードして、Minecraft Java 1.20.1 / Forge 47.4.10以上（47系）の `mods` フォルダーへ配置してください。Java 17が必要です。GitHubのファイル画面では **Download raw file** から取得できます。

Webコントローラーを同梱したLAN・USB用の配布版です。開発者専用のRender中継、Microsoft認証ツール、個人の認証情報は含みません。ライセンスは[MIT](../LICENSE)です。[導入手順](../docs/HOST_SETUP.md)も参照してください。

## 更新する人向け

```sh
sh craftlink build offline # 初回・依存未取得なら offline を省略
```

ビルドするとこのフォルダーのJARとSHA-256ファイルを更新します。通常の `git add releases/` でコミットできます。`build/` 内のdev・thin JARはコミットしません。

整合性確認（Mac）：

```sh
cd releases
shasum -a 256 -c craftlink-forge-1.20.1-0.1.0.jar.sha256
```
