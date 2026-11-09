import Foundation

public enum LabItem: String, CaseIterable, Identifiable, Sendable {
    case glucose, a1c, cholesterol, ldl, hdl, triglycerides, alt, ast, creatinine, egfr, custom
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .glucose: return "空腹血糖"
        case .a1c: return "糖化血色素（HbA1c）"
        case .cholesterol: return "總膽固醇"
        case .ldl: return "低密度膽固醇（LDL）"
        case .hdl: return "高密度膽固醇（HDL）"
        case .triglycerides: return "三酸甘油酯"
        case .alt: return "丙胺酸轉胺酶（ALT／GPT）"
        case .ast: return "天門冬胺酸轉胺酶（AST／GOT）"
        case .creatinine: return "肌酸酐"
        case .egfr: return "估算腎絲球過濾率（eGFR）"
        case .custom: return "自訂項目"
        }
    }
    public var meaning: String {
        switch self {
        case .glucose: return "記錄這次空腹抽血時的血糖。"
        case .a1c: return "反映過去約三個月的平均血糖。"
        case .cholesterol: return "量的是血液中的總膽固醇。"
        case .ldl: return "量的是血液中的低密度脂蛋白膽固醇。"
        case .hdl: return "量的是血液中的高密度脂蛋白膽固醇。"
        case .triglycerides: return "量的是血液中一種叫三酸甘油酯的脂肪。"
        case .alt: return "這是一種常用來評估肝臟的酵素。"
        case .ast: return "這種酵素在肝臟、肌肉等部位都找得到。"
        case .creatinine: return "這是身體代謝產生的物質，常用來協助評估腎功能。"
        case .egfr: return "用來估算腎臟過濾血液的能力。"
        case .custom: return "請由醫事人員說明此項目的意義。"
        }
    }
}

public enum LabStatus: String, Sendable {
    case high = "偏高", low = "偏低", unknown = "無法比對", within = "符合所填參考值"
    public var outside: Bool { self == .high || self == .low }
}

public struct LabInput: Identifiable, Equatable, Sendable {
    public var id = UUID()
    public var item: LabItem = .glucose
    public var name = ""
    public var value = ""
    public var unit = ""
    public var reference = ""
    public init() {}
}

public struct LabLine: Identifiable, Sendable {
    public let id: UUID
    public let item: LabItem
    public let name, value, unit, reference: String
    public let status: LabStatus
}

public enum ReportExplanation {
    public static let maxItems = 12
    private static let decimal = #"[+-]?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)"#
    private static func normalized(_ text: String) -> String {
        text.precomposedStringWithCompatibilityMapping.replacingOccurrences(of: "−", with: "-")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private static func captures(_ pattern: String, _ text: String) -> [String]? {
        guard let regex = try? NSRegularExpression(pattern: "^(?:\(pattern))$"),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { return nil }
        return (1..<match.numberOfRanges).compactMap { Range(match.range(at: $0), in: text).map { String(text[$0]) } }
    }
    private static func number(_ text: String) throws -> Decimal {
        guard text.count <= 24, captures(decimal, text) != nil,
              let value = Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")) else {
            throw HealthError.invalid("請輸入一個數字，例如 95 或 5.8；不接受多個數值或科學記號。")
        }
        return value
    }
    private static func field(_ value: String, limit: Int, message: String) throws -> String {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, text.count <= limit,
              !text.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) || $0.value == 0x2028 || $0.value == 0x2029 }) else {
            throw HealthError.invalid(message)
        }
        return text
    }
    public static func parse(_ input: LabInput) throws -> LabLine {
        let value = try number(normalized(input.value))
        let name = input.item == .custom ? try field(input.name, limit: 60, message: "請填寫 1–60 字的檢驗名稱。") : input.item.label
        let unit = try field(input.unit, limit: 30, message: "請填寫報告的單位，或填「無單位」。")
        let reference = normalized(input.reference)
        let status: LabStatus
        if reference.isEmpty { status = .unknown }
        else {
            guard reference.count <= 60 else { throw HealthError.invalid("參考值請勿超過 60 字。") }
            if let bounds = captures("(\(decimal))\\s*[-–—~至]\\s*(\(decimal))", reference) {
                let low = try number(bounds[0]), high = try number(bounds[1])
                guard low <= high else { throw HealthError.invalid("參考值下限不可大於上限。") }
                status = value < low ? .low : value > high ? .high : .within
            } else if let limit = captures("(<=|>=|<|>|≤|≥)\\s*(\(decimal))", reference) {
                let bound = try number(limit[1])
                switch limit[0] {
                case "<": status = value >= bound ? .high : .within
                case "<=", "≤": status = value > bound ? .high : .within
                case ">": status = value <= bound ? .low : .within
                default: status = value < bound ? .low : .within
                }
            } else { throw HealthError.invalid("請依原報告填寫參考值，例如 70–99、<100 或 ≥60。") }
        }
        return LabLine(id: input.id, item: input.item, name: name, value: input.value, unit: unit,
                       reference: input.reference, status: status)
    }
    public static func explain(_ inputs: [LabInput]) throws -> [LabLine] {
        guard (1...maxItems).contains(inputs.count) else { throw HealthError.invalid("請填寫 1 至 12 個檢驗項目。") }
        return try inputs.map(parse).enumerated().sorted {
            func rank(_ line: LabLine) -> Int { line.status.outside ? 0 : line.status == .unknown ? 1 : 2 }
            let a = rank($0.element), b = rank($1.element)
            return a == b ? $0.offset < $1.offset : a < b
        }.map(\.element)
    }
    /// Persist only names and classifications, never the original values, units, or reference intervals.
    public static func savedText(_ lines: [LabLine]) -> String {
        let outside = lines.filter { $0.status.outside }.count
        let missing = lines.filter { $0.status == .unknown }.count
        let result = "這是依您填寫的報告參考值整理的結果。\(lines.count - outside - missing) 項符合所填參考值，\(outside) 項超出，\(missing) 項缺少參考值，無法比對。"
        let question = outside > 0 ? "這次超出參考值的項目，需要和過去的結果一起看嗎？" : missing > 0 ? "報告沒列出的參考值，可以幫我確認嗎？" : "以我的健康狀況，這些項目下次何時追蹤合適？"
        return result + "\n\n" + lines.map { "\($0.name)｜\($0.status.rawValue)" }.joined(separator: "\n") + "\n\n看診時可問：" + question
    }
}
