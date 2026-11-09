import SwiftUI

struct FamilyView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var edit: Profile?
    @State private var deleting: Profile?
    var body: some View {
        SheetPage(title: "家人健康檔案") {
            ForEach(store.snapshot.profiles) { profile in
                CareCard {
                    Text(profile.name).font(.title2.bold())
                    Text("\(profile.age()) 歲・\(profile.city)")
                    CareButton(title: store.profile?.id == profile.id ? "正在查看此檔案" : "切換至此檔案", icon: "person.crop.circle") {
                        if store.commit({ $0.selectedProfileID = profile.id }) { dismiss() }
                    }
                    CareButton(title: "編輯檔案", secondary: true) { edit = profile }
                    CareButton(title: "刪除此家人與紀錄", secondary: true, destructive: true) { deleting = profile }
                }
            }
            CareButton(title: "新增家人檔案", icon: "person.badge.plus", secondary: true) { edit = Profile() }
        }
        .sheet(item: $edit) { ProfileForm(profile: $0) }
        .alert("刪除\(deleting?.name ?? "家人")的全部資料？", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("取消", role: .cancel) { deleting = nil }
            Button("刪除檔案與紀錄", role: .destructive) { if let deleting { store.deleteProfile(deleting.id) }; deleting = nil }
        } message: { Text("此檔案的健康紀錄、報告摘要及行程會一起刪除，提醒也會取消。此操作無法復原。") }
    }
}

struct ProfileForm: View {
    @State var profile: Profile
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var error = ""
    @State private var packYears = ""
    @State private var quitYears = ""
    var body: some View {
        SheetPage(title: "健康檔案") {
            CareField(title: "姓名或暱稱", text: $profile.name, placeholder: "例如：媽媽")
            CareField(title: "西元生日（年-月-日）", text: $profile.birth, placeholder: "1954-03-15", keyboard: .numbersAndPunctuation)
            Picker("篩檢規則使用的生理性別", selection: $profile.sex) {
                Text("女").tag("女"); Text("男").tag("男")
            }.font(.title3).frame(minHeight: 64)
            CareField(title: "所在縣市", text: $profile.city, placeholder: "例如：臺北市")
            CareCard {
                Text("資格背景（自行填寫）").font(.title2.bold())
                Toggle("原住民身分", isOn: $profile.indigenous).frame(minHeight: 64)
                Toggle("小兒麻痺病史", isOn: $profile.polio).frame(minHeight: 64)
                Toggle("一等親大腸癌家族史", isOn: $profile.familyColon).frame(minHeight: 64)
                Toggle("一等親肺癌家族史", isOn: $profile.familyLung).frame(minHeight: 64)
                Toggle("目前吸菸", isOn: $profile.smokes).frame(minHeight: 64)
                Toggle("嚼檳榔（含已戒）", isOn: $profile.betel).frame(minHeight: 64)
            }
            CareField(title: "吸菸包年（每日包數 × 年數）", text: $packYears, placeholder: "沒有請填 0", keyboard: .numberPad)
            CareField(title: "戒菸年數（從未吸菸或仍吸菸填 -1）", text: $quitYears, placeholder: "-1", keyboard: .numbersAndPunctuation)
            Notice(text: "這些資料只用來初步整理項目，無法取代院所資格確認。")
            if !error.isEmpty { Notice(text: error, warning: true) }
            CareButton(title: "儲存健康檔案", icon: "checkmark") { save() }.accessibilityIdentifier("save-profile")
        }.onAppear { packYears = String(profile.packYears); quitYears = String(profile.quitYears) }
    }
    private func save() {
        profile.name = profile.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !profile.name.isEmpty, profile.name.count <= 100, profile.city.count <= 100 else { error = "請填寫暱稱，姓名與縣市各限 100 字。"; return }
        guard let birth = HealthDate.parse(profile.birth), birth <= Date(), (0...130).contains(profile.age()) else { error = "請確認西元生日，例如 1954-03-15。"; return }
        guard let packs = Int(packYears), (0...300).contains(packs), let quit = Int(quitYears), (-1...130).contains(quit) else { error = "請確認吸菸包年與戒菸年數。"; return }
        profile.packYears = packs; profile.quitYears = quit
        if store.saveProfile(profile) { dismiss() } else { error = store.message ?? "儲存失敗。" }
    }
}
