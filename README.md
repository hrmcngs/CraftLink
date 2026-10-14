# CraftLink

**Minecraft Java Edition 1.20.1 / Forge 47.4.10以上（47系） / Java 17** 用のクライアントMOD。
同じLANにあるスマートフォンのブラウザをタッチコントローラーとして使います。アプリ、外部サービス、サーバー側MODは不要です。

## 導入・接続

1. Forge 1.20.1をインストールします（開発・検証対象は47.4.10）。
2. `craftlink-forge-1.20.1-0.1.0.jar` をMinecraftの `mods` に配置します。`-thin.jar` は配布用ではありません。
3. Minecraftを起動し、ワールドまたはマルチプレイヤーサーバーに入ります。
4. **F8** →「接続を開始」。キーはMinecraftの「操作設定」で変更できます。
5. PCと同じWi-FiのスマホでQRを読み取り、ブラウザで開きます。
6. Minecraftで「接続を許可して戻る」を押します。横向きで操作してください。
7. 終了時はスマホの「切断」、またはF8画面の「端末を切断」「停止」を使います。

PCのファイアウォールに確認された場合、プライベートLANに限りTCP 8765を許可します。
初回ビルド・Minecraft導入後の利用にはインターネット接続は不要です。

## 操作

| スマホの操作 | Minecraft |
|---|---|
| 左側の方向ボタン（同時押し可） | 前進・後退・左右移動 |
| 右側の空き領域をスワイプ | 視点。下部の感度スライダーで調整・端末に保存 |
| ジャンプ・スニーク・ダッシュ | 押している間有効 |
| 攻撃 / 掘る | 左クリック、長押しでブロック破壊 |
| 使う / 置く | 右クリック、長押しで継続使用 |
| 持ち物 | インベントリを開く |
| 1〜9 | ホットバーのスロット選択 |
| 捨てる・オフハンド | 1アイテムを捨てる・両手の交換（押下時に1回） |
| ESC | Minecraftのポーズメニューを開く |

移動・視点・アクションを別々の指で同時操作できます。
スマホのバックグラウンド移行時は入力を解除します。スニーク・ダッシュはMinecraft側も「長押し」設定を推奨します。
ゲーム内GUIが開いている間はリモート入力を解除し、GUI内の操作はPCで行います。

## ビルド

必要: **JDK 17、Node.js 22、npm**。Gradle Wrapper同梱。初回のみ依存ライブラリのダウンロードが必要です。

```sh
./gradlew build
# Windows
# gradlew.bat build
```

Web依存の `npm ci` → TypeScript検査 → Vite静的ビルド → Javaコンパイル → JUnitテスト →
依存ライブラリのパッケージ隔離 → Forge用の再難読化を実行します。
出力: `build/libs/craftlink-forge-1.20.1-0.1.0.jar`。
静的Web資産はJARの `web/` に同梱され、別Webサーバーは不要です。
NettyとZXingは専用パッケージへ移して同梱し、他MODのライブラリとの衝突を避けます。

```sh
./gradlew test         # セッション・タイムアウト・実HTTP/WebSocketのテスト
./gradlew runClient    # 開発用Minecraft（公式アカウントの認証環境ではありません）
npm --prefix web run build
npm --prefix web run dev # UI開発用。MODの接続はJAR内Web資産を使用
```

Javaコンパイルのみは `./gradlew compileJava`。Webを編集した場合は `build` でJARへ反映します。
GitHub Actionsでもビルドし、配布用JARをArtifactに保存します。

## 設計

- `CraftLink.java`: Forgeクライアント初期化、F8登録、クライアントtick。
- `gui/LinkScreen.java`: 起動・停止・承認・切断・URLコピー・ローカルQR生成。
- `network/LinkServer.java`: NettyのHTTP / WebSocket、非同期起動・停止。
- `security/Session.java`: 256bitトークン、30分のセッション期限。
- `input/RemoteState.java`: サイズを制限した最新入力のメールボックス。
- `input/InputBridge.java`: Minecraftメインスレッド上の通常キー入力とプレイヤー操作。
- `config/LinkConfig.java`: `config/craftlink-client.toml` の `port`（標準8765）。変更後は停止・再開始。
- `web/src/`: Pointer Eventsのマルチタッチ、30Hzの状態送信、ハートビート、再接続。

ネットワーク側からMinecraftの状態を変更しません。入力キューを無制限に積まず、視点差分も上限を設けます。
攻撃・使用はMinecraftの通常のキー処理を通し、移動速度やリーチ、サーバーのゲームルールを変更しません。
単発操作は押下の立ち上がり時のみ実行します。短いタップも状態変化時に即送信します。

## 接続と安全処理

