import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var document = BackupDocument()
    @State private var exporting = false
    @State private var exportConfirmation = false
    @State private var importing = false
    @State private var preview: HealthSnapshot?
    @State private var feedback: String?
    var body: some View {
        SheetPage(title: "設定與資料") {
            Toggle("使用更大字體", isOn: Binding(get: { store.snapshot.largeText }, set: { value in _ = store.commit { $0.largeText = value } }))
                .frame(minHeight: 64).disabled(store.loadFailure != nil)
            CareCard {
                Text("資料由您掌握").font(.title2.bold())
                Text("健康資料保存在此 iPhone 的 APP 私人儲存區，使用 iOS 檔案保護並排除自動備份，沒有帳號或自動雲端同步。移除 APP 會移除本機資料，請自行保留需要的匯出檔。")
                Text("匯出的 JSON 未額外加密，包含所有家人的健康檔案、一般紀錄與行程；不包含白話文報告摘要或語音內容。")
                CareButton(title: "匯出全部家人的備份", icon: "square.and.arrow.up", secondary: true) { exportConfirmation = true }
                    .disabled(store.loadFailure != nil || store.snapshot.profiles.isEmpty)
                CareButton(title: "匯入 Android／iOS JSON 備份", icon: "square.and.arrow.down", secondary: true) { importing = true }
                    .disabled(store.loadFailure != nil)
            }
            CareCard {
                Text("關於顧健康 iOS 1.0").font(.title2.bold())
                Text("原生 SwiftUI 介面，支援 iOS 17 以上。白話文報告為本機基本解說，沒有內建 Android 版的 Qwen 模型。衛教語音管家使用本機規則，並非醫療診斷或自由對話 AI。")
                Text("政策資料沿用 \(PolicyEngine.reviewDate) 固定版本，實際資格及費用請向院所確認。")
                Link("政策與衛教來源", destination: URL(string: "https://github.com/AngleZero-collab/gu-health/blob/main/android/SOURCES.md")!).frame(minHeight: 64)
                Link("國健署篩檢資訊", destination: URL(string: PolicyEngine.cancerSource)!).frame(minHeight: 64)
            }
        }
        .alert("匯出包含健康資訊的備份？", isPresented: $exportConfirmation) {
            Button("取消", role: .cancel) {}
            Button("選擇儲存位置") {
                do { document = BackupDocument(data: try BackupCodec.export(store.snapshot)); exporting = true }
                catch { feedback = "無法建立備份，請稍後重試。" }
            }
        } message: { Text("檔案未額外加密。請確認已取得家人同意，並選擇安全的位置。") }
        .fileExporter(isPresented: $exporting, document: document, contentType: .json, defaultFilename: "guhealth-\(HealthDate.string(Date()))") { result in
            document = BackupDocument()
            switch result {
            case .success: feedback = "備份已匯出，請妥善保管檔案。"
            case .failure: feedback = "匯出未完成，原有資料未修改。"
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            do {
                let url = try result.get()
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                let handle = try FileHandle(forReadingFrom: url)
                defer { try? handle.close() }
                let data = try handle.read(upToCount: 10_000_001) ?? Data()
                preview = try BackupCodec.preview(data)
            } catch { feedback = "匯入未完成。\(error.localizedDescription)" }
        }
        .alert("確認加入備份內容？", isPresented: Binding(get: { preview != nil }, set: { if !$0 { preview = nil } })) {
            Button("取消", role: .cancel) { preview = nil }
            Button("加入這些資料") {
                if let preview, store.merge(preview) { feedback = "資料已加入。未來行程的通知需逐項重新設定。" }
                else { feedback = store.message ?? "匯入失敗。" }
                preview = nil
            }
        } message: {
            Text("將新增 \(preview?.profiles.count ?? 0) 份家人檔案、\(preview?.records.count ?? 0) 筆紀錄與 \(preview?.appointments.count ?? 0) 個行程。\n不會覆寫現有資料；重複匯入會產生重複檔案。通知不會自動啟用。")
        }
        .alert("顧健康", isPresented: Binding(get: { feedback != nil }, set: { if !$0 { feedback = nil } })) {
            Button("知道了", role: .cancel) { feedback = nil }
        } message: { Text(feedback ?? "") }
    }
}
