# ClockCalendarWidget

アナログ時計(秒針あり)と月カレンダーを1枚にしたAndroidホーム画面ウィジェット。広告・課金・権限なし。

- 最低Android 12 (API 31)
- ウィジェットをタップすると端末の時計アプリを起動
- カレンダーは常に6週表示。前月末・翌月初は薄く表示

## インストール
[Releases](../../releases) から最新の `ClockCalendarWidget-vX.Y.Z.apk` をスマホでダウンロードして開く
(ブラウザに「提供元不明のアプリ」のインストールを許可する必要があります)。
その後、ホーム画面の空きを長押し → ウィジェット → Clock Calendar Widget から追加。

## リリース手順(開発者向け)
```
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions がAPKをビルドし、Releaseに添付します。
署名キーを使う場合は、リポジトリのSecretsに `KEYSTORE_BASE64` `KEYSTORE_PASSWORD` `KEY_ALIAS` `KEY_PASSWORD` を登録します
(未登録でもビルドできますが、デバッグ署名になり、次のバージョンを上書きインストールできない場合があります)。
