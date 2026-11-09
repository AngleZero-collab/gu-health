import AVFoundation
import SwiftUI

final class CameraController: ObservableObject {
    let session = AVCaptureSession()
    @Published private(set) var status = "尚未啟用相機。"
    private let queue = DispatchQueue(label: "com.gucare.health.camera", qos: .userInitiated)
    private var wanted = false // Access only on queue.
    private var configured = false

    func start() {
        queue.async { [weak self] in self?.wanted = true }
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            guard let self else { return }
            self.queue.async {
                guard self.wanted else { return }
                guard granted else { self.publish("未取得相機權限，仍可進行所有觀察。"); return }
                do {
                    if !self.configured {
                        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) else { throw HealthError.invalid("這台裝置沒有可用的前鏡頭。") }
                        let input = try AVCaptureDeviceInput(device: camera)
                        self.session.beginConfiguration()
                        self.session.sessionPreset = .medium
                        guard self.session.canAddInput(input) else { self.session.commitConfiguration(); throw HealthError.invalid("無法啟用前鏡頭。") }
                        self.session.addInput(input)
                        // Preview only: no photo, video, audio, or analysis output is added.
                        self.session.commitConfiguration(); self.configured = true
                    }
                    self.session.startRunning()
                    self.publish("正在顯示前鏡頭即時畫面，不拍照、不錄影、不分析。")
                } catch { self.publish(error.localizedDescription) }
            }
        }
    }
    func stop() {
        queue.async { [weak self] in
            guard let self else { return }
            self.wanted = false
            if self.session.isRunning { self.session.stopRunning() }
            self.publish("相機已停止，可重新啟用。")
        }
    }
    private func publish(_ message: String) { DispatchQueue.main.async { [weak self] in self?.status = message } }
}

private final class PreviewSurface: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> UIView {
        let view = PreviewSurface()
        view.previewLayer.session = session; view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }
    func updateUIView(_ uiView: UIView, context: Context) {}
    static func dismantleUIView(_ uiView: UIView, coordinator: ()) { (uiView as? PreviewSurface)?.previewLayer.session = nil }
}
