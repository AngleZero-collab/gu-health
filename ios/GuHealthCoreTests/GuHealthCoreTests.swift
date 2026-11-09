import Foundation
import XCTest
@testable import GuHealthCore

final class GuHealthCoreTests: XCTestCase {
    private let now = HealthDate.parse("2026-11-09")!
    private func person(age: Int, sex: String = "女") -> Profile {
        var p = Profile(); p.name = "測試家人"; p.birth = "\(2026 - age)-11-09"; p.sex = sex; return p
    }
    private func policy(_ id: String, _ p: Profile) -> ScreeningPolicy { PolicyEngine.policies(for: p, on: now).first { $0.id == id }! }
    private func lab(_ value: String, reference: String = "70–99") -> LabInput {
        var i = LabInput(); i.value = value; i.reference = reference; i.unit = "mg/dL"; return i
    }
    func testStrictDatesAndBirthdayBoundary() {
        XCTAssertNil(HealthDate.parse("2025-02-29")); XCTAssertNil(HealthDate.parse("2026-2-01"))
        XCTAssertNotNil(HealthDate.parse("2024-02-29"))
        var p = person(age: 40); p.birth = "1986-11-10"
        XCTAssertEqual(p.age(on: now), 39)
    }
    func testAdultPolicyAndExceptions() {
        XCTAssertFalse(policy("adult", person(age: 29)).eligible)
        for (age, months) in [(30,60),(39,60),(40,36),(64,36),(65,12)] {
            XCTAssertEqual(policy("adult", person(age: age)).intervalMonths, months)
        }
        var p = person(age: 55); p.indigenous = true
        XCTAssertEqual(policy("adult", p).intervalMonths, 12)
        p = person(age: 35); p.polio = true
        XCTAssertEqual(policy("adult", p).intervalMonths, 12)
    }
    func testColonAgeBoundariesAndFamilyHistory() {
        for age in [39,40,44,75] { XCTAssertFalse(policy("colon", person(age: age)).eligible) }
        for age in [45,74] { XCTAssertTrue(policy("colon", person(age: age)).eligible) }
        var p = person(age: 40); p.familyColon = true
        XCTAssertTrue(policy("colon", p).eligible)
    }
    func testFemalePoliciesAndHPVAges() {
        XCTAssertFalse(policy("breast", person(age: 40, sex: "男")).eligible)
        XCTAssertTrue(policy("breast", person(age: 40)).eligible)
        XCTAssertFalse(policy("breast", person(age: 75)).eligible)
        XCTAssertFalse(policy("cervical", person(age: 24)).eligible)
        XCTAssertEqual(policy("cervical", person(age: 29)).intervalMonths, 36)
        XCTAssertEqual(policy("cervical", person(age: 30)).intervalMonths, 12)
        for age in [35,45,65] { XCTAssertTrue(policy("hpv", person(age: age)).eligible) }
        XCTAssertFalse(policy("hpv", person(age: 46)).eligible)
    }
    func testLungAndOralRiskConditions() {
        var p = person(age: 40); p.familyLung = true
        XCTAssertTrue(policy("lung", p).eligible)
        p.sex = "男"; XCTAssertFalse(policy("lung", p).eligible)
        p = person(age: 50); p.packYears = 20; p.quitYears = 15
        XCTAssertTrue(policy("lung", p).eligible)
        p.quitYears = 16; XCTAssertFalse(policy("lung", p).eligible)
        p.smokes = true; XCTAssertTrue(policy("lung", p).eligible)
        p = person(age: 18); p.indigenous = true; p.betel = true
        XCTAssertTrue(policy("oral", p).eligible)
        p.indigenous = false; XCTAssertFalse(policy("oral", p).eligible)
    }
    func testReviewDateAndHPVCurrentAge() {
        let p = person(age: 65), hpv = policy("hpv", person(age: 65))
        XCTAssertNil(hpv.nextReview(after: now))
        XCTAssertEqual(hpv.status(last: now, profile: p, on: now), "本年齡已登錄")
        XCTAssertEqual(hpv.status(last: HealthDate.parse("2006-11-09"), profile: p, on: now), "待向院所確認・本年齡尚無登錄")
        let last = HealthDate.parse("2024-11-09")!
        XCTAssertEqual(HealthDate.string(policy("colon", p).nextReview(after: last)!), "2026-11-09")
    }
    func testRangeAndOneSidedBoundaryComparisons() throws {
        for (value, reference, expected) in [
            ("69.999", "70–99", LabStatus.low), ("70", "70–99", .within),
            ("99", "70–99", .within), ("99.001", "70–99", .high),
            ("100", "<100", .high), ("100", "<=100", .within),
            ("60", ">60", .low), ("60", "≥60", .within),
            ("-2", "-3--1", .within), ("９９", "７０～９９", .within),
            ("9", "", .unknown)
        ] { XCTAssertEqual(try ReportExplanation.parse(lab(value, reference: reference)).status, expected, "\(value) \(reference)") }
    }
    func testInvalidLabInputFailsClosed() {
        for reference in ["99–70", "70 / 99", "正常", "<1e3", "<NaN"] {
            XCTAssertThrowsError(try ReportExplanation.parse(lab("95", reference: reference)))
        }
        for value in ["NaN", "1e3", "95 99", "", "1,000"] { XCTAssertThrowsError(try ReportExplanation.parse(lab(value))) }
        var i = lab("95"); i.unit = ""
        XCTAssertThrowsError(try ReportExplanation.parse(i))
        XCTAssertThrowsError(try ReportExplanation.explain([]))
        XCTAssertThrowsError(try ReportExplanation.explain(Array(repeating: lab("95"), count: 13)))
    }
    func testReportOrderingAndSavedSummaryExcludeRawFields() throws {
        let lines = try ReportExplanation.explain([lab("95.12345"), lab("123.456789"), lab("80", reference: "")])
        XCTAssertEqual(lines.map(\.status), [.high, .unknown, .within])
        let text = ReportExplanation.savedText(lines)
        XCTAssertFalse(text.contains("123.456789")); XCTAssertFalse(text.contains("95.12345"))
        XCTAssertFalse(text.contains("mg/dL")); XCTAssertFalse(text.contains("70–99"))
        XCTAssertTrue(text.contains("偏高"))
    }
    func testRecordIsolationCascadeAndLatestScreening() {
        let p = person(age: 65), q = person(age: 40)
        var s = HealthSnapshot(); s.profiles = [p,q]; s.selectedProfileID = p.id
        s.records = [HealthRecord(profileID: p.id, kind: .screening, key: "colon", title: "檢查", date: "2026-01-01"),
                     HealthRecord(profileID: q.id, kind: .note, title: "另一位家人"),
                     HealthRecord(profileID: p.id, kind: .screening, key: "colon", title: "未來誤填", date: "2027-01-01")]
        s.appointments = [Appointment(profileID: p.id, title: "行程", at: now)]
        XCTAssertEqual(s.records(for: q.id).count, 1)
        XCTAssertEqual(HealthDate.string(s.lastScreening(profileID: p.id, key: "colon", on: now)!), "2026-01-01")
        s.deleteProfile(p.id)
        XCTAssertEqual(s.records.count, 1); XCTAssertTrue(s.appointments.isEmpty); XCTAssertEqual(s.selectedProfileID, q.id)
    }
    func testSaveReloadAndCorruptFilePreservation() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let repository = HealthRepository(directory: dir)
        XCTAssertEqual(try repository.load(), HealthSnapshot())
        var s = HealthSnapshot(); s.profiles = [person(age: 65)]
        try repository.save(s)
        XCTAssertEqual(try repository.load(), s)
        let corrupt = Data("corrupt-health-data".utf8)
        try corrupt.write(to: repository.fileURL)
        XCTAssertThrowsError(try repository.load())
        XCTAssertEqual(try Data(contentsOf: repository.fileURL), corrupt)
    }
    func testUnsupportedStoreVersionIsNotOverwritten() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let repository = HealthRepository(directory: dir)
        var s = HealthSnapshot(); s.version = 999
        try repository.save(s)
        XCTAssertThrowsError(try repository.load())
    }
    func testBackupRoundTripAndPrivacyExclusions() throws {
        let p = person(age: 65)
        var s = HealthSnapshot(); s.profiles = [p]
        s.records = [HealthRecord(profileID: p.id, kind: .bloodPressure, title: "血壓 120/80", date: "2026-11-09"),
                     HealthRecord(profileID: p.id, kind: .reportSummary, title: "機密摘要", note: "不應出現在備份")]
        var visit = Appointment(profileID: p.id, title: "回診", at: now.addingTimeInterval(3600)); visit.notificationEnabled = true
        s.appointments = [visit]
        let data = try BackupCodec.export(s, now: now)
        let text = String(decoding: data, as: UTF8.self)
        XCTAssertTrue(text.contains("\"kind\" : \"bp\"")); XCTAssertFalse(text.contains("機密摘要"))
        XCTAssertFalse(s.summary(for: p, now: now).contains("機密摘要"))
        let preview = try BackupCodec.preview(data, now: now)
        XCTAssertEqual(preview.profiles.first?.name, p.name)
        XCTAssertNotEqual(preview.profiles.first?.id, p.id)
        XCTAssertEqual(preview.records.first?.kind, .bloodPressure)
        XCTAssertEqual(preview.records.first?.profileID, preview.profiles.first?.id)
        XCTAssertFalse(preview.appointments[0].notificationEnabled)
        XCTAssertEqual(preview.appointments[0].at, visit.at)
    }
    func testBackupRejectsUnsupportedMalformedAndFutureRecord() throws {
        XCTAssertThrowsError(try BackupCodec.preview(Data("{}".utf8), now: now))
        XCTAssertThrowsError(try BackupCodec.preview(Data(repeating: 32, count: 10_000_001), now: now))
        var s = HealthSnapshot(); let p = person(age: 65); s.profiles = [p]
        s.records = [HealthRecord(profileID: p.id, kind: .note, title: "未來", date: "2099-01-01")]
        XCTAssertThrowsError(try BackupCodec.preview(BackupCodec.export(s, now: now), now: now))
    }
    func testActualAndroidV1BackupContractAndLegacyRecords() throws {
        let json = #"""
        {"format":"guhealth-backup","version":1,"exportedAt":"2026-11-09","profiles":[
          {"id":7,"name":"林媽媽（測試）","birth":"1954-03-15","sex":"女","city":"台北市",
           "indigenous":false,"polio":false,"familyColon":false,"familyLung":false,"smokes":false,"betel":false,"packYears":0,"quitYears":-1,
           "records":[{"kind":"bp","key":"","title":"血壓 120/80","date":"2026-11-01","note":"測試","flag":false},
                      {"kind":"vision_e","key":"","title":"舊 E 字練習","date":"2026-11-01","note":"舊紀錄保留","flag":false},
                      {"kind":"report_summary","key":"","title":"不匯入此摘要","date":"2026-11-01","note":"機密","flag":false}],
           "appointments":[{"key":"colon","title":"回診","at":1800000000000,"place":"測試院所","notified":false}]}]}
        """#
        let preview = try BackupCodec.preview(Data(json.utf8), now: now)
        XCTAssertEqual(preview.profiles[0].name, "林媽媽（測試）")
        XCTAssertEqual(preview.records.count, 2)
        XCTAssertTrue(preview.records.contains { $0.title == "舊 E 字練習" && $0.note == "舊紀錄保留" })
        XCTAssertEqual(preview.appointments[0].at.timeIntervalSince1970, 1_800_000_000)
        XCTAssertFalse(preview.appointments[0].notificationEnabled)
    }
    func testObservationRequiresBothEyesAndDoesNotOverwriteCompletion() {
        var session = ObservationSession()
        XCTAssertNil(session.note)
        session.answer(changed: false); XCTAssertNil(session.note); XCTAssertEqual(session.eye, .right)
        session.answer(changed: true); XCTAssertTrue(session.needsAttention); XCTAssertNotNil(session.note)
        session.answer(changed: false); XCTAssertEqual(session.rightChanged, true)
    }
    func testGuideEmergencyTakesPriorityOverOtherTopics() {
        let reply = LocalHealthGuide.reply(to: "我的眼睛不舒服而且胸痛")
        XCTAssertTrue(reply.contains("一一九")); XCTAssertFalse(reply.contains("眼科詢問"))
        XCTAssertTrue(LocalHealthGuide.reply(to: "想自行停藥").contains("不要自行停藥"))
        XCTAssertTrue(LocalHealthGuide.reply(to: "").contains("不能進行醫療診斷"))
    }
}
