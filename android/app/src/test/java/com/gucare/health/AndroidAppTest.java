package com.gucare.health;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.speech.tts.Voice;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="zh-rTW-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AndroidAppTest {
    private Application context;
    @Before public void reset(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("guhealth.db");context.getSharedPreferences("prefs",0).edit().clear().commit();}
    @Test public void databasePersistsAndProfilesStaySeparate() throws Exception {
        long firstId;
        try(HealthStore db=new HealthStore(context)){
            Profile first=new Profile();first.name="測試甲";firstId=db.save(first);Profile second=new Profile();second.name="測試乙";db.save(second);
            HealthRecord r=new HealthRecord();r.profileId=first.id;r.kind="screening";r.key="colon";r.title="糞便潛血檢查";r.date="2026-08-01";db.add(r);
            Appointment a=new Appointment();a.profileId=first.id;a.at=System.currentTimeMillis()+60000;a.title="測試提醒";db.add(a);
            assertEquals(0,db.records(second.id).size());assertTrue(db.exportJson().contains("測試甲"));
        }
        try(HealthStore reopened=new HealthStore(context)){
            assertEquals(1,reopened.records(firstId).size());assertEquals(LocalDate.of(2026,8,1),reopened.lastScreening(firstId,"colon"));
            reopened.deleteProfile(firstId);assertEquals(0,reopened.records(firstId).size());assertEquals(0,reopened.appointments(firstId).size());assertEquals(1,reopened.profiles().size());
        }
    }
    @Test public void freshInstallAndThreeChineseTabsRender() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();assertNotNull(text(a,"歡迎使用顧健康"));screenshot(a,"01-welcome");
            clickText(a,"先用示範資料體驗");screenshot(a,"02-home");
            BottomNavigationView nav=a.findViewById(R.id.bottom_navigation);
            assertEquals(3,nav.getMenu().size());assertEquals("首頁",nav.getMenu().getItem(0).getTitle());
            assertEquals("居家觀察",nav.getMenu().getItem(1).getTitle());assertEquals("我的紀錄",nav.getMenu().getItem(2).getTitle());
            assertNotNull(a.findViewById(R.id.mascot_greeting));assertNull(text(a,"待辦提醒"));
            assertNotNull(a.findViewById(R.id.fab_ai_voice));assertTouchAndText(a.getWindow().getDecorView(),a);
            tab(a,R.id.nav_vision);assertNotNull(text(a,"開始方格觀察"));assertNull(a.findViewById(R.id.fab_ai_voice));screenshot(a,"03-vision");
            tab(a,R.id.nav_records);assertNotNull(text(a,"＋ 新增健康紀錄"));screenshot(a,"04-records");
            a.findViewById(R.id.toolbar_settings).performClick();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());assertNotNull(find(dialog.getWindow().getDecorView(),"更大字體",false));dialog.dismiss();
        }
    }
    @Test public void visionHasExactlyTwoLargeAnswersAndSavesBothEyes() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=controller.get();clickText(a,"先用示範資料體驗");tab(a,R.id.nav_vision);clickText(a,"開始方格觀察");clickText(a,"我已了解，開始");
            screenshot(a,"05-immersive-vision");
            assertEquals(View.GONE,a.findViewById(R.id.toolbar).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.bottom_navigation).getVisibility());
            assertEquals(2,countClickables(a.findViewById(R.id.screen_content)));
            assertTrue(a.findViewById(R.id.vision_clear).getHeight()>=80*a.getResources().getDisplayMetrics().density);
            assertTrue(a.findViewById(R.id.vision_changed).getHeight()>=80*a.getResources().getDisplayMetrics().density);
            assertTrue(((TextView)a.findViewById(R.id.vision_instruction)).getText().toString().contains("遮住右眼"));
            a.findViewById(R.id.vision_clear).performClick();
            assertTrue(((TextView)a.findViewById(R.id.vision_instruction)).getText().toString().contains("遮住左眼"));
            a.findViewById(R.id.vision_changed).performClick();clickText(a,"儲存到我的紀錄");
            assertEquals(View.VISIBLE,a.findViewById(R.id.bottom_navigation).getVisibility());
            try(HealthStore db=new HealthStore(context)){HealthRecord result=db.records(db.profiles().get(0).id).get(0);assertEquals("amsler",result.key);assertTrue(result.flag);assertTrue(result.note.contains("左眼：看得很清楚"));assertTrue(result.note.contains("右眼：線條歪歪的／看不清楚"));assertFalse(result.note.contains("健康分"));}
        }
    }
    @Test public void eyeProgressSurvivesRecreation(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();clickText(a,"先用示範資料體驗");tab(a,R.id.nav_vision);clickText(a,"開始方格觀察");clickText(a,"我已了解，開始");a.findViewById(R.id.vision_clear).performClick();c.recreate();
            assertTrue(((TextView)c.get().findViewById(R.id.vision_instruction)).getText().toString().contains("遮住左眼"));
        }
    }
    @Test public void cardActionsUseDialAndMapsNotDirectCalls() throws Exception {
        try(HealthStore db=new HealthStore(context)){db.demo();}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();screenshot(a,"06-home-actions");
            assertNotNull(text(a,"大腸癌篩檢"));assertNull(text(a,"糞便潛血檢查"));
            assertTrue(((TextView)a.findViewById(R.id.screening_title)).getTextSize()>=24*a.getResources().getDisplayMetrics().density);
            a.findViewById(R.id.btn_dial).performClick();Intent dial=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(Intent.ACTION_DIAL,dial.getAction());assertEquals("tel",dial.getData().getScheme());
            a.findViewById(R.id.btn_maps).performClick();Intent maps=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(Intent.ACTION_VIEW,maps.getAction());assertEquals("com.google.android.apps.maps",maps.getPackage());assertFalse(maps.getDataString().contains("林媽媽"));
        }
    }
    @Test public void existingV1RecordsAndReminderIdsAreNotMigratedOrDeleted(){
        long owner;try(HealthStore db=new HealthStore(context)){owner=db.demo().id;assertEquals(1,db.getReadableDatabase().getVersion());}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){tab(c.get(),R.id.nav_records);assertNotNull(text(c.get(),"陪媽媽一起散步"));}
        try(HealthStore db=new HealthStore(context)){assertEquals(2,db.records(owner).size());assertEquals(1,db.getReadableDatabase().getVersion());}
    }
    @Test public void voiceNeverPersistsTranscriptOrResponseAndClearsResultBundle() throws Exception {
        try(HealthStore db=new HealthStore(context)){db.demo();}
        String before;try(HealthStore db=new HealthStore(context)){before=db.exportJson();}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            VoiceConsultationSheet sheet=new VoiceConsultationSheet(c.get(),()->{});sheet.show();
            assertTrue((sheet.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0);
            Bundle result=new Bundle();ArrayList<String> candidates=new ArrayList<>(Arrays.asList("我的隱私電話0912999888，我的眼睛模糊"));result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,candidates);
            sheet.consumeResults(result);assertTrue(result.isEmpty());assertTrue(candidates.isEmpty());assertNull(find(sheet.getWindow().getDecorView(),"我的隱私電話0912999888，我的眼睛模糊",false));sheet.dismiss();
            Bundle late=new Bundle();late.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList("遲到的私人文字")));sheet.consumeResults(late);assertTrue(late.isEmpty());
        }
        try(HealthStore db=new HealthStore(context)){assertEquals(before,db.exportJson());}
    }
    @Test public void speakerWipesCallerBufferEvenWhenUnavailable(){
        OfflineSpeaker speaker=new OfflineSpeaker(context,ready->{});
        char[] answer="私人回答內容".toCharArray();speaker.speakEphemeral(answer,null);
        for(char ch:answer)assertEquals('\0',ch);speaker.close();
    }
    @Test public void shortLandscapeKeepsBothAnswerTargetsAtLeast80dp(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();clickText(a,"先用示範資料體驗");tab(a,R.id.nav_vision);clickText(a,"開始方格觀察");clickText(a,"我已了解，開始");
            View decor=a.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));decor.layout(0,0,2400,960);
            float density=a.getResources().getDisplayMetrics().density;
            assertTrue(a.findViewById(R.id.vision_clear).getHeight()>=80*density);assertTrue(a.findViewById(R.id.vision_changed).getHeight()>=80*density);
            assertTrue(a.findViewById(R.id.observation_grid).getHeight()>0);
        }
    }
    @Test public void offlineVoiceSelectionRejectsNetworkAndWrongLocale(){
        Voice online=new Voice("online",Locale.TAIWAN,300,300,true,new HashSet<>());
        Voice local=new Voice("local",Locale.TAIWAN,300,300,false,new HashSet<>());
        Voice english=new Voice("local-en",Locale.US,300,300,false,new HashSet<>());
        assertFalse(OfflineSpeaker.isPrivateTaiwanVoice(online));assertTrue(OfflineSpeaker.isPrivateTaiwanVoice(local));assertFalse(OfflineSpeaker.isPrivateTaiwanVoice(english));
    }
    @Test public void manifestHasNoInternetOrCallPermission() throws Exception {
        String[] permissions=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertFalse(Arrays.asList(permissions).contains(android.Manifest.permission.INTERNET));assertFalse(Arrays.asList(permissions).contains(android.Manifest.permission.CALL_PHONE));
        assertTrue(Arrays.asList(permissions).contains(android.Manifest.permission.RECORD_AUDIO));
    }
    @Test public void reminderSchedulesAndUsesGenericNotification(){
        Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        Appointment a=new Appointment();try(HealthStore db=new HealthStore(context)){a.profileId=db.demo().id;a.title="不能出現在鎖定畫面的健康詳情";a.at=System.currentTimeMillis()+60000;db.add(a);}
        ReminderScheduler.channel(context);ReminderScheduler.schedule(context,a);
        assertNotNull(Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getNextScheduledAlarm());
        new ReminderReceiver().onReceive(context,new Intent().putExtra("appointmentId",a.id));
        android.app.Notification n=Shadows.shadowOf(context.getSystemService(NotificationManager.class)).getNotification((int)a.id);
        assertNotNull(n);assertEquals("今天有一個健康行程",n.extras.getString(android.app.Notification.EXTRA_TITLE));
        try(HealthStore db=new HealthStore(context)){assertTrue(db.appointment(a.id).notified);}
        ReminderScheduler.cancel(context,a.id);
    }
    @Test public void reportSavesOnlyExplicitSummaryAndKeepsItPrivate() throws Exception {
        long owner, other;
        try(HealthStore db=new HealthStore(context)){
            owner=db.demo().id;
            Profile another=new Profile();another.name="另一位家人";other=db.save(another);
        }
        Intent launch=new Intent(context,ReportActivity.class).putExtra(ReportActivity.PROFILE_ID,owner);
        long savedId;
        try(ActivityController<ReportActivity> c=Robolectric.buildActivity(ReportActivity.class,launch).setup().visible()){
            ReportActivity a=c.get();
            assertTrue((a.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0);
            android.widget.EditText reading=hint(a.getWindow().getDecorView(),"例如 5.8");
            reading.setText("179.25");
            hint(a.getWindow().getDecorView(),"例如 mg/dL、％ 或無單位").setText("mg/dL");
            hint(a.getWindow().getDecorView(),"例如 70–99 或 ≤100").setText("<105.75");
            clickText(a,"查看白話解說");
            View skip=text(a,"先看基本解說");if(skip!=null)skip.performClick();
            assertNotNull(text(a,"偏高"));assertNotNull(text(a,"檢驗結果：179.25 mg/dL\n原報告參考值：<105.75"));
            try(HealthStore db=new HealthStore(context)){assertEquals(2,db.records(owner).size());}
            View save=text(a,"保存摘要");save.performClick();save.performClick();
            try(HealthStore db=new HealthStore(context)){
                assertEquals(3,db.records(owner).size());assertEquals(0,db.records(other).size());
                HealthRecord record=db.records(owner).get(0);savedId=record.id;
                assertEquals(HealthRecord.REPORT_SUMMARY,record.kind);assertTrue(record.note.contains("空腹血糖｜偏高"));
                assertFalse(record.note.contains("179.25"));assertFalse(record.note.contains("105.75"));
                assertFalse(db.summary(db.profile(owner)).contains("空腹血糖｜偏高"));
                assertFalse(db.exportJson().contains(HealthRecord.REPORT_SUMMARY));
            }
            c.pause().stop();assertEquals(0,reading.length());assertTrue(a.isFinishing());
        }
        Intent detail=new Intent(context,ReportActivity.class).putExtra(ReportActivity.PROFILE_ID,owner)
                .putExtra(ReportActivity.RECORD_ID,savedId);
        try(ActivityController<ReportActivity> c=Robolectric.buildActivity(ReportActivity.class,detail).setup().visible()){
            assertNotNull(text(c.get(),"白話文健檢摘要"));assertNotNull(text(c.get(),"刪除摘要"));
        }
        detail.putExtra(ReportActivity.PROFILE_ID,other);
        try(ActivityController<ReportActivity> c=Robolectric.buildActivity(ReportActivity.class,detail).setup()){
            assertTrue(c.get().isFinishing());assertNull(text(c.get(),"白話文健檢摘要"));
        }
    }
    @Test public void reportFormKeepsLargeTextAndTouchTargets() throws Exception {
        context.getSharedPreferences("prefs",0).edit().putBoolean("large",true).commit();
        long owner;try(HealthStore db=new HealthStore(context)){owner=db.demo().id;}
        Intent intent=new Intent(context,ReportActivity.class).putExtra(ReportActivity.PROFILE_ID,owner);
        try(ActivityController<ReportActivity> c=Robolectric.buildActivity(ReportActivity.class,intent).setup().visible()){
            ReportActivity a=c.get();
            screenshot(a,"08-report-large-font");
            assertTouchAndText(a.getWindow().getDecorView(),a);
            assertNotNull(text(a,"查看白話解說"));
        }
    }
    @Test public void largeFontStillRenders() throws Exception {
        context.getSharedPreferences("prefs",0).edit().putBoolean("large",true).commit();try(HealthStore db=new HealthStore(context)){db.demo();}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            screenshot(c.get(),"07-large-font");assertNotNull(c.get().findViewById(R.id.mascot_greeting));assertTouchAndText(c.get().getWindow().getDecorView(),c.get());
        }
    }
    private static void tab(MainActivity a,int id){((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(id);}
    private static int countClickables(View v){int count=v.isClickable()?1:0;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)count+=countClickables(g.getChildAt(i));}return count;}
    @SuppressWarnings("deprecation")
    private static void assertTouchAndText(View v,Activity activity){
        if(v.getVisibility()!=View.VISIBLE)return;
        if(v instanceof TextView){TextView t=(TextView)v;if(t.getText().length()>0)assertTrue("Small text: "+t.getText(),t.getTextSize()+.1>=18*activity.getResources().getDisplayMetrics().scaledDensity);}
        if(v.isClickable())assertTrue("Small touch target: "+v.getClass().getName(),v.getHeight()>=64*activity.getResources().getDisplayMetrics().density);
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)assertTouchAndText(group.getChildAt(i),activity);}
    }
    private static View find(View v,String value,boolean description){if(description&&value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(!description&&v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=find(g.getChildAt(i),value,description);if(result!=null)return result;}}return null;}
    private static View text(Activity a,String t){return find(a.getWindow().getDecorView(),t,false);}
    private static android.widget.EditText hint(View view,String text){
        if(view instanceof android.widget.EditText&&text.equals(((android.widget.EditText)view).getHint()))return (android.widget.EditText)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
            android.widget.EditText match=hint(group.getChildAt(i),text);if(match!=null)return match;
        }}
        return null;
    }
    private static void clickText(Activity a,String t){View v=text(a,t);assertNotNull("Missing text: "+t,v);assertTrue(v.performClick());}
    private static void clickDescription(Activity a,String t){View v=find(a.getWindow().getDecorView(),t,true);assertNotNull("Missing description: "+t,v);assertTrue(v.performClick());}
    private static void screenshot(Activity a,String name) throws Exception {
        View decor=a.getWindow().getDecorView();int width=1233,height=2673;decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));File dir=new File("build/screenshots");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Could not create screenshots");try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
