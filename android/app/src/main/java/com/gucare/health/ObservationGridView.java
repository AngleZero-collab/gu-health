package com.gucare.health;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** The square stays centered at any device aspect ratio; it is not a calibrated medical chart. */
public final class ObservationGridView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ObservationGridView(Context c){this(c,null);}
    public ObservationGridView(Context c,AttributeSet attrs){super(c,attrs);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        float side=Math.max(0,Math.min(getWidth(),getHeight())-8*density);
        float x=(getWidth()-side)/2f,y=(getHeight()-side)/2f;
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);canvas.drawRect(x,y,x+side,y+side,paint);
        paint.setColor(Color.BLACK);paint.setStrokeWidth(Math.max(1f,.7f*density));
        for(int i=0;i<=20;i++){float d=side*i/20;canvas.drawLine(x+d,y,x+d,y+side,paint);canvas.drawLine(x,y+d,x+side,y+d,paint);}
        canvas.drawCircle(getWidth()/2f,getHeight()/2f,4*density,paint);
    }
}
