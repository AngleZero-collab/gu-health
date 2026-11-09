import SwiftUI

struct ReportView: View {
    let profile: Profile
    @EnvironmentObject private var store: AppStore
    @Environment(\.scenePhase) private var phase
    @State private var inputs = [LabInput()]
    @State private var lines: [LabLine] = []
    @State private var error = ""
    @State private var saved = false
    var body: some View {
        SheetPage(title: "白話文健檢報告") {
            Text("正在查看：\(profile.name)")
            Notice(text: "基本解說版：依您原報告的參考值比對，不使用通用門檻推斷疾病，也不是診斷。離開頁面或 APP 進入背景後，原始數值與參考值會清除。")
            ForEach($inputs) { $input in
                CareCard {
                    Picker("檢驗項目", selection: $input.item) {
                        ForEach(LabItem.allCases) { Text($0.label).tag($0) }
                    }.frame(minHeight: 64)
                    if input.item == .custom { CareField(title: "檢驗名稱", text: $input.name) }
                    CareField(title: "結果數值", text: $input.value, placeholder: "請依原報告填寫", keyboard: .numbersAndPunctuation)
                    CareField(title: "單位", text: $input.unit, placeholder: "例如 mg/dL，或填「無單位」")
                    CareField(title: "原報告參考值（選填）", text: $input.reference, placeholder: "例如 70–99、<100、≥60")
                    if inputs.count > 1 {
                        CareButton(title: "移除此項目", secondary: true, destructive: true) { inputs.removeAll { $0.id == input.id } }
                    }
                }
            }
            if inputs.count < ReportExplanation.maxItems {
                CareButton(title: "再新增一個項目（最多 12 項）", icon: "plus.circle", secondary: true) { inputs.append(LabInput()) }
            }
            if !error.isEmpty { Notice(text: error, warning: true) }
            CareButton(title: "產生基本解說", icon: "doc.text.magnifyingglass") {
                do { lines = try ReportExplanation.explain(inputs); error = ""; saved = false }
                catch { lines = []; self.error = error.localizedDescription }
            }
            if !lines.isEmpty {
                CareCard {
                    Text("這次的基本解說").font(.title2.bold())
                    Text(ReportExplanation.savedText(lines))
                    ForEach(lines) { line in
                        VStack(alignment: .leading, spacing: 10) {
                            Divider()
                            Text("\(line.name)｜\(line.status.rawValue)").font(.title3.bold())
                            Text("\(line.value) \(line.unit)；所填參考值：\(line.reference.isEmpty ? "未提供" : line.reference)")
                            Text(line.item.meaning)
                        }
                    }
                    Notice(text: "超出參考值不等於確診；符合參考值也無法排除疾病。請帶原始報告交由醫事人員說明。", warning: true)
                    CareButton(title: saved ? "摘要已保存" : "只保存摘要至目前家人", icon: "square.and.arrow.down") {
                        let record = HealthRecord(profileID: profile.id, kind: .reportSummary, title: "白話文健檢報告・基本解說", note: ReportExplanation.savedText(lines))
                        if store.addRecord(record) { saved = true } else { error = store.message ?? "儲存失敗。" }
                    }.disabled(saved)
                    Text("僅保存項目名稱、比對分類與提問；不保存原始數值、單位或參考區間，摘要也不納入分享與備份。")
                }
            }
            Link("如何理解檢驗報告（MedlinePlus）", destination: URL(string: "https://medlineplus.gov/lab-tests/how-to-understand-your-lab-results/")!).frame(minHeight: 64)
        }
        .privacySensitive()
        .onChange(of: inputs) { _, _ in lines = []; saved = false }
        .onChange(of: phase) { _, phase in
            if phase == .background { clear(); error = "APP 曾進入背景，原始輸入已清除，請重新填寫。" }
        }
        .onDisappear { clear() }
    }
    private func clear() { inputs = [LabInput()]; lines = []; saved = false; error = "" }
}
