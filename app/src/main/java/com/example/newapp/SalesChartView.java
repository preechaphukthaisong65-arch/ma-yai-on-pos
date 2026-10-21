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
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;float w=getWidth(),h=getHeight();
        paint.setColor(Color.rgb(35,80,48));paint.setTextSize(15*density);paint.setFakeBoldText(true);canvas.drawText(title,12*density,22*density,paint);paint.setFakeBoldText(false);
        if(labels.length==0)return;
        float left=12*density,right=w-8*density,top=36*density,bottom=h-28*density;
        paint.setColor(Color.LTGRAY);canvas.drawLine(left,bottom,right,bottom,paint);
        long max=1;for(long v:values)if(v>max)max=v;
        float slot=(right-left)/labels.length;float barW=Math.max(2*density,slot*.58f);
        for(int i=0;i<labels.length;i++){
            float bh=(bottom-top)*Math.max(0,values[i])/(float)max;float x=left+i*slot+(slot-barW)/2;
            paint.setColor(Color.rgb(70,145,85));canvas.drawRect(x,bottom-bh,x+barW,bottom,paint);
            paint.setColor(Color.DKGRAY);paint.setTextSize(9*density);String label=labels[i];float tw=paint.measureText(label);
            canvas.drawText(label,x+barW/2-tw/2,h-8*density,paint);
            if(values[i]>0&&slot>38*density){String amount=compact(values[i]);paint.setTextSize(8*density);float aw=paint.measureText(amount);canvas.drawText(amount,x+barW/2-aw/2,Math.max(top+9*density,bottom-bh-3*density),paint);}
        }
    }
    private String compact(long satang){double baht=satang/100.0;if(baht>=1000000)return String.format(Locale.US,"%.1fM",baht/1000000);if(baht>=1000)return String.format(Locale.US,"%.1fk",baht/1000);return String.format(Locale.US,"%.0f",baht);}
}