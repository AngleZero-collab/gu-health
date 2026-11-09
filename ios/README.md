# 顧健康 iOS 1.0.0 預覽版

將 Android 2.1 基本功能移植為 **SwiftUI 原生 iOS APP**，不使用 WebView。支援 iOS 17 以上的 iPhone／iPad，Bundle ID 為 `com.gucare.health`。沒有第三方套件、雲端 API、登入或分析 SDK。

本次交付為可建置的原始碼預覽版，沒有提供簽署 IPA，也尚未上架 App Store／TestFlight。相機、語音、提醒及字級仍須目標 iPhone 驗收。

## 用 Xcode 安裝到 iPhone

1. 取得本分支原始碼，在 Mac 安裝包含 iOS SDK 的 Xcode（15 以上）。
2. 以 Xcode 開啟 `ios/GuHealth.xcodeproj`，選擇 **GuHealth** scheme。
3. 在 **GuHealth target → Signing & Capabilities** 選擇自己的 **Team**。若識別碼已被其他 Team 使用，改成自己可用的 Bundle ID；更新既有安裝時需維持原識別碼與簽章團隊。
4. 連接 iPhone，完成裝置信任與開發模式設定，在 Xcode 上方選擇該手機，再按 **Run（⌘R）**。
5. 首次啟動可建立健康檔案，或主動選擇清楚標記的示範資料。只有啟用相機、語音或設定行程提醒時，才會申請對應權限。

只用 Simulator 時，安裝 Xcode 的 iOS Simulator runtime 後選擇一台模擬 iPhone 即可執行，無須設定 Team。Simulator 無法代替真機驗收本機語音與前鏡頭。

經由 TestFlight／App Store 發布時，需以自己的 Apple Developer Team 簽章、Archive，並完成 App Store Connect 的產品資料及隱私申報。本專案不包含帳號、憑證或描述檔。

## 功能對照

| Android 2.1 功能 | 本次 iOS 實作 |
| --- | --- |
| 三分頁、深綠色、大字體 | 首頁／居家觀察／我的紀錄；支援系統 Dynamic Type 與更大字體設定、大型按鈕 |
| 家人檔案、資格背景 | 新增、編輯、切換、確認後刪除；紀錄與行程依家人隔離 |
| 篩檢清單、完成日期 | 沿用 9 項固定政策規則、來源與受檢日期推估 |
| 院所聯絡 | Apple Maps 搜尋、官方合約院所入口、輸入已查證電話後交由系統確認撥號 |
| 雙眼方格觀察 | 全螢幕逐眼流程，完成兩眼後才可保存；可取消，保留非診斷說明 |
| 前鏡頭對位 | AVFoundation 即時預覽；不加入拍照、錄影或分析輸出 |
| 血壓、筆記、完成檢查 | 新增、查看、確認後刪除；血壓只記錄，不診斷 |
| 行程提醒 | UserNotifications 本機通知；行程保存後逐項啟用；刪除行程或家人時取消 |
| 語音管家 | 台灣中文本機辨識、決定式衛教規則與已安裝語音朗讀；不支援時停用，不回退至網路辨識 |
| 白話文報告 | 1–12 項基本解說、Decimal 區間／單邊參考值比對、分類排序及手動保存摘要 |
| Qwen3-0.6B / LiteRT-LM | **未移植**。畫面明確顯示基本解說，未宣稱內建生成式 AI |
| 摘要分享、JSON 匯出 | iOS 系統分享與檔案選擇器；報告摘要排除於分享及匯出 |
| Android 資料轉移 | 新增相容 `guhealth-backup` v1 的 JSON 匯入，先預覽數量再確認 |

政策沿用 Android 人工核對日期 **2026-08-31** 的固定快照，這次沒有重新核對現行資格。未連接政府、健保或院所資料庫。地圖結果不保證是公費合約院所，資格、費用與時間請先向院所確認。

## 從 Android 轉移紀錄

Android 顧健康設定 → 匯出 JSON 備份，以自己選擇的安全方式移到 iPhone → iOS 設定 → **匯入 Android／iOS JSON 備份**。確認新增的家人、紀錄與行程數量後加入。匯入不覆寫原資料，重複匯入會產生重複家人；未來行程的通知需逐項重新設定。

Android SQLite 不會自動搬到 iOS。舊 E 字練習等非標準紀錄保留原標題、內容，以一般紀錄呈現。白話文報告摘要維持不匯出／不匯入。

## 資料與權限

