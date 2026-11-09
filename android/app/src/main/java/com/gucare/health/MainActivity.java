package com.gucare.health;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int NOTIFICATIONS=41,CAMERA=42,EXPORT=43,REPORT=45;
    private HealthStore store;
    private SharedPreferences prefs;
    private Ui ui;
    private Profile profile;
    private int page;
    private LinearLayout body,root;
    private FrameLayout content;
    private BottomNavigationView navigation;
    private OfflineSpeaker visionSpeaker;
    private VoiceConsultationSheet voiceSheet;
    private int visionMode,visionStep,eyeIndex;
    private String leftObservation="",rightObservation="";
    private long visionProfileId;
    private android.window.OnBackInvokedCallback backCallback;
    private boolean backRegistered;
    private static final DateTimeFormatter DISPLAY_DATE=DateTimeFormatter.ofPattern("M 月 d 日");

    @Override protected void attachBaseContext(android.content.Context base){
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.TAIWAN);super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new HealthStore(this);prefs=getSharedPreferences("prefs",MODE_PRIVATE);
        ui=new Ui(this,prefs.getBoolean("large",false));ReminderScheduler.channel(this);
        List<Profile> profiles=store.profiles();profile=store.profile(prefs.getLong("active",0));
        if(profile==null&&!profiles.isEmpty())profile=profiles.get(0);
        if(state!=null){page=Math.min(2,Math.max(0,state.getInt("page")));visionMode=state.getInt("visionMode");visionStep=state.getInt("visionStep");eyeIndex=state.getInt("eyeIndex");leftObservation=state.getString("leftObservation","");rightObservation=state.getString("rightObservation","");visionProfileId=state.getLong("visionProfileId");}
        if(getIntent().hasExtra("profileId")){Profile p=store.profile(getIntent().getLongExtra("profileId",0));if(p!=null)profile=p;}
        render();if(getIntent().getBooleanExtra("showReminders",false))showReminders();
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);Profile p=store.profile(i.getLongExtra("profileId",0));if(p!=null)profile=p;render();if(i.getBooleanExtra("showReminders",false))showReminders();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("page",page);b.putInt("visionMode",visionMode);b.putInt("visionStep",visionStep);b.putInt("eyeIndex",eyeIndex);b.putString("leftObservation",leftObservation);b.putString("rightObservation",rightObservation);b.putLong("visionProfileId",visionProfileId);}
    @Override protected void onStop(){if(visionSpeaker!=null)visionSpeaker.stop();if(voiceSheet!=null)voiceSheet.dismiss();super.onStop();}
    @Override protected void onDestroy(){if(visionSpeaker!=null)visionSpeaker.close();if(voiceSheet!=null)voiceSheet.dismiss();store.close();super.onDestroy();}
    // Legacy Android <=32 entry point. API 33+ uses the native dispatcher registered below.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){handleBack();}
    private void handleBack(){if(visionMode>0){if(visionSpeaker!=null)visionSpeaker.stop();new CareDialogBuilder(this).setTitle("離開這次自我觀察？").setMessage("尚未儲存的內容會取消。").setNegativeButton("繼續觀察",(d,w)->announceEye()).setPositiveButton("離開",(d,w)->{visionMode=0;render();}).show();}else if(page!=0){navigate(0);}else finish();}
    private void navigate(int p){if(visionSpeaker!=null)visionSpeaker.stop();getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);page=p;visionMode=0;render();}
    private void render(){
        if(Build.VERSION.SDK_INT>=33){if(backCallback==null)backCallback=this::handleBack;if(backRegistered){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);backRegistered=false;}if(page!=0||visionMode>0){getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);backRegistered=true;}}
        if(visionSpeaker!=null)visionSpeaker.stop();
        ui=new Ui(this,prefs.getBoolean("large",false));setContentView(R.layout.activity_main);root=findViewById(R.id.app_root);content=findViewById(R.id.screen_content);navigation=findViewById(R.id.bottom_navigation);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bar=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bar.left,bar.top,bar.right,bar.bottom);return insets;});}
        else root.setFitsSystemWindows(true);
        findViewById(R.id.toolbar_reminders).setOnClickListener(v->showReminders());findViewById(R.id.toolbar_settings).setOnClickListener(v->showSettings());
        if(profile==null){findViewById(R.id.toolbar).setVisibility(View.GONE);navigation.setVisibility(View.GONE);welcome();ui.enforce(root);return;}
        prefs.edit().putLong("active",profile.id).apply();
        navigation.getMenu().findItem(page==1?R.id.nav_vision:page==2?R.id.nav_records:R.id.nav_home).setChecked(true);
        navigation.setOnItemSelectedListener(item->{int id=item.getItemId();navigate(id==R.id.nav_vision?1:id==R.id.nav_records?2:0);return true;});
        boolean immersive=visionMode>0;findViewById(R.id.toolbar).setVisibility(immersive?View.GONE:View.VISIBLE);navigation.setVisibility(immersive?View.GONE:View.VISIBLE);
        if(visionMode>0&&visionStep==1){renderVisionTest();}
        else if(page==0&&visionMode==0){dashboard();}
        else{ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);body=ui.col();body.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(28));scroll.addView(body);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));if(visionMode>0)visionFlow();else if(page==1)vision();else records();}
        ui.enforce(root);
        root.requestFocus();
    }
    private void welcome(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout box=ui.col();box.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(28));scroll.addView(box);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout logo=ui.row();logo.addView(ui.icon("shield",Ui.GREEN,31));ui.hgap(logo,10);logo.addView(ui.text("顧健康",24,Ui.GREEN,true));ui.add(box,logo);ui.gap(box,36);
        ui.spaceAdd(box,ui.title("歡迎使用顧健康"),8);ui.spaceAdd(box,ui.text("建立健康檔案，就能查看篩檢、安排提醒、記錄居家觀察。",18,Ui.MUTED,false),12);ui.gap(box,23);
        ui.add(box,featureRow("calendar","個人化篩檢清單","依年齡與背景比對官方政策"));ui.spaceAdd(box,featureRow("bell","手機提醒，不再忘記","安排自己的健康行程"),12);ui.spaceAdd(box,featureRow("shield","你的資料，留在手機","離線可用，不自動上傳健康資料"),12);ui.gap(box,26);
        ui.add(box,ui.button("建立第一份健康檔案  →",()->profileForm(null)));ui.spaceAdd(box,ui.secondary("先用示範資料體驗",()->{profile=store.demo();prefs.edit().putBoolean("demo",true).apply();page=0;render();}),10);ui.spaceAdd(box,center("本程式不是醫療診斷工具，也不代表政府機關。",12,Ui.MUTED,false),17);
    }
    private TextView center(String text,int size,int color,boolean bold){TextView t=ui.text(text,size,color,bold);t.setGravity(Gravity.CENTER);return t;}
    private LinearLayout featureRow(String icon,String title,String sub){LinearLayout r=ui.row();View v=ui.icon(icon,Ui.GREEN,27);r.addView(v);ui.hgap(r,13);LinearLayout copy=ui.col();ui.add(copy,ui.text(title,15,Ui.INK,true));ui.spaceAdd(copy,ui.text(sub,13,Ui.MUTED,false),3);ui.weighted(r,copy);return r;}
    private void heading(String title,String subtitle){ui.add(body,ui.title(title));if(!subtitle.isEmpty())ui.spaceAdd(body,ui.text(subtitle,18,Ui.MUTED,false),10);ui.gap(body,22);}
    private void info(LinearLayout parent,String message,int color,int background){TextView t=ui.text(message,13,color,false);t.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));t.setBackground(ui.bg(background,13));ui.add(parent,t);}

    private void dashboard(){
        View home=getLayoutInflater().inflate(R.layout.view_home,content,false);content.addView(home);androidx.core.view.ViewCompat.setAccessibilityHeading(home.findViewById(R.id.mascot_greeting),true);
        List<PolicyEngine.Policy> available=new ArrayList<>();
        for(PolicyEngine.Policy p:PolicyEngine.forProfile(profile,LocalDate.now())){
            if(!p.publicService||!p.eligible)continue;
            LocalDate last=store.lastScreening(profile.id,p.id);
            if(p.id.equals("hpv")&&PolicyEngine.hpvUsedForCurrentAge(profile,LocalDate.now(),last))continue;
            LocalDate next=PolicyEngine.nextReview(p,last);
            if(next==null||!next.isAfter(LocalDate.now()))available.add(p);
        }
        ((TextView)home.findViewById(R.id.mascot_greeting)).setText(getString(R.string.greeting,profile.name,available.size()));
        home.findViewById(R.id.change_profile).setOnClickListener(v->switchProfile());
        home.findViewById(R.id.home_observe).setOnClickListener(v->navigate(1));
        home.findViewById(R.id.fab_ai_voice).setOnClickListener(v->openVoice());
        RecyclerView list=home.findViewById(R.id.screening_list);list.setLayoutManager(new LinearLayoutManager(this));list.setNestedScrollingEnabled(false);
        list.setAdapter(new ScreeningAdapter(available,ui,p->policyStatus(p)+" · "+p.frequency(),this::policyDetail,p->dialClinic(),p->findClinics(p.title)));
        if(available.isEmpty()){TextView empty=home.findViewById(R.id.home_empty);empty.setVisibility(View.VISIBLE);empty.setText("目前沒有待確認的公費健檢。已登錄的檢查可在「我的紀錄」查看；仍可向醫師詢問適合的追蹤。");}
    }
    private String policyStatus(PolicyEngine.Policy p){
        if(!p.publicService)return "依醫師建議";if(!p.eligible)return "未符合基本條件";
        LocalDate last=store.lastScreening(profile.id,p.id);if(last==null)return "可向院所確認";
        if(p.id.equals("hpv"))return PolicyEngine.hpvUsedForCurrentAge(profile,LocalDate.now(),last)?"此年齡已登錄":"可向院所確認";
        LocalDate next=PolicyEngine.nextReview(p,last);return next!=null&&next.isAfter(LocalDate.now())?"已登錄紀錄":"可再次確認";
    }
    private void dialClinic(){
        try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:")));toast("請輸入院所電話；可先使用地圖查詢。");}
        catch(ActivityNotFoundException e){toast("這個裝置沒有撥號程式，請用地圖查詢院所電話。");}
    }
    private void findClinics(String title){
        String query=(profile.city.trim().isEmpty()?"台灣":profile.city)+" "+title+" 院所";
        Intent maps=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(query))).setPackage("com.google.android.apps.maps");
        try{startActivity(maps);}catch(ActivityNotFoundException e){openUrl("https://www.google.com/maps/search/?api=1&query="+Uri.encode(query));}
    }
    private void openVoice(){
        if(visionSpeaker!=null)visionSpeaker.stop();if(voiceSheet!=null)voiceSheet.dismiss();
        voiceSheet=new VoiceConsultationSheet(this,()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44));voiceSheet.show();
    }

    private void policyDetail(PolicyEngine.Policy p){
        LinearLayout box=dialogBox();ui.add(box,ui.label(p.publicService?"公費政策 · "+p.frequency():"日常追蹤 · 費用由院所確認",Ui.GREEN,Ui.MINT));ui.spaceAdd(box,ui.text(p.summary,15,Ui.MUTED,false),15);ui.spaceAdd(box,ui.text("誰可以接受？",24,Ui.INK,true),18);ui.spaceAdd(box,ui.text(p.rule,15,Ui.INK,false),7);
        LocalDate last=store.lastScreening(profile.id,p.id);if(last!=null){ui.spaceAdd(box,ui.text("上次登錄："+last,14,Ui.GREEN,true),16);LocalDate next=PolicyEngine.nextReview(p,last);if(next!=null)ui.spaceAdd(box,ui.text("規劃參考日："+next+"\n依登錄日期推估，不是政府核定的可受檢日或截止日。",13,Ui.MUTED,false),5);}
        ui.spaceAdd(box,ui.text("檢查前的小提醒",24,Ui.INK,true),18);ui.spaceAdd(box,ui.text(p.preparation,14,Ui.MUTED,false),7);ui.gap(box,18);info(box,PolicyEngine.DISCLAIMER,Ui.MUTED,Ui.MINT);
        AlertDialog d=dialog(p.title,box);ui.spaceAdd(box,ui.button("安排手機提醒",()->{d.dismiss();appointmentForm(p);}),17);ui.spaceAdd(box,ui.secondary("登錄已完成的檢查",()->{d.dismiss();recordForm(p);}),9);ui.spaceAdd(box,ui.secondary("查看官方政策  ↗",()->openUrl(p.source)),9);d.show();
    }

    private void records(){
        heading("我的紀錄",profile.name+" 的檢查與居家觀察");
        ui.add(body,ui.button(ReportActivity.TITLE,()->openReport(0)));
        ui.spaceAdd(body,ui.secondary("＋ 新增健康紀錄",()->recordForm(null)),12);
        ui.spaceAdd(body,ui.secondary("查看或新增提醒",this::showReminders),12);
        ui.spaceAdd(body,ui.secondary("分享健康摘要",this::shareSummary),12);
        List<HealthRecord> records=store.records(profile.id);
        if(records.isEmpty()){ui.gap(body,22);info(body,"還沒有紀錄。可以記下檢查、血壓，或今天想記得的事。",Ui.MUTED,Ui.MINT);return;}
        String day="";for(HealthRecord r:records){
            if(!day.equals(r.date)){day=r.date;ui.spaceAdd(body,ui.caption(r.date),24);}
            LinearLayout card=ui.card();ui.add(card,ui.text(r.title,24,Ui.INK,true));
            boolean reportSummary=HealthRecord.REPORT_SUMMARY.equals(r.kind);
            if(reportSummary){
                ui.spaceAdd(card,ui.text("點一下查看已保存的解說",18,Ui.MUTED,false),12);
                card.setOnClickListener(v->openReport(r.id));
            }else{
                ui.spaceAdd(card,ui.text(r.note.isEmpty()?"自行登錄的健康紀錄":r.note,18,Ui.MUTED,false),12);
                ui.spaceAdd(card,ui.label(r.flag?"有變化，請諮詢醫師":"自行登錄",r.flag?Ui.ORANGE:Ui.GREEN,r.flag?Ui.PEACH:Ui.MINT),12);
                card.setOnClickListener(v->recordDetail(r));
            }
            card.setFocusable(true);card.setMinimumHeight(ui.dp(64));ui.spaceAdd(body,card,12);
        }
    }
    private void openReport(long recordId){
        startActivityForResult(new Intent(this,ReportActivity.class).putExtra(ReportActivity.PROFILE_ID,profile.id)
                .putExtra(ReportActivity.RECORD_ID,recordId),REPORT);
    }
    private void recordDetail(HealthRecord r){
        new CareDialogBuilder(this).setTitle(r.title).setMessage(r.date+"\n\n"+r.note+"\n\n這是自行登錄資料，非正式診療報告。")
            .setPositiveButton("關閉",null).setNeutralButton("刪除",(d,w)->new CareDialogBuilder(this).setTitle("刪除這筆紀錄？")
            .setMessage("這會永久移除本機紀錄，無法復原。").setNegativeButton("取消",null)
            .setPositiveButton("刪除",(a,b)->{store.deleteRecord(r.id);render();}).show()).show();
    }
    private void showSettings(){
        LinearLayout box=dialogBox();AlertDialog d=dialog("設定與健康檔案",box);
        ui.add(box,ui.secondary("切換或新增家人",()->{d.dismiss();switchProfile();}));
        ui.spaceAdd(box,ui.secondary("編輯目前健康檔案",()->{d.dismiss();profileForm(profile);}),12);
        SwitchCompat large=new SwitchCompat(this);large.setText("更大字體");large.setTextSize(18);large.setMinHeight(ui.dp(64));large.setChecked(prefs.getBoolean("large",false));
        large.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("large",on).apply();d.dismiss();render();});ui.spaceAdd(box,large,16);
        SwitchCompat reminders=new SwitchCompat(this);reminders.setText("健康行程通知");reminders.setTextSize(18);reminders.setMinHeight(ui.dp(64));reminders.setChecked(prefs.getBoolean("notifications",true));
        reminders.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("notifications",on).apply();if(on){requestNotifications();ReminderScheduler.restore(this);}else for(Appointment a:store.appointments(0))ReminderScheduler.cancel(this,a.id);});ui.add(box,reminders);
        ui.spaceAdd(box,ui.secondary("手機通知設定",()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))),12);
        ui.spaceAdd(box,ui.secondary("匯出健康資料",()->{d.dismiss();exportBackup();}),12);
        ui.spaceAdd(box,ui.text("顧健康 2.1.0\n政策版本："+PolicyEngine.REVIEW_DATE+"\n健康紀錄只存在這支手機；解除安裝會移除資料。",18,Ui.MUTED,false),20);
        ui.spaceAdd(box,ui.text("語音管家使用裝置端辨識與本機規則模擬。對話不寫入資料庫、不保留歷史；不是醫療診斷。",18,Ui.MUTED,false),14);
        ui.spaceAdd(box,ui.secondary("官方篩檢政策",()->openUrl(PolicyEngine.CANCER_SOURCE)),12);d.show();
    }

    private LinearLayout dialogBox(){LinearLayout b=ui.col();b.setPadding(ui.dp(22),ui.dp(14),ui.dp(22),ui.dp(22));return b;}
    private AlertDialog dialog(String title,LinearLayout content){ScrollView scroll=new ScrollView(this);scroll.addView(content);return new CareDialogBuilder(this).setTitle(title).setView(scroll).setNegativeButton("關閉",null).create();}
    private EditText field(String hint){EditText e=new EditText(this);e.setTextSize(18*ui.scale);e.setTextColor(Ui.INK);e.setHintTextColor(Ui.MUTED);e.setHint(hint);e.setPadding(ui.dp(13),ui.dp(12),ui.dp(13),ui.dp(12));e.setMinHeight(ui.dp(64));e.setBackground(ui.outline(Ui.WHITE,12,Ui.LINE));e.setContentDescription(hint);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setMaxLines(4);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1500)});return e;}
    private void labeled(LinearLayout b,String title,View input){ui.spaceAdd(b,ui.text(title,13,Ui.MUTED,true),16);ui.spaceAdd(b,input,7);}
    private Spinner spinner(String...values){
        Spinner s=new Spinner(this);s.setMinimumHeight(ui.dp(64));s.setBackground(ui.outline(Ui.WHITE,12,Ui.LINE));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            private View style(View v){TextView t=(TextView)v;t.setTextSize(18*ui.scale);t.setTextColor(Ui.INK);t.setMinHeight(ui.dp(64));t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));return t;}
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){return style(super.getView(position,convert,parent));}
            @Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent){return style(super.getDropDownView(position,convert,parent));}
        };adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);return s;
    }
    private CheckBox checkbox(String text,boolean checked){CheckBox c=new CheckBox(this);c.setText(text);c.setTextColor(Ui.INK);c.setTextSize(18*ui.scale);c.setMinHeight(ui.dp(64));c.setChecked(checked);return c;}
    private void chooseDate(LocalDate initial,boolean past,java.util.function.Consumer<LocalDate> callback){
        LinearLayout box=dialogBox();
        EditText year=field("西元年，例如 1954"),month=field("月份，1 到 12"),day=field("日期，1 到 31");
        for(EditText e:new EditText[]{year,month,day}){e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);}
        year.setText(String.valueOf(initial.getYear()));month.setText(String.valueOf(initial.getMonthValue()));day.setText(String.valueOf(initial.getDayOfMonth()));
        labeled(box,"西元年",year);labeled(box,"月",month);labeled(box,"日",day);
        AlertDialog d=dialog("選擇日期",box);
        ui.spaceAdd(box,ui.button("確定日期",()->{
            try{LocalDate date=LocalDate.of(number(year,0),number(month,0),number(day,0));
                if(date.getYear()<1900||date.getYear()>2200||(past&&date.isAfter(LocalDate.now()))){toast("請確認年份；已完成的紀錄不能使用未來日期。");return;}
                d.dismiss();callback.accept(date);
            }catch(java.time.DateTimeException e){toast("日期不正確，請確認年、月、日。");}
        }),20);d.show();
    }
    private void chooseTime(LocalTime initial,java.util.function.Consumer<LocalTime> callback){
        LinearLayout box=dialogBox();EditText hour=field("小時，0 到 23"),minute=field("分鐘，0 到 59");
        hour.setInputType(InputType.TYPE_CLASS_NUMBER);minute.setInputType(InputType.TYPE_CLASS_NUMBER);
        hour.setText(String.valueOf(initial.getHour()));minute.setText(String.valueOf(initial.getMinute()));
        labeled(box,"小時（24 小時制）",hour);labeled(box,"分鐘",minute);AlertDialog d=dialog("選擇提醒時間",box);
        ui.spaceAdd(box,ui.button("確定時間",()->{try{LocalTime time=LocalTime.of(number(hour,-1),number(minute,-1));d.dismiss();callback.accept(time);}catch(java.time.DateTimeException e){toast("請輸入正確的小時與分鐘。");}}),20);d.show();
    }
    private void switchProfile(){
        LinearLayout box=dialogBox();AlertDialog d=dialog("選擇健康檔案",box);
        for(Profile person:store.profiles())ui.spaceAdd(box,ui.button(person.name+(person.id==profile.id?"（目前）":""),()->{profile=person;d.dismiss();navigate(0);}),12);
        ui.spaceAdd(box,ui.secondary("＋ 新增家人檔案",()->{d.dismiss();profileForm(null);}),16);d.show();
    }
    private void profileForm(Profile existing){
        LinearLayout box=dialogBox();info(box,"只會儲存在這支手機。新增家人資料前，請先取得本人同意。",Ui.GREEN,Ui.MINT);
        EditText name=field("姓名或暱稱");name.setSingleLine(true);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});if(existing!=null)name.setText(existing.name);labeled(box,"姓名或暱稱 *",name);
        final LocalDate[] birthday={existing==null?null:LocalDate.parse(existing.birth)};TextView date=ui.secondary(birthday[0]==null?"選擇出生日期":birthday[0].toString(),()->{});date.setOnClickListener(v->chooseDate(birthday[0]==null?LocalDate.of(1954,3,15):birthday[0],true,x->{birthday[0]=x;date.setText(x.toString());}));labeled(box,"出生日期 *（用來比對篩檢年齡）",date);
        Spinner sex=spinner("未提供","女","男");sex.setSelection(existing==null?0:existing.sex.equals("女")?1:existing.sex.equals("男")?2:0);labeled(box,"篩檢比對使用的生理性別",sex);EditText city=field("例如：台北市，不需要詳細地址");city.setSingleLine(true);if(existing!=null)city.setText(existing.city);labeled(box,"居住縣市（選填）",city);
        LinearLayout advanced=ui.col();advanced.setVisibility(View.GONE);ui.spaceAdd(box,ui.secondary("補充身份與病史（選填）  ＋",()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)),18);ui.add(box,advanced);
        CheckBox indigenous=checkbox("具原住民身分",existing!=null&&existing.indigenous),polio=checkbox("小兒麻痺病史",existing!=null&&existing.polio),colon=checkbox("父母、兄弟姊妹或子女有大腸癌病史",existing!=null&&existing.familyColon),lung=checkbox("父母、兄弟姊妹或子女有肺癌病史",existing!=null&&existing.familyLung),smokes=checkbox("目前仍吸菸",existing!=null&&existing.smokes),betel=checkbox("曾嚼檳榔（含已戒）",existing!=null&&existing.betel);
        ui.add(advanced,indigenous);ui.add(advanced,polio);ui.add(advanced,colon);ui.add(advanced,lung);ui.add(advanced,smokes);ui.add(advanced,betel);
        EditText packs=field("每天包數 × 吸菸年數，如 20");packs.setInputType(InputType.TYPE_CLASS_NUMBER);if(existing!=null&&existing.packYears>0)packs.setText(String.format(Locale.getDefault(),"%d",existing.packYears));labeled(advanced,"累積吸菸包年（整數，選填）",packs);
        EditText quit=field("已戒菸幾年；仍吸菸或未曾吸菸可留空");quit.setInputType(InputType.TYPE_CLASS_NUMBER);if(existing!=null&&existing.quitYears>=0)quit.setText(String.format(Locale.getDefault(),"%d",existing.quitYears));labeled(advanced,"戒菸年數（選填）",quit);
        CheckBox consent=checkbox("這是我的資料，或已取得本人同意",existing!=null);ui.spaceAdd(box,consent,18);AlertDialog d=dialog(existing==null?"新增健康檔案":"編輯健康檔案",box);
        ui.spaceAdd(box,ui.button("儲存健康檔案",()->{
            if(name.getText().toString().trim().isEmpty()){name.setError("請填寫姓名或暱稱");return;}if(birthday[0]==null){toast("請選擇出生日期");return;}if(birthday[0].isAfter(LocalDate.now())||birthday[0].isBefore(LocalDate.now().minusYears(130))){toast("請確認出生日期");return;}if(!consent.isChecked()){toast("請先確認資料使用同意");return;}
            int pack=number(packs,0),q=number(quit,-1);if(pack<0||pack>300||q>100){toast("請確認吸菸包年及戒菸年數");return;}
            Profile p=existing==null?new Profile():existing;p.name=name.getText().toString().trim();p.birth=birthday[0].toString();p.sex=sex.getSelectedItem().toString();p.city=city.getText().toString().trim();p.indigenous=indigenous.isChecked();p.polio=polio.isChecked();p.familyColon=colon.isChecked();p.familyLung=lung.isChecked();p.smokes=smokes.isChecked();p.betel=betel.isChecked();p.packYears=pack;p.quitYears=p.smokes?-1:q;
            try{store.save(p);profile=p;d.dismiss();page=0;render();toast("健康檔案已儲存在手機");}catch(Exception e){toast("儲存失敗，請再試一次");}
        }),16);
        if(existing!=null)ui.spaceAdd(box,ui.button("刪除此檔案",Ui.PEACH,Ui.RED,()->new CareDialogBuilder(this).setTitle("刪除 "+existing.name+" 的資料？").setMessage("此檔案、所有紀錄與提醒將從本機永久刪除，無法復原。建議先匯出備份。").setNegativeButton("保留",null).setPositiveButton("刪除",(a,b)->{for(Appointment ap:store.appointments(existing.id))ReminderScheduler.cancel(this,ap.id);store.deleteProfile(existing.id);List<Profile> people=store.profiles();profile=people.isEmpty()?null:people.get(0);d.dismiss();page=0;render();}).show()),12);d.show();
    }
    private int number(EditText e,int defaultValue){try{return e.getText().toString().trim().isEmpty()?defaultValue:Integer.parseInt(e.getText().toString().trim());}catch(NumberFormatException x){return Integer.MAX_VALUE;}}

    private void recordForm(PolicyEngine.Policy selected){recordForm(selected,()->{});}
    private void recordForm(PolicyEngine.Policy selected,Runnable afterSave){
        LinearLayout box=dialogBox();final long owner=profile.id;final LocalDate[] date={LocalDate.now()};Spinner type=spinner("日常照護筆記","血壓量測紀錄","已完成的檢查");type.setSelection(selected==null?0:2);labeled(box,"紀錄類型",type);
        List<PolicyEngine.Policy> all=PolicyEngine.forProfile(profile,LocalDate.now());String[] names=new String[all.size()];int index=0;for(int i=0;i<all.size();i++){names[i]=all.get(i).title;if(selected!=null&&selected.id.equals(all.get(i).id))index=i;}
        LinearLayout policyFields=ui.col();Spinner policy=spinner(names);policy.setSelection(index);labeled(policyFields,"檢查項目",policy);ui.add(box,policyFields);
        EditText title=field("例如：今天陪媽媽散步");LinearLayout titleFields=ui.col();labeled(titleFields,"標題",title);ui.add(box,titleFields);
        LinearLayout bpFields=ui.col();EditText sys=field("收縮壓 mmHg"),dia=field("舒張壓 mmHg");sys.setInputType(InputType.TYPE_CLASS_NUMBER);dia.setInputType(InputType.TYPE_CLASS_NUMBER);labeled(bpFields,"收縮壓",sys);labeled(bpFields,"舒張壓",dia);ui.spaceAdd(bpFields,ui.text("僅記錄數值，不自動診斷。若有身體不適，請就醫。",12,Ui.MUTED,false),8);ui.add(box,bpFields);
        Runnable visibility=()->{int t=type.getSelectedItemPosition();policyFields.setVisibility(t==2?View.VISIBLE:View.GONE);titleFields.setVisibility(t==0?View.VISIBLE:View.GONE);bpFields.setVisibility(t==1?View.VISIBLE:View.GONE);};visibility.run();type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){visibility.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        TextView dateBtn=ui.secondary(date[0].toString(),()->{});dateBtn.setOnClickListener(v->chooseDate(date[0],true,x->{date[0]=x;dateBtn.setText(x.toString());}));labeled(box,"日期",dateBtn);EditText note=field("檢查地點、醫師交代或想記下的事");note.setMinLines(3);note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);labeled(box,"備註（選填）",note);AlertDialog d=dialog("新增健康紀錄",box);
        ui.spaceAdd(box,ui.button("儲存這筆紀錄",()->{
            if(date[0].isAfter(LocalDate.now())){toast("完成紀錄不能使用未來日期");return;}HealthRecord r=new HealthRecord();r.profileId=owner;r.date=date[0].toString();r.note=note.getText().toString().trim();int t=type.getSelectedItemPosition();
            if(t==0){r.title=title.getText().toString().trim();if(r.title.isEmpty()){title.setError("請填寫標題");return;}}
            else if(t==1){int s=number(sys,0),di=number(dia,0);if(s<30||s>300||di<20||di>200||s<=di){toast("請確認血壓數值與順序");return;}r.kind="bp";r.title="血壓量測 "+s+" / "+di+" mmHg";}
            else{PolicyEngine.Policy pol=all.get(policy.getSelectedItemPosition());r.kind="screening";r.key=pol.id;r.title=pol.title;}
            try{store.add(r);afterSave.run();d.dismiss();render();toast("紀錄已儲存");}catch(Exception e){toast("儲存失敗，請稍後再試");}
        }),20);d.show();
    }
    private void appointmentForm(PolicyEngine.Policy p){
        LinearLayout box=dialogBox();info(box,"這是手機內的行程提醒，不會替你向院所預約。通知時間可能受 Android 省電設定影響。",Ui.MUTED,Ui.MINT);
        EditText title=field("例如：至診所領取採便管");if(p!=null)title.setText(p.title);labeled(box,"提醒名稱 *",title);final LocalDate[] date={LocalDate.now().plusDays(1)};final LocalTime[] time={LocalTime.of(9,0)};
        TextView dateBtn=ui.secondary(date[0].toString(),()->{});dateBtn.setOnClickListener(v->chooseDate(date[0],false,x->{date[0]=x;dateBtn.setText(x.toString());}));labeled(box,"提醒日期",dateBtn);
        TextView timeBtn=ui.secondary("09:00",()->{});timeBtn.setOnClickListener(v->chooseTime(time[0],value->{time[0]=value;timeBtn.setText(value.toString());}));labeled(box,"提醒時間",timeBtn);EditText place=field("地點或備註，選填");labeled(box,"地點",place);final long owner=profile.id;AlertDialog d=dialog("安排健康提醒",box);
        ui.spaceAdd(box,ui.button("加入手機提醒",()->{
            if(title.getText().toString().trim().isEmpty()){title.setError("請輸入提醒名稱");return;}long at=LocalDateTime.of(date[0],time[0]).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();if(at<=System.currentTimeMillis()){toast("請選擇未來的提醒時間");return;}
            Appointment a=new Appointment();a.profileId=owner;a.title=title.getText().toString().trim();a.key=p==null?"":p.id;a.at=at;a.place=place.getText().toString().trim();
            try{store.add(a);ReminderScheduler.schedule(this,a);d.dismiss();render();requestNotifications();toast("行程已儲存；通知需系統權限開啟");}catch(Exception e){toast("提醒儲存失敗，請稍後再試");}
        }),20);d.show();
    }
    private void showReminders(){
        if(profile==null){toast("請先建立健康檔案");return;}LinearLayout box=dialogBox();NotificationManager manager=getSystemService(NotificationManager.class);boolean enabled=prefs.getBoolean("notifications",true)&&manager.areNotificationsEnabled();info(box,enabled?"通知已開啟 · 鎖定畫面不顯示健康細節":"目前無法發送通知。可在設定開啟；已存行程仍保留。",Ui.MUTED,Ui.MINT);
        AlertDialog d=dialog(profile.name+" 的提醒",box);ui.spaceAdd(box,ui.button("＋ 新增健康行程",()->{d.dismiss();appointmentForm(null);}),15);List<Appointment> appointments=store.appointments(profile.id);
        if(appointments.isEmpty())ui.spaceAdd(box,ui.text("還沒有行程。先安排一個適合自己的時間吧。",15,Ui.MUTED,false),20);
        for(Appointment a:appointments){LinearLayout card=ui.card();ui.add(card,ui.text(a.title,17,Ui.INK,true));String when=Instant.ofEpochMilli(a.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));ui.spaceAdd(card,ui.text(when+(a.place.isEmpty()?"":"\n"+a.place),14,Ui.MUTED,false),6);ui.spaceAdd(card,ui.label(a.notified?"已發送通知":a.at<System.currentTimeMillis()?"時間已到":"等待提醒",Ui.ORANGE,Ui.PEACH),10);
            PolicyEngine.Policy policy=PolicyEngine.find(profile,a.key);if(policy!=null)ui.spaceAdd(card,ui.secondary("登錄完成",()->{d.dismiss();recordForm(policy,()->{ReminderScheduler.cancel(this,a.id);store.deleteAppointment(a.id);});}),14);
            ui.spaceAdd(card,ui.button("移除此提醒",0xffF4F5F1,Ui.MUTED,()->new CareDialogBuilder(this).setTitle("移除這個提醒？").setMessage("僅移除手機內的提醒，不會取消醫療院所預約。").setNegativeButton("保留",null).setPositiveButton("移除",(x,y)->{ReminderScheduler.cancel(this,a.id);store.deleteAppointment(a.id);d.dismiss();render();showReminders();}).show()),8);ui.spaceAdd(box,card,14);
        }d.show();
    }
    private void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);}
    private void shareSummary(){new CareDialogBuilder(this).setTitle("分享健康摘要").setMessage("摘要包含「"+profile.name+"」的姓名、年齡及健康紀錄。請確認已取得本人同意，再選擇接收的應用程式或家人。").setNegativeButton("取消",null).setPositiveButton("選擇分享對象",(d,w)->{Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,store.summary(profile));try{startActivity(Intent.createChooser(send,"分享健康摘要"));}catch(ActivityNotFoundException e){toast("手機沒有可用的分享應用程式");}}).show();}
    private void exportBackup(){new CareDialogBuilder(this).setTitle("匯出包含健康資訊的備份").setMessage("JSON 檔案未額外加密。請選擇安全的位置，不要上傳到公開空間。這個版本支援匯出，尚不支援自動還原。").setNegativeButton("取消",null).setPositiveButton("選擇儲存位置",(d,w)->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"guhealth-"+LocalDate.now()+".json");try{startActivityForResult(i,EXPORT);}catch(ActivityNotFoundException e){toast("找不到檔案儲存工具");}}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==REPORT&&result==RESULT_OK){render();return;}if(request==EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException();out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));toast("備份已匯出，請妥善保管");}catch(Exception e){toast("匯出失敗，請檢查儲存位置後再試");}}}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("找不到可開啟連結的瀏覽器");}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    private void vision(){
        heading("居家觀察","一次看一眼，記下自己的視覺變化。");
        info(body,"這不是視力檢查或醫療診斷。看得清楚，也不能排除眼睛疾病。",Ui.ORANGE,Ui.PEACH);
        ui.gap(body,24);ui.add(body,ui.button("開始方格觀察",this::startVision));
        ui.spaceAdd(body,ui.secondary("前鏡頭輔助對位",this::frontCamera),16);
        ui.spaceAdd(body,ui.text("相機只顯示即時畫面，不拍照、不儲存、不上傳。可以完全不使用相機。",18,Ui.MUTED,false),12);
        ui.gap(body,24);info(body,"若突然視力下降、視線扭曲或出現黑影，請儘速就醫，不要等待下一次觀察。",Ui.ORANGE,Ui.PEACH);
        ui.spaceAdd(body,ui.secondary("尋找附近眼科",()->findClinics("眼科")),16);
    }
    private void startVision(){visionMode=1;visionStep=0;eyeIndex=0;leftObservation="";rightObservation="";visionProfileId=profile.id;render();}
    private void visionFlow(){
        if(visionStep==0){
            heading("開始前，準備一下","配戴平常閱讀用的眼鏡，在光線充足的地方坐好。");
            ui.add(body,ui.text("手機放在舒適的閱讀距離。一次觀察一眼，輕遮另一眼，不要壓眼球。",18,Ui.INK,false));
            ui.gap(body,24);info(body,"這個數位方格未經臨床校準，只記錄您的主觀感受，不能診斷或排除疾病。",Ui.ORANGE,Ui.PEACH);
            ui.spaceAdd(body,ui.button("我已了解，開始",()->{visionStep=1;render();}),24);
            ui.spaceAdd(body,ui.secondary("返回居家觀察",()->{visionMode=0;render();}),12);return;
        }
        boolean changed=!leftObservation.equals(getString(R.string.vision_clear))||!rightObservation.equals(getString(R.string.vision_clear));
        heading("這次的觀察","您可以選擇是否留下紀錄。");
        String detail="左眼："+leftObservation+"\n右眼："+rightObservation;ui.add(body,ui.text(detail,24,Ui.INK,true));ui.gap(body,24);
        info(body,changed?"您回報有變化或看不清楚，請聯絡眼科；若是突然出現，請儘速就醫。":"看得清楚仍不能排除疾病，請依眼科醫師建議持續追蹤。",changed?Ui.ORANGE:Ui.INK,changed?Ui.PEACH:Ui.MINT);
        ui.spaceAdd(body,ui.button("儲存到我的紀錄",()->{
            if(store.profile(visionProfileId)==null){toast("健康檔案不存在，無法儲存");visionMode=0;render();return;}
            HealthRecord r=new HealthRecord();r.profileId=visionProfileId;r.kind="vision";r.key="amsler";r.title="方格視覺自我觀察";r.date=LocalDate.now().toString();r.note=detail+"\n主觀觀察，未經臨床校準，非診斷結果。";r.flag=changed;
            try{store.add(r);visionMode=0;page=2;render();toast("觀察已儲存");}catch(Exception e){toast("儲存失敗，請再試一次");}
        }),24);
        ui.spaceAdd(body,ui.secondary("不儲存，返回",()->{visionMode=0;render();}),12);
    }
    private void renderVisionTest(){
        View test=getLayoutInflater().inflate(R.layout.view_vision_test,content,false);content.addView(test);
        ((TextView)test.findViewById(R.id.vision_instruction)).setText(eyeInstruction());
        test.findViewById(R.id.vision_clear).setOnClickListener(v->answerEye(getString(R.string.vision_clear)));
        test.findViewById(R.id.vision_changed).setOnClickListener(v->answerEye(getString(R.string.vision_changed)));announceEye();
    }
    private String eyeInstruction(){return eyeIndex==0?"請遮住右眼\n用左眼看中央黑點":"請遮住左眼\n用右眼看中央黑點";}
    private void announceEye(){
        if(visionMode==0||visionStep!=1)return;
        if(visionSpeaker==null){
            visionSpeaker=new OfflineSpeaker(this,ready->{
                if(isFinishing()||isDestroyed())return;
                if(ready&&hasWindowFocus()&&visionMode>0&&visionStep==1)visionSpeaker.speakPublic(eyeInstruction());
                else if(!ready)toast("手機沒有可用的離線中文朗讀，請依畫面操作。");
            });
        }else if(visionSpeaker.isReady())visionSpeaker.speakPublic(eyeInstruction());
    }
    private void answerEye(String answer){
        if(visionSpeaker!=null)visionSpeaker.stop();
        if(eyeIndex==0){leftObservation=answer;eyeIndex=1;}else{rightObservation=answer;visionStep=2;}render();
    }
    private void frontCamera(){if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)){toast("這個裝置沒有前鏡頭，仍可進行自我觀察");return;}if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){new CareDialogBuilder(this).setTitle("使用前鏡頭對位").setMessage("只顯示即時畫面；不拍照、不儲存、不分析、不上傳。你也可以不使用相機完成觀察。").setNegativeButton("不用相機",null).setPositiveButton("允許對位",(d,w)->requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA)).show();}else new CameraPreviewDialog(this).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grant){super.onRequestPermissionsResult(request,permissions,grant);if(request==44&&voiceSheet!=null){voiceSheet.onMicrophonePermission(grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED);return;}if(request==CAMERA){if(grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED)new CameraPreviewDialog(this).show();else toast("未啟用相機，仍可使用所有自我觀察功能");}if(request==NOTIFICATIONS){if(grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED){ReminderScheduler.restore(this);toast("Android 通知權限已開啟");}else toast("通知未授權；行程仍保存在 APP，可到設定開啟");}}
}
