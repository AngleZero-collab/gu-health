import SwiftUI
import UniformTypeIdentifiers

enum CareTheme {
    static let green = Color(red: 0.08, green: 0.31, blue: 0.25)
    static let canvas = Color(red: 0.96, green: 0.97, blue: 0.94)
    static let mint = Color(red: 0.88, green: 0.94, blue: 0.89)
    static let orange = Color(red: 0.60, green: 0.25, blue: 0.08)
    static let peach = Color(red: 1.0, green: 0.93, blue: 0.85)
    static let ink = Color(red: 0.12, green: 0.20, blue: 0.17)
}

struct CareButton: View {
    let title: String
    var icon: String? = nil
    var secondary = false
    var destructive = false
    let action: () -> Void
    @Environment(\.isEnabled) private var enabled
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Image(systemName: icon).accessibilityHidden(true) }
                Text(title).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            }
            .font(.title3.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 64)
            .padding(.horizontal, 16).padding(.vertical, 4)
            .foregroundStyle(secondary ? (destructive ? CareTheme.orange : CareTheme.green) : .white)
            .background(secondary ? Color.white : (destructive ? CareTheme.orange : CareTheme.green))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(secondary ? CareTheme.green.opacity(0.25) : .clear))
            .opacity(enabled ? 1 : 0.45)
        }.buttonStyle(.plain)
    }
}

struct CareCard<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 16) { content }
            .frame(maxWidth: .infinity, alignment: .leading).padding(20)
            .background(.white).clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

struct Notice: View {
    let text: String
    var warning = false
    var body: some View {
        Text(text).font(.title3).fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading).padding(18)
            .foregroundStyle(warning ? CareTheme.orange : CareTheme.green)
            .background(warning ? CareTheme.peach : CareTheme.mint)
            .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct CareField: View {
    let title: String
    @Binding var text: String
    var placeholder = ""
    var keyboard: UIKeyboardType = .default
    var multiline = false
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.title3.weight(.semibold))
            if multiline {
                TextField(placeholder, text: $text, axis: .vertical).lineLimit(3...6).fieldStyle()
            } else {
                TextField(placeholder, text: $text).keyboardType(keyboard).fieldStyle()
            }
        }
    }
}

extension View {
    func fieldStyle() -> some View {
        self.font(.title3).padding(16).frame(minHeight: 64).background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(CareTheme.green.opacity(0.3)))
            .autocorrectionDisabled().textInputAutocapitalization(.never)
    }
    func privateWhenInactive() -> some View { modifier(PrivacyCover()) }
}

private struct PrivacyCover: ViewModifier {
    @Environment(\.scenePhase) private var phase
    func body(content: Content) -> some View {
        content.overlay {
            if phase != .active {
                CareTheme.canvas.ignoresSafeArea().overlay {
                    Label("顧健康", systemImage: "heart.fill").font(.largeTitle.bold()).foregroundStyle(CareTheme.green)
                }.accessibilityHidden(true)
            }
        }
    }
}

struct SheetPage<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading, spacing: 20) { content }.padding(20) }
                .background(CareTheme.canvas).foregroundStyle(CareTheme.ink).font(.title3)
                .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("關閉") { dismiss() }.font(.title3) } }
                .scrollDismissesKeyboard(.interactively)
        }.privateWhenInactive()
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data = Data()) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct ShareSheet: UIViewControllerRepresentable {
    let text: String
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: [text], applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
