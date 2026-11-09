package com.gucare.health;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int INK=Color.rgb(28,60,51), GREEN=Color.rgb(23,79,64), BG=Color.rgb(245,248,244),
        MUTED=Color.rgb(80,100,89), LINE=Color.rgb(223,232,224), MINT=Color.rgb(227,241,231),
        LILAC=Color.rgb(237,232,250), PURPLE=Color.rgb(109,88,155), PEACH=Color.rgb(253,236,222),
        ORANGE=Color.rgb(168,73,12), RED=ORANGE, WHITE=Color.WHITE;
    final Context c;
    final float scale;
    Ui(Context c,boolean large){this.c=c;scale=large?1.18f:1f;}
    int dp(float x){return (int)(x*c.getResources().getDisplayMetrics().density+.5f);}
    LinearLayout col(){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    GradientDrawable outline(int color,int radius,int stroke){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),stroke);return d;}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(s);float sp=Math.max(18,size);t.setTag(R.id.base_text_size,sp);t.setTextSize(sp*scale);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setLineSpacing(dp(2),1.04f);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    TextView title(String s){return text(s,27,INK,true);}
    TextView caption(String s){return text(s,18,MUTED,true);}
    TextView label(String s,int color,int background){TextView t=text(s,12,color,true);t.setPadding(dp(9),dp(4),dp(9),dp(4));t.setBackground(bg(background,8));return t;}
    LinearLayout card(){LinearLayout l=col();l.setPadding(dp(20),dp(20),dp(20),dp(20));l.setBackground(outline(WHITE,22,LINE));return l;}
    TextView button(String text,Runnable action){return button(text,GREEN,WHITE,action);}
    TextView secondary(String text,Runnable action){return button(text,MINT,GREEN,action);}
    TextView button(String text,int background,int foreground,Runnable action){TextView b=text(text,20,foreground,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(64));b.setMinWidth(dp(64));b.setPadding(dp(16),dp(14),dp(16),dp(14));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),bg(background,14),null));b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->action.run());b.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");}});return b;}
    void gap(LinearLayout l,int h){View v=new View(c);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    void spaceAdd(LinearLayout l,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);l.addView(v,p);}
    void weighted(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    void hgap(LinearLayout l,int w){View v=new View(c);l.addView(v,new LinearLayout.LayoutParams(dp(w),1));}
    View icon(String name,int color,int size){CareIcon v=new CareIcon(c,name,color);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return v;}
    View iconButton(String name,String description,Runnable action){View v=icon(name,GREEN,64);v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(new RippleDrawable(ColorStateList.valueOf(LINE),bg(WHITE,16),null));v.setContentDescription(description);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);v.setFocusable(true);v.setOnClickListener(x->action.run());return v;}

    /** Keep original sp as a tag so repeated binding never compounds text scaling. */
    @SuppressWarnings("deprecation")
    void enforce(View view){
        if(view instanceof TextView){TextView t=(TextView)view;Object tag=t.getTag(R.id.base_text_size);
            float sp=tag instanceof Float?(Float)tag:t.getTextSize()/c.getResources().getDisplayMetrics().scaledDensity;
            sp=Math.max(18,sp);t.setTag(R.id.base_text_size,sp);t.setTextSize(sp*scale);
        }
        if(view.isClickable()||view.isLongClickable()||view instanceof android.widget.EditText||view instanceof android.widget.Spinner){
            view.setMinimumHeight(Math.max(dp(64),view.getMinimumHeight()));
            ViewGroup.LayoutParams p=view.getLayoutParams();if(p!=null&&p.height>0&&p.height<dp(64)){p.height=dp(64);view.setLayoutParams(p);}
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enforce(group.getChildAt(i));}
    }

    static final class CareIcon extends View {
        final String name;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        CareIcon(Context c,String n,int color){super(c);name=n;this.color=color;}
        @Override protected void onDraw(Canvas original){super.onDraw(original);Canvas c=original;c.save();c.translate(getPaddingLeft(),getPaddingTop());c.scale((getWidth()-getPaddingLeft()-getPaddingRight())/24f,(getHeight()-getPaddingTop()-getPaddingBottom())/24f);p.setColor(color);p.setStrokeWidth(1.65f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(name){
                case "shield": path(c,12,2,21,6,21,13,18,19,12,23,6,19,3,13,3,6,12,2);path(c,8,12,11,15,16,9);break;
                case "home":path(c,3,10,12,3,21,10,21,21,15,21,15,14,9,14,9,21,3,21,3,10);break;
                case "calendar":c.drawRoundRect(3,5,21,22,3,3,p);c.drawLine(3,10,21,10,p);c.drawLine(8,2,8,7,p);c.drawLine(16,2,16,7,p);path(c,8,15,11,18,16,13);break;
                case "eye":Path eye=new Path();eye.moveTo(2,12);eye.cubicTo(7,3,17,3,22,12);eye.cubicTo(17,21,7,21,2,12);c.drawPath(eye,p);c.drawCircle(12,12,3,p);break;
                case "records":path(c,14,2,5,2,5,22,20,22,20,8,14,2,14,8,20,8);c.drawLine(9,13,16,13,p);c.drawLine(9,17,15,17,p);break;
                case "family":c.drawCircle(9,7,3.5f,p);c.drawArc(2,13,16,26,180,180,false,p);c.drawArc(13,4,21,12,-90,180,false,p);c.drawArc(14,14,23,25,260,100,false,p);break;
                case "bell":path(c,4,18,6,15,6,9,7,5,12,3,17,5,18,9,18,15,20,18,4,18);c.drawArc(9,17,15,23,0,180,false,p);break;
                case "plus":c.drawLine(12,5,12,19,p);c.drawLine(5,12,19,12,p);break;
                case "check":path(c,4,12,10,18,21,5);break;
                case "back":path(c,15,4,7,12,15,20);break;
                case "next":path(c,8,5,15,12,8,19);break;
                case "clock":c.drawCircle(12,12,9,p);path(c,12,6,12,12,16,15);break;
                case "heart":Path h=new Path();h.moveTo(12,21);h.cubicTo(-7,9,6,-4,12,6);h.cubicTo(18,-4,31,9,12,21);c.drawPath(h,p);break;
                case "settings":c.drawCircle(12,12,4,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,4,p);c.restore();}c.drawCircle(12,12,8,p);break;
                case "camera":c.drawRoundRect(2,6,22,21,3,3,p);path(c,7,6,9,3,15,3,17,6);c.drawCircle(12,13,4,p);break;
                case "map":c.drawCircle(12,9,3,p);Path m=new Path();m.moveTo(12,23);m.cubicTo(-7,6,10,-5,18,4);m.cubicTo(26,10,17,20,12,23);c.drawPath(m,p);break;
                default:c.drawCircle(12,12,9,p);c.drawLine(12,10,12,16,p);c.drawPoint(12,6,p);
            }c.restore();
        }
        private void path(Canvas c,float...pts){Path path=new Path();path.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);c.drawPath(path,p);}
    }

}
