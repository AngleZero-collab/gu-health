import AVFoundation
import Speech
import SwiftUI

@MainActor
final class OfflineSpeechController: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published private(set) var isListening = false
    @Published private(set) var isProcessing = false
    @Published private(set) var status = "按下開始說話後，才會申請權限並收音。"
    @Published private(set) var reply = ""
    private var engine: AVAudioEngine?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var recognition: SFSpeechRecognitionTask?
    private var timeout: Task<Void, Never>?
    private var generation = UUID()
    private var tapped = false
    private let synthesizer = AVSpeechSynthesizer()
    private var currentUtterance: AVSpeechUtterance?

    override init() { super.init(); synthesizer.delegate = self }

    func start() async {
        cancel()
        let token = generation
        isProcessing = true
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-TW")), recognizer.supportsOnDeviceRecognition else {
            fail("這台裝置沒有可用的台灣中文本機辨識，未啟用收音，也不會改用網路辨識。")
            return
        }
        let authorization = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
        guard token == generation else { return }
        guard authorization == .authorized else { fail("未取得語音辨識權限，您仍可直接操作畫面。可至 iPhone 設定調整權限。"); return }
        let microphone = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { continuation.resume(returning: $0) }
        }
        guard token == generation else { return }
        guard microphone else { fail("未取得麥克風權限，未啟用收音。"); return }
        guard recognizer.supportsOnDeviceRecognition, recognizer.isAvailable else {
            fail("本機辨識目前不可用，請確認已安裝台灣中文語音資源；不會改用雲端服務。")
            return
        }
        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.record, mode: .measurement)
            try audio.setActive(true)
            let engine = AVAudioEngine()
            self.engine = engine
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.requiresOnDeviceRecognition = true
            request.shouldReportPartialResults = false
            request.taskHint = .dictation
            self.request = request
            let node = engine.inputNode
            let format = node.outputFormat(forBus: 0)
            guard format.sampleRate > 0, format.channelCount > 0 else { throw HealthError.invalid("沒有可用的麥克風輸入。") }
            node.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak request] buffer, _ in request?.append(buffer) }
            tapped = true
            recognition = recognizer.recognitionTask(with: request) { [weak self] result, error in
                DispatchQueue.main.async {
                    guard let self, self.generation == token else { return }
                    if let result, result.isFinal { self.complete(result.bestTranscription.formattedString) }
                    else if error != nil { self.cancel(); self.status = "這次辨識未完成，您可以重新開始；沒有保存錄音或逐字稿。" }
                }
            }
            engine.prepare(); try engine.start()
            isProcessing = false; isListening = true
            status = "正在收音，請說一句健康問題（最多 40 秒）。"
            setTimeout(seconds: 40, token: token)
        } catch { cancel(); status = "麥克風或本機辨識無法啟動，請稍後再試。" }
    }

    func finishListening() {
        guard isListening else { return }
        stopInput()
        request?.endAudio()
        isListening = false; isProcessing = true; status = "正在使用本機規則整理回覆。"
        setTimeout(seconds: 8, token: generation)
    }

    private func setTimeout(seconds: UInt64, token: UUID) {
        timeout?.cancel()
        timeout = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: seconds * 1_000_000_000) } catch { return }
            guard let self, self.generation == token else { return }
            self.cancel(); self.status = "這次已逾時並停止收音，您可以重新開始。"
        }
    }

    private func complete(_ input: String) {
        let response = LocalHealthGuide.reply(to: input)
        cancel()
        reply = response
        speak(response)
    }

    /// Uses only a voice already listed by the OS; never requests a voice download.
    func speak(_ text: String) {
        synthesizer.stopSpeaking(at: .immediate)
        guard let voice = AVSpeechSynthesisVoice.speechVoices().first(where: { $0.language == "zh-TW" }) else {
            status = "沒有已安裝的台灣中文朗讀語音，請依畫面操作。"; return
        }
        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.playback, mode: .spokenAudio, options: .duckOthers)
            try audio.setActive(true)
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = voice; utterance.rate = 0.43
            currentUtterance = utterance
            synthesizer.speak(utterance); status = "正在朗讀本機基本衛教回覆。"
        } catch { status = "目前無法朗讀，請閱讀畫面內容。"; deactivateAudio() }
    }

    func cancel() {
        generation = UUID()
        timeout?.cancel(); timeout = nil
        stopInput(); engine = nil
        request?.endAudio(); recognition?.cancel()
        recognition = nil; request = nil
        currentUtterance = nil
        synthesizer.stopSpeaking(at: .immediate)
        isListening = false; isProcessing = false; reply = ""
        status = "已停止。語音內容沒有保存。"
        deactivateAudio()
    }

    private func stopInput() {
        engine?.stop()
        if tapped { engine?.inputNode.removeTap(onBus: 0); tapped = false }
    }
    private func deactivateAudio() { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
    private func fail(_ message: String) { cancel(); status = message }
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.currentUtterance === utterance, !self.isListening, !self.isProcessing else { return }
            self.currentUtterance = nil
            self.status = "朗讀完成。"; self.deactivateAudio()
        }
    }
}
