package com.gucare.health;

import android.content.Context;

final class ReportModel {
    ReportModel(Context context) {}
    boolean supported() { return false; }
    boolean prepare() { return false; }
    String generate(String prompt, String allowedChoices) { return null; }
    String failureReason() { return "Basic edition"; }
    void cancel() {}
}
