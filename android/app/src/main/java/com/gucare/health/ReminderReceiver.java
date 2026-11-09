package com.gucare.health;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(!c.getSharedPreferences("prefs",0).getBoolean("notifications",true))return;
        if(Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try(HealthStore store=new HealthStore(c)){
            Appointment a=store.appointment(intent.getLongExtra("appointmentId",0));
            if(a==null || a.notified)return;
            ReminderScheduler.channel(c);
            Intent open=new Intent(c,MainActivity.class).putExtra("profileId",a.profileId).putExtra("showReminders",true).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending=PendingIntent.getActivity(c,(int)a.id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(c,ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("今天有一個健康行程")
                .setContentText("打開顧健康，查看你安排的提醒。")
                .setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
            NotificationManager manager=c.getSystemService(NotificationManager.class);
            if(manager.areNotificationsEnabled()){manager.notify((int)a.id,notification);store.notified(a.id);}
        }
    }
}
