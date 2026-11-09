import SwiftUI

struct RecordsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var newRecord = false
    @State private var newVisit = false
    @State private var report = false
    @State private var deleting: HealthRecord?
    @State private var deletingVisit: Appointment?
    @State private var shareConfirmation = false
    @State private var share = false
    @State private var shareText = ""
    @State private var feedback: String?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("我的健檢與紀錄").font(.largeTitle.bold())
                if let profile = store.profile {
                    Text("正在查看：\(profile.name)")
                    CareButton(title: "新增健康紀錄", icon: "plus.circle") { newRecord = true }
                    CareButton(title: "白話文健檢報告", icon: "doc.text.magnifyingglass", secondary: true) { report = true }
                    CareButton(title: "加入健康行程", icon: "calendar.badge.plus", secondary: true) { newVisit = true }
                    CareButton(title: "分享這位家人的摘要", icon: "square.and.arrow.up", secondary: true) { shareConfirmation = true }
                    let visits = store.snapshot.appointments.filter { $0.profileID == profile.id }.sorted { $0.at < $1.at }
                    if !visits.isEmpty {
                        Text("健康行程").font(.title.bold())
                        ForEach(visits) { visit in
                            CareCard {
                                Text(visit.title).font(.title2.bold())
                                Text(visit.at.formatted(date: .abbreviated, time: .shortened))
                                if !visit.place.isEmpty { Text(visit.place) }
                                Text(visit.at <= Date() ? "此行程時間已過" : visit.notificationEnabled ? "已登錄提醒設定；送達依系統通知設定" : "行程已保存，尚未開啟提醒")
                                if visit.at > Date() {
                                    CareButton(title: "設定／重新確認提醒", icon: "bell", secondary: true) {
                                        Task { feedback = await store.enableReminder(visit.id) }
                                    }
                                }
                                CareButton(title: "刪除行程", secondary: true, destructive: true) { deletingVisit = visit }
                            }
                        }
                    }
                    Text("已登錄紀錄").font(.title.bold())
                    let records = store.snapshot.records(for: profile.id)
                    if records.isEmpty { Notice(text: "還沒有紀錄。可先登錄已完成的檢查、血壓量測或照護筆記。") }
                    ForEach(records) { record in
                        CareCard {
                            Text(record.title).font(.title2.bold())
                            Text(record.date).foregroundStyle(CareTheme.green)
                            if record.kind == .reportSummary { Text("手動保存的基本解說摘要・不納入分享或備份") }
                            if !record.note.isEmpty { Text(record.note).textSelection(.enabled) }
                            if record.flag { Notice(text: "曾回報有變化；若是突然出現，請儘速就醫。", warning: true) }
                            CareButton(title: "刪除紀錄", secondary: true, destructive: true) { deleting = record }
                        }
                    }
                    Notice(text: "這些是自行登錄的資料，並非醫療院所或健保受檢紀錄。")
                } else { Notice(text: "請先在首頁建立健康檔案。") }
            }.padding(20)
        }
        .sheet(isPresented: $newRecord) { if let profile = store.profile { RecordForm(profile: profile) } }
        .sheet(isPresented: $newVisit) { if let profile = store.profile { AppointmentForm(profile: profile) } }
        .sheet(isPresented: $report) { if let profile = store.profile { ReportView(profile: profile) } }
        .sheet(isPresented: $share, onDismiss: { shareText = "" }) { ShareSheet(text: shareText).privateWhenInactive() }
        .alert("分享健康摘要？", isPresented: $shareConfirmation) {
            Button("取消", role: .cancel) {}
            Button("選擇分享方式") {
                if let profile = store.profile { shareText = store.snapshot.summary(for: profile); share = true }
            }
        } message: { Text("摘要包含這位家人的暱稱、年齡及自行登錄的健康紀錄；請先取得本人同意。報告摘要不會包含在內。") }
        .alert("刪除這筆紀錄？", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("取消", role: .cancel) { deleting = nil }
            Button("刪除", role: .destructive) {
                if let deleting { _ = store.commit { $0.records.removeAll { $0.id == deleting.id } } }; deleting = nil
            }
        } message: { Text("刪除後無法復原。") }
        .alert("刪除此行程？", isPresented: Binding(get: { deletingVisit != nil }, set: { if !$0 { deletingVisit = nil } })) {
            Button("取消", role: .cancel) { deletingVisit = nil }
            Button("刪除行程", role: .destructive) { if let deletingVisit { store.deleteAppointment(deletingVisit.id) }; deletingVisit = nil }
        } message: { Text("對應的手機提醒也會取消。") }
        .alert("行程提醒", isPresented: Binding(get: { feedback != nil }, set: { if !$0 { feedback = nil } })) {
            Button("知道了", role: .cancel) { feedback = nil }
        } message: { Text(feedback ?? "") }
    }
}