- 起動時のサーバーは停止。明示的な開始で新しいセッションを発行。
- URLのフラグメントに一時トークンを格納。HTTP要求のパスやRefererには送信しません。
- WebSocketでトークンを確認後、PC側の承認が必要。同時に1台だけ受け付けます。
- 同一Origin確認、メッセージ長・頻度・操作名・数値範囲の検証を行います。
- 切断では入力を解除。入力通信が750ms途絶えた場合も、次のクライアントtickで解除。
- WebSocketの受信停止は5秒で切断。1秒周期のpingで往復遅延を表示。
- 一時的な通信切断時は2秒後に再接続し、PCで再承認が必要。
- 明示的な切断では自動再接続を停止。再開にはページ再読み込みが必要です。
- セッションは開始から30分で期限切れ。「停止」→「接続を開始」でQRを更新してください。
- 終了時はshutdown hookで通信停止。任意キー入力、OSコマンド、ファイル操作用の通信APIはありません。

**HTTP / WSは暗号化されません。信頼できる家庭内LAN専用です。** ポート転送やインターネット公開はしないでください。
QRとURLは他人に見せないでください。読み取り可能な人はPCに承認要求を送れます。
PCのネットワークインターフェースはIPv4のプライベートアドレスから自動選択します。複数NIC・VPNで誤選択された場合は、
URLのホストをPCのWi-Fi IPv4に置き換え、フラグメントのトークンを保持して開いてください。
`127.0.0.1` が表示される場合はLAN接続を確認して再開始してください。

## iOS / Android と既知の制限

- iOS Safari / Android Chromeを想定したPointer Events実装です。実機・各OS版での検証は別途必要です。
- HTTP上の通常WebSocketを利用し、HTTPSページからの混在コンテンツ通信を避けます。HTTPS接続ではありません。
- SafariのカメラでQRを読み取ります。Webページ内のカメラ、センサー、Pointer LockなどHTTPS必須のAPIを使用しません。
- iOSの全画面表示は端末・Safariの対応状況次第です。非対応なら通常表示のまま操作できます。
- PWAのService Worker、オフラインインストールは未実装です。LANのHTTPでは一般に利用できず、将来のHTTPS対応が必要です。
- ゲストWi-Fiの端末間隔離、VPN、ファイアウォールで接続できない場合があります。ローカルネットワーク許可を求められた場合は許可してください。
- 映像配信はありません。Minecraftの画面を見ながらスマホをコントローラーとして使います。
- インベントリのアイテム移動、チャット文字入力、メニュー操作はPCを使用します。
- PCのキー入力と同じキーを同時操作すると、リモートの解除がPC側の押下状態に影響する場合があります。
- Java Editionの通常の移動・攻撃仕様です。マルチプレイヤーではサーバーの操作補助MODの利用規約を確認してください。
- 現時点の対応は **1.20.1 Forgeのみ**。Fabric・他Minecraft版の互換性は主張しません。通信・状態管理・Webは分離しており、
  追加ローダーでは初期化・GUI・InputBridgeを差し替える構成です。まず必須版の実機検証を優先します。

## 手動動作確認

自動テストに加えて、リリース前に以下を確認してください。

1. JARを通常のForge環境へ導入し、F8の開始・停止・QR・URLを確認。
2. iOS Safari / Android Chromeで接続し、承認前には操作できないこと、2台目が操作できないことを確認。
3. 前進＋視点＋ジャンプの同時操作、斜め移動、長押し掘削・アイテム使用を確認。
4. 切断、Wi-Fiオフ、ブラウザを閉じる、画面ロックで押下が残らないことを確認。
5. 再接続・再承認、30分の期限切れ、ポート競合、Minecraft終了時のポート解放を確認。
6. シングルプレイヤーと通常のマルチプレイヤー双方で確認。専用サーバー側への導入は不要です。

## 参照・ライセンス

- [Forge 1.20.1 ダウンロード](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
- [Forge 1.20.1 開発ガイド](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)
- CraftLink: MIT（`LICENSE`）。Minecraft本体は含みません。
- [ZXing](https://github.com/zxing/zxing): Apache-2.0。
- [Netty](https://github.com/netty/netty): Apache-2.0。同梱ライブラリのライセンスはJAR内にも保持します。

## この環境での検証結果

- JDK 17 / Node.js 22の `./gradlew build` 成功。TypeScript検査・Web同梱・再難読化済みJAR生成。
- JUnit: トークン、入力上限・消費・タイムアウト、実HTTP/WebSocket、承認前入力拒否、
  不正トークン・2台目拒否、切断時解除を確認。
- `npm audit`: 検出0件（ロックファイル作成・更新時点）。Java依存全体の脆弱性監査を意味しません。
- macOS / Apple M2上の `runClient` でForge・CraftLink読み込みとリソース初期化を確認。
- スマホ実機でのQR接続、ワールド内の操作、通常マルチプレイヤーは未検証。上記の手動確認項目を実施してください。
