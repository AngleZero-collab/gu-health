import SwiftUI

struct ObservationHomeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var observing = false
    @State private var camera = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("居家視覺觀察").font(.largeTitle.bold())
                if let profile = store.profile {
                    Text("正在查看：\(profile.name)")
                    CareCard {
                        Image(systemName: "eye").font(.system(size: 56)).foregroundStyle(CareTheme.green).accessibilityHidden(true)
                        Text("一次一眼，留意日常的變化").font(.title2.bold())
                        Text("配戴平常閱讀用的眼鏡，在明亮的地方坐好。手機放在舒適閱讀距離，輕遮另一眼，不要壓眼球。")
                        CareButton(title: "開始雙眼方格觀察", icon: "eye") { observing = true }
                        CareButton(title: "前鏡頭輔助對位", icon: "camera", secondary: true) { camera = true }
                    }
                    Notice(text: "這個數位方格未經臨床校準，僅記錄主觀感受，不能量測視力、診斷或排除疾病。")
                    Notice(text: "若突然視力下降、視線扭曲或出現黑影，請儘速就醫，不要等待下一次觀察。", warning: true)
                    ClinicLinks(title: "眼科", city: profile.city)
                } else { Notice(text: "請先在首頁建立健康檔案。") }
            }.padding(20)
        }
        .fullScreenCover(isPresented: $observing) {
            if let profile = store.profile { ObservationFlowView(profile: profile) }
        }
        .sheet(isPresented: $camera) { CameraView() }
    }
}

struct ObservationFlowView: View {
    let profile: Profile
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @StateObject private var speech = OfflineSpeechController()
    @State private var session = ObservationSession()
    @State private var ready = false
    @State private var leaving = false
    @State private var error = ""
    private var instruction: String {
        session.eye == .left ? "請遮住右眼\n用左眼看中央黑點" : "請遮住左眼\n用右眼看中央黑點"
    }
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if !ready {
                    Text("開始前，準備一下").font(.largeTitle.bold())
                    Text("配戴平常閱讀用的眼鏡。手機放在舒適的閱讀距離，一次觀察一眼，輕遮另一眼，不要壓眼球。")
                    Notice(text: "方格未經臨床校準，不能診斷或排除疾病。觀察左右眼後，您可以決定是否保存。", warning: true)
                    CareButton(title: "我已了解，開始") { ready = true; speech.speak(instruction) }
                } else if session.eye != .complete {
                    Text(instruction).font(.title.bold()).multilineTextAlignment(.center)
                    ObservationGrid().aspectRatio(1, contentMode: .fit).frame(maxWidth: 500)
                    Text("線條有沒有變形、缺角或看不清楚？").font(.title2.bold())
                    CareButton(title: "沒有明顯變化") { answer(false) }.frame(minHeight: 80)
                    CareButton(title: "有變形、缺角或看不清楚", secondary: true) { answer(true) }.frame(minHeight: 80)
                } else {
                    Text("這次的觀察").font(.largeTitle.bold())
                    Text(session.note ?? "").font(.title2)
                    Notice(text: session.needsAttention ? "您回報有變化或看不清楚，請聯絡眼科；若是突然出現，請儘速就醫。" : "沒有明顯變化仍不能排除疾病，請依眼科醫師建議持續追蹤。", warning: session.needsAttention)
                    CareButton(title: "儲存至\(profile.name)的紀錄", icon: "checkmark") {
                        guard let note = session.note else { return }
                        let record = HealthRecord(profileID: profile.id, kind: .vision, key: "amsler", title: "方格視覺自我觀察", note: note, flag: session.needsAttention)
                        if store.addRecord(record) { dismiss() } else { error = store.message ?? "儲存失敗。" }
                    }
                    CareButton(title: "不儲存，返回", secondary: true) { dismiss() }
                }
                if !error.isEmpty { Notice(text: error, warning: true) }
                CareButton(title: "離開這次觀察", secondary: true) { speech.cancel(); leaving = true }
            }.padding(24).frame(maxWidth: 620)
                .frame(maxWidth: .infinity)
        }.background(CareTheme.canvas).foregroundStyle(CareTheme.ink).font(.title3)
            .interactiveDismissDisabled().privateWhenInactive()
            .alert("離開這次觀察？", isPresented: $leaving) {
                Button("繼續觀察", role: .cancel) { if ready && session.eye != .complete { speech.speak(instruction) } }
                Button("離開且不儲存", role: .destructive) { dismiss() }
            } message: { Text("尚未儲存的觀察結果會取消。") }
            .onChange(of: phase) { _, phase in if phase != .active { speech.cancel() } }
            .onDisappear { speech.cancel() }
    }
    private func answer(_ changed: Bool) {
        speech.cancel(); session.answer(changed: changed)
        if session.eye != .complete { speech.speak(instruction) }
    }
}

private struct ObservationGrid: View {
    var body: some View {
        Canvas { context, size in
            let side = min(size.width, size.height), inset: CGFloat = 1
            var path = Path()
            for i in 0...20 {
                let point = inset + (side - 2 * inset) * CGFloat(i) / 20
                path.move(to: CGPoint(x: point, y: inset)); path.addLine(to: CGPoint(x: point, y: side - inset))
                path.move(to: CGPoint(x: inset, y: point)); path.addLine(to: CGPoint(x: side - inset, y: point))
            }
            context.stroke(path, with: .color(.black), lineWidth: 1)
            context.fill(Path(ellipseIn: CGRect(x: side / 2 - 5, y: side / 2 - 5, width: 10, height: 10)), with: .color(.black))
        }.background(.white).accessibilityLabel("中央黑點與方格，請以目前觀察的眼睛注視黑點，留意線條變化。")
    }
}
