package com.gucare.health;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Offline zh-TW only. No silent cloud voice fallback, no utterance queue or history owned by this class. */
final class OfflineSpeaker implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready,closed;
    private Runnable completion;
    private int sequence;
    private String activeId;
    OfflineSpeaker(Context context,Consumer<Boolean> initialized){
        tts=new TextToSpeech(context.getApplicationContext(),status->main.post(()->{
            if(closed)return;
            if(status==TextToSpeech.SUCCESS){
                tts.setLanguage(Locale.TAIWAN);
                Set<Voice> voices=tts.getVoices();
                if(voices!=null)for(Voice voice:voices){
                    if(isPrivateTaiwanVoice(voice)&&tts.setVoice(voice)==TextToSpeech.SUCCESS){ready=true;break;}
                }
                if(ready)tts.setSpeechRate(.85f);
            }
            initialized.accept(ready);
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){finish(id);}
            @Override public void onError(String id){finish(id);}
            private void finish(String id){main.post(()->{if(closed||!id.equals(activeId))return;activeId=null;Runnable done=completion;completion=null;if(done!=null)done.run();});}
        });
    }
    static boolean isPrivateTaiwanVoice(Voice voice){
        return voice!=null&&!voice.isNetworkConnectionRequired()&&"zh".equals(voice.getLocale().getLanguage())
            &&"TW".equals(voice.getLocale().getCountry())
            &&(voice.getFeatures()==null||!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    }
    boolean isReady(){return ready&&!closed;}
    void speakPublic(String instruction){speakEphemeral(instruction.toCharArray(),null);}
    /** The caller's buffer is zeroed in every path, immediately after the synchronous Binder enqueue. */
    boolean speakEphemeral(char[] response,Runnable done){
        try{
            if(!isReady()||response==null)return false;
            completion=done;
            activeId="local-utterance-"+(++sequence);
            int result=tts.speak(CharBuffer.wrap(response),TextToSpeech.QUEUE_FLUSH,null,activeId);
            if(result!=TextToSpeech.SUCCESS){completion=null;return false;}return true;
        }finally{if(response!=null)Arrays.fill(response,'\0');}
    }
    void stop(){completion=null;activeId=null;if(tts!=null)tts.stop();}
    @Override public void close(){if(closed)return;closed=true;ready=false;stop();main.removeCallbacksAndMessages(null);if(tts!=null){tts.shutdown();tts=null;}}
}
