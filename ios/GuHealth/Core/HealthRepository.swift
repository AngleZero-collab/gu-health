import Foundation

/// The only persistent store. Voice input and report input never enter this type.
public final class HealthRepository {
    public let fileURL: URL
    public init(directory: URL) { fileURL = directory.appendingPathComponent("health-v1.json") }

    public func load() throws -> HealthSnapshot {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return HealthSnapshot() }
        let data = try Data(contentsOf: fileURL)
        let snapshot = try JSONDecoder().decode(HealthSnapshot.self, from: data)
        guard snapshot.version == 1 else { throw HealthError.invalid("資料版本較新，請先更新顧健康；原有資料未修改。") }
        let ids = Set(snapshot.profiles.map(\.id))
        guard ids.count == snapshot.profiles.count,
              snapshot.records.allSatisfy({ ids.contains($0.profileID) }),
              snapshot.appointments.allSatisfy({ ids.contains($0.profileID) }) else {
            throw HealthError.invalid("資料關聯不完整，原有檔案未修改。")
        }
        return snapshot
    }

    public func save(_ snapshot: HealthSnapshot) throws {
        let directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try protect(directory)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(snapshot)
        #if os(iOS)
        try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
        #else
        try data.write(to: fileURL, options: .atomic)
        #endif
        // Atomic replacement can reset resource values: reapply on every successful save.
        try protect(fileURL)
    }

    private func protect(_ url: URL) throws {
        #if os(iOS)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: url.path)
        #endif
        var resource = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try resource.setResourceValues(values)
    }
}
