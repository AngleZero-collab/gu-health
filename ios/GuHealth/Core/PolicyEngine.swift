import Foundation

public struct ScreeningPolicy: Identifiable, Equatable, Sendable {
    public let id, title, category, summary, rule: String
    public let eligible, publicService: Bool
    public let intervalMonths: Int
    public let source, preparation: String
    public var frequency: String {
        if id == "hpv" { return "35、45、65 歲各 1 次" }
        if !publicService { return "依醫師建議安排" }
        return intervalMonths == 12 ? "每年 1 次" : "每 \(intervalMonths / 12) 年 1 次"
    }
    public func nextReview(after date: Date) -> Date? {
        guard intervalMonths > 0, id != "hpv" else { return nil }
        return HealthDate.calendar.date(byAdding: .month, value: intervalMonths, to: date)
    }
    public func status(last: Date?, profile: Profile, on now: Date) -> String {
        guard let last else { return "待向院所確認" }
        if id == "hpv" {
            return profile.age(on: last) == profile.age(on: now) ? "本年齡已登錄" : "待向院所確認・本年齡尚無登錄"
        }
        guard let next = nextReview(after: last) else { return "已登錄・依醫師建議追蹤" }
        return next <= now ? "已登錄・可詢問下次安排" : "已登錄・\(HealthDate.string(next)) 再確認"
    }
}

/// Direct port of Android's fixed 2026-08-31 snapshot; not a live eligibility service.
public enum PolicyEngine {
    public static let reviewDate = "2026-08-31"
    public static let cancerSource = "https://www.hpa.gov.tw/Pages/List.aspx?nodeid=211"
    public static let adultSource = "https://www.mohw.gov.tw/cp-2704-82262-1.html"
    public static let clinics = "https://escreening.hpa.gov.tw/Home"
    public static let disclaimer = "依填寫資料初步比對；未連接健保受檢紀錄。實際補助、受檢間隔及是否適合篩檢，請由院所確認。"
    public static func policies(for profile: Profile, on now: Date = Date()) -> [ScreeningPolicy] {
        let age = profile.age(on: now)
        let female = profile.sex == "女"
        let adultMonths = age >= 65 || (profile.indigenous && age >= 55) || (profile.polio && age >= 35) ? 12 : age >= 40 ? 36 : 60
        let familyLung = profile.familyLung && age <= 74 && age >= (female ? 40 : 45)
        let smoker = between(age, 50, 74) && profile.packYears >= 20 && (profile.smokes || (profile.quitYears >= 0 && profile.quitYears <= 15))
        return [
            ScreeningPolicy(id: "adult", title: "成人預防保健", category: "全身健康", summary: "從血壓、血糖到腎功能，一次認識自己的健康狀況。", rule: "30–39 歲每 5 年；40–64 歲每 3 年；65 歲以上每年。55 歲以上原住民、35 歲以上小兒麻痺患者每年。", eligible: age >= 30, publicService: true, intervalMonths: adultMonths, source: adultSource, preparation: "攜帶健保卡；請先向院所確認空腹與用藥注意事項，不要自行停藥。"),
            ScreeningPolicy(id: "colon", title: "大腸癌篩檢", category: "大腸癌篩檢", summary: "簡便的採便檢查，掌握大腸健康。", rule: "45–74 歲，或 40–44 歲且父母、兄弟姊妹或子女曾罹患大腸癌，每 2 年 1 次。", eligible: between(age,45,74) || (between(age,40,44) && profile.familyColon), publicService: true, intervalMonths: 24, source: cancerSource, preparation: "先向合約院所領取採便管。免疫法糞便潛血通常不需限制飲食；依院所指示採檢並送回。有大腸癌或息肉病史請先諮詢醫師。"),
            ScreeningPolicy(id: "breast", title: "乳癌篩檢", category: "乳癌篩檢", summary: "規律檢查，及早發現乳房變化。", rule: "40–74 歲女性，每 2 年 1 次。", eligible: female && between(age,40,74), publicService: true, intervalMonths: 24, source: cancerSource, preparation: "請先預約並告知是否懷孕、哺乳或有乳房手術史；如有症狀，請直接就醫。"),
            ScreeningPolicy(id: "cervical", title: "子宮頸癌篩檢", category: "子宮頸癌篩檢", summary: "定期追蹤細胞變化，保護女性健康。", rule: "25–29 歲女性每 3 年補助 1 次；30 歲以上每年補助 1 次（建議每 3 年至少 1 次）。", eligible: female && age >= 25, publicService: true, intervalMonths: age < 30 ? 36 : 12, source: cancerSource, preparation: "請向婦產科確認檢查前注意事項；是否仍需篩檢應依個人病史與醫師評估。"),
            ScreeningPolicy(id: "hpv", title: "子宮頸病毒檢查", category: "女性健康", summary: "了解人類乳突病毒感染狀況。", rule: "35、45、65 歲女性各可接受 1 次補助檢測。", eligible: female && (age == 35 || age == 45 || age == 65), publicService: true, intervalMonths: 120, source: "https://www.hpa.gov.tw/Pages/Detail.aspx?nodeid=4809&pid=18712", preparation: "檢測結果需由醫事人員說明；不要自行以結果判定癌症。"),
            ScreeningPolicy(id: "lung", title: "肺癌篩檢", category: "肺癌篩檢", summary: "針對特定高風險族群的肺部檢查。", rule: "有肺癌一等親家族史：45–74 歲男性、40–74 歲女性；或 50–74 歲吸菸 ≥20 包年且仍吸菸或戒菸未超過 15 年。每 2 年 1 次。", eligible: familyLung || smoker, publicService: true, intervalMonths: 24, source: cancerSource, preparation: "由醫師確認適用資格、吸菸史與檢查風險，並依院所安排接受檢查。"),
            ScreeningPolicy(id: "oral", title: "口腔癌篩檢", category: "口腔癌篩檢", summary: "留意口腔黏膜、潰瘍與異常變化。", rule: "30 歲以上吸菸或嚼檳榔（含已戒）者；18–29 歲嚼檳榔（含已戒）原住民，每 2 年 1 次。", eligible: (age >= 30 && (profile.smokes || profile.betel)) || (between(age,18,29) && profile.indigenous && profile.betel), publicService: true, intervalMonths: 24, source: cancerSource, preparation: "攜帶健保卡，向牙科或耳鼻喉科詢問。持續不癒的口腔潰瘍請直接就醫。"),
            ScreeningPolicy(id: "dental", title: "牙科定期追蹤", category: "日常照護", summary: "讓咀嚼與笑容，都被好好照顧。", rule: "一般牙科追蹤，不等同公費癌症篩檢；頻率及費用由牙醫評估。", eligible: true, publicService: false, intervalMonths: 0, source: "https://www.hpa.gov.tw/", preparation: "攜帶過去治療紀錄，告知用藥與身體狀況。"),
            ScreeningPolicy(id: "eye", title: "眼科定期追蹤", category: "日常照護", summary: "留意視力、眼底與日常視覺的變化。", rule: "一般眼科追蹤，不列為人人適用的免費篩檢；頻率及費用請諮詢眼科。", eligible: true, publicService: false, intervalMonths: 0, source: "https://www.nei.nih.gov/eye-health-information/eye-conditions-and-diseases/age-related-macular-degeneration", preparation: "視線突然變形、中心黑影或視力突然下降，請儘速就醫，不要等待下一次自我檢查。")
        ]
    }
    private static func between(_ age: Int, _ low: Int, _ high: Int) -> Bool { age >= low && age <= high }
}
