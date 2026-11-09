package com.gucare.health;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;

/** Preview only: no capture request for stills, files, ImageReader, upload, or medical inference. */
final class CameraPreviewDialog extends Dialog {
    private final Activity activity;
    private TextureView texture;
    private TextView status;
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private Surface previewSurface;
    private volatile boolean stopped;
    CameraPreviewDialog(Activity activity){super(activity);this.activity=activity;}
    @Override protected void onCreate(Bundle b){super.onCreate(b);setTitle("前鏡頭輔助對位");Ui ui=new Ui(activity,false);LinearLayout box=ui.col();box.setPadding(ui.dp(22),ui.dp(22),ui.dp(22),ui.dp(22));ui.add(box,ui.text("前鏡頭輔助對位",21,Ui.INK,true));texture=new TextureView(activity);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.dp(290));p.topMargin=ui.dp(16);box.addView(texture,p);status=ui.text("正在準備相機…",14,Ui.MUTED,false);ui.spaceAdd(box,status,12);ui.spaceAdd(box,ui.text("只用於即時對位，不拍照、不分析、不儲存、不上傳。這個畫面不能用來檢查眼底。",13,Ui.MUTED,false),10);ui.spaceAdd(box,ui.button("完成對位，關閉相機",this::dismiss),18);setContentView(box);if(getWindow()!=null)getWindow().setLayout(-1,-2);
        thread=new HandlerThread("care-camera-preview");thread.start();handler=new Handler(thread.getLooper());
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){open(w,h);}public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){close();return true;}public void onSurfaceTextureUpdated(SurfaceTexture s){}});
    }
    @SuppressLint("MissingPermission") private void open(int width,int height){
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);String front=null;CameraCharacteristics chars=null;
            for(String id:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(id);Integer facing=c.get(CameraCharacteristics.LENS_FACING);if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT){front=id;chars=c;break;}}
            if(front==null||chars==null){message("找不到可使用的前鏡頭。你仍可不使用相機進行觀察。");return;}
            StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);Size size=new Size(640,480);if(map!=null){Size[] sizes=map.getOutputSizes(SurfaceTexture.class);if(sizes!=null&&sizes.length>0){size=sizes[0];for(Size s:sizes)if(s.getWidth()<=1280&&s.getWidth()>=640){size=s;break;}}}
            SurfaceTexture st=texture.getSurfaceTexture();if(st==null)return;st.setDefaultBufferSize(size.getWidth(),size.getHeight());previewSurface=new Surface(st);
            Integer orientation=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);int rotation=activity.getWindowManager().getDefaultDisplay().getRotation()*90;
            Matrix transform=new Matrix();int degrees=((orientation==null?270:orientation)+rotation)%360;float videoAspect=degrees%180==0?(float)size.getWidth()/size.getHeight():(float)size.getHeight()/size.getWidth();float viewAspect=(float)width/height;
            if(viewAspect>videoAspect)transform.setScale(1,viewAspect/videoAspect,width/2f,height/2f);else transform.setScale(videoAspect/viewAspect,1,width/2f,height/2f);
            if(rotation==90||rotation==270)transform.postRotate(-rotation,width/2f,height/2f);texture.setTransform(transform);
            manager.openCamera(front,new CameraDevice.StateCallback(){public void onOpened(CameraDevice device){if(stopped){device.close();return;}camera=device;startPreview();}public void onDisconnected(CameraDevice device){device.close();camera=null;message("相機已中斷，請關閉後重試。");}public void onError(CameraDevice device,int error){device.close();camera=null;message("相機目前無法使用。你仍可不使用相機進行觀察。");}},handler);
        }catch(Exception e){message("無法開啟前鏡頭。可在 Android 設定檢查相機權限。");}
    }
    private void startPreview(){try{camera.createCaptureSession(Collections.singletonList(previewSurface),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(stopped||camera==null){s.close();return;}session=s;try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(previewSurface);s.setRepeatingRequest(request.build(),null,handler);message("讓臉部保持在畫面中央，手機穩定即可。");}catch(Exception e){message("預覽無法啟動，請關閉後重試。");}}public void onConfigureFailed(CameraCaptureSession s){message("此裝置無法顯示預覽，可直接略過相機。");}},handler);}catch(Exception e){message("相機目前忙碌，請稍後再試。");}}
    private void message(String text){activity.runOnUiThread(()->{if(!stopped)status.setText(text);});}
    private synchronized void close(){stopped=true;if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(previewSurface!=null){previewSurface.release();previewSurface=null;}if(thread!=null){thread.quitSafely();thread=null;}}
    @Override protected void onStop(){close();super.onStop();}
}
