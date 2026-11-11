package com.gucare.health;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;

/** Fully offline, non-diagnostic guided self observations. No network permissions. */
public final class SelfCheckActivity extends Activity {
    public static final String PROFILE_ID="profileId";
    private static final String[] NAMES={"畫鐘測驗","FAST 中風辨識","小腿手指圈","五次坐站","睜眼單腳平衡","法倫氏手腕測驗","直抬腿症狀紀錄","指甲回血"};
    private static final String[] INTRO={
        "畫一個完整時鐘，包含 1 到 12 及 10 點 10 分的指針，請家屬協助觀察。",
        "辨識突然發生的嘴角下垂、單側手臂無力、口齒不清。任何突然出現的異常都應立即撥打 119。",
        "坐著以雙手食指與拇指圈住非慣用腳小腿最粗處。",
        "在穩固靠牆的椅子上，安全地完成五次站起及坐下，請家屬陪同。",
        "在扶手旁、有人陪同且睜眼的情況下單腳站立，最多 30 秒。",
        "輕貼雙手手背並舒服地彎曲手腕，最多 60 秒，觀察手部麻刺感。",
        "僅紀錄已有的放射痛症狀，切勿請人強行拉抬腿部。",
        "在溫暖、明亮環境中按壓指甲床約 5 秒，鬆開後觀察恢復原色的秒數。"
    };
    private static final String[] SAFETY={
        "此項不能診斷或排除失智，教育、視力及動作能力都可能影響表現。",
        "突發單側無力、語言困難、嘴歪，或突然失衡與視力異常：立即撥打 119，記錄最後正常時間，不要自行開車。",
        "手指大小、水腫、體型均影響結果；不能單憑本項確診肌少症。",
        "若胸痛、暈眩、腿痛、最近跌倒或無法安全站起，請停止，不要勉強。",
        "不要閉眼進行；容易跌倒者應跳過。不用這項測驗辨識微型中風。",
        "切勿強行彎曲手腕；疼痛或麻刺即停止。陽性不能單獨確診腕隧道症。",
        "新出現大小便控制異常、會陰麻木或進行性雙腿無力，應立即急診。",
        "冷環境、年齡、光照及指甲油都影響結果；不能單憑回血時間診斷休克。"
    };
    private static final String[] ADVICE={
        "若近期記憶或日常功能退化，請安排記憶門診評估；畫鐘正常也不能排除問題。",
        "只要突然出現 FAST 任一異常，即使後來恢復，仍需立即撥打 119。",
        "如手指交疊，或伴隨肌力下降、體重減輕，可與醫師討論肌少症評估。",
        "完成時間較長或需扶持，可與醫師或物理治療師討論下肢功能；單一秒數不是診斷。",
        "若反覆跌倒或平衡明顯退步，請安排跌倒風險評估；突發失衡需緊急求救。",
        "若反覆手麻、夜間惡化或抓握無力，可詢問復健科或神經內科。",
        "放射痛可能與神經根刺激有關，但不能只以角度確診椎間盤突出。",
        "回血較慢可能有多種原因；若持續疼痛、明顯變色或全身不適，請就醫。"
    };
    private static final String[][] QUESTIONS={
        {"畫出 1–12 與 10:10 的時鐘","鐘面、數字及兩支指針是否完整？","數字與指針位置是否大致正確？"},
        {"F：微笑時突然單側嘴角下垂？","A：雙臂前舉約 10 秒，突然有一側無力下垂？","S：突然說話含糊、難以理解或無法說話？","T：請記下最後一次正常的時間，並立即求救（如有異常）。"},
        {"手指環繞小腿最粗處的情況？"},
        {"完成五次站起坐下並停止計時","能否安全完成？"},
        {"腳放下或需要支撐時停止計時","過程需要扶持嗎？"},
        {"最多維持 60 秒，症狀出現即停止","是否出現拇指、食指、中指等麻刺？"},
        {"是否有從臀部向膝下或腳部延伸的疼痛、麻木？","是否有進行性無力、會陰麻木或新發生排尿排便異常？"},
        {"回復原色時停止計時","是否持續冰冷、疼痛或變色？"}
    };
    private static final String[][][] OPTIONS={
        {null,{"完整","不完整","不確定"},{"大致正確","不正確","不確定"}},
        {{"沒有","有","不確定"},{"沒有","有","不確定"},{"沒有","有","不確定"},null},
        {{"指尖碰不到","剛好碰到","指尖交疊","無法測量"}},
        {null,{"完成且沒有扶持","完成但需要扶持","無法完成"}},
        {null,{"自行完成","需要扶持","無法安全進行"}},
        {null,{"沒有","有","不確定"}},
        {{"沒有","有","不確定"},{"沒有","有","不確定"}},
        {null,{"沒有","有","不確定"}}
    };
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<String> answers=new ArrayList<>();
    private Ui ui;
    private LinearLayout body;
    private int test=-1,step=0;
    private long profileId;
    private long started;
    private boolean ticking;
    private TextView timerLabel;
    private Drawing drawing;
    private boolean flagged;
    @Override public void onCreate(Bundle b){super.onCreate(b);profileId=getIntent().getLongExtra(PROFILE_ID,0);ui=new Ui(this,getSharedPreferences("prefs",MODE_PRIVATE).getBoolean("large",false));showList();}
    @Override protected void onStop(){stopClock();super.onStop();}
    private TextView text(String s){return ui.text(s,18,Ui.INK,false);}
    private void add(String s){ui.spaceAdd(body,text(s),14);}
    private void button(String s,Runnable action){ui.spaceAdd(body,ui.button(s,action),14);}
    private void secondary(String s,Runnable action){ui.spaceAdd(body,ui.secondary(s,action),12);}
    private void layout(String title){ScrollView scroll=new ScrollView(this);body=ui.col();body.setPadding(ui.dp(20),ui.dp(28),ui.dp(20),ui.dp(40));scroll.addView(body);setContentView(scroll);ui.add(body,ui.title(title));ui.gap(body,14);}
    private void showList(){stopClock();test=-1;layout("居家健康自我觀察");add("八項居家觀察，僅記錄主觀變化，不能取代醫師檢查。所有資料只留在手機。");
        add("突然嘴歪、單側無力、說話異常或突然無法保持平衡：立即撥打 119。");
        for(int i=0;i<NAMES.length;i++){final int n=i;button(NAMES[i],()->startTest(n));}
        secondary("返回居家觀察",this::finish);
    }
    private void startTest(int n){test=n;step=0;answers.clear();for(int i=0;i<QUESTIONS[n].length;i++)answers.add("");renderStep();}
    private void renderStep(){stopClock();layout(NAMES[test]);add(INTRO[test]);add("注意："+SAFETY[test]);add("第 "+(step+1)+" / "+QUESTIONS[test].length+" 步");ui.add(body,ui.text(QUESTIONS[test][step],23,Ui.INK,true));
        String[] options=OPTIONS[test][step];
        if(test==0&&step==0){drawing=new Drawing();ui.spaceAdd(body,drawing,14);add("以手指畫鐘。不會自動評分或保存畫布圖像。");secondary("清除重畫",()->drawing.clear());}
        else if((test==3||test==4||test==5||test==7)&&step==0){showTimer();}
        else if(options!=null){for(String opt:options){button(opt,()->{answers.set(step,opt);if(test==1&&(opt.equals("有")||opt.equals("不確定")))emergency();else if(test==6&&step==1&&opt.equals("有"))emergencyBack();else next();});}}
        else add("如果症狀突然出現，請立即打 119，記錄最後正常時間。");
        if(step>0)secondary("上一步",()->{step--;renderStep();});
        if(options==null)button("下一步",this::next);
        secondary("取消並返回列表",this::showList);
    }
    private void showTimer(){timerLabel=ui.text(answers.get(step).isEmpty()?"0.0 秒":answers.get(step)+" 秒",34,Ui.GREEN,true);ui.spaceAdd(body,timerLabel,18);
        final int max=test==3?120:test==4?30:test==5?60:15;
        button("開始計時",()->{stopClock();started=SystemClock.elapsedRealtime();ticking=true;tick(max);});
        secondary("停止並記錄秒數",()->{if(ticking){long ms=SystemClock.elapsedRealtime()-started;stopClock();answers.set(step,String.format(java.util.Locale.US,"%.1f",Math.min(max,ms/1000.0)));timerLabel.setText(answers.get(step)+" 秒");}});
        add("如不安全請直接停止，不必完成。");}
    private void tick(int max){if(!ticking)return;long time=SystemClock.elapsedRealtime()-started;timerLabel.setText(String.format(java.util.Locale.US,"%.1f 秒",Math.min(max,time/1000.0)));if(time>=max*1000L){answers.set(step,String.valueOf(max));stopClock();return;}handler.postDelayed(()->tick(max),100);}
    private void stopClock(){ticking=false;handler.removeCallbacksAndMessages(null);}
    private void next(){if(ticking){new AlertDialog.Builder(this).setMessage("請先停止計時。").setPositiveButton("知道了",null).show();return;}if(step+1<QUESTIONS[test].length){step++;renderStep();}else result();}
    private void emergency(){stopClock();layout("疑似中風：立即求救");add("若為突然出現的臉部、手臂或語言異常，請立即撥打 119，記錄最後正常時間，即使症狀自行恢復也不要等待。");button("開啟 119 撥號",()->startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:119"))));secondary("返回列表",this::showList);}
    private void emergencyBack(){stopClock();layout("危險神經徵象");add("若有新發生大小便控制異常、會陰麻木或進行性腿無力，請立即急診。");button("開啟 119 撥號",()->startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:119"))));secondary("返回列表",this::showList);}
    private void result(){stopClock();layout("本次觀察結果");StringBuilder detail=new StringBuilder();flagged=false;
        for(int i=0;i<QUESTIONS[test].length;i++){detail.append(QUESTIONS[test][i]).append("\n").append(answers.get(i).isEmpty()?"未測量／未填寫":answers.get(i)).append("\n\n");}
        add(detail.toString());add("提醒："+ADVICE[test]);add("此結果僅供主觀觀察，不能診斷或排除疾病。");
        for(String answer:answers)if(answer.equals("有")||answer.equals("不完整")||answer.equals("不正確")||answer.equals("指尖交疊")||answer.contains("需要扶持")||answer.equals("無法完成")||answer.equals("無法安全進行"))flagged=true;
        if(test==3||test==4||test==7){try{double seconds=Double.parseDouble(answers.get(0));if((test==3&&seconds>12)||(test==4&&seconds<10)||(test==7&&seconds>3))flagged=true;}catch(Exception ignored){}}
        button("儲存到我的紀錄",()->{try(HealthStore store=new HealthStore(this)){if(store.profile(profileId)==null){add("健康檔案不存在，無法儲存。");return;}HealthRecord r=new HealthRecord();r.profileId=profileId;r.kind="selfcheck";r.key=new String[]{"clock","fast","calf","chair","balance","phalen","slr","crt"}[test];r.title=NAMES[test];r.date=LocalDate.now().toString();r.note=detail+"\n主觀觀察，非診斷。";r.flag=flagged;store.add(r);new AlertDialog.Builder(this).setMessage("已儲存到目前家人的健康紀錄。").setPositiveButton("完成",(d,w)->finish()).show();}catch(Exception e){add("無法儲存紀錄，請檢查裝置儲存狀態。");}});
        secondary("不儲存，返回列表",this::showList);
    }
    @Override public void onBackPressed(){if(test>=0)showList();else super.onBackPressed();}
    /** Canvas intentionally not persisted; only subjective responses enter SQLite. */
    private final class Drawing extends View {
        private final Paint paint=new Paint(3);
        private final Path path=new Path();
        Drawing(){super(SelfCheckActivity.this);paint.setColor(Color.rgb(23,79,64));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(3));paint.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(Color.WHITE);setMinimumHeight(ui.dp(320));}
        void clear(){path.reset();invalidate();}
        @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);setMeasuredDimension(width,Math.max(ui.dp(300),width));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawPath(path,paint);}
        @Override public boolean onTouchEvent(MotionEvent event){switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:path.moveTo(event.getX(),event.getY());invalidate();return true;case MotionEvent.ACTION_MOVE:path.lineTo(event.getX(),event.getY());invalidate();return true;case MotionEvent.ACTION_UP:performClick();return true;default:return true;}}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
