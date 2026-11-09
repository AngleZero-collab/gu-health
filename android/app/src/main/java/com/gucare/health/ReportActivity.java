package com.gucare.health;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReportActivity extends Activity {
    static final String TITLE = "AI 白話文健檢報告";
    static final String PROFILE_ID = "reportProfileId";
    static final String RECORD_ID = "reportRecordId";
    private static final long MODEL_TIMEOUT_MS = 30_000;
    private static final ExecutorService MODEL_WORK = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<InputRow> rows = new ArrayList<>();
    private HealthStore store;
    private long owner;
    private Ui ui;
    private ScrollView scroll;
    private LinearLayout body, form, rowList, result;
    private TextView add, save;
    private List<ReportExplanation.Line> lines;
    private ReportExplanation.Summary summary;
    private ReportModel model;
    private Future<?> preparation, generation;
    private volatile boolean modelReady;
    private int request;
    private boolean saved;

    @Override protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.TAIWAN);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        owner = getIntent().getLongExtra(PROFILE_ID, 0);
        store = new HealthStore(this);
        Profile profile = store.profile(owner);
        if (profile == null) { finish(); return; }
        ui = new Ui(this, getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("large", false));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setSaveFromParentEnabled(false);
        scroll.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 30) scroll.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else {
            scroll.setFitsSystemWindows(true);
        }
        body = ui.col();
        body.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24));
        body.setFocusableInTouchMode(true);
        scroll.addView(body);
        setContentView(scroll);
        ui.add(body, ui.secondary("返回我的紀錄", this::finish));
        long recordId = getIntent().getLongExtra(RECORD_ID, 0);
        if (recordId != 0) {
            showSaved(recordId);
        } else {
            ui.spaceAdd(body, heading(TITLE), 20);
            ui.spaceAdd(body, ui.text("目前檔案：" + profile.name, 18, Ui.MUTED, true), 8);
            createForm();
            model = new ReportModel(getApplicationContext());
            ReportModel local = model;
            if (local.supported()) preparation = MODEL_WORK.submit(() -> modelReady = local.prepare());
        }
        body.requestFocus();
    }

    private TextView heading(String title) {
        TextView view = ui.title(title);
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    private void createForm() {
        form = ui.col();
        ui.spaceAdd(body, form, 16);
        ui.add(form, ui.text("逐欄輸入或貼上數值。請照原報告填寫，結果與參考值要使用同一個單位。離開此頁或切換到其他 App 時會清除草稿。", 18, Ui.MUTED, false));
        rowList = ui.col();
        ui.spaceAdd(form, rowList, 16);
        add = ui.secondary("＋ 新增檢驗項目", this::addRow);
        ui.spaceAdd(form, add, 16);
        ui.spaceAdd(form, ui.button("查看白話解說", this::submit), 12);
        result = ui.col();
        result.setVisibility(View.GONE);
        ui.spaceAdd(body, result, 20);
        addRow();
    }

    private void addRow() {
        if (rows.size() == ReportExplanation.MAX_ITEMS) return;
        InputRow row = new InputRow();
        rows.add(row);
        ui.spaceAdd(rowList, row.card, rows.size() == 1 ? 0 : 12);
        labelRows();
    }

    private void labelRows() {
        for (int index = 0; index < rows.size(); index++) {
            InputRow row = rows.get(index);
            row.title.setText("第 " + (index + 1) + " 項檢驗");
            row.remove.setContentDescription("移除第 " + (index + 1) + " 項檢驗");
            row.remove.setVisibility(rows.size() > 1 ? View.VISIBLE : View.GONE);
        }
        add.setVisibility(rows.size() < ReportExplanation.MAX_ITEMS ? View.VISIBLE : View.GONE);
    }

    private void submit() {
        List<ReportExplanation.Line> checked = new ArrayList<>();
        for (InputRow row : rows) {
            try {
                checked.add(ReportExplanation.parse(ReportExplanation.Item.values()[row.item.getSelectedItemPosition()],
                        value(row.name), value(row.value), value(row.unit), value(row.reference)));
            } catch (ReportExplanation.InvalidField error) {
                EditText field = row.fieldFor(error.field);
                field.setError(error.getMessage());
                field.requestFocus();
                return;
            }
        }
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(body.getWindowToken(), 0);
        body.requestFocus();
        lines = ReportExplanation.ordered(checked);
        ReportExplanation.Facts facts = new ReportExplanation.Facts(lines);
        form.setVisibility(View.GONE);
        result.setVisibility(View.VISIBLE);
        scroll.smoothScrollTo(0, 0);
        if (!model.supported()) {
            showResult(facts.summarize(null));
            return;
        }
        result.removeAllViews();
        TextView busy = ui.text("正在準備本機 AI 解說…", 20, Ui.GREEN, true);
        busy.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(result, busy);
        ui.spaceAdd(result, ui.secondary("先看基本解說", () -> finishRequest(facts)), 12);

        final int token = ++request;
        ReportModel local = model;
        if (preparation == null) preparation = MODEL_WORK.submit(() -> modelReady = local.prepare());
        generation = MODEL_WORK.submit(() -> {
            String reply = modelReady ? local.generate(facts.prompt(), facts.allowedChoices()) : null;
            main.post(() -> {
                if (token != request) return;
                generation = null;
                main.removeCallbacksAndMessages(null);
                showResult(facts.summarize(reply));
            });
        });
        main.postDelayed(() -> {
            if (token == request) finishRequest(facts);
        }, MODEL_TIMEOUT_MS);
    }

    private void finishRequest(ReportExplanation.Facts facts) {
        cancelGeneration();
        showResult(facts.summarize(null));
    }

    private void cancelGeneration() {
        request++;
        main.removeCallbacksAndMessages(null);
        if (generation != null) {
            generation.cancel(true);
            generation = null;
            model.cancel();
        }
    }

    private void showResult(ReportExplanation.Summary value) {
        summary = value;
        result.removeAllViews();
        ui.add(result, heading("檢驗結果"));
        for (ReportExplanation.Line line : lines) {
            LinearLayout card = ui.card();
            ui.add(card, ui.text(line.name, 24, Ui.INK, true));
            ui.spaceAdd(card, ui.label(line.status.label, line.status.outside() ? Ui.ORANGE : Ui.GREEN,
                    line.status.outside() ? Ui.PEACH : Ui.MINT), 8);
            String reference = line.reference.isEmpty() ? "缺參考值，無法比對" : "原報告參考值：" + line.reference;
            ui.spaceAdd(card, ui.text("檢驗結果：" + line.value + " " + line.unit + "\n" + reference, 18, Ui.INK, false), 8);
            if (!line.item.meaning.isEmpty()) ui.spaceAdd(card, ui.text(line.item.meaning, 18, Ui.MUTED, false), 8);
            ui.spaceAdd(result, card, 12);
        }
        ui.spaceAdd(result, heading(value.label()), 24);
        ui.spaceAdd(result, ui.text(value.text, 18, Ui.INK, false), 8);
        ui.spaceAdd(result, ui.text("帶著原報告詢問健檢院所或家庭醫學科：\n" + value.question, 18, Ui.INK, false), 12);
        ui.spaceAdd(result, ui.text("這份解說用於衛教與看診準備。請依健檢院所的通知安排追蹤。", 18, Ui.MUTED, false), 12);
        ui.spaceAdd(result, ui.text("保存內容包含日期、項目名稱、比對狀態與白話摘要。", 18, Ui.MUTED, false), 16);
        save = ui.button("保存摘要", this::saveSummary);
        save.setEnabled(!saved);
        ui.spaceAdd(result, save, 8);
        ui.spaceAdd(result, ui.secondary("返回編輯數值", () -> {
            lines = null;
            summary = null;
            saved = false;
            result.removeAllViews();
            result.setVisibility(View.GONE);
            form.setVisibility(View.VISIBLE);
            scroll.smoothScrollTo(0, 0);
        }), 12);
    }

    private void saveSummary() {
        if (saved || summary == null) return;
        save.setEnabled(false);
        HealthRecord record = new HealthRecord();
        record.profileId = owner;
        record.kind = HealthRecord.REPORT_SUMMARY;
        record.title = "白話文健檢摘要";
        record.date = LocalDate.now().toString();
        record.note = ReportExplanation.savedText(lines, summary);
        try {
            store.add(record);
            saved = true;
            setResult(RESULT_OK);
            save.setText("摘要已保存");
            notice("摘要已保存到我的紀錄");
        } catch (android.database.sqlite.SQLiteException error) {
            save.setEnabled(true);
            notice("無法保存摘要，請再試一次");
        }
    }

    private void showSaved(long id) {
        HealthRecord record = store.records(owner).stream()
                .filter(row -> row.id == id && HealthRecord.REPORT_SUMMARY.equals(row.kind)).findFirst().orElse(null);
        if (record == null) { finish(); return; }
        ui.spaceAdd(body, heading(record.title), 20);
        ui.spaceAdd(body, ui.text(record.date, 18, Ui.MUTED, true), 8);
        ui.spaceAdd(body, ui.text(record.note, 20, Ui.INK, false), 14);
        ui.spaceAdd(body, ui.secondary("刪除摘要", () -> new CareDialogBuilder(this).setTitle("刪除這份摘要？")
                .setNegativeButton("取消", null).setPositiveButton("刪除", (dialog, which) -> {
                    try {
                        store.deleteRecord(record.id);
                        setResult(RESULT_OK);
                        finish();
                    } catch (android.database.sqlite.SQLiteException error) {
                        notice("無法刪除摘要，請再試一次");
                    }
                }).show()), 24);
    }

    private void notice(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override protected void onStop() {
        cancelGeneration();
        if (preparation != null) preparation.cancel(true);
        for (InputRow row : rows) {
            for (EditText field : new EditText[]{row.name, row.value, row.unit, row.reference}) field.getText().clear();
        }
        rows.clear();
        lines = null;
        summary = null;
        if (result != null) result.removeAllViews();
        if (rowList != null) rowList.removeAllViews();
        if (body != null) body.removeAllViews();
        finish();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }

    private static String value(EditText field) { return field.getText().toString(); }

    private void labeled(LinearLayout parent, String title, View field) {
        field.setId(View.generateViewId());
        TextView label = ui.text(title, 18, Ui.INK, true);
        label.setLabelFor(field.getId());
        ui.spaceAdd(parent, label, 12);
        ui.spaceAdd(parent, field, 6);
    }

    private EditText newInput(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setSaveEnabled(false);
        field.setTextSize(18 * ui.scale);
        field.setTextColor(Ui.INK);
        field.setHintTextColor(Ui.MUTED);
        field.setHint(hint);
        field.setMinHeight(ui.dp(64));
        field.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        field.setBackground(ui.outline(Ui.WHITE, 12, Ui.LINE));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        return field;
    }

    private final class InputRow {
        final LinearLayout card = ui.card();
        final LinearLayout nameGroup = ui.col();
        final TextView title = heading("");
        final Spinner item = new Spinner(ReportActivity.this);
        final EditText name = newInput("照報告填寫項目名稱");
        final EditText value = newInput("例如 5.8");
        final EditText unit = newInput("例如 mg/dL、％ 或無單位");
        final EditText reference = newInput("例如 70–99 或 ≤100");
        final TextView remove = ui.secondary("移除這個項目", () -> {
            rows.remove(this);
            rowList.removeView(card);
            labelRows();
        });

        InputRow() {
            ui.add(card, title);
            List<String> names = new ArrayList<>();
            for (ReportExplanation.Item option : ReportExplanation.Item.values()) names.add(option.label);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(ReportActivity.this, android.R.layout.simple_spinner_item, names) {
                private View style(View view) {
                    TextView text = (TextView) view;
                    text.setTextSize(18 * ui.scale);
                    text.setMinimumHeight(ui.dp(64));
                    text.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
                    return text;
                }
                @Override public View getView(int position, View convert, android.view.ViewGroup parent) {
                    return style(super.getView(position, convert, parent));
                }
                @Override public View getDropDownView(int position, View convert, android.view.ViewGroup parent) {
                    return style(super.getDropDownView(position, convert, parent));
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            item.setAdapter(adapter);
            item.setMinimumHeight(ui.dp(64));
            item.setBackground(ui.outline(Ui.WHITE, 12, Ui.LINE));
            labeled(card, "檢驗項目", item);
            labeled(nameGroup, "項目名稱", name);
            ui.add(card, nameGroup);
            nameGroup.setVisibility(View.GONE);
            item.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    nameGroup.setVisibility(ReportExplanation.Item.values()[position] == ReportExplanation.Item.CUSTOM ? View.VISIBLE : View.GONE);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { nameGroup.setVisibility(View.GONE); }
            });
            labeled(card, "檢驗結果", value);
            labeled(card, "報告單位", unit);
            labeled(card, "原報告參考值（可留空）", reference);
            ui.spaceAdd(card, remove, 12);
        }

        EditText fieldFor(ReportExplanation.Field field) {
            switch (field) {
                case NAME: return name;
                case UNIT: return unit;
                case REFERENCE: return reference;
                default: return value;
            }
        }
    }
}
