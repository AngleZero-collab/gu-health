package com.gucare.health;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * One utterance at a time. Never write to HealthStore, preferences, files, logs or analytics here.
 * Android returns immutable Strings: clear references promptly, but do NOT claim forensic memory erasure.
 */
final class VoiceConsultationSheet extends BottomSheetDialog {
    private final Activity activity;
    private final Runnable requestPermission;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final TextView status;
    private final MaterialButton start;
    private final OfflineSpeaker speaker;
    private SpeechRecognizer recognizer;
    private boolean available,closed,busy;
    private int generation;
    private final Runnable timeout=()->finishAttempt("這次等待已結束，麥克風已關閉。您可以再試一次。");

    VoiceConsultationSheet(Activity activity,Runnable requestPermission){
        super(activity);this.activity=activity;this.requestPermission=requestPermission;
        View view=activity.getLayoutInflater().inflate(R.layout.sheet_ai_voice,new android.widget.FrameLayout(activity),false);
        view.setSaveEnabled(false);view.setSaveFromParentEnabled(false);setContentView(view);
        status=view.findViewById(R.id.voice_status);start=view.findViewById(R.id.voice_start);
        view.findViewById(R.id.voice_close).setOnClickListener(v->dismiss());
        start.setOnClickListener(v->begin());
        if(getWindow()!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        new Ui(activity,activity.getSharedPreferences("prefs",0).getBoolean("large",false)).enforce(view);
        available=onDeviceAvailable(activity);
        speaker=new OfflineSpeaker(activity,ready->{
            if(closed)return;
            if(!available)status.setText("這支手機沒有可用的裝置端辨識服務。為保護隱私，語音功能暫不開放，不會改用雲端辨識。");
            else if(!ready)status.setText("手機未提供離線台灣中文朗讀。請先在手機語音設定安裝中文語音；此功能不使用雲端朗讀。");
            else status.setText("請按下方按鈕，再用國語說出問題。只處理這一次問答，完成後就清除 APP 暫存。");
            start.setEnabled(available&&ready);
        });
        setOnShowListener(d->{getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);getBehavior().setSkipCollapsed(true);});
        setOnDismissListener(d->closeResources());
    }
    static boolean onDeviceAvailable(Activity activity){
        if(Build.VERSION.SDK_INT<31)return false;
        try{return SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);}catch(RuntimeException ignored){return false;}
    }
    void onMicrophonePermission(boolean granted){
        if(closed)return;
        status.setText(granted?"麥克風已允許。請按下方按鈕開始說話。":"未允許麥克風，尚未收音。可到手機設定開啟權限後再試。");
    }
    private void begin(){
        if(closed||busy||!available||!speaker.isReady()||Build.VERSION.SDK_INT<31)return;
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermission.run();return;}
        speaker.stop();destroyRecognizer();busy=true;start.setEnabled(false);status.setText("正在開啟麥克風，請稍候…");
        final int token=++generation;
        try{
            // Never use createSpeechRecognizer(): EXTRA_PREFER_OFFLINE alone is not a privacy guarantee.
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean active(){return !closed&&busy&&token==generation;}
                @Override public void onReadyForSpeech(Bundle params){clearBundle(params);if(active())status.setText("請說話，我正在聽。說完後請稍等。");}
                @Override public void onBeginningOfSpeech(){}
                @Override public void onRmsChanged(float rms){}
                @Override public void onBufferReceived(byte[] buffer){if(buffer!=null)Arrays.fill(buffer,(byte)0);}
                @Override public void onEndOfSpeech(){if(active())status.setText("已停止收音，正在手機內整理衛教…");}
                @Override public void onError(int error){if(active())finishAttempt(errorMessage(error));}
                @Override public void onResults(Bundle results){if(active())consumeResults(results);else clearBundle(results);}
                @Override public void onPartialResults(Bundle partial){clearBundle(partial);}
                @Override public void onEvent(int event,Bundle params){clearBundle(params);}
                @Override public void onSegmentResults(Bundle results){clearBundle(results);}
                @Override public void onLanguageDetection(Bundle results){clearBundle(results);}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.TAIWAN.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            if(Build.VERSION.SDK_INT>=33)intent.putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT,false);
            recognizer.startListening(intent);main.postDelayed(timeout,40000);
        }catch(RuntimeException ignored){finishAttempt("無法啟動本機辨識。麥克風已關閉，不會改用雲端服務。");}
    }
    /** Package visible for privacy/lifecycle tests; contains no persistence or UI transcript rendering. */
    void consumeResults(Bundle results){
        char[] input=null,response=null;
        try{
            if(closed){clearBundle(results);return;}
            ArrayList<String> candidates=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if(candidates!=null&&!candidates.isEmpty()&&candidates.get(0)!=null){
                String text=candidates.get(0);input=new char[Math.min(text.length(),2000)];text.getChars(0,input.length,input,0);
            }
            clearBundle(results);main.removeCallbacks(timeout);busy=false;generation++;destroyRecognizer();
            if(input==null||input.length==0){finishAttempt("沒有聽清楚，請按按鈕再說一次。");return;}
            response=LocalHealthGuide.reply(input);
            Arrays.fill(input,'\0');input=null;
            status.setText("正在朗讀基本衛教。APP 不保留這次對話。");
            boolean spoken=speaker.speakEphemeral(response,()->{
                if(!closed){status.setText("這次問答已結束，APP 暫存已清除。需要時可再問一次。");start.setEnabled(available&&speaker.isReady());}
            });
            if(!spoken)finishAttempt("無法使用離線朗讀。這次內容已從 APP 暫存清除，請直接向醫師詢問。");
        }finally{
            if(input!=null)Arrays.fill(input,'\0');if(response!=null)Arrays.fill(response,'\0');clearBundle(results);
        }
    }
    static void clearBundle(Bundle bundle){
        if(bundle==null)return;
        ArrayList<String> strings=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(strings!=null){for(int i=0;i<strings.size();i++)strings.set(i,null);strings.clear();}bundle.clear();
    }
    private static String errorMessage(int error){
        switch(error){
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:return "本機台灣中文辨識尚未安裝或不支援。請在手機語音設定確認；不會改用雲端。";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "麥克風權限未開啟，沒有收音。請在手機設定確認。";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
            case SpeechRecognizer.ERROR_NO_MATCH:return "沒有聽清楚，麥克風已關閉。請按按鈕再試一次。";
            default:return "本機辨識暫時無法使用，麥克風已關閉。請稍後再試。";
        }
    }
    private void finishAttempt(String message){
        busy=false;generation++;main.removeCallbacks(timeout);destroyRecognizer();
        if(!closed){status.setText(message);start.setEnabled(available&&speaker.isReady());}
    }
    private void destroyRecognizer(){SpeechRecognizer old=recognizer;recognizer=null;if(old!=null){old.cancel();old.destroy();}}
    private void closeResources(){if(closed)return;closed=true;busy=false;generation++;main.removeCallbacksAndMessages(null);destroyRecognizer();speaker.close();status.setText("");}
    @Override protected void onStop(){closeResources();super.onStop();}
}
