# ClockCalendarWidget

アナログ時計(秒針あり)と月カレンダーを1枚にしたAndroidホーム画面ウィジェット。広告・課金・権限なし。

- 最低Android 12 (API 31)
- ウィジェットをタップすると端末の時計アプリを起動
- カレンダーは常に6週表示。前月末・翌月初は薄く表示

## インストール

1. [Releases](../../releases) から最新の `clock-calendar-widget-release-vX.Y.Z.apk` をスマホでダウンロードして開く
   (ブラウザに「提供元不明のアプリ」のインストールを許可する必要があります)
2. ホーム画面の空きを長押し → ウィジェット → Clock Calendar Widget から追加

アップデートも同じ手順で、新しいAPKを開いて上書きインストールします。

## リリース手順(開発者向け)

リリースはGitHub Actionsの手動実行で行います。タグの作成、APKのビルド、Releaseへの添付まで自動です。

1. GitHubの Actions → **Build and Release APK** → **Run workflow**
2. バージョンアップの種類を選んで実行
   - `patch`: 不具合修正・見た目の微調整 (1.0.2 → 1.0.3)
   - `minor`: 機能追加 (1.0.2 → 1.1.0)
   - `major`: 大きな作り直し (1.0.2 → 2.0.0)
3. 実行画面の Summary で、現在のバージョンと更新後のバージョンを確認
4. 問題なければ **Review deployments** → `release` にチェック → **Approve and deploy**
   (違っていれば **Reject** してやり直す)

### バージョンの決まり方

- `versionName`: 最新の `vX.Y.Z` タグから自動で計算(タグがなければ 1.0.0 が起点)
- `versionCode`: Actionsの実行番号 + 1(自動)
- 一度出したタグやAPKは作り直さない。直したいときは新しいバージョンを切る

### 初回設定(最初の1回だけ)

**1. 署名キーをSecretsに登録する**

Settings → Secrets and variables → Actions に次の4つを登録します。未登録だとワークフローは失敗します。

| 名前 | 内容 |
|---|---|
| `KEYSTORE_BASE64` | keystoreファイルをBase64にした文字列 |
| `KEYSTORE_PASSWORD` | keystoreのパスワード |
| `KEY_ALIAS` | キーのエイリアス |
| `KEY_PASSWORD` | キーのパスワード |

Base64への変換:

```powershell
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

```bash
# Linux
base64 -w0 release.jks
```

署名キーを変えると、入れ替え前のアプリに上書きインストールできなくなります(アンインストールが必要)。
同じキーを使い続け、バックアップを必ず取ってください。keystoreファイルはリポジトリに入れないこと。

**2. Environment `release` を作る**

Settings → Environments → New environment → 名前を `release` にして作成 →
**Required reviewers** に自分を追加します。「Prevent self-review」はオフのままにします(自分で承認するため)。

## ローカルビルド

Android Studioの Build メニュー、または `./gradlew assembleRelease` でAPKを作れます。
署名キーを設定していないときはデバッグ署名、バージョンは `1.0.0` になります。動作確認用で、配布には使いません。
デバッグ署名のアプリの上に、ReleaseのAPKは上書きインストールできません(署名が違うため)。
