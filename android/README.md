# 顧健康 Android 2.1

繁體中文原生手機 APP，Java + XML Android Views + Material Components，非 WebView。
套件名稱維持 `com.gucare.health`，版本 `2.1.0 / versionCode 3`，支援 Android 8.0 以上。

## 新功能開發分支：居家健康自我觀察

本分支新增原生 `SelfCheckActivity`，由「居家觀察」進入八項逐步自測：畫鐘、FAST、中老年小腿手指圈、五次坐站、睜眼單腳平衡、Phalen、直抬腿症狀紀錄及指甲回血。畫鐘畫布不保存；主觀回答和計時結果經使用者同意後存於目前健康檔案既有 SQLite `records` 表，無需升級 schema。

這些觀察不提供診斷；FAST 突然異常立即指示撥打 119。直抬腿不要求家人施力，平衡測驗保持睜眼及扶手保護。仍應由醫事人員審核文字與適用族群。

### 手機測試 APK

GitHub Actions 工作流程 `Android self-check APK` 成功完成後，在對應執行頁面「Artifacts」取得 `GuHealth-selfchecks-Android-debug`，解壓縮後安裝 APK。這是測試版，**建置及裝置安裝是否成功需以 GitHub Actions 執行結果及手機實際操作確認**。若既有安裝檔的簽署金鑰不同，Android 無法覆蓋更新；請先匯出備份，勿直接解除安裝導致私人資料消失。APK 僅含基本版，不包含 Qwen 本機模型。

## 安裝與更新

基本版安裝檔為 `dist/GuHealth-Android-2.1-basic-debug.apk`。內建模型版為 `dist/GuHealth-Android-2.1-local-ai-arm64-debug.apk`，適用 ARM64 裝置。兩者擇一安裝。
既有 V1 可以同簽章覆蓋更新，保留健康資料；不要先解除安裝，也不要清除 APP 資料。
這是開發金鑰簽署的測試版，不是 Play 商店正式版、醫療器材或醫療認證產品。

## V2 實作順序

1. **XML 與三分頁**：`res/layout/activity_main.xml` 配合 `res/menu/bottom_navigation.xml`，只有首頁、居家觀察、我的紀錄。移除舊篩檢與家人分頁；切換家人放在首頁，設定維持單層對話框。
2. **首頁**：`view_home.xml`、`item_screening.xml`、`ScreeningAdapter.java`。以暱稱與待確認項目問候，無健康分數、統計圖、提醒數字或英文裝飾標籤。篩檢標題使用大腸癌篩檢、乳癌篩檢等簡稱。
3. **聯絡院所**：每張卡片兩個大型 MaterialButton。電話開啟 ACTION_DIAL，不直接撥出；尚無院所電話資料庫，因此撥號頁需自行輸入電話。地圖開啟 Google Maps，以縣市及篩檢名稱搜尋；未安裝 Maps 時用瀏覽器開啟地圖。結果不是公費合約院所保證，費用與資格須向院所確認。搜尋會交給地圖服務，不包含姓名、生日或健康紀錄。
4. **沉浸觀察**：`view_vision_test.xml`、`ObservationGridView.java` 與 `MainActivity.java`。觀察時隱藏上下導覽，左右眼分開，只提供兩個大按鈕；兩眼完成後才詢問是否儲存。保留舊的 E 字練習紀錄，但移除 V2 的 E 字練習入口以簡化操作。可用 Android 返回手勢離開。
5. **語音管家**：`fab_ai_voice` → `sheet_ai_voice.xml` → `VoiceConsultationSheet.java` → `LocalHealthGuide.java` → `OfflineSpeaker.java`。以本機規則模擬 Edge AI；不是雲端聊天或真正的語言模型。僅提供三句內基本衛教與就診科別，不進行診斷。

## 白話文健檢報告

從「我的紀錄」開啟。依原報告輸入每項數值、單位與參考值後，可閱讀基本解說。內建模型版使用 Qwen3-0.6B 選擇解說句型和看診問題，比對仍由程式完成。手機有至少 5 GiB 系統回報的實體記憶體、約 750 MB 額外空間且模型可用時，會優先使用本機 AI。

