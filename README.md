# ゴミ通知リマインダー（再構成ソース）

地域ごとのごみ収集日を登録し、前日と当日の朝に通知するAndroidアプリです。

## 主な機能
- 複数地域・複数ごみ種類
- 毎週の複数曜日、第1〜第5・最終曜日
- 前日通知 / 当日通知、時刻変更
- 通知アクション: 「出した」「30分後」「今回は通知不要」
- 収集なし日 / 振替収集日
- 次回収集日表示
- 編集 / ON-OFF / 削除
- ホーム画面ウィジェット
- JSONバックアップ / 復元
- 再起動、時刻・タイムゾーン変更、アプリ更新後の再予約

## バックグラウンド設計
常駐Service、WorkManager、Timer、独自ポーリングは使用していません。通知時刻だけAlarmManagerへ予約します。

## Android側の注意
- Android 13以降は通知権限が必要です。
- Android 12以降は「アラームとリマインダー」を許可すると通知精度が上がります。未許可時は通常アラームへフォールバックします。
- 強制停止中はAndroid仕様により通知できません。

## 開発環境
- Kotlin / Jetpack Compose / Material 3
- minSdk 26
- targetSdk / compileSdk 36
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1
- JDK 17

## ビルド
Android Studioで開いてGradle Sync後に `app` を実行してください。
CLIでは `./gradlew assembleDebug` です。

> このZIPは、保存されていたREADMEと監査記録を基準に再構成したソースです。元の生成ZIPそのもののバイト一致コピーではありません。
