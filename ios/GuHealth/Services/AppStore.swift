import SwiftUI
import UserNotifications

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var snapshot = HealthSnapshot()
    @Published var message: String?
    private(set) var loadFailure: String?
    private let repository: HealthRepository

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var directoryName = "GuHealth"
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-ui-testing") { directoryName = "GuHealthUITests" }
        #endif
        repository = HealthRepository(directory: support.appendingPathComponent(directoryName, isDirectory: true))
        do { snapshot = try repository.load() }
        catch {
            // Never replace an unreadable file with a fresh, empty database.
            loadFailure = "目前無法讀取資料，原檔案已保留。請確認 iPhone 已解鎖並重新開啟 APP，或聯絡開發者。\n\(error.localizedDescription)"
        }
    }

    var profile: Profile? {
        snapshot.profiles.first { $0.id == snapshot.selectedProfileID } ?? snapshot.profiles.first
    }

    @discardableResult
    func commit(_ change: (inout HealthSnapshot) -> Void) -> Bool {
        guard loadFailure == nil else { message = loadFailure; return false }
        var next = snapshot
        change(&next)
        do { try repository.save(next); snapshot = next; return true }
        catch { message = "儲存失敗，請確認儲存空間與裝置解鎖狀態，再試一次。"; return false }
    }

    func saveProfile(_ profile: Profile) -> Bool {
        commit { state in
            if let index = state.profiles.firstIndex(where: { $0.id == profile.id }) { state.profiles[index] = profile }
            else { state.profiles.append(profile) }
            state.selectedProfileID = profile.id
        }
    }

    func addDemo() {
        var p = Profile(); p.name = "林媽媽（示範）"
        if saveProfile(p) {
            _ = addRecord(HealthRecord(profileID: p.id, kind: .note, title: "陪媽媽一起散步（示範）", note: "示範資料：今天到公園走了 20 分鐘。"))
        }
    }

    func deleteProfile(_ id: UUID) {
        let removed = snapshot.appointments.filter { $0.profileID == id }.map(\.id)
        if commit({ $0.deleteProfile(id) }) { ReminderService.cancel(removed) }
    }

    func addRecord(_ record: HealthRecord) -> Bool {
        guard snapshot.profiles.contains(where: { $0.id == record.profileID }) else { message = "家人檔案已不存在。"; return false }
        return commit { $0.records.append(record) }
    }

    func merge(_ preview: HealthSnapshot) -> Bool {
        commit {
            $0.profiles += preview.profiles
            $0.records += preview.records
            $0.appointments += preview.appointments
            if $0.selectedProfileID == nil { $0.selectedProfileID = preview.profiles.first?.id }
        }
    }

    func enableReminder(_ id: UUID) async -> String {
        guard let appointment = snapshot.appointments.first(where: { $0.id == id }), appointment.at > Date() else {
            return "此行程時間已過，無法設定提醒。"
        }
        let enabledCount = snapshot.appointments.filter { $0.notificationEnabled && $0.at > Date() && $0.id != id }.count
        guard enabledCount < 60 else { return "已設定 60 個未來提醒，請先刪除不需要的行程。" }
        do {
            try await ReminderService.schedule(appointment)
            // The profile/appointment may have been deleted while the system dialog was open.
            guard snapshot.appointments.contains(where: { $0.id == id }) else {
                ReminderService.cancel([id]); return "行程已移除，提醒已取消。"
            }
            if commit({ state in
                if let i = state.appointments.firstIndex(where: { $0.id == id }) { state.appointments[i].notificationEnabled = true }
            }) { return "提醒已設定，實際顯示時間仍受通知與專注模式設定影響。" }
            ReminderService.cancel([id])
            return message ?? "提醒設定未完成。"
        } catch { return "行程已保留，但提醒尚未啟用。\(error.localizedDescription)" }
    }

    func deleteAppointment(_ id: UUID) {
        if commit({ $0.appointments.removeAll { $0.id == id } }) { ReminderService.cancel([id]) }
    }
}

enum ReminderService {
    static func identifier(_ id: UUID) -> String { "guhealth.\(id.uuidString)" }
    static func schedule(_ appointment: Appointment) async throws {
        let center = UNUserNotificationCenter.current()
        guard try await center.requestAuthorization(options: [.alert, .sound, .badge]) else {
            throw HealthError.invalid("請到 iPhone「設定 → 通知 → 顧健康」開啟權限。")
        }
        guard appointment.at > Date() else { throw HealthError.invalid("提醒時間已過，請重新安排。") }
        let content = UNMutableNotificationContent()
        content.title = "顧健康｜行程提醒"
        content.body = "您有一項自行登錄的健康行程，請開啟顧健康查看。"
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, appointment.at.timeIntervalSinceNow), repeats: false)
        try await center.add(UNNotificationRequest(identifier: identifier(appointment.id), content: content, trigger: trigger))
    }
    static func cancel(_ ids: [UUID]) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ids.map(identifier))
        center.removeDeliveredNotifications(withIdentifiers: ids.map(identifier))
    }
}
