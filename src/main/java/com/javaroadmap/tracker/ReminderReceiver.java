package com.javaroadmap.tracker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_TITLE="title";
    static final String EXTRA_TASK_ID="taskId";
    static final String EXTRA_SCHEDULE_ID="scheduleId";

    @Override public void onReceive(Context context, Intent intent){
        try{
            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
            NotificationHelper.ensureChannel(context);
            String title=intent.getStringExtra(EXTRA_TITLE);
            if(title==null||title.trim().isEmpty())title="Planned activity";
            Intent open=new Intent(context,MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(context,NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("DevTrack alarm")
                    .setContentText(title)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build();
            String key=intent.getStringExtra(EXTRA_SCHEDULE_ID);
            if(key==null)key=intent.getStringExtra(EXTRA_TASK_ID);
            context.getSystemService(NotificationManager.class).notify(Math.abs((key+"").hashCode()),n);
            String scheduleId=intent.getStringExtra(EXTRA_SCHEDULE_ID);
            if(scheduleId!=null) scheduleNextSchedule(context,scheduleId);
        }catch(Exception e){android.util.Log.e("DevTrack","Reminder failed",e);}
    }

    public static void schedule(Context c, JSONObject t){
        try{
            String date=t.optString("date"), start=t.optString("start");
            if(date.isEmpty()||start.isEmpty())return;
            String[] d=date.split("-"), hm=start.split(":");
            Calendar cal=Calendar.getInstance();
            cal.set(Calendar.YEAR,Integer.parseInt(d[0]));cal.set(Calendar.MONTH,Integer.parseInt(d[1])-1);cal.set(Calendar.DAY_OF_MONTH,Integer.parseInt(d[2]));
            cal.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0]));cal.set(Calendar.MINUTE,Integer.parseInt(hm[1]));cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            cancel(c,t.optString("id")); if(cal.getTimeInMillis()<=System.currentTimeMillis())return;
            Intent i=new Intent(c,ReminderReceiver.class);i.putExtra(EXTRA_TITLE,t.optString("title","Task reminder"));i.putExtra(EXTRA_TASK_ID,t.optString("id"));
            schedulePending(c,t.optString("id"),cal.getTimeInMillis(),i);
        }catch(Exception e){android.util.Log.e("DevTrack","Task alarm schedule failed",e);}
    }

    public static void scheduleAt(Context c, JSONObject b, long when){
        try{
            String id=b.optString("id");if(id.isEmpty()||when<=System.currentTimeMillis())return;
            Intent i=new Intent(c,ReminderReceiver.class);i.putExtra(EXTRA_TITLE,b.optString("title","Planned activity"));i.putExtra(EXTRA_SCHEDULE_ID,id);
            schedulePending(c,id,when,i);
        }catch(Exception e){android.util.Log.e("DevTrack","Schedule alarm failed",e);}
    }

    static void schedulePending(Context c,String id,long when,Intent i){
        int requestId=Math.abs(id.hashCode());
        PendingIntent pi=PendingIntent.getBroadcast(c,requestId,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi); else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }

    static void scheduleNextSchedule(Context c,String id){
        try{
            String raw=c.getSharedPreferences(MainActivity.PREFS,0).getString("state",null);if(raw==null)return;
            JSONArray arr=new JSONObject(raw).optJSONArray("schedules");if(arr==null)return;
            for(int i=0;i<arr.length();i++){JSONObject b=arr.optJSONObject(i);if(b!=null&&id.equals(b.optString("id"))){scheduleNext(c,b);return;}}
        }catch(Exception e){android.util.Log.e("DevTrack","Next schedule failed",e);}
    }

    static void scheduleNext(Context c,JSONObject b){
        JSONObject alarm=b.optJSONObject("alarm");if(alarm==null||!alarm.optBoolean("enabled",false))return;
        Calendar cal=Calendar.getInstance();String[] days={"SUNDAY","MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"};
        for(int d=0;d<7;d++){
            Calendar candidate=(Calendar)cal.clone();candidate.add(Calendar.DATE,d);
            String day=days[candidate.get(Calendar.DAY_OF_WEEK)-1];if(!matchesDay(b,day))continue;
            try{String[] hm=b.optString("start").split(":");candidate.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0]));candidate.set(Calendar.MINUTE,Integer.parseInt(hm[1]));candidate.set(Calendar.SECOND,0);candidate.set(Calendar.MILLISECOND,0);candidate.add(Calendar.MINUTE,-Math.max(0,alarm.optInt("minutesBefore",5)));if(candidate.after(cal)){scheduleAt(c,b,candidate.getTimeInMillis());return;}}catch(Exception ignored){}
        }
    }

    static boolean matchesDay(JSONObject b,String day){JSONArray a=b.optJSONArray("days");if(a==null||a.length()==0)return true;for(int i=0;i<a.length();i++)if(day.equals(a.optString(i).toUpperCase(Locale.US)))return true;return false;}

    public static void cancel(Context c,String id){
        if(id==null||id.isEmpty())return;Intent i=new Intent(c,ReminderReceiver.class);int requestId=Math.abs(id.hashCode());PendingIntent pi=PendingIntent.getBroadcast(c,requestId,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)am.cancel(pi);}
    }
}