只有手動保存的摘要會出現在目前家人的紀錄。原始數值與參考區間不保存，摘要也不納入分享或匯出。報告參考值的使用依據：[MedlinePlus](https://medlineplus.gov/lab-tests/how-to-understand-your-lab-results/)。

## 長輩操作規格

- APP 自有互動控制項至少 64dp；首頁行動按鈕至少 72dp，觀察回答至少 80dp。
- 文字至少 18sp、主標題至少 24sp；可於設定開啟更大字體，並支援系統字級。
- 主色深綠，警示橘色。選項同時用文字表示，不只依賴顏色。
- 日期和時間使用大字欄位輸入，避免小型日曆格；下拉選項列高至少 64dp。
- Android 系統權限對話框、鍵盤、撥號及地圖 APP 由系統或第三方控制，無法由本 APP 強制調整尺寸。

## 語音隱私與裝置要求

- STT 只使用 Android 12 / API 31 起的 `createOnDeviceSpeechRecognizer`。不呼叫一般雲端辨識工廠，也不以 `EXTRA_PREFER_OFFLINE` 作唯一隱私保證。
- 手機必須提供裝置端辨識服務及台灣中文辨識模型。缺少服務或模型時顯示原因，不自動切換雲端，不自動下載模型。
- TTS 明確設定 `Locale.TAIWAN`，只接受不需網路且已安裝的台灣中文 Voice；沒有可用離線語音就不開放語音問答。居家觀察仍可依畫面操作。
- 只有按下「開始說話」且授權麥克風後才收音。40 秒逾時、關閉面板、APP 進入背景或離開流程會取消辨識並釋放麥克風。
- 不顯示逐字稿、不保留對話歷史、不寫入 SQLite、偏好設定、檔案或日誌；沒有雲端 API、分析 SDK 或 INTERNET 權限。
- 辨識結果轉入可清除字元陣列後，立即清空結果 Bundle 與候選清單；本機規則產生回覆後抹除輸入陣列，交給離線 TTS 後即抹除回覆陣列，失敗及取消路徑同樣清理。
- **不能宣稱鑑識級立即記憶體抹除**：Android STT 回傳不可變 String，Binder / GC / 系統語音引擎可能持有暫時副本；APP 無法逐位元清除其他行程或 JVM 所有副本。APP 保證的是不持久化、縮短存活期並抹除其可控制的陣列；系統引擎依裝置實作。朗讀完成或取消時釋放語音資源。
- 問答面板使用 FLAG_SECURE，禁止正常系統截圖／最近使用畫面擷取內容。

## 保留的 V1 功能與資料

健康檔案、受檢日期、血壓／筆記、雙眼觀察、行程通知、摘要分享及 JSON 匯出。
**實際 V1 使用 SQLiteOpenHelper，不是 Room**；本次保留 `guhealth.db` 與 schema 1，沒有破壞性遷移、不建立語音對話表。
備份仍只支援匯出，尚無匯入還原。資料在 APP 私人儲存區，未宣稱額外資料庫加密；Android 自動備份關閉。

政策為人工核對的固定版本（2026-08-31），不是政府或健保即時查詢。首頁僅依已登錄日期推估待確認項目，不能證明未曾受檢。
通知為非精確排程，受省電與權限影響；不適用精準用藥／醫療處置。
居家方格未經臨床校準，不能量測視力或診斷疾病。

## 編譯與測試

Android Studio 開啟此 `android` 資料夾。JDK 21、SDK 36、Gradle Wrapper 8.14.3。

```powershell
.\build-android.ps1
```

內建模型版使用 [e668717 的 Qwen3-0.6B.litertlm](https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e668717/Qwen3-0.6B.litertlm)。先將檔案下載到建置電腦，再執行 `./build-android.ps1 -ModelPath <檔案路徑>`。Gradle 驗證 SHA-256 後打包，手機首次使用時會在本機解出模型副本。

腳本把必要來源複製到新英文暫存路徑（避開 Windows 中文路徑測試載入問題），執行 assembleDebug、testDebugUnitTest、lintDebug。
成功後交付 APK、原始碼 ZIP、測試報告、Lint 報告與 Robolectric 原生畫面；原專案和暫存建置均保留。

測試涵蓋政策年齡條件、資料保留與隔離、三分頁、觸控／字級下限、雙眼流程／旋轉、撥號和地圖 Intent、本機衛教規則、語音暫存清理、無資料庫寫入及離線 Voice 選擇。
測試繪圖不等於實機畫面；實機測試另見交付說明。完整收音及 TTS 效果仍須使用者在目標手機以實際語音驗收。

政策、技術與衛教來源：`SOURCES.md`。
