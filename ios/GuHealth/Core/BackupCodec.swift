import Foundation

/// Android's guhealth-backup v1 interchange format. Report summaries are deliberately excluded.
public enum BackupCodec {
    private struct Backup: Codable {
        var format = "guhealth-backup"
        var version = 1
        var exportedAt: String
        var profiles: [Person]
    }
    private struct Person: Codable {
        var id: Int
        var name, birth, sex, city: String
        var indigenous, polio, familyColon, familyLung, smokes, betel: Bool
        var packYears, quitYears: Int
        var records: [Entry]
        var appointments: [Visit]
    }
    private struct Entry: Codable {
        var kind, key, title, date, note: String
        var flag: Bool
    }
    private struct Visit: Codable {
        var key, title: String
        var at: Double
        var place: String
        var notified: Bool
    }

    public static func export(_ snapshot: HealthSnapshot, now: Date = Date()) throws -> Data {
        let people = snapshot.profiles.enumerated().map { index, p in
            Person(id: index + 1, name: p.name, birth: p.birth, sex: p.sex, city: p.city,
                   indigenous: p.indigenous, polio: p.polio, familyColon: p.familyColon,
                   familyLung: p.familyLung, smokes: p.smokes, betel: p.betel,
                   packYears: p.packYears, quitYears: p.quitYears,
                   records: snapshot.records(for: p.id).filter { $0.kind != .reportSummary }.map {
                       Entry(kind: $0.kind == .bloodPressure ? "bp" : $0.kind.rawValue, key: $0.key,
                             title: $0.title, date: $0.date, note: $0.note, flag: $0.flag)
                   }, appointments: snapshot.appointments.filter { $0.profileID == p.id }.map {
                       Visit(key: $0.key, title: $0.title, at: $0.at.timeIntervalSince1970 * 1000, place: $0.place, notified: false)
                   })
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(Backup(exportedAt: HealthDate.string(now), profiles: people))
    }

    /// Returns an isolated preview; the caller must explicitly confirm before merging it.
    public static func preview(_ data: Data, now: Date = Date()) throws -> HealthSnapshot {
        guard data.count <= 10_000_000 else { throw HealthError.invalid("備份超過 10 MB，請確認檔案來源。") }
        let backup: Backup
        do { backup = try JSONDecoder().decode(Backup.self, from: data) }
        catch { throw HealthError.invalid("無法讀取，請選擇顧健康匯出的 JSON 備份。") }
        guard backup.format == "guhealth-backup", backup.version == 1,
              backup.profiles.count <= 100, Set(backup.profiles.map(\.id)).count == backup.profiles.count,
              backup.profiles.reduce(0, { $0 + $1.records.count + $1.appointments.count }) <= 20_000 else {
            throw HealthError.invalid("不支援此備份格式、版本或資料數量；目前資料未修改。")
        }
        var result = HealthSnapshot()
        for person in backup.profiles {
            guard !person.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  person.name.count <= 100, person.city.count <= 100,
                  let birth = HealthDate.parse(person.birth), birth <= now,
                  (0...130).contains(HealthDate.age(birth: person.birth, on: now)),
                  ["男", "女"].contains(person.sex), (0...300).contains(person.packYears),
                  (-1...130).contains(person.quitYears) else { throw HealthError.invalid("備份中的家人檔案格式不完整。") }
            var p = Profile()
            p.name = person.name; p.birth = person.birth; p.sex = person.sex; p.city = person.city
            p.indigenous = person.indigenous; p.polio = person.polio
            p.familyColon = person.familyColon; p.familyLung = person.familyLung
            p.smokes = person.smokes; p.betel = person.betel
            p.packYears = person.packYears; p.quitYears = person.quitYears
            result.profiles.append(p)
            for entry in person.records {
                if entry.kind == "report_summary" || entry.kind == "reportSummary" { continue }
                guard let date = HealthDate.parse(entry.date), date <= now,
                      entry.title.count <= 300, entry.note.count <= 20_000, entry.key.count <= 100 else {
                    throw HealthError.invalid("備份中的紀錄日期或內容格式不正確。")
                }
                let kind = entry.kind == "bp" ? RecordKind.bloodPressure : RecordKind(rawValue: entry.kind) ?? .note
                result.records.append(HealthRecord(profileID: p.id, kind: kind, key: entry.key,
                                                   title: entry.title, date: entry.date, note: entry.note, flag: entry.flag))
            }
            for visit in person.appointments {
                guard visit.at.isFinite, (0...32_503_680_000_000).contains(visit.at),
                      visit.title.count <= 300, visit.place.count <= 1_000, visit.key.count <= 100 else {
                    throw HealthError.invalid("備份中的行程格式不正確。")
                }
                // Importing data never silently requests notification access or re-enables alerts.
                result.appointments.append(Appointment(profileID: p.id, key: visit.key, title: visit.title,
                                                       at: Date(timeIntervalSince1970: visit.at / 1000), place: visit.place))
            }
        }
        result.selectedProfileID = result.profiles.first?.id
        return result
    }
}
