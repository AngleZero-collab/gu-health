package com.gucare.health;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class ReminderScheduler {
    public static final String CHANNEL="care_reminders";
    public static void channel(Context c) {
        NotificationChannel ch=new NotificationChannel(CHANNEL,"健康行程提醒",NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("只顯示一般提醒，不在鎖定畫面顯示健康細節");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
    private static PendingIntent intent(Context c,long id) {
        Intent i=new Intent(c,ReminderReceiver.class).putExtra("appointmentId",id);
        i.setAction("com.gucare.health.REMIND."+id);
        return PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c,Appointment a) {
        if(!c.getSharedPreferences("prefs",0).getBoolean("notifications",true) || a.notified || a.at <= System.currentTimeMillis())return;
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,a.at,intent(c,a.id));
    }
    public static void cancel(Context c,long id){c.getSystemService(AlarmManager.class).cancel(intent(c,id));}
    public static void restore(Context c) {
        try(HealthStore db=new HealthStore(c)){for(Appointment a:db.appointments(0))schedule(c,a);}
    }
}
