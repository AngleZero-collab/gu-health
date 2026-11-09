import SwiftUI

struct CameraView: View {
    @StateObject private var camera = CameraController()
    @Environment(\.scenePhase) private var phase
    var body: some View {
        SheetPage(title: "前鏡頭輔助對位") {
            Notice(text: "只顯示即時畫面，協助確認自己的位置。不拍照、不儲存、不分析、不上傳；也不會測量眼睛距離。您可以不使用相機完成觀察。")
            CameraPreview(session: camera.session).frame(height: 360).clipShape(RoundedRectangle(cornerRadius: 24))
                .accessibilityLabel("前鏡頭即時預覽")
            Text(camera.status)
            CareButton(title: "啟用前鏡頭", icon: "camera") { camera.start() }
            CareButton(title: "停止相機", secondary: true) { camera.stop() }
        }
        .onChange(of: phase) { _, phase in if phase == .background { camera.stop() } }
        .onDisappear { camera.stop() }
    }
}
