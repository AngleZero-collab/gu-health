import Foundation

public enum HealthDate {
    public static var calendar: Calendar {
        var result = Calendar(identifier: .gregorian)
        result.timeZone = TimeZone(identifier: "Asia/Taipei")!
        return result
    }

    public static func parse(_ text: String) -> Date? {
        guard text.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil else { return nil }
        let parts = text.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3,
              let date = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])),
              string(date) == text else { return nil }
        return date
    }

    public static func string(_ date: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    public static func age(birth: String, on date: Date) -> Int {
        guard let birthDate = parse(birth) else { return 0 }
        return calendar.dateComponents([.year], from: birthDate, to: calendar.startOfDay(for: date)).year ?? 0
    }
}

public struct Profile: Codable, Identifiable, Equatable, Sendable {
    public var id = UUID()
    public var name = ""
    public var birth = "1954-03-15"
    public var sex = "女"
    public var city = "臺北市"
    public var indigenous = false
    public var polio = false
    public var familyColon = false
    public var familyLung = false
    public var smokes = false
    public var betel = false
    public var packYears = 0
    public var quitYears = -1
    public init() {}
    public func age(on date: Date = Date()) -> Int { HealthDate.age(birth: birth, on: date) }
}

public enum RecordKind: String, Codable, CaseIterable, Sendable {
    case screening, note, bloodPressure, vision, reportSummary
}

public struct HealthRecord: Codable, Identifiable, Equatable, Sendable {
    public var id = UUID()
    public var profileID: UUID
    public var kind: RecordKind
    public var key: String
    public var title: String
    public var date: String
    public var note: String
    public var flag: Bool
    public init(profileID: UUID, kind: RecordKind, key: String = "", title: String,
                date: String = HealthDate.string(Date()), note: String = "", flag: Bool = false) {
        self.profileID = profileID; self.kind = kind; self.key = key
        self.title = title; self.date = date; self.note = note; self.flag = flag
    }
}

public struct Appointment: Codable, Identifiable, Equatable, Sendable {
    public var id = UUID()
    public var profileID: UUID
    public var key: String
    public var title: String
    public var at: Date
    public var place: String
    public var notificationEnabled = false
    public init(profileID: UUID, key: String = "", title: String, at: Date, place: String = "") {
        self.profileID = profileID; self.key = key; self.title = title; self.at = at; self.place = place
    }
}

public struct HealthSnapshot: Codable, Equatable, Sendable {
    public var version = 1
    public var profiles: [Profile] = []
    public var records: [HealthRecord] = []
    public var appointments: [Appointment] = []
    public var selectedProfileID: UUID?
    public var largeText = false
    public init() {}

    public func records(for profileID: UUID) -> [HealthRecord] {
        records.filter { $0.profileID == profileID }.sorted { $0.date > $1.date }
    }

    public func lastScreening(profileID: UUID, key: String, on now: Date) -> Date? {
        records(for: profileID).filter { $0.kind == .screening && $0.key == key }
            .compactMap { HealthDate.parse($0.date) }.filter { $0 <= now }.max()
    }

    public mutating func deleteProfile(_ id: UUID) {
        profiles.removeAll { $0.id == id }
        records.removeAll { $0.profileID == id }
        appointments.removeAll { $0.profileID == id }
        if selectedProfileID == id { selectedProfileID = profiles.first?.id }
    }

    public func summary(for profile: Profile, now: Date = Date()) -> String {
        let lines = records(for: profile.id).filter { $0.kind != .reportSummary }
            .map { "\($0.date)｜\($0.title)\n\($0.note)" }.joined(separator: "\n\n")
        return "顧健康｜個人健康摘要\n\(profile.name)・\(profile.age(on: now)) 歲\n產生日期：\(HealthDate.string(now))\n\n\(lines)\n\n自行登錄資料，非醫療院所正式報告；居家觀察不構成診斷。"
    }
}

public enum HealthError: LocalizedError {
    case invalid(String)
    public var errorDescription: String? {
        switch self { case .invalid(let message): return message }
    }
}

public struct ObservationSession: Equatable {
    public enum Eye { case left, right, complete }
    public private(set) var eye: Eye = .left
    public private(set) var leftChanged: Bool?
    public private(set) var rightChanged: Bool?
    public init() {}
    public mutating func answer(changed: Bool) {
        switch eye {
        case .left: leftChanged = changed; eye = .right
        case .right: rightChanged = changed; eye = .complete
        case .complete: break
        }
    }
    public var needsAttention: Bool { leftChanged == true || rightChanged == true }
    public var note: String? {
        guard let leftChanged, let rightChanged else { return nil }
        return "左眼：\(leftChanged ? "有變形／缺角" : "沒有明顯變化")；右眼：\(rightChanged ? "有變形／缺角" : "沒有明顯變化")。居家自我觀察，非視力檢測或診斷。"
    }
}
