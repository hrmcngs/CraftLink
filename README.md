# CraftLink

スマホのブラウザからMinecraftを操作するMOD。**Minecraft Java 1.20.1 / Forge 47.4.10（47系） / Java 17** 対応。

## 起動（Mac）

```sh
sh school.sh          # 中継付き起動・BOT参加
sh school.sh --online # 初回の依存取得・起動
sh run.sh --offline   # LANで起動（依存取得済み）
sh menu.sh            # 対話式メニュー
```

- VS Code：**⌘ShiftB**。Finder：**学校で起動.command**。
- 中継の初回設定にはRenderの認証キーが必要。
- ワールドを開き、表示されたQRをスマホで読むとBOTで参加。
- 通常起動は **F8 → 接続を開始**。**F9** でBOT全員停止。
- 名前・スキン・操作設定：スマホの **☰ → 設定**。

## ビルド

Java 17・Node.js 22・npmが必要です。

```sh
sh build.sh          # 初回・通常ビルド
sh build.sh offline  # 依存取得済みならオフライン可
```

配布用JAR：`build/libs/craftlink-forge-1.20.1-0.1.0.jar`。Forgeの `mods` に配置します。

## 接続方法

| 方法 | 条件 |
| --- | --- |
| LAN / Wi-Fi | PCとスマホが直接通信できるネットワーク |
| Android USB | `sh run-usb.sh`。adb・USBデバッグ許可が必要 |
| 別Wi-Fi / モバイル通信 | `sh school.sh`。HTTPS中継と双方のネット接続が必要 |

学校などの通信制限によっては中継に接続できません。QR・認証キーは公開しないでください。

## 制限

- 複数BOTはシングルプレイ／LAN公開のホスト用。通常サーバーでは自分のプレイヤーを操作。
- BOT映像は参加者全体で最大20回/秒の描画。人数・PC・回線で画質と遅延が変わります。
- Fabulous・シェーダーMODは未対応／未検証。iPhoneはAndroid USBモード非対応。
- Macの起動スプラッシュ対策は自動適用。

## 詳細

- [実装の仕組み・構成・データの流れ](docs/ARCHITECTURE.md)
- [操作・接続設定・トラブル対処・検証記録](docs/USER_GUIDE.md)
- [ライセンス：MIT](LICENSE)
