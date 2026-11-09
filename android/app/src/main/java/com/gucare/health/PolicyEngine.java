package com.gucare.health;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Curated policy snapshot, not a live government eligibility determination. */
public final class PolicyEngine {
    public static final String REVIEW_DATE = "2026-08-31";
    public static final String CANCER_SOURCE = "https://www.hpa.gov.tw/Pages/List.aspx?nodeid=211";
    public static final String ADULT_SOURCE = "https://www.mohw.gov.tw/cp-2704-82262-1.html";
    public static final String CLINICS = "https://escreening.hpa.gov.tw/Home";
    public static final String DISCLAIMER = "依填寫資料初步比對；未連接健保受檢紀錄。實際補助、受檢間隔及是否適合篩檢，請由院所確認。";

    public static final class Policy {
        public final String id, title, category, summary, rule, source, preparation;
        public final boolean eligible, publicService;
        public final int intervalMonths;
        Policy(String id, String title, String category, String summary, String rule, boolean eligible,
               boolean publicService, int intervalMonths, String source, String preparation) {
            this.id=id; this.title=title; this.category=category; this.summary=summary; this.rule=rule;
            this.eligible=eligible; this.publicService=publicService; this.intervalMonths=intervalMonths;
            this.source=source; this.preparation=preparation;
        }
        public String frequency() {
            if (id.equals("hpv")) return "35、45、65 歲各 1 次";
            if (!publicService) return "依醫師建議安排";
            return intervalMonths == 12 ? "每年 1 次" : "每 " + (intervalMonths / 12) + " 年 1 次";
        }
    }

    public static List<Policy> forProfile(Profile p, LocalDate now) {
        int age = p.age(now);
        boolean female = "女".equals(p.sex);
        int adultMonths = age >= 65 || (p.indigenous && age >= 55) || (p.polio && age >= 35) ? 12 : age >= 40 ? 36 : 60;
        List<Policy> all = new ArrayList<>();
        all.add(new Policy("adult", "成人預防保健", "全身健康", "從血壓、血糖到腎功能，一次認識自己的健康狀況。",
            "30–39 歲每 5 年；40–64 歲每 3 年；65 歲以上每年。55 歲以上原住民、35 歲以上小兒麻痺患者每年。",
            age >= 30, true, adultMonths, ADULT_SOURCE, "攜帶健保卡；請先向院所確認空腹與用藥注意事項，不要自行停藥。"));
        all.add(new Policy("colon", "大腸癌篩檢", "大腸癌篩檢", "簡便的採便檢查，掌握大腸健康。",
            "45–74 歲，或 40–44 歲且父母、兄弟姊妹或子女曾罹患大腸癌，每 2 年 1 次。",
            between(age,45,74) || (between(age,40,44) && p.familyColon), true, 24, CANCER_SOURCE,
            "先向合約院所領取採便管。免疫法糞便潛血通常不需限制飲食；依院所指示採檢並送回。有大腸癌或息肉病史請先諮詢醫師。"));
        all.add(new Policy("breast", "乳癌篩檢", "乳癌篩檢", "規律檢查，及早發現乳房變化。",
            "40–74 歲女性，每 2 年 1 次。", female && between(age,40,74), true, 24, CANCER_SOURCE,
            "請先預約並告知是否懷孕、哺乳或有乳房手術史；如有症狀，請直接就醫。"));
        all.add(new Policy("cervical", "子宮頸癌篩檢", "子宮頸癌篩檢", "定期追蹤細胞變化，保護女性健康。",
            "25–29 歲女性每 3 年補助 1 次；30 歲以上每年補助 1 次（建議每 3 年至少 1 次）。",
            female && age >= 25, true, age < 30 ? 36 : 12, CANCER_SOURCE,
            "請向婦產科確認檢查前注意事項；是否仍需篩檢應依個人病史與醫師評估。"));
        all.add(new Policy("hpv", "子宮頸病毒檢查", "女性健康", "了解人類乳突病毒感染狀況。",
            "35、45、65 歲女性各可接受 1 次補助檢測。", female && (age==35 || age==45 || age==65), true, 120,
            "https://www.hpa.gov.tw/Pages/Detail.aspx?nodeid=4809&pid=18712", "檢測結果需由醫事人員說明；不要自行以結果判定癌症。"));
        boolean familyLung = p.familyLung && age <= 74 && age >= (female ? 40 : 45);
        boolean smoker = between(age,50,74) && p.packYears >= 20 && (p.smokes || (p.quitYears >= 0 && p.quitYears <= 15));
        all.add(new Policy("lung", "肺癌篩檢", "肺癌篩檢", "針對特定高風險族群的肺部檢查。",
            "有肺癌一等親家族史：45–74 歲男性、40–74 歲女性；或 50–74 歲吸菸 ≥20 包年且仍吸菸或戒菸未超過 15 年。每 2 年 1 次。",
            familyLung || smoker, true, 24, CANCER_SOURCE, "由醫師確認適用資格、吸菸史與檢查風險，並依院所安排接受檢查。"));
        all.add(new Policy("oral", "口腔癌篩檢", "口腔癌篩檢", "留意口腔黏膜、潰瘍與異常變化。",
            "30 歲以上吸菸或嚼檳榔（含已戒）者；18–29 歲嚼檳榔（含已戒）原住民，每 2 年 1 次。",
            (age >=30 && (p.smokes || p.betel)) || (between(age,18,29) && p.indigenous && p.betel), true,24,CANCER_SOURCE,
            "攜帶健保卡，向牙科或耳鼻喉科詢問。持續不癒的口腔潰瘍請直接就醫。"));
        all.add(new Policy("dental", "牙科定期追蹤", "日常照護", "讓咀嚼與笑容，都被好好照顧。", "一般牙科追蹤，不等同公費癌症篩檢；頻率及費用由牙醫評估。",
            true,false,0,"https://www.hpa.gov.tw/", "攜帶過去治療紀錄，告知用藥與身體狀況。"));
        all.add(new Policy("eye", "眼科定期追蹤", "日常照護", "留意視力、眼底與日常視覺的變化。", "一般眼科追蹤，不列為人人適用的免費篩檢；頻率及費用請諮詢眼科。",
            true,false,0,"https://www.nei.nih.gov/eye-health-information/eye-conditions-and-diseases/age-related-macular-degeneration",
            "視線突然變形、中心黑影或視力突然下降，請儘速就醫，不要等待下一次自我檢查。"));
        return all;
    }
    public static boolean between(int age,int low,int high) { return age >= low && age <= high; }
    public static LocalDate nextReview(Policy p, LocalDate last) {
        if (last==null || p.intervalMonths <= 0 || p.id.equals("hpv")) return null;
        return last.plusMonths(p.intervalMonths);
    }
    public static boolean hpvUsedForCurrentAge(Profile p, LocalDate now, LocalDate last) {
        return last!=null && !last.isAfter(now) && p.age(last)==p.age(now);
    }
    public static Policy find(Profile p, String id) {
        for (Policy rule:forProfile(p,LocalDate.now())) if (rule.id.equals(id)) return rule;
        return null;
    }
}
