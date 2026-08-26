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
    static final String KEY_ROADMAPS="roadmaps";
    static final String KEY_CURRENT_ROADMAP="currentRoadmapId";
    static final String KEY_TASKS="tasks";
    static final String KEY_SESSIONS="sessions";
    static final int REQ_NOTIFICATION=200;
    static final int REQ_IMPORT=201, REQ_EXPORT=202, REQ_SCHEDULE_IMPORT=302, REQ_SCHEDULE_EXPORT=303;

    FrameLayout content;
    JSONObject roadmap;
    JSONArray roadmaps;
    String currentRoadmapId;
    JSONArray tasks, sessions, schedules;
    HashSet<String> completed=new HashSet<>();
    TextView title;
    int page=0;
    boolean roadmapDetailOpen=false;
    String startupError;
    final Handler uiHandler = new Handler(Looper.getMainLooper());
    TextView timerText, timerTitle, timerMeta;
    Button timerButton, timerCompleteButton;
    boolean timerUiAttached = false;
    final Runnable timerTicker = new Runnable(){ @Override public void run(){ updateTimerUi(); if(timerUiAttached) uiHandler.postDelayed(this,1000); }};
    static final String KEY_ACTIVE_TIMER="activeTimer";
    static final String KEY_TRACKING_MODE="trackingMode";
    static final String KEY_COMPLETED_PLANS="completedPlans";

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    TextView tv(String text,float sp){
        TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(Color.parseColor("#F4F6FB"));
        v.setPadding(dp(4),dp(4),dp(4),dp(4)); return v;
    }
    Button btn(String text){
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.parseColor("#F4F6FB"));
        b.setAllCaps(false); b.setBackgroundResource(R.drawable.secondary_button_bg); b.setMinHeight(dp(44)); b.setPadding(dp(16),dp(8),dp(16),dp(8)); return b;
    }
    Button primaryBtn(String text){Button b=btn(text);b.setBackgroundResource(R.drawable.primary_button_bg);return b;}
    Button iconBtn(String icon,String description){
        Button b=btn(icon); b.setContentDescription(description);
        b.setMinWidth(dp(44)); b.setMinimumWidth(dp(44));
        b.setPadding(dp(8),dp(8),dp(8),dp(8)); return b;
    }
    LinearLayout card(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16),dp(14),dp(16),dp(14)); l.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(7),0,dp(7)); l.setLayoutParams(p); return l;
    }
    LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    void addText(LinearLayout l,String s,float size){ l.addView(tv(s,size),new LinearLayout.LayoutParams(-1,-2)); }
    void addSpace(LinearLayout l,int h){ Space s=new Space(this); l.addView(s,new LinearLayout.LayoutParams(1,dp(h))); }

    @Override protected void onResume(){
        super.onResume();
        timerUiAttached = true;
        uiHandler.removeCallbacks(timerTicker);
        uiHandler.post(timerTicker);
    }

    @Override protected void onPause(){
        timerUiAttached = false;
        uiHandler.removeCallbacks(timerTicker);
        super.onPause();
    }

    @Override public void onBackPressed(){
        if(page==1 && roadmapDetailOpen){showLearn();return;}
        super.onBackPressed();
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        applySystemInsets();
        content=findViewById(R.id.content);
        try {
            loadState();
            migrateLegacyCompletion();
            loadRoadmap();
            try { NotificationHelper.ensureChannel(this); } catch (Exception ignored) { }
            try { scheduleAllImportedAlarms(); } catch (Exception ignored) { }
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

    void applySystemInsets(){
        final View root=findViewById(R.id.root);
        if(root==null)return;
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=0,bottom=0;
            if(Build.VERSION.SDK_INT>=30){top=insets.getInsets(WindowInsets.Type.statusBars()).top;bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;}
            else {top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(v.getPaddingLeft(),top,v.getPaddingRight(),bottom);
            return insets;
        });
        root.requestApplyInsets();
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
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16),dp(24),dp(16),dp(20));
        title=tv(heading,28); title.setTypeface(null,1); box.addView(title);
        if(sub!=null){ TextView s=tv(sub,13); s.setTextColor(Color.parseColor("#9AA4B8")); box.addView(s); }
        sc.addView(box); content.addView(sc);
        content.setTag(box);
    }
    void baseWithBack(String heading,String sub,Runnable backAction){
        base(heading,sub);
        LinearLayout root=box(); root.removeAllViews();
        Button back=btn("‹  Back"); back.setContentDescription("Back"); back.setOnClickListener(v->backAction.run());
        root.addView(back,new LinearLayout.LayoutParams(-2,dp(44)));
        title=tv(heading,28); title.setTypeface(null,1); root.addView(title);
        if(sub!=null){TextView st=tv(sub,13);st.setTextColor(Color.parseColor("#9AA4B8"));root.addView(st);}
    }
    LinearLayout box(){ return (LinearLayout)content.getTag(); }

    void showHome(){
        page=0; roadmapDetailOpen=false; base("DevTrack","Your day, your time, your progress");
        String today=date();
        long tracked=todaySessionMs(today)+activeElapsedMsForDate(today);
        int total=topicCount(), done=completedFor(currentRoadmapId).size(), pct=total==0?0:Math.round(done*100f/total);

        LinearLayout hero=card();
        addText(hero,"TODAY",11);
        addText(hero,formatDuration(tracked)+" tracked",28);
        addText(hero,done+" / "+total+" "+roadmapItemLabel(roadmap)+" · "+pct+"%",13);
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); pb.setMax(100); pb.setProgress(pct); hero.addView(pb,new LinearLayout.LayoutParams(-1,dp(7)));
        box().addView(hero);

        addTrackingCard(box());

        LinearLayout quick=card(); addText(quick,"Quick actions",18);
        Button plan=btn("✓ Open today's plan"); plan.setOnClickListener(v->showPlan()); quick.addView(plan);
        Button log=btn("＋ Log past activity"); log.setOnClickListener(v->activityLogDialog()); quick.addView(log);
        Button start=btn("▶ Start manual activity"); start.setOnClickListener(v->manualStartDialog()); quick.addView(start);
        box().addView(quick);
    }

    void addTrackingCard(LinearLayout parent){
        LinearLayout c=card(); addText(c,"TIME TRACKING",11); JSONObject active=getActiveTimer();
        if(active!=null){
            addText(c,"● RUNNING",12); timerTitle=tv(active.optString("title","Activity"),20); c.addView(timerTitle);
            timerMeta=tv(active.optString("category","OTHER"),12); timerMeta.setTextColor(Color.parseColor("#9AA4B8")); c.addView(timerMeta);
            timerText=tv("00:00:00",34); timerText.setTypeface(null,1); timerText.setGravity(Gravity.CENTER); c.addView(timerText,new LinearLayout.LayoutParams(-1,dp(54)));
            LinearLayout r=row(); timerButton=primaryBtn("■ Stop & Save"); timerButton.setOnClickListener(v->stopActiveTimer(false)); r.addView(timerButton,new LinearLayout.LayoutParams(0,-2,1));
            timerCompleteButton=btn("✓ Complete"); timerCompleteButton.setOnClickListener(v->stopActiveTimer(true)); r.addView(timerCompleteButton,new LinearLayout.LayoutParams(0,-2,1)); c.addView(r);
        }else{
            addText(c,isManualMode()?"MANUAL MODE":"AUTOMATIC PLAN MODE",12); addText(c,"No timer is running",18);
            addText(c,"Today's schedule is managed from the Plan tab.",12); LinearLayout r=row();
            Button plan=primaryBtn("✓ Open today's plan"); plan.setOnClickListener(v->showPlan()); r.addView(plan,new LinearLayout.LayoutParams(0,-2,1));
            Button manual=btn("▶ Manual"); manual.setOnClickListener(v->manualStartDialog()); r.addView(manual,new LinearLayout.LayoutParams(0,-2,1)); c.addView(r);
        } parent.addView(c);
    }
    void addTodayPlanCard(LinearLayout parent,String today,boolean includeManualTasks){
        LinearLayout plan=card(); addText(plan,"TODAY'S PLAN",19);
        ArrayList<JSONObject> blocks=todaysScheduleBlocks(today);
        boolean any=false;
        for(JSONObject b:blocks){ any=true; addCompactPlanRow(plan,b); }
        if(includeManualTasks){
            for(int i=0;i<tasks.length();i++){
                JSONObject t=tasks.optJSONObject(i); if(t==null||!today.equals(t.optString("date")))continue;
                any=true; addTaskRow(plan,t,i);
            }
        }
        if(!any)addText(plan,"No activities planned for today. Import a schedule or add a task.",13);
        Button add=btn("＋ Add Task"); add.setOnClickListener(v->taskDialog(null,-1)); plan.addView(add);
        parent.addView(plan);
    }

    void addCompactPlanRow(LinearLayout parent,JSONObject b){
        LinearLayout c=row(); c.setPadding(0,dp(7),0,dp(7));
        View stripe=new View(this); int color; try{color=Color.parseColor(b.optString("color","#4F7CFF"));}catch(Exception e){color=Color.parseColor("#4F7CFF");}
        stripe.setBackgroundColor(color); c.addView(stripe,new LinearLayout.LayoutParams(dp(5),dp(66)));
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setPadding(dp(10),0,dp(8),0);
        addText(mid,b.optString("start","")+" – "+b.optString("end",""),12); addText(mid,b.optString("title","Planned activity"),16); addText(mid,b.optString("category","OTHER")+(b.optBoolean("track",true)?" · tracked":""),11);
        c.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        boolean complete=isPlanCompletedForDate(b.optString("id"),date());
        Button st=primaryBtn(complete?"✓ Done":(hasOpenSessionForSchedule(b)?"▶ Resume": "▶ Start"));
        st.setOnClickListener(v->{if(!complete)startScheduleBlock(b);}); c.addView(st);
        parent.addView(c);
    }

    void addTaskRow(LinearLayout parent, JSONObject t,int index){
        LinearLayout r=row(); r.setPadding(0,dp(8),0,dp(8));
        CheckBox cb=new CheckBox(this); cb.setChecked("completed".equals(t.optString("status"))); r.addView(cb,new LinearLayout.LayoutParams(dp(42),-2));
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL);
        addText(mid,t.optString("title"),15);
        String effort="Estimate "+formatMin(t.optInt("estimateMin",0))+" · Actual "+formatMin(t.optInt("actualMin",0))+" · Remaining "+formatMin(t.optInt("remainingMin",0));
        TextView e=tv(effort,11); e.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(e);
        r.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        Button st=primaryBtn("completed".equals(t.optString("status"))?"✓ Done":"▶ Start");
        st.setOnClickListener(v->{ if(!"completed".equals(t.optString("status"))) startTimerForTask(index); }); r.addView(st);
        cb.setOnClickListener(v->{try{if(cb.isChecked()){ if(getActiveTimer()!=null&&t.optString("id").equals(getActiveTimer().optString("taskId")))stopActiveTimer(true); else {completeTask(t);showHome();}}else{t.put("status","not_started");t.remove("completedAt");saveState();showHome();}}catch(Exception ignored){}});
        parent.addView(r);
    }

    void showLearn(){
        page=1; roadmapDetailOpen=false; base("Learn","Dynamic roadmaps — import, edit and track each learning path separately.");
        if(roadmaps==null||roadmaps.length()==0){
            LinearLayout empty=card(); addText(empty,"No roadmaps yet",20); addText(empty,"Import a roadmap JSON to get started.",13); Button imp=primaryBtn("📥 Import Roadmap JSON"); imp.setOnClickListener(v->importRoadmap()); empty.addView(imp); box().addView(empty); return;
        }
        for(int i=0;i<roadmaps.length();i++){ JSONObject r=roadmaps.optJSONObject(i); if(r==null)continue; addRoadmapCard(box(),r); }
        LinearLayout actions=card();
        Button imp=primaryBtn("📥 Import Roadmap JSON"); imp.setOnClickListener(v->importRoadmap()); actions.addView(imp);
        Button exp=btn("📤 Export Current Roadmap JSON"); exp.setOnClickListener(v->exportRoadmap()); actions.addView(exp);
        Button dsa=btn("🧠 Add included DSA starter"); dsa.setOnClickListener(v->importBundledRoadmap("dsa-roadmap.json")); actions.addView(dsa);
        box().addView(actions);
        LinearLayout sessionsCard=card(); addText(sessionsCard,"Recent Study Sessions",20); ArrayList<JSONObject> recent=recentSessionObjects(30); int from=Math.max(0,recent.size()-8); for(int i=recent.size()-1;i>=from;i--)addText(sessionsCard,sessionLine(recent.get(i)),13); if(recent.isEmpty())addText(sessionsCard,"No saved sessions yet.",13); box().addView(sessionsCard);
    }
    void addRoadmapCard(LinearLayout parent,JSONObject r){
        String id=r.optString("id",roadmapId(r)); int total=topicCount(r); int done=completedFor(id).size(); int pct=total==0?0:Math.round(done*100f/total);
        LinearLayout c=card();
        LinearLayout head=row();
        addText(head,r.optString("icon","📚")+"  "+roadmapName(r),20); c.addView(head);
        addText(c,roadmapDescription(r),12);
        addText(c,done+" / "+total+" "+roadmapItemLabel(r)+" · "+pct+"% complete",13);
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(pct);c.addView(pb,new LinearLayout.LayoutParams(-1,dp(8)));
        LinearLayout row=row();
        Button open=primaryBtn(id.equals(currentRoadmapId)?"✓ Open roadmap":"Open roadmap"); open.setOnClickListener(v->{setCurrentRoadmap(id);showRoadmap();}); row.addView(open,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=iconBtn("✎","Edit roadmap"); edit.setOnClickListener(v->editRoadmapDialog(r)); row.addView(edit);
        c.addView(row); parent.addView(c);
    }
    void showRoadmap(){
        if(roadmap==null){showLearn();return;}
        page=1; roadmapDetailOpen=true;
        baseWithBack(roadmapName(roadmap),roadmapDescription(roadmap),()->showLearn());
        LinearLayout c=card(); addText(c,"ROADMAP PROGRESS",11);
        int total=topicCount(), done=completedFor(currentRoadmapId).size(), pct=total==0?0:Math.round(done*100f/total);
        addText(c,done+" / "+total+" "+roadmapItemLabel(roadmap)+" · "+pct+"%",26);
        int savedCount=0,laterCount=0; JSONArray allPh=roadmap.optJSONArray("phases"); if(allPh!=null)for(int pi=0;pi<allPh.length();pi++){JSONArray ts=phaseTopics(allPh.optJSONObject(pi));for(int ti=0;ti<ts.length();ti++){JSONObject tt=ts.optJSONObject(ti);if(tt!=null){if(tt.optBoolean("saved",false))savedCount++;if(tt.optBoolean("later",false))laterCount++;}}}
        addText(c,"★ Saved "+savedCount+"   ·   ↺ Later "+laterCount,11);
        ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); p.setMax(100); p.setProgress(pct); c.addView(p,new LinearLayout.LayoutParams(-1,dp(8)));
        LinearLayout actions=row(); Button edit=btn("✎ Edit"); edit.setOnClickListener(v->editRoadmapDialog(roadmap)); actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1)); Button addPhase=btn("＋ Add phase"); addPhase.setOnClickListener(v->addPhaseDialog()); actions.addView(addPhase,new LinearLayout.LayoutParams(0,-2,1)); c.addView(actions); box().addView(c);
        JSONArray phases=roadmap.optJSONArray("phases");
        if(phases==null){addText(box(),"No phases available. Import a valid roadmap JSON.",13);return;}
        for(int i=0;i<phases.length();i++){
            JSONObject ph=phases.optJSONObject(i); if(ph==null)continue;
            LinearLayout pc=card();
            LinearLayout phaseHead=row();
            View stripe=new View(this); int phaseColor; try{phaseColor=Color.parseColor(ph.optString("color","#4F7CFF"));}catch(Exception e){phaseColor=Color.parseColor("#4F7CFF");} stripe.setBackgroundColor(phaseColor); phaseHead.addView(stripe,new LinearLayout.LayoutParams(dp(6),dp(58)));
            LinearLayout phaseTitle=new LinearLayout(this); phaseTitle.setOrientation(LinearLayout.VERTICAL); phaseTitle.setPadding(dp(12),0,0,0);
            String phaseIcon=ph.optString("icon",""); addText(phaseTitle,(phaseIcon.isEmpty()?"":phaseIcon+"  ")+ph.optInt("number",i+1)+"  "+ph.optString("title"),18); phaseHead.addView(phaseTitle,new LinearLayout.LayoutParams(0,-2,1)); pc.addView(phaseHead);
            JSONArray topics=phaseTopics(ph); int d=0; for(int j=0;j<topics.length();j++){JSONObject t=topics.optJSONObject(j);if(t!=null&&completedFor(currentRoadmapId).contains(t.optString("id")))d++;}
            int pp=topics.length()==0?0:Math.round(d*100f/topics.length());
            addText(pc,ph.optString("duration","")+" · "+topics.length()+" items · "+d+" done · "+pp+"%",12);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(pp); pc.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));
            LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); pc.addView(list);
            LinearLayout phaseActions=row(); Button ep=iconBtn("✎","Edit phase"); ep.setOnClickListener(v->editPhaseDialog(ph)); phaseActions.addView(ep); Button at=btn("＋ Add item"); at.setOnClickListener(v->editTopicDialog(ph,null)); phaseActions.addView(at,new LinearLayout.LayoutParams(0,-2,1)); list.addView(phaseActions);
            boolean expanded=ph.optBoolean("expanded",false); list.setVisibility(expanded?View.VISIBLE:View.GONE);
            phaseHead.setOnClickListener(v->{boolean show=list.getVisibility()!=View.VISIBLE;list.setVisibility(show?View.VISIBLE:View.GONE);try{ph.put("expanded",show);saveRoadmaps();}catch(Exception ignored){}});
            for(int j=0;j<topics.length();j++){JSONObject t=topics.optJSONObject(j);if(t!=null)addTopicRow(list,t,ph);}
            box().addView(pc);
        }
    }
    JSONArray phaseTopics(JSONObject ph){
        JSONArray out=new JSONArray(); if(ph==null)return out;
        JSONArray cats=ph.optJSONArray("categories");
        if(cats!=null) for(int i=0;i<cats.length();i++){JSONObject cat=cats.optJSONObject(i);if(cat==null)continue;JSONArray ts=cat.optJSONArray("topics");if(ts!=null)for(int j=0;j<ts.length();j++)out.put(ts.optJSONObject(j));}
        JSONArray direct=ph.optJSONArray("topics"); if(direct!=null)for(int j=0;j<direct.length();j++)out.put(direct.optJSONObject(j));
        return out;
    }
    void addTopicRow(LinearLayout list,JSONObject t,JSONObject phase){
        LinearLayout c=card();
        String id=t.optString("id"); boolean done=completedFor(currentRoadmapId).contains(id);
        TextView name=tv((done?"✓ ":"")+t.optString("title"),15); name.setTypeface(null,1); c.addView(name);
        StringBuilder meta=new StringBuilder();
        String priority=t.optString("priority",""); if(!priority.isEmpty())meta.append(priority.toUpperCase(Locale.US));
        if(t.optBoolean("interview",false)){if(meta.length()>0)meta.append(" · ");meta.append("INTERVIEW");}
        String difficulty=t.optString("difficulty",""); if(!difficulty.isEmpty()){if(meta.length()>0)meta.append(" · ");meta.append(difficulty.toUpperCase(Locale.US));}
        if(meta.length()>0)addText(c,meta.toString(),11);
        JSONArray subs=t.optJSONArray("subtopics"); if(subs!=null&&subs.length()>0)addText(c,"Topics: "+joinJsonArray(subs),11);
        JSONArray tags=t.optJSONArray("tags"); if(tags!=null&&tags.length()>0)addText(c,"Tags: "+joinJsonArray(tags),11);
        LinearLayout actions=row();
        CheckBox cb=new CheckBox(this); cb.setChecked(done); cb.setText("DONE"); cb.setTextColor(Color.parseColor("#F4F6FB")); actions.addView(cb,new LinearLayout.LayoutParams(0,-2,1));
        String primary=t.optString("url",""); JSONArray links=t.optJSONArray("links"); boolean hasLink=!primary.trim().isEmpty()||(links!=null&&links.length()>0);
        if(hasLink){Button open=btn("🔗 Open");open.setOnClickListener(v->openTopicLinks(t));actions.addView(open,new LinearLayout.LayoutParams(0,-2,1));}
        boolean saved=t.optBoolean("saved",false); Button save=btn(saved?"★ Saved":"☆ Save"); save.setOnClickListener(v->{try{t.put("saved",!t.optBoolean("saved",false));saveRoadmaps();showRoadmap();}catch(Exception ignored){}});actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        boolean later=t.optBoolean("later",false); Button laterBtn=btn(later?"↺ Later":"Later"); laterBtn.setOnClickListener(v->{try{t.put("later",!t.optBoolean("later",false));saveRoadmaps();showRoadmap();}catch(Exception ignored){}});actions.addView(laterBtn,new LinearLayout.LayoutParams(0,-2,1));
        c.addView(actions);
        LinearLayout secondary=row(); Button track=btn("▶ Track");track.setOnClickListener(v->startPersistentTimer(t.optString("title","Roadmap item"),"LEARNING",null,null));secondary.addView(track,new LinearLayout.LayoutParams(0,-2,1)); Button edit=iconBtn("✎","Edit roadmap item");edit.setOnClickListener(v->editTopicDialog(phase,t));secondary.addView(edit);c.addView(secondary);
        cb.setOnClickListener(v->{HashSet<String> set=completedFor(currentRoadmapId);if(cb.isChecked())set.add(id);else set.remove(id);saveCompletedFor(currentRoadmapId,set);showRoadmap();});
        list.addView(c);
    }
    void openTopicLinks(JSONObject t){
        try{
            ArrayList<String> titles=new ArrayList<>(); ArrayList<String> urls=new ArrayList<>();
            String u=t.optString("url","").trim(); if(!u.isEmpty()){titles.add("Primary link");urls.add(u);}
            JSONArray links=t.optJSONArray("links"); if(links!=null)for(int i=0;i<links.length();i++){JSONObject l=links.optJSONObject(i);if(l==null)continue;String lu=l.optString("url","").trim();if(lu.isEmpty())continue;titles.add(l.optString("title","Resource "+(i+1)));urls.add(lu);}
            if(urls.isEmpty()){toast("No link configured");return;}
            if(urls.size()==1){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(urls.get(0))));return;}
            new AlertDialog.Builder(this).setTitle(t.optString("title","Resources")).setItems(titles.toArray(new String[0]),(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(urls.get(w))));}catch(Exception e){toast("No app can open this link");}}).show();
        }catch(Exception e){toast("Could not open link");}
    }

    void showPlan(){
        page=2; roadmapDetailOpen=false; base("Plan","Your schedule is the source of truth for today's planned activities.");
        LinearLayout actions=card(); addText(actions,"DAILY SCHEDULE",19);
        Button imp=primaryBtn("📥 Import Daily Schedule JSON"); imp.setOnClickListener(v->importDailySchedule()); actions.addView(imp);
        Button exp=btn("📤 Export Daily Schedule JSON"); exp.setOnClickListener(v->exportDailySchedule()); actions.addView(exp);
        Button addBlock=btn("＋ Add schedule block"); addBlock.setOnClickListener(v->scheduleBlockDialog(null,-1)); actions.addView(addBlock);
        box().addView(actions);
        addTrackingCard(box());

        String today=date(); LinearLayout timeline=card(); addText(timeline,"Today · "+today,20);
        ArrayList<JSONObject> blocks=todaysScheduleBlocks(today);
        if(blocks.isEmpty()) addText(timeline,"No schedule imported. Import a JSON schedule or add a block.",13);
        else for(JSONObject b:blocks) addScheduleBlockRow(timeline,b);
        box().addView(timeline);

        LinearLayout tasksCard=card(); addText(tasksCard,"TODAY'S TASKS",19); boolean any=false;
        for(int i=0;i<this.tasks.length();i++){JSONObject t=this.tasks.optJSONObject(i); if(t!=null&&today.equals(t.optString("date"))){any=true;taskCard(tasksCard,t,i);}}
        if(!any)addText(tasksCard,"No manually added tasks. Your schedule above is already tracked automatically.",13);
        Button add=btn("＋ Add Task"); add.setOnClickListener(v->taskDialog(null,-1)); tasksCard.addView(add); box().addView(tasksCard);

        addSessionSummaryCard(box(),today);
    }

    void addSessionSummaryCard(LinearLayout parent,String day){
        LinearLayout history=card(); history.setMinimumHeight(0); addText(history,"TODAY'S TRACKED ACTIVITIES",19);
        ArrayList<JSONObject> groups=aggregateSessions(day);
        if(groups.isEmpty()){addText(history,"No activities tracked today.",13);parent.addView(history);return;}
        for(JSONObject g:groups){
            LinearLayout r=row(); r.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL); r.setMinimumHeight(0); r.setPadding(0,dp(6),0,dp(6));
            LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);mid.setMinimumHeight(0);
            addText(mid,g.optString("title"),16);addText(mid,g.optString("category","OTHER")+" · "+formatDurationSmart(g.optLong("durationMs",0))+" · "+g.optInt("sessionCount",0)+" session"+(g.optInt("sessionCount",0)==1?"":"s"),11);
            r.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
            if(g.optBoolean("resumable",false)){Button resume=btn("Resume");resume.setOnClickListener(v->resumeSessionFromGroup(g));r.addView(resume,new LinearLayout.LayoutParams(-2,-2));}
            if(g.optBoolean("completed",false)){TextView check=tv("✓",16);check.setGravity(Gravity.CENTER);r.addView(check,new LinearLayout.LayoutParams(dp(36),dp(44)));}
            history.addView(r,new LinearLayout.LayoutParams(-1,-2));
        } parent.addView(history);
    }
    void taskCard(LinearLayout parent,JSONObject t,int idx){
        LinearLayout c=card(); addText(c,t.optString("title"),17); addText(c,t.optString("date")+" · "+t.optString("start","")+"–"+t.optString("end",""),12);
        addText(c,"Estimate "+formatMin(t.optInt("estimateMin",0))+" · Actual "+formatMin(t.optInt("actualMin",0))+" · Remaining "+formatMin(t.optInt("remainingMin",0)),12);
        addText(c,"Status: "+t.optString("status","not_started")+" · Priority: "+t.optString("priority","medium")+" · Category: "+t.optString("category","LEARNING"),11);
        LinearLayout r=row();
        Button st=primaryBtn("completed".equals(t.optString("status"))?"✓ Completed":"▶ Start / Resume"); st.setOnClickListener(v->{if(!"completed".equals(t.optString("status")))startTimerForTask(idx);}); r.addView(st,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=iconBtn("✎","Edit task"); edit.setOnClickListener(v->taskDialog(t,idx)); r.addView(edit);
        if(!"completed".equals(t.optString("status"))){Button done=btn("✓ End task");done.setOnClickListener(v->{completeTask(t);showPlan();});r.addView(done);}
        c.addView(r); parent.addView(c);
    }

    void showProgress(){
        page=3; roadmapDetailOpen=false; base("Progress","Understand where your time goes and whether it moves you forward.");
        String today=date(); long todayMs=todaySessionMs(today)+activeElapsedMsForDate(today);
        LinearLayout day=card(); addText(day,"TODAY",11); addText(day,formatDuration(todayMs)+" tracked",28);
        addText(day,"Real sessions: "+realSessionCount(today),13); box().addView(day);

        LinearLayout road=card(); addText(road,"ROADMAP PROGRESS",18);
        if(roadmaps!=null&&roadmaps.length()>0){
            for(int i=0;i<roadmaps.length();i++){JSONObject r=roadmaps.optJSONObject(i);if(r==null)continue;int total=topicCount(r);int done=completedFor(r.optString("id",roadmapId(r))).size();int pct=total==0?0:Math.round(done*100f/total);addText(road,r.optString("icon","📚")+"  "+roadmapName(r)+"   "+done+" / "+total+" "+roadmapItemLabel(r)+" · "+pct+"%",14);ProgressBar rb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);rb.setMax(100);rb.setProgress(pct);road.addView(rb,new LinearLayout.LayoutParams(-1,dp(6)));}
        } else addText(road,"No roadmaps imported.",13); box().addView(road);

        long learning=0,project=0,work=0,distraction=0,other=0;
        for(JSONObject x:sessionObjectsForDate(today)){long m=x.optLong("durationMs",0);String c=x.optString("category","OTHER");if("LEARNING".equals(c)||"CAREER".equals(c))learning+=m;else if("PROJECT".equals(c))project+=m;else if("WORK".equals(c))work+=m;else if("DISTRACTION".equals(c))distraction+=m;else other+=m;}
        LinearLayout dist=card(); addText(dist,"TIME DISTRIBUTION",18); addText(dist,"Learning / Career    "+formatDuration(learning),14); addText(dist,"Projects             "+formatDuration(project),14); addText(dist,"Work                 "+formatDuration(work),14); addText(dist,"Distraction           "+formatDuration(distraction),14); addText(dist,"Other / neutral       "+formatDuration(other),14); box().addView(dist);

        LinearLayout week=card(); addText(week,"THIS WEEK",18); long ms=0;int count=0;for(JSONObject x:recentSessionObjects(7)){ms+=x.optLong("durationMs",0);count++;}addText(week,"Tracked: "+formatDuration(ms),22);addText(week,"Sessions: "+count,13);box().addView(week);

        LinearLayout settings=card(); addText(settings,"TRACKING SETTINGS",18); addText(settings,"Automatic mode follows today's schedule. Manual mode lets you choose every activity.",12);
        RadioGroup rg=new RadioGroup(this); rg.setOrientation(RadioGroup.VERTICAL);
        RadioButton auto=new RadioButton(this);auto.setText("Automatic from Plan");auto.setTextColor(Color.parseColor("#F4F6FB"));auto.setId(View.generateViewId());
        RadioButton manual=new RadioButton(this);manual.setText("Manual");manual.setTextColor(Color.parseColor("#F4F6FB"));manual.setId(View.generateViewId());rg.addView(auto);rg.addView(manual);auto.setChecked(!isManualMode());manual.setChecked(isManualMode());
        rg.setOnCheckedChangeListener((g,id)->{boolean manualSelected=id==manual.getId();getSharedPreferences(PREFS,0).edit().putBoolean(KEY_TRACKING_MODE,manualSelected).apply();showProgress();});settings.addView(rg);
        Button manualStart=btn("▶ Start manual activity");manualStart.setOnClickListener(v->manualStartDialog());settings.addView(manualStart);
        Button alarm=btn("🔔 Test alarm");alarm.setOnClickListener(v->NotificationHelper.showTest(this));settings.addView(alarm);
        if(Build.VERSION.SDK_INT>=31){
            AlarmManager amCheck=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
            boolean exact=amCheck!=null&&amCheck.canScheduleExactAlarms();
            addText(settings,exact?"Exact alarms: allowed":"Exact alarms: not allowed — reminders may fire late.",11);
            if(!exact){Button exactBtn=btn("Allow exact alarms");exactBtn.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("Could not open alarm settings");}});settings.addView(exactBtn);}
        }
        Button impS=btn("📥 Import daily schedule");impS.setOnClickListener(v->importDailySchedule());settings.addView(impS);
        Button imp=btn("📥 Import full backup");imp.setOnClickListener(v->importBackup());settings.addView(imp);
        Button exp=btn("📤 Export full backup");exp.setOnClickListener(v->exportBackup());settings.addView(exp);
        Button reset=btn("Reset all data");reset.setOnClickListener(v->confirmReset());settings.addView(reset);box().addView(settings);
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
        EditText categoryE=new EditText(this); categoryE.setHint("Category: LEARNING / PROJECT / WORK / DISTRACTION / OTHER"); categoryE.setText(existing==null?"LEARNING":existing.optString("category","LEARNING")); l.addView(categoryE);
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
                    String cat=categoryE.getText().toString().trim().toUpperCase(Locale.US);
                    t.put("category",cat.isEmpty()?"LEARNING":cat);
                    if(!t.has("actualMin"))t.put("actualMin",0); if(!t.has("status"))t.put("status","not_started");
                    if(existing==null) tasks.put(t); scheduleReminder(this,t); saveState(); showPlan();
                }catch(Exception e){toast("Could not save task: "+e.getMessage());}
            }).setNegativeButton("Cancel",null).show();
    }

    ArrayAdapter<String> categoryAdapter(){
        final String[] cats={"WORK","LEARNING","PROJECT","CAREER","HEALTH","PERSONAL","FAMILY","COMMUTE","FOOD","SLEEP","BREAK","ENTERTAINMENT","DISTRACTION","OTHER"};
        return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,cats){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convertView,parent);v.setTextColor(Color.parseColor("#F4F6FB"));v.setTextSize(14);v.setPadding(dp(12),dp(10),dp(12),dp(10));return v;}
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(Color.parseColor("#F4F6FB"));v.setBackgroundColor(Color.parseColor("#1B1F2A"));v.setTextSize(14);v.setPadding(dp(12),dp(10),dp(12),dp(10));return v;}
        };
    }

    Spinner categorySpinner(String selected){
        Spinner sp=new Spinner(this); sp.setAdapter(categoryAdapter()); String[] cats={"WORK","LEARNING","PROJECT","CAREER","HEALTH","PERSONAL","FAMILY","COMMUTE","FOOD","SLEEP","BREAK","ENTERTAINMENT","DISTRACTION","OTHER"};
        for(int i=0;i<cats.length;i++)if(cats[i].equals(selected)){sp.setSelection(i);break;} return sp;
    }

    void activityLogDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);
        addText(l,"Add a completed activity that happened earlier. For live tracking use Start / Stop.",12);
        EditText titleE=new EditText(this);titleE.setHint("Activity");l.addView(titleE);
        EditText durE=new EditText(this);durE.setHint("Duration in minutes");durE.setInputType(2);l.addView(durE);
        Spinner cat=categorySpinner("OTHER");l.addView(cat);
        new AlertDialog.Builder(this).setTitle("Log past activity").setView(l).setPositiveButton("Save",(d,w)->{try{int min=Math.max(1,Integer.parseInt(durE.getText().toString().trim()));JSONObject s=new JSONObject();s.put("id",UUID.randomUUID().toString());s.put("title",titleE.getText().toString().trim());s.put("category",String.valueOf(cat.getSelectedItem()));s.put("date",date());s.put("durationMs",min*60000L);s.put("createdAt",dateTime());sessions.put(s);saveState();showHome();}catch(Exception e){toast("Could not log activity: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();
    }

    void manualStartDialog(){
        if(getActiveTimer()!=null){toast("Stop the current timer first.");return;}
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);
        EditText titleE=new EditText(this);titleE.setHint("What are you doing?");l.addView(titleE);
        Spinner cat=categorySpinner("LEARNING");l.addView(cat);
        new AlertDialog.Builder(this).setTitle("Start manual activity").setView(l).setPositiveButton("Start",(d,w)->{String title=titleE.getText().toString().trim();if(title.isEmpty()){toast("Enter an activity name.");return;}startPersistentTimer(title,String.valueOf(cat.getSelectedItem()),null,null);}).setNegativeButton("Cancel",null).show();
    }

    void chooseTaskForTimer(){
        if(getActiveTimer()!=null){toast("A timer is already running.");return;}
        if(tasks.length()==0){toast("Create a task first.");return;}
        String[] names=new String[tasks.length()]; for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);names[i]=t.optString("title");}
        new AlertDialog.Builder(this).setTitle("Start task").setItems(names,(d,w)->startTimerForTask(w)).show();
    }

    void startTimerForTask(int idx){
        JSONObject t=tasks.optJSONObject(idx); if(t==null)return;
        if("completed".equals(t.optString("status"))){toast("This task is already complete.");return;}
        if(getActiveTimer()!=null){toast("Stop the current timer first.");return;}
        try{t.put("status","in_progress");saveState();startPersistentTimer(t.optString("title","Task"),t.optString("category","LEARNING"),t.optString("id"),null);}
        catch(Exception e){toast("Could not start task: "+safeMessage(e));}
    }

    void startScheduleBlock(JSONObject b){
        if(b==null)return; if(getActiveTimer()!=null){toast("Stop the current timer first.");return;}
        if(isPlanCompletedForDate(b.optString("id"),date())){toast("This planned activity is already complete.");return;}
        startPersistentTimer(b.optString("title","Planned activity"),b.optString("category","OTHER"),null,b.optString("id"));
    }

    void startPersistentTimer(String title,String category,String taskId,String scheduleId){
        try{JSONObject a=new JSONObject();a.put("id",UUID.randomUUID().toString());a.put("title",title);a.put("category",category);a.put("taskId",taskId==null?"":taskId);a.put("scheduleId",scheduleId==null?"":scheduleId);a.put("startAt",System.currentTimeMillis());a.put("date",date());a.put("completed",false);saveActiveTimer(a);toast("Timer started");showHome();}
        catch(Exception e){toast("Could not start timer: "+safeMessage(e));}
    }

    JSONObject getActiveTimer(){
        String raw=getSharedPreferences(PREFS,0).getString(KEY_ACTIVE_TIMER,null);if(raw==null||raw.trim().isEmpty())return null;try{return new JSONObject(raw);}catch(Exception e){getSharedPreferences(PREFS,0).edit().remove(KEY_ACTIVE_TIMER).apply();return null;}
    }
    void saveActiveTimer(JSONObject a){getSharedPreferences(PREFS,0).edit().putString(KEY_ACTIVE_TIMER,a.toString()).apply();}
    void clearActiveTimer(){getSharedPreferences(PREFS,0).edit().remove(KEY_ACTIVE_TIMER).apply();}
    boolean isManualMode(){return getSharedPreferences(PREFS,0).getBoolean(KEY_TRACKING_MODE,false);}

    long activeElapsedMsForDate(String d){JSONObject a=getActiveTimer();if(a==null||!d.equals(a.optString("date")))return 0;return Math.max(0,System.currentTimeMillis()-a.optLong("startAt",System.currentTimeMillis()));}
    void updateTimerUi(){JSONObject a=getActiveTimer();if(timerText!=null&&a!=null){long elapsed=Math.max(0,System.currentTimeMillis()-a.optLong("startAt",System.currentTimeMillis()));timerText.setText(formatClock(elapsed));}if(timerText!=null&&a==null){timerText=null;timerTitle=null;timerMeta=null;timerButton=null;timerCompleteButton=null;}}
    String formatClock(long ms){long sec=Math.max(0,ms/1000);long h=sec/3600;long m=(sec%3600)/60;long s=sec%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,s);}

    void stopActiveTimer(boolean complete){
        JSONObject a=getActiveTimer();if(a==null){toast("No timer is running.");return;}
        long end=System.currentTimeMillis();long duration=Math.max(0,end-a.optLong("startAt",end));
        if(duration<1000){clearActiveTimer();saveState();toast("No measurable time was recorded.");showHome();return;}
        try{
            JSONObject session=new JSONObject();session.put("id",UUID.randomUUID().toString());session.put("title",a.optString("title"));session.put("category",a.optString("category","OTHER"));session.put("date",a.optString("date",date()));session.put("durationMs",duration);session.put("startAt",a.optLong("startAt",end));session.put("endAt",end);session.put("createdAt",dateTime());session.put("taskId",a.optString("taskId",""));session.put("scheduleId",a.optString("scheduleId",""));session.put("completed",complete);sessions.put(session);
            String taskId=a.optString("taskId","");if(!taskId.isEmpty()){JSONObject task=findTask(taskId);if(task!=null){int min=Math.max(1,(int)Math.round(duration/60000f));task.put("actualMin",task.optInt("actualMin",0)+min);task.put("remainingMin",Math.max(0,task.optInt("remainingMin",task.optInt("estimateMin",min))-min));if(complete){task.put("status","completed");task.put("remainingMin",0);task.put("completedAt",dateTime());}else task.put("status","in_progress");}}
            String scheduleId=a.optString("scheduleId","");if(complete&&!scheduleId.isEmpty())markPlanCompleted(scheduleId,a.optString("date",date()));
            clearActiveTimer();saveState();toast("Saved "+formatDurationSmart(duration));showHome();
        }catch(Exception e){toast("Could not save session: "+safeMessage(e));}
    }

    void resumeSession(JSONObject s){
        if(getActiveTimer()!=null){toast("Stop the current timer first.");return;}
        String taskId=s.optString("taskId","");String scheduleId=s.optString("scheduleId","");
        if(s.optBoolean("completed",false)){toast("This activity is already complete.");return;}
        if(!scheduleId.isEmpty()&&isPlanCompletedForDate(scheduleId,date())){toast("This planned activity is already complete.");return;}
        if(!taskId.isEmpty()){JSONObject t=findTask(taskId);if(t==null){toast("The original task is no longer available.");return;}if("completed".equals(t.optString("status"))){toast("This task is already complete.");return;}try{t.put("status","in_progress");saveState();}catch(Exception ignored){}startPersistentTimer(t.optString("title",s.optString("title")),t.optString("category",s.optString("category","OTHER")),taskId,scheduleId);return;}
        startPersistentTimer(s.optString("title"),s.optString("category","OTHER"),null,scheduleId);
    }

    void resumeSessionFromGroup(JSONObject g){
        if(getActiveTimer()!=null){toast("Stop the current timer first.");return;}
        if(g.optBoolean("completed",false)){toast("This activity is already complete.");return;}
        JSONObject source=g.optJSONObject("lastSession");if(source!=null){resumeSession(source);return;}
        startPersistentTimer(g.optString("title"),g.optString("category","OTHER"),g.optString("taskId",""),g.optString("scheduleId",""));
    }

    JSONObject findTask(String id){for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
    void completeTask(JSONObject t){try{if(getActiveTimer()!=null&&t.optString("id").equals(getActiveTimer().optString("taskId"))){stopActiveTimer(true);return;}t.put("status","completed");t.put("remainingMin",0);t.put("completedAt",dateTime());saveState();toast("Task completed");}catch(Exception e){toast("Could not complete task: "+safeMessage(e));}}

    void scheduleReminder(Context c,JSONObject t){ ReminderReceiver.schedule(c,t); }

    void loadRoadmap(){
        try{
            SharedPreferences sp=getSharedPreferences(PREFS,0); String stored=sp.getString(KEY_ROADMAPS,null); roadmaps=stored==null?new JSONArray():new JSONArray(stored);
            if(roadmaps.length()==0){ String override=sp.getString("roadmapOverride",null); JSONObject r=override==null?new JSONObject(readAsset("roadmap.json")):new JSONObject(override); normalizeRoadmap(r,"java-backend","Java Backend","☕"); roadmaps.put(r); saveRoadmaps(); }
            currentRoadmapId=sp.getString(KEY_CURRENT_ROADMAP,null); if(currentRoadmapId==null||findRoadmap(currentRoadmapId)==null)currentRoadmapId=roadmaps.optJSONObject(0).optString("id","java-backend"); roadmap=findRoadmap(currentRoadmapId); if(roadmap==null)throw new Exception("No roadmap available");
        }catch(Exception e){ throw new RuntimeException("Roadmap load failed: "+safeMessage(e),e); }
    }
    String readAsset(String name)throws Exception{try(InputStream in=getAssets().open(name);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toString("UTF-8");}}
    void loadState(){
        String raw=getSharedPreferences(PREFS,0).getString("state",null);
        try{ if(raw==null){tasks=new JSONArray();sessions=new JSONArray();schedules=loadBundledSchedule();return;} JSONObject st=new JSONObject(raw); tasks=st.optJSONArray("tasks");sessions=st.optJSONArray("sessions");schedules=st.optJSONArray("schedules");if(tasks==null)tasks=new JSONArray();if(sessions==null)sessions=new JSONArray();if(schedules==null)schedules=loadBundledSchedule(); }catch(Exception e){tasks=new JSONArray();sessions=new JSONArray();schedules=loadBundledSchedule();}
    }
    JSONArray loadBundledSchedule(){ try{JSONObject sch=new JSONObject(readAsset("daily-schedule.json"));validateSchedule(sch);JSONArray blocks=sch.optJSONArray("blocks");return blocks==null?new JSONArray():blocks;}catch(Exception e){return new JSONArray();} }
    void saveState(){ try{JSONObject st=new JSONObject();st.put("tasks",tasks);st.put("sessions",sessions);st.put("schedules",schedules);getSharedPreferences(PREFS,0).edit().putString("state",st.toString()).apply();saveRoadmaps();}catch(Exception e){android.util.Log.e("DevTrack","saveState failed",e);} }
    void saveRoadmaps(){if(roadmaps==null)return;getSharedPreferences(PREFS,0).edit().putString(KEY_ROADMAPS,roadmaps.toString()).putString(KEY_CURRENT_ROADMAP,currentRoadmapId==null?"":currentRoadmapId).apply();}
    void migrateLegacyCompletion(){try{SharedPreferences sp=getSharedPreferences(PREFS,0);if(sp.contains("completedByRoadmap"))return;String raw=sp.getString("state",null);if(raw==null)return;JSONObject st=new JSONObject(raw);JSONArray old=st.optJSONArray("completed");if(old==null)return;JSONObject all=new JSONObject();all.put("java-backend",old);sp.edit().putString("completedByRoadmap",all.toString()).apply();}catch(Exception ignored){}}
    JSONObject findRoadmap(String id){if(roadmaps==null)return null;for(int i=0;i<roadmaps.length();i++){JSONObject r=roadmaps.optJSONObject(i);if(r!=null&&id.equals(r.optString("id")))return r;}return null;}
    String roadmapId(JSONObject r){String id=r.optString("id");if(!id.isEmpty())return id;return UUID.randomUUID().toString();}
    String roadmapName(JSONObject r){String n=r.optString("name");if(!n.isEmpty())return n;return r.optJSONObject("metadata")!=null?r.optJSONObject("metadata").optString("title","Roadmap"):"Roadmap";}
    String roadmapDescription(JSONObject r){return r.optString("description",r.optJSONObject("metadata")!=null?r.optJSONObject("metadata").optString("target","Editable learning roadmap"):"Editable learning roadmap");}
    void normalizeRoadmap(JSONObject r,String fallbackId,String fallbackName,String icon){
        try{
            if(!r.has("id")||r.optString("id").trim().isEmpty())r.put("id",fallbackId);
            JSONObject meta=r.optJSONObject("metadata");String metaTitle=meta==null?"":meta.optString("title","").trim();
            if(!r.has("name")||r.optString("name").trim().isEmpty()||"Imported Roadmap".equalsIgnoreCase(r.optString("name")))r.put("name",metaTitle.isEmpty()?fallbackName:metaTitle);
            if(!r.has("icon")||r.optString("icon").trim().isEmpty())r.put("icon",icon);
            if(!r.has("description")||r.optString("description").trim().isEmpty())r.put("description",meta==null?"Editable learning roadmap":meta.optString("target","Editable learning roadmap"));
            if(!r.has("format"))r.put("format","devtrack-roadmap");if(!r.has("version"))r.put("version",1);
        }catch(Exception ignored){}
    }
    void setCurrentRoadmap(String id){currentRoadmapId=id;roadmap=findRoadmap(id);saveRoadmaps();}
    HashSet<String> completedFor(String id){HashSet<String> out=new HashSet<>();try{String raw=getSharedPreferences(PREFS,0).getString("completedByRoadmap","{}");JSONObject all=new JSONObject(raw);JSONArray a=all.optJSONArray(id);if(a!=null)for(int i=0;i<a.length();i++)out.add(a.optString(i));}catch(Exception ignored){}return out;}
    void saveCompletedFor(String id,HashSet<String> set){try{SharedPreferences sp=getSharedPreferences(PREFS,0);JSONObject all=new JSONObject(sp.getString("completedByRoadmap","{}"));JSONArray a=new JSONArray();for(String x:set)a.put(x);all.put(id,a);sp.edit().putString("completedByRoadmap",all.toString()).apply();completed.clear();completed.addAll(set);}catch(Exception ignored){}}
    void editRoadmapDialog(JSONObject r){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);EditText name=new EditText(this);name.setHint("Roadmap name");name.setText(roadmapName(r));l.addView(name);EditText desc=new EditText(this);desc.setHint("Description");desc.setText(roadmapDescription(r));l.addView(desc);EditText icon=new EditText(this);icon.setHint("Icon, e.g. 🧠");icon.setText(r.optString("icon","📚"));l.addView(icon);
        new AlertDialog.Builder(this).setTitle("Edit roadmap").setView(l).setPositiveButton("Save",(d,w)->{try{r.put("name",name.getText().toString().trim());r.put("description",desc.getText().toString().trim());r.put("icon",icon.getText().toString().trim());saveRoadmaps();showRoadmap();}catch(Exception e){toast("Could not edit roadmap");}}).setNegativeButton("Cancel",null).show();
    }
    void editPhaseDialog(JSONObject ph){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("Phase title");name.setText(ph.optString("title"));l.addView(name);EditText duration=new EditText(this);duration.setHint("Duration");duration.setText(ph.optString("duration"));l.addView(duration);new AlertDialog.Builder(this).setTitle("Edit phase").setView(l).setPositiveButton("Save",(d,w)->{try{ph.put("title",name.getText().toString().trim());ph.put("duration",duration.getText().toString().trim());saveRoadmaps();showRoadmap();}catch(Exception e){toast("Could not edit phase");}}).setNegativeButton("Cancel",null).show();}
    void editTopicDialog(JSONObject ph,JSONObject existing){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);
        EditText name=new EditText(this);name.setHint("Title / question");name.setText(existing==null?"":existing.optString("title"));l.addView(name);
        EditText priority=new EditText(this);priority.setHint("Priority: low / medium / high");priority.setText(existing==null?"medium":existing.optString("priority","medium"));l.addView(priority);
        EditText difficulty=new EditText(this);difficulty.setHint("Difficulty: EASY / MEDIUM / HARD");difficulty.setText(existing==null?"":existing.optString("difficulty"));l.addView(difficulty);
        CheckBox interview=new CheckBox(this);interview.setText("Interview item");interview.setTextColor(Color.parseColor("#F4F6FB"));interview.setChecked(existing!=null&&existing.optBoolean("interview",false));l.addView(interview);
        EditText subs=new EditText(this);subs.setHint("Subtopics, comma separated");subs.setText(existing==null?"":joinJsonArray(existing.optJSONArray("subtopics")));l.addView(subs);
        EditText tags=new EditText(this);tags.setHint("Tags, comma separated");tags.setText(existing==null?"":joinJsonArray(existing.optJSONArray("tags")));l.addView(tags);
        EditText url=new EditText(this);url.setHint("Primary coding/resource URL");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setText(existing==null?"":existing.optString("url",""));l.addView(url);
        EditText links=new EditText(this);links.setHint("Extra links: Title | https://... (one per line)");links.setMinLines(2);links.setText(existing==null?"":linksToText(existing.optJSONArray("links")));l.addView(links);
        new AlertDialog.Builder(this).setTitle(existing==null?"Add roadmap item":"Edit roadmap item").setView(l).setPositiveButton("Save",(d,w)->{try{
            JSONObject t=existing==null?new JSONObject():existing;if(existing==null){t.put("id",UUID.randomUUID().toString());JSONArray ts=ph.optJSONArray("topics");if(ts==null){ts=new JSONArray();ph.put("topics",ts);}ts.put(t);}
            t.put("title",name.getText().toString().trim());t.put("priority",priority.getText().toString().trim());t.put("difficulty",difficulty.getText().toString().trim().toUpperCase(Locale.US));t.put("interview",interview.isChecked());
            String raw=subs.getText().toString().trim();JSONArray a=new JSONArray();if(!raw.isEmpty())for(String x:raw.split(","))if(!x.trim().isEmpty())a.put(x.trim());if(a.length()>0)t.put("subtopics",a);else t.remove("subtopics");
            String tagRaw=tags.getText().toString().trim();JSONArray tagArray=new JSONArray();if(!tagRaw.isEmpty())for(String x:tagRaw.split(","))if(!x.trim().isEmpty())tagArray.put(x.trim());if(tagArray.length()>0)t.put("tags",tagArray);else t.remove("tags");
            String primary=url.getText().toString().trim();if(primary.isEmpty())t.remove("url");else t.put("url",primary);
            JSONArray extra=parseLinks(links.getText().toString());if(extra.length()>0)t.put("links",extra);else t.remove("links");
            saveRoadmaps();showRoadmap();
        }catch(Exception e){toast("Could not save item: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();
    }
    String linksToText(JSONArray links){if(links==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<links.length();i++){JSONObject l=links.optJSONObject(i);if(l==null)continue;if(b.length()>0)b.append("\n");b.append(l.optString("title","Resource")).append(" | ").append(l.optString("url",""));}return b.toString();}
    JSONArray parseLinks(String raw){JSONArray out=new JSONArray();if(raw==null||raw.trim().isEmpty())return out;for(String line:raw.split("\\n")){String[] p=line.split("\\|",2);if(p.length<2)continue;String title=p[0].trim(),url=p[1].trim();if(url.isEmpty())continue;JSONObject o=new JSONObject();try{o.put("title",title.isEmpty()?"Resource":title);o.put("url",url);out.put(o);}catch(Exception ignored){}}return out;}
    String joinJsonArray(JSONArray a){if(a==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)b.append(", ");b.append(a.optString(i));}return b.toString();}
    void addPhaseDialog(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("Phase title");l.addView(name);EditText duration=new EditText(this);duration.setHint("Duration, e.g. Week 1");l.addView(duration);new AlertDialog.Builder(this).setTitle("Add phase").setView(l).setPositiveButton("Add",(d,w)->{try{JSONArray ps=roadmap.optJSONArray("phases");if(ps==null){ps=new JSONArray();roadmap.put("phases",ps);}JSONObject ph=new JSONObject();ph.put("id",UUID.randomUUID().toString());ph.put("number",ps.length()+1);ph.put("title",name.getText().toString().trim());ph.put("duration",duration.getText().toString().trim());ph.put("topics",new JSONArray());ps.put(ph);saveRoadmaps();showRoadmap();}catch(Exception e){toast("Could not add phase");}}).setNegativeButton("Cancel",null).show();}
    String roadmapItemLabel(JSONObject r){
        if(r!=null){String type=r.optString("itemType","").trim();if("question".equalsIgnoreCase(type))return "questions";String name=roadmapName(r).toLowerCase(Locale.US);if(name.contains("dsa")||name.contains("data structures")||name.contains("algorithm"))return "questions";}
        return "topics";
    }
    int topicCount(){return topicCount(roadmap);}
    int topicCount(JSONObject r){if(r==null)return 0;JSONArray ps=r.optJSONArray("phases");if(ps==null)return 0;int n=0;for(int i=0;i<ps.length();i++)n+=phaseTopics(ps.optJSONObject(i)).length();return n;}
    long todaySessionMs(String d){long n=0;for(JSONObject s:sessionObjectsForDate(d))n+=s.optLong("durationMs",0);return n;}
    int realSessionCount(String d){return sessionObjectsForDate(d).size();}
    ArrayList<JSONObject> sessionObjectsForDate(String d){ArrayList<JSONObject> out=new ArrayList<>();for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s==null||!d.equals(s.optString("date"))||s.optLong("durationMs",0)<=0)continue;out.add(s);}return out;}
    ArrayList<JSONObject> recentSessionObjects(int days){ArrayList<JSONObject> out=new ArrayList<>();for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s==null||s.optLong("durationMs",0)<=0)continue;if(withinDaysInclusive(s.optString("date"),days))out.add(s);}return out;}
    boolean withinDaysInclusive(String d,int days){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);Date x=f.parse(d), now=f.parse(date());long diff=(now.getTime()-x.getTime())/86400000L;return diff>=0&&diff<days;}catch(Exception e){return false;}}
    ArrayList<JSONObject> aggregateSessions(String day){
        LinkedHashMap<String,JSONObject> map=new LinkedHashMap<>();
        for(JSONObject s:sessionObjectsForDate(day)){
            String key=s.optString("scheduleId","");if(key.isEmpty())key=s.optString("taskId","");if(key.isEmpty())key=s.optString("title","")+"|"+s.optString("category","OTHER");
            JSONObject g=map.get(key);if(g==null){g=new JSONObject();try{g.put("key",key);g.put("title",s.optString("title"));g.put("category",s.optString("category","OTHER"));g.put("durationMs",0);g.put("sessionCount",0);g.put("taskId",s.optString("taskId",""));g.put("scheduleId",s.optString("scheduleId",""));g.put("completed",false);g.put("resumable",false);map.put(key,g);}catch(Exception ignored){}}
            try{g.put("durationMs",g.optLong("durationMs",0)+s.optLong("durationMs",0));g.put("sessionCount",g.optInt("sessionCount",0)+1);if(!s.optBoolean("completed",false)){g.put("resumable",true);g.put("completed",false);g.put("lastSession",s);}else if(!g.optBoolean("resumable",false)){g.put("completed",true);}}catch(Exception ignored){}
        }
        ArrayList<JSONObject> out=new ArrayList<>(map.values());for(JSONObject g:out){try{String sid=g.optString("scheduleId","");if(!sid.isEmpty()&&isPlanCompletedForDate(sid,day)){g.put("completed",true);g.put("resumable",false);}}catch(Exception ignored){}}return out;
    }
    boolean hasOpenSessionForSchedule(JSONObject b){String id=b.optString("id","");if(id.isEmpty())return false;for(JSONObject s:sessionObjectsForDate(date()))if(id.equals(s.optString("scheduleId"))&&!s.optBoolean("completed",false))return true;return false;}
    String formatDurationSmart(long ms){if(ms<60000)return Math.max(1,ms/1000)+"s";return formatDuration(ms);}
    String sessionLine(JSONObject s){return s.optString("date")+" · "+s.optString("title")+" · "+formatDurationSmart(s.optLong("durationMs",0));}

    String date(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    String dateTime(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
    String shiftDate(String d,int days){try{Date x=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(d);Calendar c=Calendar.getInstance();c.setTime(x);c.add(Calendar.DATE,days);return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());}catch(Exception e){return date();}}
    boolean withinDays(String d,int days){try{Date x=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(d);long diff=System.currentTimeMillis()-x.getTime();return diff>=0&&diff<=days*86400000L;}catch(Exception e){return false;}}
    String formatMin(int m){if(m<60)return m+"m";return (m/60)+"h"+(m%60==0?"":(" "+m%60+"m"));}
    String formatDuration(long ms){long min=Math.max(0,Math.round(ms/60000f));return formatMin((int)min);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    boolean isCurrentSchedule(JSONObject b){String now=new SimpleDateFormat("HH:mm",Locale.US).format(new Date());return b.optString("start").compareTo(now)<=0&&b.optString("end").compareTo(now)>0;}
    boolean isPlanCompletedForDate(String scheduleId,String day){if(scheduleId==null||scheduleId.isEmpty())return false;String raw=getSharedPreferences(PREFS,0).getString(KEY_COMPLETED_PLANS,"{}");try{JSONObject o=new JSONObject(raw);JSONArray a=o.optJSONArray(day);if(a==null)return false;for(int i=0;i<a.length();i++)if(scheduleId.equals(a.optString(i)))return true;}catch(Exception ignored){}return false;}
    void markPlanCompleted(String scheduleId,String day){if(scheduleId==null||scheduleId.isEmpty())return;try{SharedPreferences sp=getSharedPreferences(PREFS,0);JSONObject o=new JSONObject(sp.getString(KEY_COMPLETED_PLANS,"{}"));JSONArray a=o.optJSONArray(day);if(a==null)a=new JSONArray();boolean exists=false;for(int i=0;i<a.length();i++)if(scheduleId.equals(a.optString(i)))exists=true;if(!exists)a.put(scheduleId);o.put(day,a);sp.edit().putString(KEY_COMPLETED_PLANS,o.toString()).apply();}catch(Exception ignored){}}

    JSONObject currentOrNextTrackableSchedule(String day){
        ArrayList<JSONObject> a=todaysScheduleBlocks(day);String now=new SimpleDateFormat("HH:mm",Locale.US).format(new Date());
        for(JSONObject b:a){if(!b.optBoolean("track",true)||isPlanCompletedForDate(b.optString("id"),day))continue;if(b.optString("start").compareTo(now)<=0&&b.optString("end").compareTo(now)>0)return b;}
        for(JSONObject b:a){if(!b.optBoolean("track",true)||isPlanCompletedForDate(b.optString("id"),day))continue;if(b.optString("start").compareTo(now)>=0)return b;}
        return null;
    }
    JSONObject nextTrackedScheduleBlock(String day){return currentOrNextTrackableSchedule(day);}
    JSONObject nextOrCurrentScheduleBlock(String day){return currentOrNextTrackableSchedule(day);}

    void validateSchedule(JSONObject sch)throws Exception{ if(sch==null)throw new Exception("Schedule is empty"); JSONArray blocks=sch.optJSONArray("blocks"); if(blocks==null)throw new Exception("Schedule is missing blocks"); for(int i=0;i<blocks.length();i++){JSONObject b=blocks.optJSONObject(i); if(b==null)throw new Exception("Invalid schedule block #"+(i+1)); if(b.optString("title").trim().isEmpty())throw new Exception("Block #"+(i+1)+" has no title"); if(b.optString("start").trim().isEmpty()||b.optString("end").trim().isEmpty())throw new Exception("Block #"+(i+1)+" needs start/end time"); String color=b.optString("color","#4F7CFF"); try{Color.parseColor(color);}catch(Exception e){throw new Exception("Invalid color in block #"+(i+1));}} }
    ArrayList<JSONObject> todaysScheduleBlocks(String day){ ArrayList<JSONObject> out=new ArrayList<>(); String dow; try{ Date parsed=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day); dow=new SimpleDateFormat("EEEE",Locale.US).format(parsed).toUpperCase(Locale.US); }catch(Exception e){ dow=new SimpleDateFormat("EEEE",Locale.US).format(new Date()).toUpperCase(Locale.US); } for(int i=0;i<schedules.length();i++){JSONObject b=schedules.optJSONObject(i); if(b==null)continue; JSONArray days=b.optJSONArray("days"); boolean ok=days==null||days.length()==0; if(days!=null)for(int j=0;j<days.length();j++)if(dow.equals(days.optString(j).toUpperCase(Locale.US)))ok=true; if(ok)out.add(b);} Collections.sort(out,(a,b)->a.optString("start").compareTo(b.optString("start"))); return out; }
    JSONObject nextScheduleBlock(String day){ ArrayList<JSONObject> a=todaysScheduleBlocks(day); String now=new SimpleDateFormat("HH:mm",Locale.US).format(new Date()); for(JSONObject b:a)if(b.optString("end").compareTo(now)>=0)return b; return null; }
    boolean hasTrackedSessionForSchedule(JSONObject b){String id=b.optString("id","");if(id.isEmpty())return false;for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s!=null&&date().equals(s.optString("date"))&&id.equals(s.optString("scheduleId")))return true;}return false;}

    void addScheduleBlockRow(LinearLayout parent,JSONObject b){
        LinearLayout row=row(); row.setPadding(0,dp(7),0,dp(7));
        View stripe=new View(this);int color;try{color=Color.parseColor(b.optString("color","#4F7CFF"));}catch(Exception e){color=Color.parseColor("#4F7CFF");}stripe.setBackgroundColor(color);row.addView(stripe,new LinearLayout.LayoutParams(dp(6),dp(72)));
        LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);mid.setPadding(dp(12),0,dp(8),0);
        addText(mid,b.optString("start")+" – "+b.optString("end"),12);addText(mid,b.optString("title"),16);addText(mid,b.optString("category","OTHER")+(b.optBoolean("track",true)?" · tracked":""),11);row.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        JSONObject alarm=b.optJSONObject("alarm");if(alarm!=null&&alarm.optBoolean("enabled",false)){TextView al=tv("🔔",16);row.addView(al,new LinearLayout.LayoutParams(dp(34),-2));}
        boolean complete=isPlanCompletedForDate(b.optString("id"),date());
        Button st=primaryBtn(complete?"✓ Done":(hasOpenSessionForSchedule(b)?"▶ Resume":(isCurrentSchedule(b)?"▶ Start":"Start")));st.setOnClickListener(v->{if(!complete)startScheduleBlock(b);});row.addView(st);
        Button edit=iconBtn("✎","Edit schedule block");edit.setOnClickListener(v->scheduleBlockDialog(b,-1));row.addView(edit);parent.addView(row);
    }

    void scheduleBlockDialog(JSONObject existing,int index){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);
        EditText titleE=new EditText(this);titleE.setHint("Title");titleE.setText(existing==null?"":existing.optString("title"));l.addView(titleE);
        EditText startE=new EditText(this);startE.setHint("Start HH:mm");startE.setText(existing==null?"20:00":existing.optString("start"));l.addView(startE);
        EditText endE=new EditText(this);endE.setHint("End HH:mm");endE.setText(existing==null?"21:00":existing.optString("end"));l.addView(endE);
        Spinner cat=categorySpinner(existing==null?"LEARNING":existing.optString("category","OTHER"));l.addView(cat);
        EditText colorE=new EditText(this);colorE.setHint("Color hex, e.g. #4F7CFF");colorE.setText(existing==null?"#4F7CFF":existing.optString("color","#4F7CFF"));l.addView(colorE);
        EditText daysE=new EditText(this);daysE.setHint("Days: MONDAY,TUESDAY,... (blank = every day)");daysE.setText(existing==null?"":daysString(existing));l.addView(daysE);
        CheckBox track=new CheckBox(this);track.setText("Include in time tracking");track.setTextColor(Color.parseColor("#F4F6FB"));track.setChecked(existing==null||existing.optBoolean("track",true));l.addView(track);
        CheckBox alarm=new CheckBox(this);alarm.setText("Alarm");alarm.setTextColor(Color.parseColor("#F4F6FB"));alarm.setChecked(existing!=null&&existing.optJSONObject("alarm")!=null&&existing.optJSONObject("alarm").optBoolean("enabled",false));l.addView(alarm);
        EditText minsE=new EditText(this);minsE.setHint("Alarm minutes before");minsE.setInputType(2);minsE.setText(existing==null?"5":String.valueOf(existing.optJSONObject("alarm")!=null?existing.optJSONObject("alarm").optInt("minutesBefore",5):5));l.addView(minsE);
        new AlertDialog.Builder(this).setTitle(existing==null?"Add schedule block":"Edit schedule block").setView(l).setPositiveButton("Save",(d,w)->{try{
            JSONObject b=existing==null?new JSONObject():existing;if(existing==null)b.put("id",UUID.randomUUID().toString());b.put("title",titleE.getText().toString().trim());b.put("start",startE.getText().toString().trim());b.put("end",endE.getText().toString().trim());b.put("category",String.valueOf(cat.getSelectedItem()));String color=colorE.getText().toString().trim();Color.parseColor(color);b.put("color",color);b.put("track",track.isChecked());String ds=daysE.getText().toString().trim();JSONArray days=new JSONArray();if(!ds.isEmpty())for(String x:ds.split(","))days.put(x.trim().toUpperCase(Locale.US));b.put("days",days);JSONObject a=new JSONObject();a.put("enabled",alarm.isChecked());a.put("minutesBefore",Math.max(0,Integer.parseInt(minsE.getText().toString().trim())));a.put("sound","alarm");b.put("alarm",a);if(existing==null)schedules.put(b);if(!a.optBoolean("enabled",false))ReminderReceiver.cancel(this,b.optString("id"));else ReminderReceiver.scheduleNext(this,b);saveState();showPlan();
        }catch(Exception e){toast("Could not save schedule: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();
    }

    String daysString(JSONObject b){StringBuilder x=new StringBuilder();JSONArray a=b.optJSONArray("days");if(a!=null)for(int i=0;i<a.length();i++){if(i>0)x.append(",");x.append(a.optString(i));}return x.toString();}
    void quickStartSchedule(JSONObject b){startScheduleBlock(b);}
    int minutesBetween(String a,String b){try{String[] x=a.split(":");String[] y=b.split(":");int m1=Integer.parseInt(x[0])*60+Integer.parseInt(x[1]);int m2=Integer.parseInt(y[0])*60+Integer.parseInt(y[1]);return m2>=m1?m2-m1:(24*60-m1+m2);}catch(Exception e){return 60;}}
    void scheduleAllImportedAlarms(){for(int i=0;i<schedules.length();i++){JSONObject b=schedules.optJSONObject(i);if(b!=null)ReminderReceiver.scheduleNext(this,b);}}
    void importBundledRoadmap(String assetName){
        try{
            JSONObject r=new JSONObject(readAsset(assetName)); normalizeRoadmap(r,UUID.randomUUID().toString(),"Imported Roadmap","📚");
            String id=r.optString("id",UUID.randomUUID().toString()); if(findRoadmap(id)!=null)id=UUID.randomUUID().toString(); r.put("id",id); roadmaps.put(r); setCurrentRoadmap(id); saveRoadmaps(); showRoadmap(); toast(roadmapName(r)+" added");
        }catch(Exception e){toast("Could not add roadmap: "+safeMessage(e));}
    }
    void importRoadmap(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_IMPORT);}
    void exportRoadmap(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,roadmapName(roadmap).replaceAll("[^A-Za-z0-9_-]","_")+".json");startActivityForResult(i,REQ_EXPORT);}
    void importDailySchedule(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_SCHEDULE_IMPORT);}
    void exportDailySchedule(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"devtrack-daily-schedule.json");startActivityForResult(i,REQ_SCHEDULE_EXPORT);}

    void importBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,300);}
    void exportBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"devtrack-backup-"+date()+".json");startActivityForResult(i,301);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;try{
        if(req==REQ_IMPORT){String raw=read(data.getData());JSONObject r=new JSONObject(raw);if(r.optJSONArray("phases")==null)throw new Exception("This JSON doesn't look like a roadmap (no 'phases' array found)");normalizeRoadmap(r,UUID.randomUUID().toString(),"Imported Roadmap","📚");String id=r.optString("id");new AlertDialog.Builder(this).setTitle("Import roadmap").setMessage("Import "+roadmapName(r)+" as a new editable roadmap?").setPositiveButton("Import",(d,w)->{try{String finalId=findRoadmap(id)!=null?UUID.randomUUID().toString():id;r.put("id",finalId);roadmaps.put(r);setCurrentRoadmap(finalId);saveRoadmaps();showLearn();toast("Roadmap imported");}catch(Exception e){toast("Could not import roadmap: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();}
        else if(req==REQ_EXPORT){write(data.getData(),roadmap.toString(2));toast("Roadmap exported");}
        else if(req==REQ_SCHEDULE_IMPORT){
            String raw=read(data.getData()); JSONObject sch=new JSONObject(raw); validateSchedule(sch);
            JSONArray imported=sch.optJSONArray("blocks");
            new AlertDialog.Builder(this).setTitle("Import daily schedule").setMessage("Replace the current schedule with "+imported.length()+" blocks? Alarm settings included in the JSON will be applied.").setPositiveButton("Replace",(d,w)->{
                for(int i=0;i<schedules.length();i++){JSONObject old=schedules.optJSONObject(i);if(old!=null)ReminderReceiver.cancel(this,old.optString("id"));} schedules=imported; saveState(); scheduleAllImportedAlarms(); showPlan(); toast("Daily schedule imported");
            }).setNegativeButton("Cancel",null).show();
        }
        else if(req==REQ_SCHEDULE_EXPORT){ JSONObject sch=new JSONObject(); sch.put("format","devtrack-daily-schedule"); sch.put("version",1); sch.put("timezone",java.util.TimeZone.getDefault().getID()); sch.put("days",new JSONArray().put("MONDAY").put("TUESDAY").put("WEDNESDAY").put("THURSDAY").put("FRIDAY").put("SATURDAY").put("SUNDAY")); sch.put("blocks",schedules); write(data.getData(),sch.toString(2)); toast("Daily schedule exported"); }
        else if(req==300){String raw=read(data.getData());JSONObject s=new JSONObject(raw);if(s.has("roadmaps")){roadmaps=s.optJSONArray("roadmaps");if(roadmaps==null)roadmaps=new JSONArray();currentRoadmapId=s.optString("currentRoadmapId",roadmaps.length()>0?roadmaps.optJSONObject(0).optString("id"):null);roadmap=findRoadmap(currentRoadmapId);saveRoadmaps();}else if(s.has("roadmap")){JSONObject importedRoadmap=s.optJSONObject("roadmap");if(importedRoadmap!=null){normalizeRoadmap(importedRoadmap,"java-backend","Java Backend","☕");roadmaps=new JSONArray().put(importedRoadmap);currentRoadmapId=importedRoadmap.optString("id");roadmap=importedRoadmap;saveRoadmaps();}}if(s.has("completed")){completed.clear();JSONArray a=s.optJSONArray("completed");for(int i=0;i<a.length();i++)completed.add(a.optString(i));}tasks=s.optJSONArray("tasks");sessions=s.optJSONArray("sessions");schedules=s.optJSONArray("schedules");if(tasks==null)tasks=new JSONArray();if(sessions==null)sessions=new JSONArray();if(schedules==null)schedules=new JSONArray();saveState();scheduleAllImportedAlarms();toast("Backup imported");showHome();}
        else if(req==301){JSONObject s=new JSONObject();JSONArray c=new JSONArray();for(String id:completed)c.put(id);s.put("completed",c);s.put("tasks",tasks);s.put("sessions",sessions);s.put("schedules",schedules);s.put("roadmap",roadmap);s.put("roadmaps",roadmaps);s.put("currentRoadmapId",currentRoadmapId);write(data.getData(),s.toString(2));toast("Backup exported");}
    }catch(Exception e){toast("Import/export failed: "+e.getMessage());}}
    String read(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return b.toString("UTF-8");}
    void write(Uri u,String s)throws Exception{OutputStream out=getContentResolver().openOutputStream(u);out.write(s.getBytes(StandardCharsets.UTF_8));out.close();}
    void confirmReset(){new AlertDialog.Builder(this).setTitle("Reset all data?").setMessage("This deletes roadmap progress, tasks and study sessions from this device.").setPositiveButton("Delete",(d,w)->{completed.clear();tasks=new JSONArray();sessions=new JSONArray();schedules=new JSONArray();clearActiveTimer();getSharedPreferences(PREFS,0).edit().remove("roadmapOverride").remove(KEY_COMPLETED_PLANS).remove(KEY_ROADMAPS).remove(KEY_CURRENT_ROADMAP).remove("completedByRoadmap").apply();saveState();showHome();}).setNegativeButton("Cancel",null).show();}
}
