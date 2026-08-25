package com.javaroadmap.tracker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final String PREFS="devtrack";
    static final String KEY_COMPLETED="completed";
    static final String KEY_TASKS="tasks";
    static final String KEY_SESSIONS="sessions";
    static final int REQ_NOTIFICATION=200;
    static final int REQ_IMPORT=201, REQ_EXPORT=202;

    FrameLayout content;
    JSONObject roadmap;
    JSONArray tasks, sessions;
    HashSet<String> completed=new HashSet<>();
    TextView title;
    int page=0;
    String startupError;

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    TextView tv(String text,float sp){
        TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(Color.parseColor("#F4F6FB"));
        v.setPadding(dp(4),dp(4),dp(4),dp(4)); return v;
    }
    Button btn(String text){
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.parseColor("#F4F6FB"));
        b.setAllCaps(false); return b;
    }
    LinearLayout card(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14),dp(12),dp(14),dp(12)); l.setBackgroundColor(Color.parseColor("#171A22"));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(7),0,dp(7)); l.setLayoutParams(p); return l;
    }
    LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    void addText(LinearLayout l,String s,float size){ l.addView(tv(s,size),new LinearLayout.LayoutParams(-1,-2)); }
    void addSpace(LinearLayout l,int h){ Space s=new Space(this); l.addView(s,new LinearLayout.LayoutParams(1,dp(h))); }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        content=findViewById(R.id.content);
        try {
            loadState();
            loadRoadmap();
            try { NotificationHelper.ensureChannel(this); } catch (Exception ignored) { }
            setupNav();
            if (startupError != null) showStartupError(startupError);
            else showHome();
        } catch (Exception e) {
            startupError = e.getClass().getSimpleName()+": "+safeMessage(e);
            showStartupError(startupError);
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            try { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATION); } catch (Exception ignored) { }
        }
    }

    String safeMessage(Throwable e){
        String m=e.getMessage();
        return (m==null||m.trim().isEmpty()) ? "Unexpected startup error" : m;
    }

    void showStartupError(String message){
        if(content==null)return;
        base("DevTrack","The app opened safely, but startup data needs attention.");
        LinearLayout c=card();
        addText(c,"Startup recovery",20);
        addText(c,"DevTrack did not crash. It stopped the failing startup operation so your app remains usable.",13);
        TextView err=tv(message,12); err.setTextColor(Color.parseColor("#FFB4AB")); c.addView(err);
        Button retry=btn("↻ Retry startup");
        retry.setOnClickListener(v->{ startupError=null; try{ loadState(); loadRoadmap(); NotificationHelper.ensureChannel(this); showHome(); } catch(Exception e){ startupError=e.getClass().getSimpleName()+": "+safeMessage(e); showStartupError(startupError); }});
        c.addView(retry);
        Button resetRoadmap=btn("Reset imported roadmap");
        resetRoadmap.setOnClickListener(v->{ getSharedPreferences(PREFS,0).edit().remove("roadmapOverride").apply(); startupError=null; loadRoadmap(); showHome(); });
        c.addView(resetRoadmap);
        box().addView(c);
    }
    void setupNav(){
        findViewById(R.id.navHome).setOnClickListener(v->showHome());
        findViewById(R.id.navLearn).setOnClickListener(v->showLearn());
        findViewById(R.id.navPlan).setOnClickListener(v->showPlan());
        findViewById(R.id.navProgress).setOnClickListener(v->showProgress());
    }
    void base(String heading,String sub){
        content.removeAllViews();
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16),dp(18),dp(16),dp(20));
        title=tv(heading,28); title.setTypeface(null,1); box.addView(title);
        if(sub!=null){ TextView s=tv(sub,13); s.setTextColor(Color.parseColor("#9AA4B8")); box.addView(s); }
        sc.addView(box); content.addView(sc);
        content.setTag(box);
    }
    LinearLayout box(){ return (LinearLayout)content.getTag(); }

    void showHome(){
        page=0; base("DevTrack","Personal Developer Learning Tracker");
        int total=topicCount(), done=completed.size(), pct=total==0?0:Math.round(done*100f/total);
        LinearLayout c=card(); addText(c,"TODAY",12); addText(c,pct+"% roadmap complete",22);
        addText(c,done+" / "+total+" topics completed",13);
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); pb.setMax(100); pb.setProgress(pct);
        c.addView(pb,new LinearLayout.LayoutParams(-1,dp(8))); box().addView(c);
        LinearLayout tasksCard=card(); addText(tasksCard,"Today's Tasks",19);
        String today=date();
        boolean any=false;
        for(int i=0;i<tasks.length();i++){ JSONObject t=tasks.optJSONObject(i); if(today.equals(t.optString("date"))){ any=true; addTaskRow(tasksCard,t,i); }}
        if(!any) addText(tasksCard,"No tasks planned for today.",13);
        Button add=btn("＋ Add Task"); add.setOnClickListener(v->taskDialog(null,-1)); tasksCard.addView(add);
        box().addView(tasksCard);
        LinearLayout sess=card(); addText(sess,"Study Today",19);
        long ms=todaySessionMs(today); addText(sess,formatDuration(ms),25); addText(sess,"Actual study time from saved sessions",12); box().addView(sess);
        LinearLayout cont=card(); addText(cont,"Quick Start",19);
        Button start=btn("▶ Start a study session"); start.setOnClickListener(v->chooseTaskForTimer()); cont.addView(start); box().addView(cont);
    }
    void addTaskRow(LinearLayout parent, JSONObject t,int index){
        LinearLayout r=row(); r.setPadding(0,dp(8),0,dp(8));
        CheckBox cb=new CheckBox(this); cb.setChecked("completed".equals(t.optString("status"))); r.addView(cb,new LinearLayout.LayoutParams(dp(42),-2));
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL);
        addText(mid,t.optString("title"),15);
        String effort="Estimate "+formatMin(t.optInt("estimateMin",0))+" · Remaining "+formatMin(t.optInt("remainingMin",0));
        TextView e=tv(effort,11); e.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(e);
        r.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        Button st=btn("Start"); st.setOnClickListener(v->startTimerForTask(index)); r.addView(st);
        cb.setOnClickListener(v->{try{t.put("status",cb.isChecked()?"completed":"not_started"); saveState(); if(cb.isChecked()) t.put("completedAt",dateTime()); showHome();}catch(Exception ignored){}}); 
        parent.addView(r);
    }

    void showLearn(){
        page=1; base("Learn","Roadmap and study sessions");
        LinearLayout c=card(); addText(c,"Roadmap",20);
        int total=topicCount(), done=completed.size(), pct=total==0?0:Math.round(done*100f/total);
        addText(c,done+"/"+total+" · "+pct+"%",14);
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); pb.setMax(100); pb.setProgress(pct); c.addView(pb,new LinearLayout.LayoutParams(-1,dp(8)));
        Button open=btn("🗺 Open Roadmap"); open.setOnClickListener(v->showRoadmap()); c.addView(open);
        Button imp=btn("📥 Import Roadmap JSON"); imp.setOnClickListener(v->importRoadmap()); c.addView(imp);
        Button exp=btn("📤 Export Roadmap JSON"); exp.setOnClickListener(v->exportRoadmap()); c.addView(exp);
        box().addView(c);
        LinearLayout s=card(); addText(s,"Recent Study Sessions",20);
        for(int i=Math.max(0,sessions.length()-8);i<sessions.length();i++) addText(s,sessionLine(sessions.optJSONObject(i)),13);
        if(sessions.length()==0) addText(s,"No sessions yet.",13);
        box().addView(s);
    }

    void showRoadmap(){
        base("Java Backend Roadmap","Tap a phase to view topics. Progress is saved on this device.");
        LinearLayout c=card(); addText(c,"Overall",14); int total=topicCount(), done=completed.size(), pct=total==0?0:Math.round(done*100f/total); addText(c,done+"/"+total+" · "+pct+"%",22);
        ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); p.setMax(100); p.setProgress(pct); c.addView(p,new LinearLayout.LayoutParams(-1,dp(8))); box().addView(c);
        JSONArray phases=roadmap==null?null:roadmap.optJSONArray("phases");
        if(phases==null){
            addText(box(),"No roadmap phases are available. Import a valid roadmap JSON from Learn to continue.",13);
            return;
        }
        for(int i=0;i<phases.length();i++){
            JSONObject ph=phases.optJSONObject(i); LinearLayout pc=card();
            TextView h=tv(ph.optInt("number",i+1)+"  "+ph.optString("title"),18); h.setTypeface(null,1); pc.addView(h);
            JSONArray topics=phaseTopics(ph); int d=0; for(int j=0;j<topics.length();j++) if(completed.contains(topics.optJSONObject(j).optString("id"))) d++;
            int pp=topics.length()==0?0:Math.round(d*100f/topics.length());
            addText(pc,(ph.optString("duration","")+" · "+d+"/"+topics.length()+" · "+pp+"%"),12);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(pp); pc.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));
            LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setVisibility(View.GONE); pc.addView(list);
            h.setOnClickListener(v->list.setVisibility(list.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
            for(int j=0;j<topics.length();j++){ JSONObject t=topics.optJSONObject(j); addTopicRow(list,t); }
            box().addView(pc);
        }
    }
    JSONArray phaseTopics(JSONObject ph){
        JSONArray out=new JSONArray();
        if(ph==null)return out;
        JSONArray cats=ph.optJSONArray("categories");
        if(cats!=null) for(int i=0;i<cats.length();i++){ JSONArray ts=cats.optJSONObject(i).optJSONArray("topics"); if(ts!=null) for(int j=0;j<ts.length();j++) out.put(ts.optJSONObject(j)); }
        JSONArray direct=ph.optJSONArray("topics"); if(direct!=null) for(int j=0;j<direct.length();j++) out.put(direct.optJSONObject(j));
        return out;
    }
    void addTopicRow(LinearLayout list,JSONObject t){
        LinearLayout r=row(); CheckBox cb=new CheckBox(this); cb.setChecked(completed.contains(t.optString("id"))); r.addView(cb);
        TextView name=tv(t.optString("title"),14); r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        cb.setOnClickListener(v->{String id=t.optString("id"); if(cb.isChecked()) completed.add(id); else completed.remove(id); saveState(); showRoadmap();});
        list.addView(r);
    }

    void showPlan(){
        page=2; base("Plan","Plan work, estimate effort and set reminders.");
        LinearLayout top=card(); addText(top,"Today's Plan",20); Button add=btn("＋ Add Task"); add.setOnClickListener(v->taskDialog(null,-1)); top.addView(add); box().addView(top);
        String today=date(); for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i); if(today.equals(t.optString("date"))) taskCard(box(),t,i);}
        LinearLayout all=card(); addText(all,"Upcoming",20); for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i); if(!today.equals(t.optString("date"))) taskCard(all,t,i);} box().addView(all);
    }
    void taskCard(LinearLayout parent,JSONObject t,int idx){
        LinearLayout c=card(); addText(c,t.optString("title"),17); addText(c,t.optString("date")+" · "+t.optString("start","")+"–"+t.optString("end",""),12);
        addText(c,"Estimate "+formatMin(t.optInt("estimateMin",0))+" · Actual "+formatMin(t.optInt("actualMin",0))+" · Remaining "+formatMin(t.optInt("remainingMin",0)),12);
        addText(c,"Status: "+t.optString("status","not_started")+" · Priority: "+t.optString("priority","medium"),11);
        LinearLayout r=row(); Button st=btn("▶ Start"); st.setOnClickListener(v->startTimerForTask(idx)); r.addView(st);
        Button edit=btn("Edit"); edit.setOnClickListener(v->taskDialog(t,idx)); r.addView(edit);
        Button move=btn("Tomorrow"); move.setOnClickListener(v->{try{t.put("date",shiftDate(t.optString("date"),1)); scheduleReminder(this,t); saveState(); showPlan();}catch(Exception ignored){}}); r.addView(move);
        c.addView(r); parent.addView(c);
    }

    void showProgress(){
        page=3; base("Progress","See effort, sessions and goals.");
        int total=topicCount(), done=completed.size(), pct=total==0?0:Math.round(done*100f/total);
        LinearLayout c=card(); addText(c,"Roadmap Progress",18); addText(c,done+"/"+total+" · "+pct+"%",28); ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); pb.setMax(100);pb.setProgress(pct);c.addView(pb,new LinearLayout.LayoutParams(-1,dp(8)));box().addView(c);
        LinearLayout w=card(); addText(w,"This Week",18); long ms=0; for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i); if(withinDays(s.optString("date"),7)) ms+=s.optLong("durationMs",0);} addText(w,"Study time: "+formatDuration(ms),22); addText(w,"Sessions: "+sessions.length(),13);box().addView(w);
        LinearLayout set=card(); addText(set,"Settings & Data",18);
        Button alarm=btn("🔔 Test Alarm"); alarm.setOnClickListener(v->NotificationHelper.showTest(this)); set.addView(alarm);
        Button imp=btn("📥 Import Full Backup"); imp.setOnClickListener(v->importBackup()); set.addView(imp);
        Button exp=btn("📤 Export Full Backup"); exp.setOnClickListener(v->exportBackup()); set.addView(exp);
        Button reset=btn("Reset All Data"); reset.setOnClickListener(v->confirmReset()); set.addView(reset); box().addView(set);
    }

    void taskDialog(JSONObject existing,int index){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(10),0,dp(10),0);
        EditText titleE=new EditText(this); titleE.setHint("Task title"); l.addView(titleE);
        EditText dateE=new EditText(this); dateE.setHint("Date YYYY-MM-DD"); dateE.setText(existing==null?date():existing.optString("date")); l.addView(dateE);
        EditText startE=new EditText(this); startE.setHint("Start HH:mm"); startE.setText(existing==null?"":existing.optString("start")); l.addView(startE);
        EditText endE=new EditText(this); endE.setHint("End HH:mm"); endE.setText(existing==null?"":existing.optString("end")); l.addView(endE);
        EditText estE=new EditText(this); estE.setHint("Estimate minutes (e.g. 90)"); estE.setInputType(2); estE.setText(existing==null?"60":String.valueOf(existing.optInt("estimateMin",60))); l.addView(estE);
        EditText remE=new EditText(this); remE.setHint("Remaining minutes"); remE.setInputType(2); remE.setText(existing==null?"60":String.valueOf(existing.optInt("remainingMin",60))); l.addView(remE);
        EditText priorityE=new EditText(this); priorityE.setHint("Priority: low / medium / high"); priorityE.setText(existing==null?"medium":existing.optString("priority","medium")); l.addView(priorityE);
        new AlertDialog.Builder(this).setTitle(existing==null?"Add Task":"Edit Task").setView(l)
            .setPositiveButton("Save",(d,w)->{
                try{
                    JSONObject t=existing==null?new JSONObject():existing;
                    if(existing==null) t.put("id",UUID.randomUUID().toString());
                    t.put("title",titleE.getText().toString().trim());
                    t.put("date",dateE.getText().toString().trim());
                    t.put("start",startE.getText().toString().trim()); t.put("end",endE.getText().toString().trim());
                    t.put("estimateMin",Integer.parseInt(estE.getText().toString().trim()));
                    t.put("remainingMin",Integer.parseInt(remE.getText().toString().trim()));
                    t.put("priority",priorityE.getText().toString().trim());
                    if(!t.has("actualMin"))t.put("actualMin",0); if(!t.has("status"))t.put("status","not_started");
                    if(existing==null) tasks.put(t); scheduleReminder(this,t); saveState(); showPlan();
                }catch(Exception e){toast("Could not save task: "+e.getMessage());}
            }).setNegativeButton("Cancel",null).show();
    }

    void chooseTaskForTimer(){
        if(tasks.length()==0){toast("Create a task first.");return;}
        String[] names=new String[tasks.length()]; for(int i=0;i<tasks.length();i++)names[i]=tasks.optJSONObject(i).optString("title");
        new AlertDialog.Builder(this).setTitle("Start study session").setItems(names,(d,w)->startTimerForTask(w)).show();
    }
    void startTimerForTask(int idx){
        JSONObject t=tasks.optJSONObject(idx); if(t==null)return;
        final long start=System.currentTimeMillis();
        new AlertDialog.Builder(this).setTitle("Studying: "+t.optString("title"))
            .setMessage("Timer started. Keep this dialog open while studying.")
            .setNegativeButton("Stop & Save",(d,w)->{
                long duration=System.currentTimeMillis()-start; try{
                    t.put("actualMin",t.optInt("actualMin",0)+Math.max(1,Math.round(duration/60000f)));
                    t.put("remainingMin",Math.max(0,t.optInt("remainingMin",0)-Math.max(1,Math.round(duration/60000f))));
                    t.put("status","in_progress");
                    JSONObject s=new JSONObject(); s.put("id",UUID.randomUUID().toString()); s.put("taskId",t.optString("id")); s.put("title",t.optString("title")); s.put("date",date()); s.put("durationMs",duration); s.put("createdAt",dateTime()); sessions.put(s); saveState(); toast("Session saved: "+formatDuration(duration)); showHome();
                }catch(Exception e){toast("Could not save session");}
            }).show();
    }

    void scheduleReminder(Context c,JSONObject t){ ReminderReceiver.schedule(c,t); }

    void loadRoadmap(){
        startupError=null;
        try{
            String override=getSharedPreferences(PREFS,0).getString("roadmapOverride",null);
            if(override!=null && !override.trim().isEmpty()){
                roadmap=new JSONObject(override);
                validateRoadmap(roadmap);
                return;
            }
            try(InputStream in=getAssets().open("roadmap.json"); ByteArrayOutputStream b=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0)b.write(buf,0,n);
                roadmap=new JSONObject(b.toString("UTF-8"));
            }
            validateRoadmap(roadmap);
        }catch(Exception e){
            roadmap=new JSONObject();
            startupError="Roadmap could not be loaded: "+safeMessage(e);
        }
    }

    void validateRoadmap(JSONObject r) throws Exception{
        if(r==null)throw new Exception("Roadmap data is empty");
        JSONArray phases=r.optJSONArray("phases");
        if(phases==null)throw new Exception("Roadmap is missing the phases array");
    }
    void loadState(){
        String raw=getSharedPreferences(PREFS,0).getString("state",null);
        try{
            if(raw==null){tasks=new JSONArray();sessions=new JSONArray();return;}
            JSONObject s=new JSONObject(raw); tasks=s.optJSONArray("tasks"); sessions=s.optJSONArray("sessions"); if(tasks==null)tasks=new JSONArray(); if(sessions==null)sessions=new JSONArray();
            JSONArray c=s.optJSONArray("completed"); if(c!=null)for(int i=0;i<c.length();i++)completed.add(c.optString(i));
        }catch(Exception e){tasks=new JSONArray();sessions=new JSONArray();}
    }
    void saveState(){
        try{JSONObject s=new JSONObject(); JSONArray c=new JSONArray(); for(String id:completed)c.put(id); s.put("completed",c);s.put("tasks",tasks);s.put("sessions",sessions);getSharedPreferences(PREFS,0).edit().putString("state",s.toString()).apply();}catch(Exception ignored){}
    }

    int topicCount(){
        if(roadmap==null)return 0;
        JSONArray ps=roadmap.optJSONArray("phases");
        if(ps==null)return 0;
        int n=0;
        for(int i=0;i<ps.length();i++)n+=phaseTopics(ps.optJSONObject(i)).length();
        return n;
    }
    long todaySessionMs(String d){long n=0;for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(d.equals(s.optString("date")))n+=s.optLong("durationMs",0);}return n;}
    String sessionLine(JSONObject s){return s.optString("date")+" · "+s.optString("title")+" · "+formatDuration(s.optLong("durationMs",0));}
    String date(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    String dateTime(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
    String shiftDate(String d,int days){try{Date x=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(d);Calendar c=Calendar.getInstance();c.setTime(x);c.add(Calendar.DATE,days);return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());}catch(Exception e){return date();}}
    boolean withinDays(String d,int days){try{Date x=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(d);long diff=System.currentTimeMillis()-x.getTime();return diff>=0&&diff<=days*86400000L;}catch(Exception e){return false;}}
    String formatMin(int m){if(m<60)return m+"m";return (m/60)+"h"+(m%60==0?"":(" "+m%60+"m"));}
    String formatDuration(long ms){long min=Math.max(0,Math.round(ms/60000f));return formatMin((int)min);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    void importRoadmap(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_IMPORT);}
    void exportRoadmap(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"devtrack-roadmap.json");startActivityForResult(i,REQ_EXPORT);}
    void importBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,300);}
    void exportBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"devtrack-backup-"+date()+".json");startActivityForResult(i,301);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;try{
        if(req==REQ_IMPORT){String raw=read(data.getData());JSONObject r=new JSONObject(raw);if(!"trackit-roadmap".equals(r.optString("format")))throw new Exception("Unsupported roadmap format");new AlertDialog.Builder(this).setTitle("Import roadmap").setMessage("Replace the current roadmap with the imported roadmap?").setPositiveButton("Replace",(d,w)->{roadmap=r;try{getSharedPreferences(PREFS,0).edit().putString("roadmapOverride",r.toString()).apply();}catch(Exception ignored){}showRoadmap();}).setNegativeButton("Cancel",null).show();}
        else if(req==REQ_EXPORT){write(data.getData(),roadmap.toString(2));toast("Roadmap exported");}
        else if(req==300){String raw=read(data.getData());JSONObject s=new JSONObject(raw);if(s.has("roadmap")){roadmap=s.optJSONObject("roadmap");if(roadmap!=null)getSharedPreferences(PREFS,0).edit().putString("roadmapOverride",roadmap.toString()).apply();}if(s.has("completed")){completed.clear();JSONArray a=s.optJSONArray("completed");for(int i=0;i<a.length();i++)completed.add(a.optString(i));}tasks=s.optJSONArray("tasks");sessions=s.optJSONArray("sessions");if(tasks==null)tasks=new JSONArray();if(sessions==null)sessions=new JSONArray();saveState();toast("Backup imported");showHome();}
        else if(req==301){JSONObject s=new JSONObject();JSONArray c=new JSONArray();for(String id:completed)c.put(id);s.put("completed",c);s.put("tasks",tasks);s.put("sessions",sessions);s.put("roadmap",roadmap);write(data.getData(),s.toString(2));toast("Backup exported");}
    }catch(Exception e){toast("Import/export failed: "+e.getMessage());}}
    String read(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return b.toString("UTF-8");}
    void write(Uri u,String s)throws Exception{OutputStream out=getContentResolver().openOutputStream(u);out.write(s.getBytes(StandardCharsets.UTF_8));out.close();}
    void confirmReset(){new AlertDialog.Builder(this).setTitle("Reset all data?").setMessage("This deletes roadmap progress, tasks and study sessions from this device.").setPositiveButton("Delete",(d,w)->{completed.clear();tasks=new JSONArray();sessions=new JSONArray();saveState();showHome();}).setNegativeButton("Cancel",null).show();}
}
