package com.javaroadmap.tracker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public final class NotificationHelper {
    public static final String CHANNEL_ID="task_reminders_v2";
    private NotificationHelper(){}

    public static void ensureChannel(Context c){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=c.getSystemService(NotificationManager.class);
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Task reminders",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("DevTrack task and study reminders");
            ch.enableVibration(true);
            ch.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            nm.createNotificationChannel(ch);
        }
    }
    public static void showTest(Context c){
        ensureChannel(c);
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            android.widget.Toast.makeText(c,"Enable notifications in Android settings first.",android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        android.app.Notification n=new Notification.Builder(c,CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("DevTrack test alarm")
                .setContentText("Your reminder notifications are working.")
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        c.getSystemService(NotificationManager.class).notify(99991,n);
    }
}