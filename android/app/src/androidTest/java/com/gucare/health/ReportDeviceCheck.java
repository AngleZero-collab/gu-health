package com.gucare.health;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A device smoke check that creates and cleans up only its own temporary profile. */
public final class ReportDeviceCheck extends Instrumentation {
    private boolean expectAi;
    private boolean probeAi;
    private boolean forceBasic, forceTimeout, largeFont;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        expectAi = "true".equals(arguments.getString("expectAi"));
        probeAi = "true".equals(arguments.getString("probeAi"));
        forceBasic = "true".equals(arguments.getString("forceBasic"));
        forceTimeout = "true".equals(arguments.getString("forceTimeout"));
        largeFont = "true".equals(arguments.getString("largeFont"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        if (probeAi) {
            ReportModel model = new ReportModel(getTargetContext());
            boolean ready = model.supported() && model.prepare();
            long started = SystemClock.elapsedRealtime();
            String output = ready ? model.generate("Return exactly A.", "A") : null;
            result.putString("stream", "\nPROBE ready=" + ready + " output=" + output + " elapsedMs="
                    + (SystemClock.elapsedRealtime() - started) + " failure=" + model.failureReason() + "\n");
            finish(Activity.RESULT_OK, result);
            return;
        }
        try (HealthStore store = new HealthStore(getTargetContext())) {
            if (expectAi && Settings.Global.getInt(getTargetContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 1) {
                throw new AssertionError("Airplane mode is required for the AI check");
            }
            Profile synthetic = new Profile();
            synthetic.name = "健檢功能驗收";
            long owner = store.save(synthetic);
            android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("prefs", 0);
            boolean originalFont = prefs.getBoolean("large", false);
            if (largeFont) prefs.edit().putBoolean("large", true).commit();
            CountDownLatch resumeWorker = new CountDownLatch(1);
            Future<?> delay = null;
            try {
                if (forceTimeout) {
                    java.lang.reflect.Field executor = ReportActivity.class.getDeclaredField("MODEL_WORK");
                    executor.setAccessible(true);
                    delay = ((ExecutorService) executor.get(null)).submit(() -> {
                        try { resumeWorker.await(40, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    });
                }
                checkReport(store, owner);
                result.putString("stream", "\nPASS: " + (forceTimeout ? "AI timeout uses basic explanation" : forceBasic ? "cancel AI and use basic explanation" : expectAi ? "bundled AI in airplane mode" : "basic report")
                        + (largeFont ? ", large font and touch targets" : "")
                        + ", explicit save, reopen, and private record contents.\n");
            } finally {
                resumeWorker.countDown();
                if (delay != null) delay.cancel(true);
                if (largeFont) prefs.edit().putBoolean("large", originalFont).commit();
                store.deleteProfile(owner);
            }
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, result);
            return;
        }
        finish(Activity.RESULT_OK, result);
    }

    private void checkReport(HealthStore store, long owner) {
        Activity report = open(owner, 0);
        long savedId;
        try {
            long started = SystemClock.elapsedRealtime();
            runOnMainSync(() -> {
                report.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                View root = report.getWindow().getDecorView();
                checkAccessibility(report, root);
                ((EditText) find(root, "例如 5.8", true)).setText("121.5");
                ((EditText) find(root, "例如 mg/dL、％ 或無單位", true)).setText("mg/dL");
                ((EditText) find(root, "例如 70–99 或 ≤100", true)).setText("70–99");
                find(root, "查看白話解說", false).performClick();
                if (forceBasic) {
                    View skip = find(root, "先看基本解說", false);
                    if (skip == null) throw new AssertionError("Missing cancel AI action");
                    skip.performClick();
                }
            });
            if (!waitForText(report, expectAi && !forceBasic && !forceTimeout ? "本機 AI 解說" : "基本解說")) {
                throw new AssertionError(expectAi ? "The real model did not complete" : "The basic report did not load");
            }
            if (forceTimeout && SystemClock.elapsedRealtime() - started < 29_000) {
                throw new AssertionError("The timeout was not exercised");
            }
            if (!store.records(owner).isEmpty()) throw new AssertionError("Draft was written to the database");
            runOnMainSync(() -> {
                View root = report.getWindow().getDecorView();
                if (find(root, "偏高", false) == null) throw new AssertionError("Value was not classified correctly");
                find(root, "保存摘要", false).performClick();
            });
            HealthRecord record = store.records(owner).get(0);
            if (!record.note.contains("空腹血糖｜偏高") || record.note.contains("121.5") || record.note.contains("70–99")) {
                throw new AssertionError("Unexpected summary contents");
            }
            savedId = record.id;
        } finally {
            runOnMainSync(report::finish);
        }
        Activity saved = open(owner, savedId);
        try {
            if (!waitForText(saved, "白話文健檢摘要")) throw new AssertionError("Could not reopen the saved summary");
        } finally {
            runOnMainSync(saved::finish);
        }
    }

    private Activity open(long owner, long recordId) {
        Intent intent = new Intent(getTargetContext(), ReportActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ReportActivity.PROFILE_ID, owner).putExtra(ReportActivity.RECORD_ID, recordId);
        Activity activity = startActivitySync(intent);
        waitForIdleSync();
        return activity;
    }

    private boolean waitForText(Activity activity, String expected) {
        AtomicBoolean present = new AtomicBoolean();
        long end = SystemClock.elapsedRealtime() + 35_000;
        do {
            runOnMainSync(() -> present.set(find(activity.getWindow().getDecorView(), expected, false) != null));
            if (present.get()) return true;
            SystemClock.sleep(150);
        } while (SystemClock.elapsedRealtime() < end);
        return false;
    }

    @SuppressWarnings("deprecation")
    private static void checkAccessibility(Activity activity, View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        if (view.isClickable() && view.getHeight() + 1 < 64 * metrics.density) {
            throw new AssertionError("Touch target below 64dp");
        }
        if (view instanceof TextView && ((TextView) view).length() > 0
                && ((TextView) view).getTextSize() + 1 < 18 * metrics.scaledDensity) {
            throw new AssertionError("Text below 18sp");
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkAccessibility(activity, group.getChildAt(i));
        }
    }

    private static View find(View view, String value, boolean hint) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence candidate = hint ? text.getHint() : text.getText();
            if (candidate != null && value.contentEquals(candidate)) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = find(group.getChildAt(i), value, hint);
                if (child != null) return child;
            }
        }
        return null;
    }
}
