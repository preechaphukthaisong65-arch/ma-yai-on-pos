package com.example.newapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.view.View;
import java.util.Locale;

final class SalesChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String title="";
    private String[] labels=new String[0];
    private long[] values=new long[0];
    public SalesChartView(Context context){super(context);setBackgroundColor(Color.WHITE);}
    void setData(String chartTitle,String[] chartLabels,long[] chartValues){title=chartTitle;labels=chartLabels;values=chartValues;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;float w=getWidth(),h=getHeight();
        paint.setColor(Color.rgb(35,80,48));paint.setTextSize(16*d);paint.setFakeBoldText(true);canvas.drawText(title,10*d,21*d,paint);paint.setFakeBoldText(false);
        if(labels.length==0||w<=0||h<=0)return;
        float left=52*d,right=w-10*d,top=38*d,bottom=h-30*d;if(right<=left||bottom<=top)return;
        long max=1;for(long v:values)if(v>max)max=v;
        paint.setStrokeWidth(1*d);paint.setTextSize(10*d);
        for(int i=0;i<=4;i++){
            float y=bottom-(bottom-top)*i/4f;paint.setColor(Color.rgb(225,230,225));canvas.drawLine(left,y,right,y,paint);
            long tick=max*i/4;paint.setColor(Color.DKGRAY);String label=compact(tick);float tw=paint.measureText(label);canvas.drawText(label,left-tw-5*d,y+3*d,paint);
        }
        paint.setColor(Color.GRAY);canvas.drawLine(left,top,left,bottom,paint);canvas.drawLine(left,bottom,right,bottom,paint);
        float slot=(right-left)/(Math.max(1,labels.length-1));float[] xs=new float[labels.length],ys=new float[labels.length];
        for(int i=0;i<labels.length;i++){xs[i]=left+i*slot;ys[i]=bottom-(bottom-top)*Math.max(0,values[i])/(float)max;}
        paint.setColor(Color.rgb(50,130,70));paint.setStrokeWidth(2.5f*d);paint.setStyle(Paint.Style.STROKE);
        for(int i=1;i<labels.length;i++)canvas.drawLine(xs[i-1],ys[i-1],xs[i],ys[i],paint);
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<labels.length;i++){
            paint.setColor(Color.rgb(50,130,70));canvas.drawCircle(xs[i],ys[i],3*d,paint);
            paint.setColor(Color.DKGRAY);paint.setTextSize(9*d);float tw=paint.measureText(labels[i]);canvas.drawText(labels[i],xs[i]-tw/2,bottom+16*d,paint);
        }
        paint.setTextSize(9*d);paint.setColor(Color.DKGRAY);canvas.save();canvas.rotate(-90,12*d,(top+bottom)/2);canvas.drawText("ยอดขาย (บาท)",12*d,(top+bottom)/2,paint);canvas.restore();
    }
    private String compact(long satang){double baht=satang/100.0;if(baht>=1000000)return String.format(Locale.US,"%.1fM",baht/1000000);if(baht>=1000)return String.format(Locale.US,"%.1fk",baht/1000);return String.format(Locale.US,"%.0f",baht);}
}
