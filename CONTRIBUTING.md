# 更新與版本管理

## 每次交付

1. 修改程式後，在 `CHANGELOG.md` 頂端新增 `## [版本識別] - YYYY-MM-DD`，列出變更與實際完成的驗證。日期使用台灣時間。
2. Android 可安裝版本同步調整 `versionCode`、`versionName`，網站調整對應的資源及離線快取版本。
   iOS 更新 `ios/scripts/generate-project.py` 的版本及建置號後重建專案；先執行核心測試、適用的 Xcode 建置與 UI 測試。未簽章建置不得標示為可安裝 IPA，尚未真機驗收的功能須清楚列出。
3. 執行適用測試，將原始碼與更新紀錄一起暫存，再執行 `node .github/scripts/check-update-history.mjs --staged`。
4. 建立具體描述變更的 Git commit，推送後確認 GitHub Actions 更新紀錄檢查成功。
5. 完成交付時建立附註標籤，例如 `git tag -a android-v2.1.1 -m "Android 2.1.1"`，再明確推送該標籤。

## 自動檢查

GitHub Actions 會檢查每次 push 或 pull request 的變更。網站程式、Android 或 iOS 原始碼／建置設定、自動化檢查有更動時，必須同時新增更新紀錄的版本日期標題及至少一條說明。缺少紀錄時，檢查會失敗。

這項檢查以一次 push 或 pull request 的提交範圍為單位。Git 自身保留範圍內的各筆提交與逐檔差異。檢查本身不修改分支保護或自動合併設定。

## 上傳內容

原始碼、測試、建置腳本、固定模型來源與驗證雜湊均應納入 Git。金鑰、本機設定、個人健康資料、建置暫存與模型權重由 `.gitignore` 排除。

APK 保存在本機 `android/dist/`。大型安裝檔適合另以 GitHub Releases 的資產交付，避免把每次重新建置的二進位檔加入原始碼歷史。此儲存庫目前追蹤可重建的原始碼快照。
