package com.gucare.health;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static com.gucare.health.ReportExplanation.Status.*;
import static org.junit.Assert.*;

public class ReportExplanationTest {
    @Test public void respectsInclusiveAndExclusiveBoundsAndDecimalPrecision() {
        assertEquals(WITHIN, line("７０.００", "７０～９９").status);
        assertEquals(WITHIN, line("99", "70–99").status);
        assertEquals(LOW, line("69.999", "70-99").status);
        assertEquals(HIGH, line("99.0001", "70-99").status);
        assertEquals(WITHIN, line("-1.5", "-2--1").status);
        assertEquals(HIGH, line("100.0", "<100").status);
        assertEquals(WITHIN, line("100.0", "≤100").status);
        assertEquals(LOW, line("60", ">60").status);
        assertEquals(WITHIN, line("60", "≥60").status);
        assertEquals(UNKNOWN, line("0.32", "").status);
    }

    @Test public void validationIdentifiesWhichFieldToCorrect() {
        expectInvalid(ReportExplanation.Field.VALUE, () -> line(">60", "≥60"));
        expectInvalid(ReportExplanation.Field.REFERENCE, () -> line("1", "10–1"));
        expectInvalid(ReportExplanation.Field.REFERENCE, () -> line("1", "70–99 mg/dL"));
        expectInvalid(ReportExplanation.Field.UNIT, () -> ReportExplanation.parse(ReportExplanation.Item.A1C, "", "6.4", "", ""));
        expectInvalid(ReportExplanation.Field.NAME, () -> ReportExplanation.parse(ReportExplanation.Item.CUSTOM, " ", "1", "無單位", ""));
    }

    @Test public void modelUsesCodesAndOnlyAcceptsApplicableChoices() {
        ReportExplanation.Line custom = ReportExplanation.parse(ReportExplanation.Item.CUSTOM,
                "私人名稱", "140.51", "特殊單位", "60–100.30");
        ReportExplanation.Facts facts = new ReportExplanation.Facts(Arrays.asList(line("1", ""), custom));
        assertFalse(facts.prompt().contains("私人名稱"));
        assertFalse(facts.prompt().contains("140.51"));
        assertFalse(facts.prompt().contains("特殊單位"));
        assertFalse(facts.prompt().contains("100.30"));
        assertEquals("ABCDEF", facts.allowedChoices());
        assertTrue(facts.summarize("D").ai);
        assertTrue(facts.summarize("F").question.contains("參考值"));
        assertFalse(facts.summarize("D and invented advice").ai);
        assertFalse(new ReportExplanation.Facts(Collections.singletonList(line("1", "≥1"))).summarize("F").ai);
        String saved = ReportExplanation.savedText(Collections.singletonList(custom), facts.summarize(null));
        assertTrue(saved.contains("私人名稱｜偏高"));
        assertFalse(saved.contains("140.51"));
        assertFalse(saved.contains("100.30"));
        assertFalse(saved.contains("特殊單位"));
    }

    private static ReportExplanation.Line line(String value, String reference) {
        return ReportExplanation.parse(ReportExplanation.Item.GLUCOSE, "", value, "mg/dL", reference);
    }

    private static void expectInvalid(ReportExplanation.Field field, Runnable action) {
        try {
            action.run();
            fail("Expected an invalid " + field);
        } catch (ReportExplanation.InvalidField error) {
            assertEquals(field, error.field);
        }
    }
}
