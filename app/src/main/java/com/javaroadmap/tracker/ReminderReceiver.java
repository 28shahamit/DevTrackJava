package com.javaroadmap.tracker;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_TITLE="title";
    static final String EXTRA_TASK_ID="taskId";

    @Override public void onReceive(Context context, Intent intent){
        try{
            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                return;
            }
            NotificationHelper.ensureChannel(context);
            String title=intent.getStringExtra(EXTRA_TITLE);
            if(title==null||title.trim().isEmpty())title="Planned task";
            Intent open=new Intent(context,MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(context,NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("DevTrack reminder")
                    .setContentText(title)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build();
            context.getSystemService(NotificationManager.class).notify(Math.abs((intent.getStringExtra(EXTRA_TASK_ID)+"").hashCode()),n);
        }catch(Exception ignored){}
    }

    public static void schedule(Context c, JSONObject t){
        try{
            String date=t.optString("date"), start=t.optString("start");
            if(date.isEmpty()||start.isEmpty())return;
            String[] d=date.split("-"), hm=start.split(":");
            java.util.Calendar cal=java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.YEAR,Integer.parseInt(d[0]));
            cal.set(java.util.Calendar.MONTH,Integer.parseInt(d[1])-1);
            cal.set(java.util.Calendar.DAY_OF_MONTH,Integer.parseInt(d[2]));
            cal.set(java.util.Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0]));
            cal.set(java.util.Calendar.MINUTE,Integer.parseInt(hm[1]));
            cal.set(java.util.Calendar.SECOND,0);cal.set(java.util.Calendar.MILLISECOND,0);
            long when=cal.getTimeInMillis();
            cancel(c,t.optString("id"));
            if(when<=System.currentTimeMillis())return;
            Intent i=new Intent(c,ReminderReceiver.class);
            i.putExtra(EXTRA_TITLE,t.optString("title","Task reminder"));
            i.putExtra(EXTRA_TASK_ID,t.optString("id"));
            int id=Math.abs(t.optString("id").hashCode());
            PendingIntent pi=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        }catch(Exception ignored){}
    }
    public static void cancel(Context c,String taskId){
        if(taskId==null||taskId.isEmpty())return;
        Intent i=new Intent(c,ReminderReceiver.class);
        int id=Math.abs(taskId.hashCode());
        PendingIntent pi=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null)((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
    }
}