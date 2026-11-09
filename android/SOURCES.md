# 政策與衛教來源

人工核對日期：2026-08-31。APP 不代表政府機關，沒有宣稱即時同步。

| 範圍 | 官方或專業來源 |
| --- | --- |
| 成人預防保健、原住民及小兒麻痺身份例外 | [衛生福利部：定期健檢、及早發現三高風險](https://www.mohw.gov.tw/cp-2704-82262-1.html) |
| 大腸癌擴大篩檢 | [國健署：誰需要接受大腸癌篩檢](https://www.hpa.gov.tw/Pages/Detail.aspx?nodeid=5053&pid=20022&sid=20024) |
| 五大癌症篩檢政策彙整 | [國健署：癌症篩檢介紹](https://www.hpa.gov.tw/Pages/List.aspx?nodeid=211) |
| 2025 年起擴大篩檢及 HPV 指定年齡 | [衛生福利部：健康臺灣—114 年起擴大癌症篩檢](https://www.mohw.gov.tw/cp-6644-80948-1.html) |
| 合約院所查詢入口 | [全國癌症篩檢活動暨醫療院所資訊查詢](https://escreening.hpa.gov.tw/Home) |
| 黃斑部變化警訊及醫療診斷方式 | [National Eye Institute: Age-related macular degeneration](https://www.nei.nih.gov/eye-health-information/eye-conditions-and-diseases/age-related-macular-degeneration) |

規則摘要：成人保健 30–39 歲每 5 年、40–64 歲每 3 年、65 歲以上每年，指定身份有例外。糞便潛血 45–74 歲及 40–44 歲具一等親家族史每 2 年；乳房攝影 40–74 歲女性每 2 年；抹片 25–29 歲每 3 年、30 歲以上每年補助；HPV 35、45、65 歲各 1 次。肺癌、口腔癌另有身份、家族史及吸菸／檳榔條件，詳見程式內完整說明與官方來源。

程式中的衛教內容為摘要，沒有複製長篇原文。畫面上的自行登錄紀錄、示範資料及模擬環境圖片不是官方健保資料或臨床結果。

## V2 衛教與 Android 技術依據

- [國健署：胸悶、喘不過氣等症狀應立即撥打 119](https://www.hpa.gov.tw/Pages/Detail.aspx?nodeid=127&pid=11946)。V2 規則只提示尋求急救，不推斷病因。
- [衛福部臺南醫院：胸痛注意事項](https://netreg.pntn.mohw.gov.tw/he/20%E8%83%B8%E7%97%9B%E7%9A%84%E6%B3%A8%E6%84%8F%E4%BA%8B%E9%A0%85.pdf)，包含緊急症狀時避免自行開車。
- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)：裝置端辨識工廠、可用性檢查與資源釋放。
- [Android RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent)：EXTRA_PREFER_OFFLINE 可能被辨識服務忽略，因此 V2 不依賴此旗標保證離線。
- [Android TTS Voice](https://developer.android.com/reference/android/speech/tts/Voice)：檢查 isNetworkConnectionRequired，只選擇離線台灣中文語音。
- [Material BottomNavigationView](https://developer.android.com/reference/com/google/android/material/bottomnavigation/BottomNavigationView)：三個 XML 選單項目與大型字級設定。

語音問答是本機決定式規則模擬，不是 LLM 或醫療評估系統；應安排醫療專業人員審核後再正式發布。
