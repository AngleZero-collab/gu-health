import SwiftUI
import AVFoundation

struct VoiceGuideView: View {
    @StateObject private var speech = OfflineSpeechController()
    @Environment(\.scenePhase) private var phase
    var body: some View {
        SheetPage(title: "衛教語音管家") {
            Image(systemName: speech.isListening ? "waveform" : "mic.circle.fill")
                .font(.system(size: 72)).foregroundStyle(CareTheme.green).frame(maxWidth: .infinity).accessibilityHidden(true)
            Notice(text: "只提供三句內基本衛教與就診科別建議，不能診斷。使用台灣中文本機辨識，缺少支援時會停用，不切換到網路辨識。")
            Text(speech.status).accessibilityAddTraits(.updatesFrequently)
            if speech.isListening {
                CareButton(title: "說完了，結束收音", icon: "stop.circle") { speech.finishListening() }
            } else {
                CareButton(title: "開始說話", icon: "mic.fill") { Task { await speech.start() } }
                    .disabled(speech.isProcessing)
            }
            CareButton(title: "取消並清除這次內容", secondary: true) { speech.cancel() }
            if !speech.reply.isEmpty {
                CareCard { Text("這次的衛教回覆").font(.title2.bold()); Text(speech.reply) }
            }
            Text("不保存錄音、逐字稿或對話歷史。關閉面板、進入背景、音訊中斷或逾時會停止並清除 APP 目前內容；系統語音服務可能仍持有暫時副本，無法保證鑑識級記憶體抹除。")
            Text("朗讀使用系統已安裝的台灣中文語音；離線效果仍需在目標 iPhone 的飛航模式驗收。")
        }
        .privacySensitive()
        .onChange(of: phase) { _, phase in if phase == .background { speech.cancel() } }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.interruptionNotification)) { _ in speech.cancel() }
        .onDisappear { speech.cancel() }
    }
}
