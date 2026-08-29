package com.javaroadmap.tracker;

import android.content.*;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        String raw=context.getSharedPreferences(MainActivity.PREFS,0).getString("state",null);
        if(raw==null)return;
        try{
            JSONObject state=new JSONObject(raw);
            JSONArray tasks=state.optJSONArray("tasks");
            if(tasks!=null)for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&"not_started".equals(t.optString("status")))ReminderReceiver.schedule(context,t);}
            JSONArray schedules=state.optJSONArray("schedules");
            if(schedules!=null)for(int i=0;i<schedules.length();i++){JSONObject b=schedules.optJSONObject(i);if(b!=null)ReminderReceiver.scheduleNext(context,b);}
        }catch(Exception e){android.util.Log.e("DevTrack","Boot reschedule failed",e);}
    }
}
