# 顧健康｜國民健康預防篩檢整合平台

這個儲存庫包含「顧健康」的 Android 原生 App、iOS 原生預覽版與響應式 PWA 網站，面向高齡者與家屬，整理預防篩檢、居家視覺觀察與健康紀錄。

Android 目前版本為 **2.1.0**，包含裝置端 AI 白話文健檢報告。原始碼、編譯與安裝說明見 [Android 說明](android/README.md)。下列功能與啟動方式介紹 PWA 網站。

iOS **1.0.0 預覽版**採用 SwiftUI，支援 iOS 17 以上，可在 Xcode 開啟 `ios/GuHealth.xcodeproj`。已移植基本報告解說與 Android JSON 備份匯入，尚未移植 Qwen 模型，也未提供已簽署 IPA。功能對照、iPhone 安裝及驗證限制見 [iOS 說明](ios/README.md)。

每次交付的內容記錄於 [更新紀錄](CHANGELOG.md)，後續更新流程見 [版本管理說明](CONTRIBUTING.md)。GitHub 的 Commits 可查看逐次差異，Tags 對應各次交付的原始碼快照。

網站面向高齡者與家屬，採用大字體、大觸控區、三個主功能與清楚的醫療限制說明。所有自行輸入的健康檔案與紀錄只保存在目前瀏覽器的 `localStorage`，不會自動上傳，也沒有連接健保或政府資料庫。

## 已完成功能

- 首頁：依年齡、性別與自填背景整理可向院所確認的預防篩檢
- 篩檢卡片：電話、附近院所地圖、資格條件、準備事項與官方政策來源
- 居家觀察：左右眼分開進行的方格視覺自我觀察，只有兩個大按鈕
- 我的健檢與紀錄：清楚區分已登錄與待向院所確認項目，並可自行登錄檢查、血壓與照護筆記
- 衛教小幫手：本機規則式回覆，不儲存問題與回答，不提供醫療診斷
- PWA：包含 manifest、App icon 與 Service Worker 離線快取

## 啟動方式

若要測試 PWA 與 Service Worker，請在專案根目錄啟動本機 HTTP Server：

```powershell
python -m http.server 4173
```

再開啟 `http://localhost:4173/`。

公開網站由 GitHub Pages 提供：<https://anglezero-collab.github.io/gu-health/>

## 使用限制

首次開啟會顯示清楚標記的示範資料。網站的資格整理是依固定政策版本做初步比對，並非政府或健保資格查詢；實際資格、費用與檢查時間仍須向醫療院所確認。居家視覺觀察與衛教回覆都不能取代醫師診斷。