struct RecordForm: View {
    let profile: Profile
    var selectedPolicy: ScreeningPolicy? = nil
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var kind = "note"
    @State private var key = "adult"
    @State private var title = ""
    @State private var date = HealthDate.string(Date())
    @State private var note = ""
    @State private var systolic = ""
    @State private var diastolic = ""
    @State private var error = ""
    var body: some View {
        SheetPage(title: "新增健康紀錄") {
            Text("保存給：\(profile.name)")
            Picker("紀錄類型", selection: $kind) {
                Text("日常照護筆記").tag("note")
                Text("血壓量測").tag("bp")
                Text("已完成的檢查").tag("screening")
            }.frame(minHeight: 64)
            if kind == "screening" {
                Picker("檢查項目", selection: $key) {
                    ForEach(PolicyEngine.policies(for: profile)) { Text($0.title).tag($0.id) }
                }.frame(minHeight: 64)
            } else if kind == "bp" {
                CareField(title: "收縮壓（mmHg）", text: $systolic, keyboard: .numberPad)
                CareField(title: "舒張壓（mmHg）", text: $diastolic, keyboard: .numberPad)
                Notice(text: "僅記錄數值，不自動診斷。若有身體不適，請就醫。")
            } else { CareField(title: "紀錄標題", text: $title, placeholder: "例如：今天一起散步") }
            CareField(title: "完成日期（西元年-月-日）", text: $date, placeholder: "2026-11-09", keyboard: .numbersAndPunctuation)
            CareField(title: "備註（選填）", text: $note, placeholder: "檢查地點、醫師交代或想記下的事", multiline: true)
            if !error.isEmpty { Notice(text: error, warning: true) }
            CareButton(title: "儲存這筆紀錄", icon: "checkmark") { save() }
        }.onAppear { if let selectedPolicy { kind = "screening"; key = selectedPolicy.id } }
    }
    private func save() {
        guard let parsed = HealthDate.parse(date), parsed <= Date() else { error = "請填寫有效的完成日期，不能是未來日期。"; return }
        guard title.count <= 300, note.count <= 20_000 else { error = "標題限 300 字，備註限 20,000 字。"; return }
        var record = HealthRecord(profileID: profile.id, kind: .note, title: title.trimmingCharacters(in: .whitespacesAndNewlines), date: date, note: note)
        if kind == "bp" {
            guard let s = Int(systolic), let d = Int(diastolic), (30...300).contains(s), (20...200).contains(d), s > d else { error = "請確認血壓數值與收縮壓、舒張壓的順序。"; return }
            record.kind = .bloodPressure; record.title = "血壓量測 \(s) / \(d) mmHg"
        } else if kind == "screening", let policy = PolicyEngine.policies(for: profile).first(where: { $0.id == key }) {
            record.kind = .screening; record.key = key; record.title = policy.title
        } else if record.title.isEmpty { error = "請填寫紀錄標題。"; return }
        if store.addRecord(record) { dismiss() } else { error = store.message ?? "儲存失敗。" }
    }
}

struct AppointmentForm: View {
    let profile: Profile
    var policy: ScreeningPolicy? = nil
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var date = HealthDate.string(Date().addingTimeInterval(86400))
    @State private var time = "09:00"
    @State private var place = ""
    @State private var error = ""
    var body: some View {
        SheetPage(title: "加入健康行程") {
            Text("保存給：\(profile.name)")
            Notice(text: "這是手機內的行程，不會替您向院所預約。儲存後可在紀錄頁自行開啟通知提醒；不適用精準用藥或醫療處置。")
            CareField(title: "行程名稱", text: $title, placeholder: "例如：至診所領取採便管")
            CareField(title: "西元日期（年-月-日）", text: $date, keyboard: .numbersAndPunctuation)
            CareField(title: "時間（24 小時制）", text: $time, placeholder: "09:00", keyboard: .numbersAndPunctuation)
            Text("日期與時間使用臺灣時區；前往其他時區時仍維持原定時刻。")
            CareField(title: "地點或備註（選填）", text: $place)
            if !error.isEmpty { Notice(text: error, warning: true) }
            CareButton(title: "儲存行程", icon: "calendar.badge.plus") {
                guard let day = HealthDate.parse(date), time.range(of: #"^(?:[01]\d|2[0-3]):[0-5]\d$"#, options: .regularExpression) != nil else { error = "請確認日期與時間格式，例如 2026-11-10、09:00。"; return }
                let parts = time.split(separator: ":").compactMap { Int($0) }
                guard let at = HealthDate.calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: day), at > Date() else { error = "請選擇未來的時間。"; return }
                let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !clean.isEmpty, clean.count <= 300, place.count <= 1_000 else { error = "請填寫 1–300 字的名稱，地點限 1,000 字。"; return }
                let visit = Appointment(profileID: profile.id, key: policy?.id ?? "", title: clean, at: at, place: place)
                if store.commit({ $0.appointments.append(visit) }) { dismiss() } else { error = store.message ?? "儲存失敗。" }
            }
        }.onAppear { title = policy?.title ?? "" }
    }
}
