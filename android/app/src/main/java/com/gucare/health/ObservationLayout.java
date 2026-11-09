package com.gucare.health;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** On short/landscape screens preserve two real 80dp targets, not only a nominal XML minHeight. */
public final class ObservationLayout extends LinearLayout {
    public ObservationLayout(Context context){this(context,null);}
    public ObservationLayout(Context context,AttributeSet attrs){super(context,attrs);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(getChildCount()==2&&MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED){
            int available=MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom();
            int minimum=Math.round(184*getResources().getDisplayMetrics().density);
            int lower=Math.max(minimum,available/2);
            LayoutParams top=(LayoutParams)getChildAt(0).getLayoutParams(),bottom=(LayoutParams)getChildAt(1).getLayoutParams();
            top.height=Math.max(0,available-lower);top.weight=0;bottom.height=lower;bottom.weight=0;
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
