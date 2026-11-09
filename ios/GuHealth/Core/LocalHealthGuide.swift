import Foundation

/// Deterministic education rules; no network, history, diagnosis, or language model.
public enum LocalHealthGuide {
    public static func reply(to input: String) -> String {
        if ["胸痛","胸口痛","喘不過氣","呼吸困難","昏倒","嘴歪","手腳無力","說話不清","想自殺","不想活"].contains(where: { input.contains($0) }) { return "我不能進行診斷。若您現在胸痛、呼吸困難、突然無力或有立即危險，請立刻撥打一一九，或請身邊的人協助去急診。不要等待語音問答或自行開車。" }
        if ["眼","視力","看不清","黑影","線條","视力","看不见"].contains(where: { input.contains($0) }) { return "我不能判定眼睛是否有疾病。若視力突然下降、線條變形或出現黑影，請儘速就醫。平時的視覺困擾，可以向眼科詢問。" }
        if ["牙","口腔","牙齦"].contains(where: { input.contains($0) }) { return "我不能診斷牙齒或口腔疾病。可以保持日常口腔清潔，若疼痛、流血或潰瘍持續，請找牙科檢查。若有嚴重腫脹或呼吸困難，請立即就醫。" }
        if ["藥","药","劑量","停藥","加藥"].contains(where: { input.contains($0) }) { return "我不能建議您自行調整藥物或劑量。請帶著藥袋詢問原開藥醫師或藥師，不要自行停藥或加藥。若有急性嚴重不適，請立即就醫。" }
        if ["篩檢","健檢","檢查","筛检","健检"].contains(where: { input.contains($0) }) { return "可以在首頁查看初步符合的篩檢項目。實際資格、時間與費用，請先向院所確認，也可以詢問家庭醫學科。若已有不舒服的症狀，請直接就醫，不要只等待篩檢。" }
        if ["血壓","血糖","頭暈","血压","头晕"].contains(where: { input.contains($0) }) { return "我不能根據語音內容診斷疾病。可以把量測數值與不舒服的時間記下來，帶到家庭醫學科或內科討論。若突然劇烈不適、胸痛或無力，請立即就醫。" }
        return "我是提供基本衛教的健康管家，不能進行醫療診斷。身體持續不舒服或不知道要看哪一科，可以先詢問家庭醫學科。若突然出現嚴重不適，請立即就醫。"
    }
}
