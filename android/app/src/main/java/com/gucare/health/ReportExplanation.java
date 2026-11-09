package com.gucare.health;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReportExplanation {
    static final int MAX_ITEMS = 12;
    private static final String DECIMAL = "[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)";
    private static final Pattern NUMBER = Pattern.compile(DECIMAL);
    private static final Pattern RANGE = Pattern.compile("(" + DECIMAL + ")\\s*[-–—~至]\\s*(" + DECIMAL + ")");
    private static final Pattern LIMIT = Pattern.compile("(<=|>=|<|>|≤|≥)\\s*(" + DECIMAL + ")");
    private static final Pattern CHOICE = Pattern.compile("[A-F]");

    enum Item {
        GLUCOSE("空腹血糖", "記錄這次空腹抽血時的血糖。"),
        A1C("糖化血色素（HbA1c）", "反映過去約三個月的平均血糖。"),
        CHOLESTEROL("總膽固醇", "量的是血液中的總膽固醇。"),
        LDL("低密度膽固醇（LDL）", "量的是血液中的低密度脂蛋白膽固醇。"),
        HDL("高密度膽固醇（HDL）", "量的是血液中的高密度脂蛋白膽固醇。"),
        TRIGLYCERIDES("三酸甘油酯", "量的是血液中一種叫三酸甘油酯的脂肪。"),
        ALT("丙胺酸轉胺酶（ALT／GPT）", "這是一種常用來評估肝臟的酵素。"),
        AST("天門冬胺酸轉胺酶（AST／GOT）", "這種酵素在肝臟、肌肉等部位都找得到。"),
        CREATININE("肌酸酐", "這是身體代謝產生的物質，常用來協助評估腎功能。"),
        EGFR("估算腎絲球過濾率（eGFR）", "用來估算腎臟過濾血液的能力。"),
        CUSTOM("自訂項目", "");

        final String label, meaning;

        Item(String label, String meaning) {
            this.label = label;
            this.meaning = meaning;
        }
    }

    enum Status {
        HIGH("偏高", 0), LOW("偏低", 0), UNKNOWN("無法比對", 1), WITHIN("符合所填參考值", 2);

        final String label;
        final int order;

        Status(String label, int order) {
            this.label = label;
            this.order = order;
        }

        boolean outside() { return this == HIGH || this == LOW; }
    }

    static final class Line {
        final Item item;
        final String name, value, unit, reference;
        final Status status;

        Line(Item item, String name, String value, String unit, String reference, Status status) {
            this.item = item;
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.reference = reference;
            this.status = status;
        }
    }

    enum Field { NAME, VALUE, UNIT, REFERENCE }

    static final class InvalidField extends IllegalArgumentException {
        final Field field;

        InvalidField(Field field, String message) {
            super(message);
            this.field = field;
        }
    }

    private ReportExplanation() {}

    static Line parse(Item item, String customName, String value, String unit, String reference) {
        String name = item == Item.CUSTOM ? singleLine(customName, 60, Field.NAME, "請輸入檢驗名稱") : item.label;
        String originalValue = value.trim();
        BigDecimal number = number(normalize(originalValue), Field.VALUE, "請輸入一個數字，例如 95 或 5.8");
        String originalUnit = singleLine(unit, 30, Field.UNIT, "請填寫報告的單位，或填「無單位」");
        String originalReference = reference.trim();
        Status status = compare(number, normalize(originalReference));
        return new Line(item, name, originalValue, originalUnit, originalReference, status);
    }

    private static String singleLine(String value, int limit, Field field, String error) {
        String text = value.trim();
        if (text.isEmpty() || text.length() > limit || text.matches("(?s).*[\\p{C}\\p{Zl}\\p{Zp}].*")) {
            throw new InvalidField(field, error);
        }
        return text;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replace('−', '-').trim();
    }

    private static BigDecimal number(String text, Field field, String error) {
        if (text.length() > 24 || !NUMBER.matcher(text).matches()) throw new InvalidField(field, error);
        return new BigDecimal(text);
    }

    private static Status compare(BigDecimal value, String reference) {
        if (reference.isEmpty()) return Status.UNKNOWN;
        String error = "請依原報告填寫，例如 70–99、<100 或 ≥60";
        if (reference.length() > 60) throw new InvalidField(Field.REFERENCE, error);
        Matcher range = RANGE.matcher(reference);
        if (range.matches()) {
            BigDecimal low = number(range.group(1), Field.REFERENCE, error);
            BigDecimal high = number(range.group(2), Field.REFERENCE, error);
            if (low.compareTo(high) > 0) throw new InvalidField(Field.REFERENCE, "參考值下限不可大於上限");
            if (value.compareTo(low) < 0) return Status.LOW;
            if (value.compareTo(high) > 0) return Status.HIGH;
            return Status.WITHIN;
        }
        Matcher limit = LIMIT.matcher(reference);
        if (!limit.matches()) throw new InvalidField(Field.REFERENCE, error);
        int relation = value.compareTo(number(limit.group(2), Field.REFERENCE, error));
        switch (limit.group(1)) {
            case "<": return relation >= 0 ? Status.HIGH : Status.WITHIN;
            case "<=": case "≤": return relation > 0 ? Status.HIGH : Status.WITHIN;
            case ">": return relation <= 0 ? Status.LOW : Status.WITHIN;
            default: return relation < 0 ? Status.LOW : Status.WITHIN;
        }
    }

    static List<Line> ordered(List<Line> lines) {
        List<Line> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparingInt(line -> line.status.order));
        return ordered;
    }

    static final class Summary {
        final String text, question;
        final boolean ai;

        Summary(String text, String question, boolean ai) {
            this.text = text;
            this.question = question;
            this.ai = ai;
        }

        String label() { return ai ? "本機 AI 解說" : "基本解說"; }
    }

    static final class Facts {
        final String codes;
        final int outside, missing, total;

        Facts(List<Line> lines) {
            StringBuilder identifiers = new StringBuilder();
            int outsideCount = 0, missingCount = 0;
            for (Line line : lines) {
                identifiers.append(line.item.name()).append('=').append(line.status.name()).append(' ');
                if (line.status.outside()) outsideCount++;
                if (line.status == Status.UNKNOWN) missingCount++;
            }
            codes = identifiers.toString();
            outside = outsideCount;
            missing = missingCount;
            total = lines.size();
        }

        String prompt() {
            return "Choose one plain-language report template. Reply with one letter only. "
                    + "A = direct introduction, ask about future follow-up. B = gentle introduction, ask about future follow-up. "
                    + (outside > 0 ? "C = direct introduction, ask about out-of-range results. D = gentle introduction, ask about out-of-range results. " : "")
                    + (missing > 0 ? "E = direct introduction, ask for missing reference values. F = gentle introduction, ask for missing reference values. " : "")
                    + "Item status codes: " + codes;
        }

        String allowedChoices() { return "AB" + (outside > 0 ? "CD" : "") + (missing > 0 ? "EF" : ""); }

        Summary summarize(String response) {
            Matcher choice = CHOICE.matcher(response == null ? "" : response.trim());
            boolean accepted = choice.matches() && allowedChoices().contains(choice.group());
            int selection = accepted ? choice.group().charAt(0) - 'A' : 0;
            String intro = selection % 2 == 1 ? "先一起看看這次的結果。" : "這是依您填寫的報告參考值整理的結果。";
            String results = (total - outside - missing) + " 項符合所填參考值，" + outside + " 項超出，"
                    + missing + " 項缺少參考值，無法比對。";
            int questionCode = accepted ? selection / 2 : outside > 0 ? 1 : missing > 0 ? 2 : 0;
            String question;
            switch (questionCode) {
                case 1: question = "這次超出參考值的項目，需要和過去的結果一起看嗎？"; break;
                case 2: question = "報告沒列出的參考值，可以幫我確認嗎？"; break;
                default: question = "以我的健康狀況，這些項目下次何時追蹤合適？";
            }
            return new Summary(intro + results, question, accepted);
        }
    }

    static String savedText(List<Line> lines, Summary summary) {
        StringBuilder text = new StringBuilder(summary.text).append("\n\n");
        for (Line line : lines) text.append(line.name).append("｜").append(line.status.label).append('\n');
        return text.append("\n看診時可問：").append(summary.question).toString();
    }
}