- 資料存於 Application Support 的 `GuHealth/health-v1.json`，以原子寫入、iOS complete file protection 與排除自動備份的資源屬性保存。未宣稱自製資料庫加密。移除 APP 會移除本機資料，請主動保留需要的匯出檔。
- 讀取失敗或版本不相容時保留原檔並停止寫入，不會用空資料覆寫。不要刪除 APP 來處理讀取問題。
- 匯出的 JSON **未額外加密**，包含所有家人的一般健康資料，請確認本人同意與安全儲存位置。
- 報告原始數值、單位、參考區間僅在畫面記憶體使用，關閉或進入背景時清除；只有按下保存才寫入不含這些欄位的分類摘要。
- 語音先檢查 `supportsOnDeviceRecognition`，並設定 `requiresOnDeviceRecognition = true`；不使用網路回退或自動下載模型。不保存錄音、逐字稿與歷史；取消、背景、音訊中斷與逾時會釋放收音資源。Swift／Apple 語音服務可能保留暫時副本，**不保證鑑識級記憶體清除**。
- 朗讀只選擇系統已列出的 `zh-TW` 語音，飛航模式效果仍需真機驗收。
- 非作用中時覆蓋 APP 與內容面板的隱私畫面；沒有宣稱能阻止 iOS 使用者截圖。
- 行程以臺灣時區輸入、依絕對時刻排程。鎖定畫面只有通用提醒文字，不顯示姓名、院所或健康內容。最多啟用 60 個未來提醒；實際顯示受權限、通知及專注模式影響，不適用精準用藥／醫療處置。
- 包含相機、麥克風、語音辨識用途說明及無追蹤／無開發者資料收集的 `PrivacyInfo.xcprivacy`。新增 SDK 或改變資料流時須重新檢視隱私申報。

## 建置與自動驗證

```bash
swift test --package-path ios
bash ios/scripts/build-ios.sh simulator
bash ios/scripts/build-ios.sh device

# 實際啟動 Simulator 的 UI 測試；名稱改成自己可用的模擬器
xcodebuild -project ios/GuHealth.xcodeproj -scheme GuHealth \
  -destination 'platform=iOS Simulator,name=iPhone 16' test
```

專案已提供，不需 XcodeGen、CocoaPods 或 npm。新增 Swift 檔案後，可執行 `python3 ios/scripts/generate-project.py` 重建專案；這會重設手動修改的 Xcode 設定，請先保留自己的簽章設定。

核心測試涵蓋政策與生日邊界、HPV 不同年齡登錄、報告參考值與無效輸入、摘要不含原始欄位、家人隔離與連動刪除、存檔重開／壞檔保留、Android JSON 契約、雙眼流程與衛教規則。UI 測試涵蓋三分頁、示範檔案重開與逐眼取消；使用 DEBUG 專用 `GuHealthUITests` 目錄，與一般資料分開。

GitHub Actions 會檢查產生的專案、執行核心測試、編譯 iPhone 目標，並在可用 iPhone Simulator 執行 UI 測試。

## 本機驗證及發布前待辦

已以 Xcode 27.2 / iOS 27.2 SDK 完成不簽章的 iPhone 目標編譯及 Simulator `build-for-testing`，核心測試 17 項通過。本機工具環境沒有完整 Simulator 應用程式／執行服務，**沒有在本機執行 UI 測試，也未宣稱實機通過**。本機編譯額外使用 `OTHER_SWIFT_FLAGS='$(inherited) -Xfrontend -disable-sandbox'` 讓編譯器巨集外掛在受限工具環境啟動；旗標沒有寫入專案，也不關閉 iOS APP 沙箱，一般 Xcode／CI 不需要它。

安裝交付前需在目標 iPhone 驗收：飛航模式台灣中文語音／朗讀、首次與拒絕授權、40 秒逾時／結束說話／來電／背景時麥克風釋放、相機啟停、通知實際送達、最大字級及橫向／小螢幕捲動、兩位家人報告隔離、Android 備份匯入與更新後資料保留。

## 來源

- [Android 功能與限制](../android/README.md)、[原專案政策與衛教來源](../android/SOURCES.md)
- [Apple：requiresOnDeviceRecognition](https://developer.apple.com/documentation/speech/sfspeechrecognitionrequest/requiresondevicerecognition)、[supportsOnDeviceRecognition](https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition)
- [Apple：備份資料管理](https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup)
- [MedlinePlus：理解檢驗報告](https://medlineplus.gov/lab-tests/how-to-understand-your-lab-results/)
