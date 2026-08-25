package com.javaroadmap.tracker;

import android.content.*;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        String raw=context.getSharedPreferences(MainActivity.PREFS,0).getString("state",null);
        if(raw==null)return;
        try{
            JSONArray tasks=new JSONObject(raw).optJSONArray("tasks");
            if(tasks==null)return;
            for(int i=0;i<tasks.length();i++){
                JSONObject t=tasks.optJSONObject(i);
                if(t!=null && !"completed".equals(t.optString("status"))) ReminderReceiver.schedule(context,t);
            }
        }catch(Exception ignored){}
    }
}