import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dynamicTypeSize) private var systemTypeSize
    @State private var tab = 0
    @State private var settings = false
    @State private var voice = false
    private let tabs = [("首頁", "house.fill"), ("居家觀察", "eye.fill"), ("我的紀錄", "list.bullet.rectangle")]
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Label("顧健康", systemImage: "heart.fill").font(.title.bold())
                Spacer(minLength: 8)
                Button { settings = true } label: {
                    Label("設定", systemImage: "gearshape").font(.title3).frame(minWidth: 72, minHeight: 64)
                }.accessibilityIdentifier("settings")
            }.padding(.horizontal, 20).foregroundStyle(CareTheme.green).background(CareTheme.canvas)
            if let failure = store.loadFailure {
                ScrollView { Notice(text: failure, warning: true).padding(20) }
            } else {
                Group {
                    if tab == 0 { HomeView() }
                    else if tab == 1 { ObservationHomeView() }
                    else { RecordsView() }
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
                Button { voice = true } label: {
                    Label("衛教語音管家", systemImage: "mic.fill").font(.title3.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .foregroundStyle(CareTheme.green).background(CareTheme.mint)
                }.buttonStyle(.plain).accessibilityIdentifier("voice-guide")
                HStack(spacing: 0) {
                    ForEach(tabs.indices, id: \.self) { index in
                        Button { tab = index } label: {
                            VStack(spacing: 6) {
                                Image(systemName: tabs[index].1).font(.title2)
                                Text(tabs[index].0).font(.title3).fixedSize(horizontal: false, vertical: true)
                            }.frame(maxWidth: .infinity, minHeight: 76).padding(.vertical, 8)
                                .foregroundStyle(tab == index ? CareTheme.green : CareTheme.ink.opacity(0.75))
                                .background(tab == index ? CareTheme.mint.opacity(0.6) : .white)
                        }.buttonStyle(.plain).accessibilityAddTraits(tab == index ? .isSelected : [])
                            .accessibilityIdentifier("tab-\(index)")
                    }
                }.background(.white)
            }
        }
        .background(CareTheme.canvas).foregroundStyle(CareTheme.ink).font(.title3)
        .dynamicTypeSize(store.snapshot.largeText ? max(systemTypeSize, .xxxLarge) : systemTypeSize)
        .sheet(isPresented: $settings) { SettingsView() }
        .sheet(isPresented: $voice) { VoiceGuideView() }
        .privateWhenInactive()
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var family = false
    @State private var addProfile = false
    @State private var detail: ScreeningPolicy?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if let profile = store.profile {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("\(profile.name)，您好").font(.largeTitle.bold()).accessibilityIdentifier("greeting")
                            Text("一起照顧日常的健康").font(.title3)
                        }
                    }
                    CareButton(title: "切換／管理家人", icon: "person.2", secondary: true) { family = true }
                    let policies = PolicyEngine.policies(for: profile).filter(\.eligible)
                    Text("可向院所確認的項目").font(.title.bold())
                    Notice(text: "\(PolicyEngine.disclaimer)\n政策版本：\(PolicyEngine.reviewDate)，並非即時查詢。")
                    ForEach(policies) { policy in
                        CareCard {
                            Text(policy.title).font(.title2.bold())
                            Text(policy.summary)
                            Text(policy.frequency).foregroundStyle(CareTheme.green)
                            let last = store.snapshot.lastScreening(profileID: profile.id, key: policy.id, on: Date())
                            Text(policy.status(last: last, profile: profile, on: Date())).font(.title3.weight(.medium))
                            CareButton(title: "查看資格與登錄", icon: "doc.text", secondary: true) { detail = policy }
                            ClinicLinks(title: policy.title, city: profile.city)
                        }
                    }
                } else {
                    Text("把健康，好好放在心上").font(.largeTitle.bold())
                    Text("建立自己或家人的檔案，整理篩檢、居家觀察與健康紀錄。")
                    CareCard {
                        Image(systemName: "heart.text.clipboard").font(.system(size: 56)).foregroundStyle(CareTheme.green).accessibilityHidden(true)
                        Text("從一份健康檔案開始").font(.title2.bold())
                        CareButton(title: "建立健康檔案", icon: "person.badge.plus") { addProfile = true }
                        CareButton(title: "先看看示範資料", secondary: true) { store.addDemo() }
                    }
                    Notice(text: "健康資料保存在此 iPhone。沒有帳號登入或自動雲端同步；可在設定匯入 Android 版的 JSON 備份。")
                }
            }.padding(20)
        }
        .sheet(isPresented: $family) { FamilyView() }
        .sheet(isPresented: $addProfile) { ProfileForm(profile: Profile()) }
        .sheet(item: $detail) { policy in
            if let profile = store.profile { PolicyDetailView(policy: policy, profile: profile) }
        }
    }
}

struct ClinicLinks: View {
    let title: String
    let city: String
    @Environment(\.openURL) private var openURL
    @State private var phone = false
    var body: some View {
        VStack(spacing: 10) {
            CareButton(title: "院所電話", icon: "phone", secondary: true) { phone = true }
            CareButton(title: "尋找附近院所", icon: "map", secondary: true) {
                var c = URLComponents(string: "https://maps.apple.com/")!
                c.queryItems = [URLQueryItem(name: "q", value: "\(city) \(title) 診所")]
                if let url = c.url { openURL(url) }
            }
        }.sheet(isPresented: $phone) { ClinicPhoneView() }
    }
}

private struct ClinicPhoneView: View {
    @Environment(\.openURL) private var openURL
    @State private var number = ""
    @State private var error = ""
    var body: some View {
        SheetPage(title: "聯絡院所") {
            Notice(text: "目前未內建院所電話資料庫。請輸入已查證的院所電話；按鈕會交由 iPhone 確認撥號。")
            CareField(title: "院所電話", text: $number, placeholder: "例如 0223456789", keyboard: .phonePad)
            if !error.isEmpty { Notice(text: error, warning: true) }
            CareButton(title: "開啟電話確認", icon: "phone") {
                let cleaned = number.filter { "0123456789+".contains($0) }
                guard (5...20).contains(cleaned.count), let url = URL(string: "tel:\(cleaned)") else { error = "請確認電話號碼。"; return }
                openURL(url) { success in if !success { error = "此裝置無法撥號，請改用可通話的手機。" } }
            }
            Link("開啟官方合約院所查詢", destination: URL(string: PolicyEngine.clinics)!).frame(minHeight: 64)
            Text("地圖搜尋只提供縣市與項目名稱；搜尋結果不保證是公費合約院所，資格與費用請先確認。")
        }
    }
}

struct PolicyDetailView: View {
    let policy: ScreeningPolicy
    let profile: Profile
    @State private var record = false
    @State private var appointment = false
    var body: some View {
        SheetPage(title: policy.title) {
            CareCard {
                Text("補助與適用條件").font(.title2.bold())
                Text(policy.rule)
                Text("安排前請注意").font(.title2.bold())
                Text(policy.preparation)
                Link("查看政策來源", destination: URL(string: policy.source)!).frame(minHeight: 64)
            }
            Notice(text: PolicyEngine.disclaimer)
            CareButton(title: "登錄已完成的檢查", icon: "checkmark.circle") { record = true }
            CareButton(title: "加入健康行程", icon: "calendar.badge.plus", secondary: true) { appointment = true }
            ClinicLinks(title: policy.title, city: profile.city)
        }
        .sheet(isPresented: $record) { RecordForm(profile: profile, selectedPolicy: policy) }
        .sheet(isPresented: $appointment) { AppointmentForm(profile: profile, policy: policy) }
    }
}
