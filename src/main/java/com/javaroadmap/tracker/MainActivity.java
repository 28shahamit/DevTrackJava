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
    HashSet<String> expandedTopics=new HashSet<>();
    HashSet<String> expandedSessionGroups=new HashSet<>();
    TextView title;
    int page=0;
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
        b.setAllCaps(false); b.setBackgroundResource(R.drawable.secondary_button_bg); b.setMinHeight(dp(40)); b.setMinimumHeight(dp(40)); b.setPadding(dp(14),dp(7),dp(14),dp(7)); b.setTextSize(14); return b;
    }
    Button primaryBtn(String text){Button b=btn(text);b.setBackgroundResource(R.drawable.primary_button_bg);return b;}
    /** Visible-text destructive action button (CR-005: destructive actions should be clearly identifiable, not just an icon). */
    Button dangerBtn(String text){Button b=btn(text); b.setTextColor(Color.parseColor("#FF6B6B")); return b;}
    Button iconBtn(String icon,String description){
        Button b=btn(icon); b.setContentDescription(description);
        if(Build.VERSION.SDK_INT>=26)b.setTooltipText(description);
        b.setMinWidth(dp(40)); b.setMinimumWidth(dp(40));
        b.setPadding(dp(6),dp(6),dp(6),dp(6)); return b;
    }
    Button dangerIconBtn(String icon,String description){
        Button b=iconBtn(icon,description); b.setTextColor(Color.parseColor("#FF6B6B")); return b;
    }
    Button dangerMicroIconBtn(String icon,String description){
        Button b=microIconBtn(icon,description); b.setTextColor(Color.parseColor("#FF6B6B")); return b;
    }

    /** Even smaller icon-only button for dense rows (topic actions, etc). Still a real tap target, just visually lighter. */
    Button tinyIconBtn(String icon,String description){
        Button b=new Button(this); b.setText(icon); b.setTextColor(Color.parseColor("#C7CEDC"));
        b.setAllCaps(false); b.setBackgroundResource(R.drawable.secondary_button_bg);
        b.setMinWidth(dp(38)); b.setMinimumWidth(dp(38));
        b.setMinHeight(dp(38)); b.setMinimumHeight(dp(38));
        b.setPadding(dp(4),dp(4),dp(4),dp(4)); b.setTextSize(14);
        b.setContentDescription(description);
        if(Build.VERSION.SDK_INT>=26)b.setTooltipText(description);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(0,0,dp(6),0); b.setLayoutParams(lp);
        return b;
    }
    /** Smallest icon-only button — for packing several actions into a single heading row (e.g. a topic row). */
    Button microIconBtn(String icon,String description){
        Button b=new Button(this); b.setText(icon); b.setTextColor(Color.parseColor("#C7CEDC"));
        b.setAllCaps(false); b.setBackgroundResource(R.drawable.secondary_button_bg);
        b.setMinWidth(dp(24)); b.setMinimumWidth(dp(24));
        b.setMinHeight(dp(24)); b.setMinimumHeight(dp(24));
        b.setPadding(dp(1),dp(1),dp(1),dp(1)); b.setTextSize(11);
        b.setContentDescription(description);
        if(Build.VERSION.SDK_INT>=26)b.setTooltipText(description);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(dp(3),0,0,0); b.setLayoutParams(lp);
        return b;
    }

    static final String[] CATEGORY_NAMES={"WORK","LEARNING","PROJECT","CAREER","HEALTH","PERSONAL","FAMILY","COMMUTE","FOOD","SLEEP","BREAK","ENTERTAINMENT","DISTRACTION","OTHER"};
    int categoryColor(String cat){
        if(cat==null)cat="OTHER";
        switch(cat.toUpperCase(Locale.US)){
            case "WORK": return Color.parseColor("#4F7CFF");
            case "LEARNING": return Color.parseColor("#34C77B");
            case "PROJECT": return Color.parseColor("#FFB84F");
            case "CAREER": return Color.parseColor("#B983FF");
            case "HEALTH": return Color.parseColor("#FF6B6B");
            case "PERSONAL": return Color.parseColor("#4FD1FF");
            case "FAMILY": return Color.parseColor("#FF8FB1");
            case "COMMUTE": return Color.parseColor("#A0A4AE");
            case "FOOD": return Color.parseColor("#FFD166");
            case "SLEEP": return Color.parseColor("#7A7FEA");
            case "BREAK": return Color.parseColor("#6EE7B7");
            case "ENTERTAINMENT": return Color.parseColor("#F472B6");
            case "DISTRACTION": return Color.parseColor("#F87171");
            default: return Color.parseColor("#9AA4B8");
        }
    }
    View dot(int color,int sizeDp){
        View v=new View(this);
        android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL); gd.setColor(color);
        v.setBackground(gd); return v;
    }
    LinearLayout labelWithDot(String text,int color,float size){
        LinearLayout r=row();
        r.addView(dot(color,10),new LinearLayout.LayoutParams(dp(10),dp(10)){{setMargins(0,0,dp(6),0);}});
        r.addView(tv(text,size),new LinearLayout.LayoutParams(-2,-2));
        return r;
    }
    LinearLayout card(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14),dp(12),dp(14),dp(12)); l.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(6)); l.setLayoutParams(p); return l;
    }
    /** CR-002: every dialog in the app should be built through this so it matches the dark theme
     *  instead of the platform's default gray Material dialog surface. */
    AlertDialog.Builder dlg(){ return new AlertDialog.Builder(this,R.style.AppTheme_Dialog); }
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
        if(page==1 && roadmap!=null){showLearn();return;}
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
    void setNavSelected(int id){
        int[] all={R.id.navHome,R.id.navLearn,R.id.navPlan,R.id.navProgress};
        for(int navId:all){View v=findViewById(navId);if(v!=null)v.setSelected(navId==id);}
    }
    /** Rebuilds the heading + scrollable body for a top-level screen. Reuses the existing
     *  ScrollView/body instance when one is already mounted (instead of tearing the whole
     *  content tree down and rebuilding it from scratch) so switching screens or refreshing
     *  in place doesn't flash an empty frame before the new content is measured. */
    void base(String heading,String sub){
        LinearLayout box;
        if(content.getChildCount()>0 && content.getChildAt(0) instanceof ScrollView){
            ScrollView sc=(ScrollView)content.getChildAt(0);
            box=(LinearLayout)sc.getChildAt(0);
            box.removeAllViews();
        } else {
            content.removeAllViews();
            ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
            box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16),dp(24),dp(16),dp(20));
            sc.addView(box); content.addView(sc);
        }
        LinearLayout headRow=row(); headRow.setGravity(Gravity.CENTER_VERTICAL);
        title=tv(heading,28); title.setTypeface(null,1);
        headRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button gear=microIconBtn("⚙","Settings"); gear.setOnClickListener(v->showSettings());
        headRow.addView(gear);
        box.addView(headRow);
        if(sub!=null){ TextView s=tv(sub,13); s.setTextColor(Color.parseColor("#9AA4B8")); box.addView(s); }
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
    int currentScrollY(){
        if(content!=null && content.getChildCount()>0 && content.getChildAt(0) instanceof ScrollView){
            return ((ScrollView)content.getChildAt(0)).getScrollY();
        }
        return 0;
    }
    void restoreScrollY(int y){
        if(content!=null && content.getChildCount()>0 && content.getChildAt(0) instanceof ScrollView){
            ScrollView sv=(ScrollView)content.getChildAt(0);
            sv.post(()->sv.scrollTo(0,y));
        }
    }
    /** Rebuilds the current roadmap screen in place, keeping the user's scroll position
     *  (e.g. after toggling a topic, checking a box, or editing a phase). */
    void refreshRoadmap(){ int y=currentScrollY(); showRoadmap(); restoreScrollY(y); }

    void showHome(){
        page=0; base("DevTrack","Your day, your time, your progress"); setNavSelected(R.id.navHome);
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
        Button log=btn("＋ Log past activity"); log.setOnClickListener(v->activityLogDialog()); quick.addView(log);
        box().addView(quick);
    }

    /** Slim "a timer is running" notice for screens other than Home, so users aren't surprised
     *  a timer is active without duplicating the full Start/Stop controls here. */
    void addActiveTimerBanner(LinearLayout parent){
        JSONObject active=getActiveTimer(); if(active==null)return;
        LinearLayout c=card();
        LinearLayout r=row();
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL);
        TextView running=tv("🔴 Timer running",12); running.setTextColor(Color.parseColor("#FF6B6B")); mid.addView(running);
        addText(mid,active.optString("title","Activity"),16);
        r.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        Button view=btn("View"); view.setContentDescription("View running timer on Home"); view.setOnClickListener(v->showHome()); r.addView(view);
        c.addView(r); parent.addView(c);
    }
    void addTrackingCard(LinearLayout parent){
        LinearLayout c=card(); addText(c,"TIME TRACKING",11); JSONObject active=getActiveTimer();
        if(active!=null){
            TextView runningTv=tv("🔴 RUNNING",13); runningTv.setTextColor(Color.parseColor("#FF6B6B")); runningTv.setTypeface(null,1); c.addView(runningTv);
            timerTitle=tv(active.optString("title","Activity"),20); c.addView(timerTitle);
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
        stripe.setBackgroundColor(color); c.addView(stripe,new LinearLayout.LayoutParams(dp(5),dp(84)));
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setPadding(dp(10),0,dp(8),0);
        addText(mid,b.optString("start","")+" – "+b.optString("end",""),12); addText(mid,blockTitle(b),16); mid.addView(labelWithDot(b.optString("category","OTHER")+(b.optBoolean("track",true)?" · in time tracking":""),categoryColor(b.optString("category","OTHER")),11));
        String stateLabel=scheduleStateLabelWithMeta(b); TextView stateTv=tv(stateLabel,12); stateTv.setTextColor(stateColor(stateLabel)); mid.addView(stateTv);
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
        String taskState=taskStateLabelWithMeta(t); TextView stateTv=tv(taskState,12); stateTv.setTextColor(stateColor(taskState)); mid.addView(stateTv);
        String effort="Estimate "+formatMin(t.optInt("estimateMin",0))+" · Actual "+formatMin(t.optInt("actualMin",0))+" · Remaining "+formatMin(t.optInt("remainingMin",0));
        TextView e=tv(effort,11); e.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(e);
        r.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        Button st=primaryBtn("completed".equals(t.optString("status"))?"✓ Done":"▶ Start");
        st.setOnClickListener(v->{ if(!"completed".equals(t.optString("status"))) startTimerForTask(index); }); r.addView(st);
        Button del=dangerIconBtn("🗑","Delete task"); del.setOnClickListener(v->deleteTask(index)); r.addView(del);
        cb.setOnClickListener(v->{try{if(cb.isChecked()){ if(getActiveTimer()!=null&&t.optString("id").equals(getActiveTimer().optString("taskId")))stopActiveTimer(true); else {completeTask(t);showHome();}}else{t.put("status","not_started");t.remove("completedAt");saveState();showHome();}}catch(Exception ignored){}});
        parent.addView(r);
    }

    void showLearn(){
        page=1; base("Learn","Dynamic roadmaps — import, edit and track each learning path separately."); setNavSelected(R.id.navLearn);
        if(roadmaps==null||roadmaps.length()==0){
            LinearLayout empty=card(); addText(empty,"No roadmaps yet",20); addText(empty,"Import a roadmap JSON to get started.",13); Button imp=primaryBtn("📥 Import Roadmap JSON"); imp.setOnClickListener(v->importRoadmap()); empty.addView(imp); box().addView(empty); return;
        }
        for(int i=0;i<roadmaps.length();i++){ JSONObject r=roadmaps.optJSONObject(i); if(r==null)continue; addRoadmapCard(box(),r); }
        LinearLayout actions=card();
        Button imp=primaryBtn("📥 Import Roadmap JSON"); imp.setOnClickListener(v->importRoadmap()); actions.addView(imp);
        Button exp=btn("📤 Export Current Roadmap JSON"); exp.setOnClickListener(v->exportRoadmap()); actions.addView(exp);
        Button dsa=btn("🧠 Add included DSA starter"); dsa.setOnClickListener(v->importBundledRoadmap("dsa-roadmap.json")); actions.addView(dsa);
        box().addView(actions);
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
        Button del=dangerIconBtn("🗑","Delete roadmap"); del.setOnClickListener(v->deleteRoadmap(r)); row.addView(del);
        c.addView(row); parent.addView(c);
    }
    void showRoadmap(){
        if(roadmap==null){showLearn();return;}
        baseWithBack(roadmapName(roadmap),roadmapDescription(roadmap),()->showLearn()); setNavSelected(R.id.navLearn);
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
            LinearLayout phaseActions=row(); Button ep=microIconBtn("✎","Edit phase"); ep.setOnClickListener(v->editPhaseDialog(ph)); phaseActions.addView(ep); Button dp=dangerMicroIconBtn("🗑","Delete phase"); dp.setOnClickListener(v->deletePhase(ph)); phaseActions.addView(dp); Button at=btn("＋ Add item"); at.setOnClickListener(v->editTopicDialog(ph,null)); phaseActions.addView(at,new LinearLayout.LayoutParams(0,-2,1)); list.addView(phaseActions);
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
        LinearLayout c=card(); c.setPadding(dp(8),dp(5),dp(6),dp(5));
        String id=t.optString("id"); boolean done=completedFor(currentRoadmapId).contains(id);
        boolean saved=t.optBoolean("saved",false); boolean later=t.optBoolean("later",false);
        boolean expanded=expandedTopics.contains(id);

        LinearLayout head=row(); head.setGravity(Gravity.CENTER_VERTICAL);
        Button chevron=microIconBtn(expanded?"▾":"▸","Toggle details");
        chevron.setOnClickListener(v->{if(expanded)expandedTopics.remove(id);else expandedTopics.add(id);refreshRoadmap();});
        head.addView(chevron);
        CheckBox cb=new CheckBox(this); cb.setChecked(done); cb.setContentDescription("Mark done");
        head.addView(cb,new LinearLayout.LayoutParams(dp(26),-2));
        TextView name=tv(t.optString("title"),14); name.setTypeface(null,1);
        name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(done)name.setTextColor(Color.parseColor("#7FE0A8"));
        name.setOnClickListener(v->chevron.performClick());
        head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(saved){TextView star=tv("★",12);star.setTextColor(Color.parseColor("#FFD166"));star.setPadding(dp(3),0,0,0);head.addView(star,new LinearLayout.LayoutParams(-2,-2));}
        if(later){TextView lat=tv("↺",12);lat.setTextColor(Color.parseColor("#4FD1FF"));lat.setPadding(dp(3),0,0,0);head.addView(lat,new LinearLayout.LayoutParams(-2,-2));}
        Button track=microIconBtn("▶","Track time");track.setOnClickListener(v->startPersistentTimer(t.optString("title","Roadmap item"),"LEARNING",null,null));head.addView(track);
        Button more=microIconBtn("⋮","More options");more.setOnClickListener(v->topicOverflowMenu(phase,t));head.addView(more);
        c.addView(head);

        if(expanded){
            StringBuilder meta=new StringBuilder();
            String priority=t.optString("priority",""); if(!priority.isEmpty())meta.append(priority.toUpperCase(Locale.US));
            if(t.optBoolean("interview",false)){if(meta.length()>0)meta.append(" · ");meta.append("INTERVIEW");}
            String difficulty=t.optString("difficulty",""); if(!difficulty.isEmpty()){if(meta.length()>0)meta.append(" · ");meta.append(difficulty.toUpperCase(Locale.US));}
            LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(32),dp(2),dp(4),dp(2));
            if(meta.length()>0){TextView m=tv(meta.toString(),11);m.setTextColor(Color.parseColor("#9AA4B8"));details.addView(m);}
            JSONArray subs=t.optJSONArray("subtopics");
            if(subs!=null&&subs.length()>0){TextView s=tv("Topics: "+joinJsonArray(subs),11);s.setTextColor(Color.parseColor("#9AA4B8"));details.addView(s);}
            JSONArray tags=t.optJSONArray("tags");
            if(tags!=null&&tags.length()>0){TextView tgv=tv("Tags: "+joinJsonArray(tags),11);tgv.setTextColor(Color.parseColor("#9AA4B8"));details.addView(tgv);}
            String primary=t.optString("url",""); JSONArray links=t.optJSONArray("links"); boolean hasLink=!primary.trim().isEmpty()||(links!=null&&links.length()>0);
            if(hasLink){
                TextView open=tv("🔗 Open resource",12); open.setTextColor(Color.parseColor("#4F8EF7")); open.setPadding(0,dp(4),0,0);
                open.setOnClickListener(v->openTopicLinks(t)); details.addView(open);
            }
            if(meta.length()==0&&(subs==null||subs.length()==0)&&(tags==null||tags.length()==0)&&!hasLink){
                TextView none=tv("No extra details for this item.",11); none.setTextColor(Color.parseColor("#5A6172")); details.addView(none);
            }
            c.addView(details);
        }

        cb.setOnClickListener(v->{HashSet<String> set=completedFor(currentRoadmapId);if(cb.isChecked())set.add(id);else set.remove(id);saveCompletedFor(currentRoadmapId,set);refreshRoadmap();});
        list.addView(c);
    }
    void topicOverflowMenu(JSONObject phase,JSONObject t){
        boolean saved=t.optBoolean("saved",false); boolean later=t.optBoolean("later",false);
        String[] opts={saved?"★ Remove from saved":"☆ Save for later",later?"↺ Remove from later":"↺ Mark for later","✎ Edit","🗑 Delete","Cancel"};
        dlg().setTitle(t.optString("title","Item")).setItems(opts,(d,w)->{
            try{
                if(w==0){t.put("saved",!saved);saveRoadmaps();refreshRoadmap();}
                else if(w==1){t.put("later",!later);saveRoadmaps();refreshRoadmap();}
                else if(w==2)editTopicDialog(phase,t);
                else if(w==3)deleteTopic(phase,t);
            }catch(Exception ignored){}
        }).show();
    }
    void openTopicLinks(JSONObject t){
        try{
            ArrayList<String> titles=new ArrayList<>(); ArrayList<String> urls=new ArrayList<>();
            String u=t.optString("url","").trim(); if(!u.isEmpty()){titles.add("Primary link");urls.add(u);}
            JSONArray links=t.optJSONArray("links"); if(links!=null)for(int i=0;i<links.length();i++){JSONObject l=links.optJSONObject(i);if(l==null)continue;String lu=l.optString("url","").trim();if(lu.isEmpty())continue;titles.add(l.optString("title","Resource "+(i+1)));urls.add(lu);}
            if(urls.isEmpty()){toast("No link configured");return;}
            if(urls.size()==1){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(urls.get(0))));return;}
            dlg().setTitle(t.optString("title","Resources")).setItems(titles.toArray(new String[0]),(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(urls.get(w))));}catch(Exception e){toast("No app can open this link");}}).show();
        }catch(Exception e){toast("Could not open link");}
    }

    void showPlan(){
        page=2; base("Plan","Your schedule is the source of truth for today's planned activities."); setNavSelected(R.id.navPlan);
        LinearLayout actions=card(); addText(actions,"DAILY SCHEDULE",19);
        Button imp=primaryBtn("📥 Import Daily Schedule JSON"); imp.setOnClickListener(v->importDailySchedule()); actions.addView(imp);
        Button exp=btn("📤 Export Daily Schedule JSON"); exp.setOnClickListener(v->exportDailySchedule()); actions.addView(exp);
        Button addBlock=btn("＋ Add schedule block"); addBlock.setOnClickListener(v->scheduleBlockDialog(null,-1)); actions.addView(addBlock);
        box().addView(actions);
        addActiveTimerBanner(box());

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
        String taskState=taskStateLabelWithMeta(t); TextView stateTv=tv(taskState,13); stateTv.setTextColor(stateColor(taskState)); c.addView(stateTv);
        addText(c,"Estimate "+formatMin(t.optInt("estimateMin",0))+" · Actual "+formatMin(t.optInt("actualMin",0))+" · Remaining "+formatMin(t.optInt("remainingMin",0)),12);
        addText(c,"Priority: "+t.optString("priority","medium")+" · Category: "+t.optString("category","LEARNING"),11);
        LinearLayout r=row();
        Button st=primaryBtn("completed".equals(t.optString("status"))?"✓ Completed":"▶ Start / Resume"); st.setOnClickListener(v->{if(!"completed".equals(t.optString("status")))startTimerForTask(idx);}); r.addView(st,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=iconBtn("✎","Edit task"); edit.setOnClickListener(v->taskDialog(t,idx)); r.addView(edit);
        Button del=dangerIconBtn("🗑","Delete task"); del.setOnClickListener(v->deleteTask(idx)); r.addView(del);
        if(!"completed".equals(t.optString("status"))){Button done=btn("✓ End task");done.setOnClickListener(v->{completeTask(t);showPlan();});r.addView(done);}
        c.addView(r); parent.addView(c);
    }

    void showProgress(){
        page=3; base("Progress","Understand where your time goes and whether it moves you forward."); setNavSelected(R.id.navProgress);
        String today=date(); long todayMs=todaySessionMs(today)+activeElapsedMsForDate(today);
        LinearLayout day=card(); addText(day,"TODAY",11); addText(day,formatDuration(todayMs)+" tracked",28);
        addText(day,"Real sessions: "+realSessionCount(today),13); box().addView(day);

        addStreakCard(box());

        LinearLayout road=card(); addText(road,"ROADMAP PROGRESS",18);
        if(roadmaps!=null&&roadmaps.length()>0){
            for(int i=0;i<roadmaps.length();i++){JSONObject r=roadmaps.optJSONObject(i);if(r==null)continue;int total=topicCount(r);int done=completedFor(r.optString("id",roadmapId(r))).size();int pct=total==0?0:Math.round(done*100f/total);addText(road,r.optString("icon","📚")+"  "+roadmapName(r)+"   "+done+" / "+total+" "+roadmapItemLabel(r)+" · "+pct+"%",14);ProgressBar rb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);rb.setMax(100);rb.setProgress(pct);road.addView(rb,new LinearLayout.LayoutParams(-1,dp(6)));}
        } else addText(road,"No roadmaps imported.",13); box().addView(road);

        LinearLayout sessionsCard=card(); addText(sessionsCard,"RECENT STUDY SESSIONS",18);
        ArrayList<JSONObject> groups=groupedRecentSessions(30); int shown=Math.min(10,groups.size());
        for(int i=0;i<shown;i++)addSessionGroupRow(sessionsCard,groups.get(i));
        if(groups.isEmpty())addText(sessionsCard,"No saved sessions yet.",13);
        box().addView(sessionsCard);

        long learning=0,project=0,work=0,distraction=0,other=0;
        for(JSONObject x:sessionObjectsForDate(today)){long m=x.optLong("durationMs",0);String c=x.optString("category","OTHER");if("LEARNING".equals(c)||"CAREER".equals(c))learning+=m;else if("PROJECT".equals(c))project+=m;else if("WORK".equals(c))work+=m;else if("DISTRACTION".equals(c))distraction+=m;else other+=m;}
        LinearLayout dist=card(); addText(dist,"TIME DISTRIBUTION",18); addText(dist,"Learning / Career    "+formatDuration(learning),14); addText(dist,"Projects             "+formatDuration(project),14); addText(dist,"Work                 "+formatDuration(work),14); addText(dist,"Distraction           "+formatDuration(distraction),14); addText(dist,"Other / neutral       "+formatDuration(other),14); box().addView(dist);

        LinearLayout week=card(); addText(week,"THIS WEEK",18); long ms=0;int count=0;for(JSONObject x:recentSessionObjects(7)){ms+=x.optLong("durationMs",0);count++;}addText(week,"Tracked: "+formatDuration(ms),22);addText(week,"Sessions: "+count,13);box().addView(week);
    }

    /** Tracking mode, alarms, and data import/export/reset — pulled out of the Progress tab
     *  (where it didn't belong alongside your stats) into its own screen, reachable from the
     *  ⚙ icon on every top-level tab. Returns to whichever tab you opened it from. */
    void showSettings(){
        int from=page;
        baseWithBack("Settings","Tracking mode, alarms, and data management.",()->{
            switch(from){case 1:showLearn();break;case 2:showPlan();break;case 3:showProgress();break;default:showHome();}
        });
        setNavSelected(-1);
        LinearLayout root=box();

        LinearLayout settings=card(); addText(settings,"TRACKING MODE",18); addText(settings,"Automatic mode follows today's schedule. Manual mode lets you choose every activity.",12);
        RadioGroup rg=new RadioGroup(this); rg.setOrientation(RadioGroup.VERTICAL);
        RadioButton auto=new RadioButton(this);auto.setText("Automatic from Plan");auto.setTextColor(Color.parseColor("#F4F6FB"));auto.setId(View.generateViewId());
        RadioButton manual=new RadioButton(this);manual.setText("Manual");manual.setTextColor(Color.parseColor("#F4F6FB"));manual.setId(View.generateViewId());rg.addView(auto);rg.addView(manual);auto.setChecked(!isManualMode());manual.setChecked(isManualMode());
        rg.setOnCheckedChangeListener((g,id)->{boolean manualSelected=id==manual.getId();getSharedPreferences(PREFS,0).edit().putBoolean(KEY_TRACKING_MODE,manualSelected).apply();showSettings();});settings.addView(rg);
        Button manualStart=btn("▶ Start manual activity");manualStart.setOnClickListener(v->manualStartDialog());settings.addView(manualStart);
        root.addView(settings);

        LinearLayout alarms=card(); addText(alarms,"ALARMS",18);
        Button alarm=btn("🔔 Test alarm");alarm.setOnClickListener(v->NotificationHelper.showTest(this));alarms.addView(alarm);
        if(Build.VERSION.SDK_INT>=31){
            AlarmManager amCheck=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
            boolean exact=amCheck!=null&&amCheck.canScheduleExactAlarms();
            addText(alarms,exact?"Exact alarms: allowed":"Exact alarms: not allowed — reminders may fire late.",11);
            if(!exact){Button exactBtn=btn("Allow exact alarms");exactBtn.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("Could not open alarm settings");}});alarms.addView(exactBtn);}
        }
        root.addView(alarms);

        LinearLayout data=card(); addText(data,"DATA",18);
        Button impS=btn("📥 Import daily schedule");impS.setOnClickListener(v->importDailySchedule());data.addView(impS);
        Button imp=btn("📥 Import full backup");imp.setOnClickListener(v->importBackup());data.addView(imp);
        Button exp=btn("📤 Export full backup");exp.setOnClickListener(v->exportBackup());data.addView(exp);
        Button reset=btn("Reset all data");reset.setOnClickListener(v->confirmReset());data.addView(reset);
        root.addView(data);
    }

    /** CR-001: redesigned Add Task dialog — explicit labels, logical grouping, native date/time
     *  pickers, an auto-calculated Duration, dropdowns for Priority/Category, inline validation
     *  (including overlap checking against other tasks that day), and it preserves existing data
     *  when editing instead of re-deriving it. */
    void taskDialog(JSONObject existing,int index){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16),dp(8),dp(16),dp(8));

        addText(l,"Task title",11);
        EditText titleE=new EditText(this); titleE.setHint("e.g. Study Streams API");
        titleE.setText(existing==null?"":existing.optString("title")); l.addView(titleE);
        TextView titleErr=tv("",11); titleErr.setTextColor(Color.parseColor("#FF6B6B")); titleErr.setVisibility(View.GONE); l.addView(titleErr);

        addText(l,"Date",11);
        String[] dateHolder={existing==null?null:existing.optString("date",null)};
        l.addView(datePickerField(dateHolder));

        addText(l,"Start                                          End",11);
        LinearLayout timeRow=row();
        final Runnable[] recompute=new Runnable[1];
        EditText startE=timePickerField("Start",existing==null?"20:00":existing.optString("start","20:00"),()->{if(recompute[0]!=null)recompute[0].run();});
        EditText endE=timePickerField("End",existing==null?"21:00":existing.optString("end","21:00"),()->{if(recompute[0]!=null)recompute[0].run();});
        timeRow.addView(startE,new LinearLayout.LayoutParams(0,-2,1));
        addSpace(timeRow,12);
        timeRow.addView(endE,new LinearLayout.LayoutParams(0,-2,1));
        l.addView(timeRow);
        TextView timeErr=tv("",11); timeErr.setTextColor(Color.parseColor("#FF6B6B")); timeErr.setVisibility(View.GONE); l.addView(timeErr);

        addText(l,"Duration",11);
        TextView durationTv=tv("",15); durationTv.setTextColor(Color.parseColor("#9AA4B8")); l.addView(durationTv);
        int[] durationMin={existing!=null?existing.optInt("estimateMin",60):60};
        recompute[0]=()->{
            int sm=toMinutes(startE.getText().toString().trim()), em=toMinutes(endE.getText().toString().trim());
            if(sm>=0&&em>=0){ durationMin[0]=em>sm?em-sm:(24*60-sm+em); }
            durationTv.setText(formatMin(durationMin[0])+" (calculated from start/end)");
        };
        recompute[0].run();

        addText(l,"Priority",11);
        Spinner priority=prioritySpinner(existing==null?"MEDIUM":existing.optString("priority","MEDIUM")); l.addView(priority);

        addText(l,"Category",11);
        Spinner cat=categorySpinner(existing==null?"LEARNING":existing.optString("category","LEARNING")); l.addView(cat);

        ScrollView scroll=new ScrollView(this); scroll.addView(l);
        AlertDialog dialog=dlg().setTitle(existing==null?"Add Task":"Edit Task").setView(scroll)
            .setPositiveButton("Save",(d,w)->{}) // overridden below to control dismissal on validation failure
            .setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(dd->{
            Button saveBtn=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v->{
                titleErr.setVisibility(View.GONE); timeErr.setVisibility(View.GONE);
                String titleVal=titleE.getText().toString().trim();
                String startVal=startE.getText().toString().trim(), endVal=endE.getText().toString().trim();
                String dateVal=dateHolder[0];
                boolean valid=true;
                if(titleVal.isEmpty()){titleErr.setText("Task title is required");titleErr.setVisibility(View.VISIBLE);valid=false;}
                int sm=toMinutes(startVal), em=toMinutes(endVal);
                if(sm<0||em<0){timeErr.setText("Please set both start and end time");timeErr.setVisibility(View.VISIBLE);valid=false;}
                else if(em<=sm){timeErr.setText("End time must be after start time");timeErr.setVisibility(View.VISIBLE);valid=false;}
                if(!valid)return;
                try{
                    JSONObject candidate=new JSONObject();
                    candidate.put("date",dateVal); candidate.put("start",startVal); candidate.put("end",endVal);
                    ArrayList<JSONObject> overlaps=findOverlappingTasks(candidate,existing==null?-1:index);
                    Runnable commit=()->{
                        try{
                            JSONObject t=existing==null?new JSONObject():existing;
                            if(existing==null) t.put("id",UUID.randomUUID().toString());
                            t.put("title",titleVal);
                            t.put("date",dateVal);
                            t.put("start",startVal); t.put("end",endVal);
                            t.put("estimateMin",durationMin[0]);
                            if(existing==null) t.put("remainingMin",durationMin[0]);
                            t.put("priority",String.valueOf(priority.getSelectedItem()));
                            t.put("category",String.valueOf(cat.getSelectedItem()));
                            if(!t.has("actualMin"))t.put("actualMin",0); if(!t.has("status"))t.put("status","not_started");
                            if(existing==null) tasks.put(t);
                            scheduleReminder(this,t); saveState(); dialog.dismiss(); showPlan();
                        }catch(Exception e){toast("Could not save task: "+safeMessage(e));}
                    };
                    if(!overlaps.isEmpty()){
                        StringBuilder names=new StringBuilder();
                        for(JSONObject o:overlaps){if(names.length()>0)names.append(", ");names.append(o.optString("title","Task")).append(" (").append(o.optString("start")).append("–").append(o.optString("end")).append(")");}
                        dlg().setTitle("Overlapping time")
                            .setMessage("This overlaps with: "+names+". Save anyway?")
                            .setPositiveButton("Save anyway",(dd2,ww2)->commit.run())
                            .setNegativeButton("Go back",null).show();
                    } else commit.run();
                }catch(Exception e){toast("Could not save task: "+safeMessage(e));}
            });
        });
        dialog.show();
    }
    /** CR-001: warn (don't silently allow) when a task's date/time overlaps another task the same day. */
    ArrayList<JSONObject> findOverlappingTasks(JSONObject candidate,int excludeIndex){
        ArrayList<JSONObject> out=new ArrayList<>();
        String dateVal=candidate.optString("date","");
        for(int i=0;i<tasks.length();i++){
            if(i==excludeIndex)continue;
            JSONObject t=tasks.optJSONObject(i); if(t==null)continue;
            if(!dateVal.equals(t.optString("date","")))continue;
            if(timesOverlap(candidate.optString("start"),candidate.optString("end"),t.optString("start"),t.optString("end")))out.add(t);
        }
        return out;
    }

    LinearLayout dotAdapterRow(String label,int color,View convert){
        LinearLayout row; TextView lbl;
        if(convert instanceof LinearLayout && convert.getTag()instanceof TextView){row=(LinearLayout)convert;lbl=(TextView)convert.getTag();}
        else{
            row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(10),dp(12),dp(10));
            View d=dot(Color.WHITE,10); LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(dp(10),dp(10)); dp2.setMargins(0,0,dp(8),0); row.addView(d,dp2);
            lbl=new TextView(this); lbl.setTextColor(Color.parseColor("#F4F6FB")); lbl.setTextSize(14); row.addView(lbl);
            row.setTag(lbl);
        }
        android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable(); gd.setShape(android.graphics.drawable.GradientDrawable.OVAL); gd.setColor(color);
        row.getChildAt(0).setBackground(gd);
        lbl.setText(label);
        return row;
    }
    ArrayAdapter<String> categoryAdapter(){
        return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,CATEGORY_NAMES){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){LinearLayout v=dotAdapterRow(getItem(position),categoryColor(getItem(position)),convertView);v.setBackgroundColor(Color.parseColor("#1B202B"));return v;}
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){LinearLayout v=dotAdapterRow(getItem(position),categoryColor(getItem(position)),convertView);v.setBackgroundColor(Color.parseColor("#1B1F2A"));return v;}
        };
    }

    Spinner categorySpinner(String selected){
        Spinner sp=new Spinner(this); sp.setAdapter(categoryAdapter());
        for(int i=0;i<CATEGORY_NAMES.length;i++)if(CATEGORY_NAMES[i].equals(selected)){sp.setSelection(i);break;} return sp;
    }

    static final String[] PRIORITY_LEVELS={"LOW","MEDIUM","HIGH","CRITICAL"};
    static final String[] DIFFICULTY_LEVELS={"UNSET","EASY","MEDIUM","HARD"};
    int priorityColor(String p){
        if(p==null)p="MEDIUM";
        switch(p.toUpperCase(Locale.US)){
            case "LOW": return Color.parseColor("#6EE7B7");
            case "HIGH": return Color.parseColor("#FFB84F");
            case "CRITICAL": return Color.parseColor("#FF6B6B");
            default: return Color.parseColor("#4FD1FF");
        }
    }
    int difficultyColor(String d){
        if(d==null)d="";
        switch(d.toUpperCase(Locale.US)){
            case "EASY": return Color.parseColor("#6EE7B7");
            case "HARD": return Color.parseColor("#FF6B6B");
            case "MEDIUM": return Color.parseColor("#FFD166");
            default: return Color.parseColor("#5A6172");
        }
    }
    Spinner prioritySpinner(String selected){
        Spinner sp=new Spinner(this);
        ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,PRIORITY_LEVELS){
            @Override public View getView(int p,View cv,android.view.ViewGroup pr){LinearLayout v=dotAdapterRow(getItem(p),priorityColor(getItem(p)),cv);v.setBackgroundColor(Color.parseColor("#1B202B"));return v;}
            @Override public View getDropDownView(int p,View cv,android.view.ViewGroup pr){LinearLayout v=dotAdapterRow(getItem(p),priorityColor(getItem(p)),cv);v.setBackgroundColor(Color.parseColor("#1B1F2A"));return v;}
        };
        sp.setAdapter(ad);
        String sel=(selected==null||selected.trim().isEmpty())?"MEDIUM":selected.trim().toUpperCase(Locale.US);
        int idx=1; for(int i=0;i<PRIORITY_LEVELS.length;i++)if(PRIORITY_LEVELS[i].equals(sel)){idx=i;break;}
        sp.setSelection(idx); return sp;
    }
    Spinner difficultySpinner(String selected){
        Spinner sp=new Spinner(this);
        ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,DIFFICULTY_LEVELS){
            @Override public View getView(int p,View cv,android.view.ViewGroup pr){LinearLayout v=dotAdapterRow(getItem(p),difficultyColor("UNSET".equals(getItem(p))?"":getItem(p)),cv);v.setBackgroundColor(Color.parseColor("#1B202B"));return v;}
            @Override public View getDropDownView(int p,View cv,android.view.ViewGroup pr){LinearLayout v=dotAdapterRow(getItem(p),difficultyColor("UNSET".equals(getItem(p))?"":getItem(p)),cv);v.setBackgroundColor(Color.parseColor("#1B1F2A"));return v;}
        };
        sp.setAdapter(ad);
        String sel=(selected==null||selected.trim().isEmpty())?"UNSET":selected.trim().toUpperCase(Locale.US);
        int idx=0; for(int i=0;i<DIFFICULTY_LEVELS.length;i++)if(DIFFICULTY_LEVELS[i].equals(sel)){idx=i;break;}
        sp.setSelection(idx); return sp;
    }

    void activityLogDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);
        addText(l,"Add a completed activity that happened earlier. For live tracking use Start / Stop.",12);
        EditText titleE=new EditText(this);titleE.setHint("Activity");l.addView(titleE);
        EditText durE=new EditText(this);durE.setHint("Duration in minutes");durE.setInputType(2);l.addView(durE);
        Spinner cat=categorySpinner("OTHER");l.addView(cat);
        dlg().setTitle("Log past activity").setView(l).setPositiveButton("Save",(d,w)->{try{int min=Math.max(1,Integer.parseInt(durE.getText().toString().trim()));JSONObject s=new JSONObject();s.put("id",UUID.randomUUID().toString());s.put("title",titleE.getText().toString().trim());s.put("category",String.valueOf(cat.getSelectedItem()));s.put("date",date());s.put("durationMs",min*60000L);s.put("createdAt",dateTime());sessions.put(s);saveState();showHome();}catch(Exception e){toast("Could not log activity: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();
    }

    void manualStartDialog(){
        if(getActiveTimer()!=null){toast("Stop the current timer first.");return;}
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);
        EditText titleE=new EditText(this);titleE.setHint("What are you doing?");l.addView(titleE);
        Spinner cat=categorySpinner("LEARNING");l.addView(cat);
        dlg().setTitle("Start manual activity").setView(l).setPositiveButton("Start",(d,w)->{String title=titleE.getText().toString().trim();if(title.isEmpty()){toast("Enter an activity name.");return;}startPersistentTimer(title,String.valueOf(cat.getSelectedItem()),null,null);}).setNegativeButton("Cancel",null).show();
    }

    void chooseTaskForTimer(){
        if(getActiveTimer()!=null){toast("A timer is already running.");return;}
        if(tasks.length()==0){toast("Create a task first.");return;}
        String[] names=new String[tasks.length()]; for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);names[i]=t.optString("title");}
        dlg().setTitle("Start task").setItems(names,(d,w)->startTimerForTask(w)).show();
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
        startPersistentTimer(b.optString("title","Planned activity"),b.optString("category","OTHER"),null,b.optString("id"),b.optString("start",""));
    }

    void startPersistentTimer(String title,String category,String taskId,String scheduleId){ startPersistentTimer(title,category,taskId,scheduleId,""); }
    /** plannedStart: "HH:mm" the block was scheduled for, or "" if this activity has no fixed schedule (manual/task). */
    void startPersistentTimer(String title,String category,String taskId,String scheduleId,String plannedStart){
        try{
            JSONObject a=new JSONObject();a.put("id",UUID.randomUUID().toString());a.put("title",title);a.put("category",category);a.put("taskId",taskId==null?"":taskId);a.put("scheduleId",scheduleId==null?"":scheduleId);a.put("startAt",System.currentTimeMillis());a.put("date",date());a.put("completed",false);
            if(plannedStart!=null&&!plannedStart.isEmpty()){
                a.put("plannedStart",plannedStart);
                int plannedMin=toMinutes(plannedStart);
                if(plannedMin>=0){
                    Calendar now=Calendar.getInstance();
                    int actualMin=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
                    a.put("adherenceMin",actualMin-plannedMin);
                }
            }
            saveActiveTimer(a);toast("Timer started");showHome();
        }catch(Exception e){toast("Could not start timer: "+safeMessage(e));}
    }
    /** Human label for how a tracked session compared to its scheduled start time. */
    String adherenceLabel(int diffMin){
        if(diffMin<=-6)return formatMin(Math.abs(diffMin))+" early";
        if(diffMin>=6)return formatMin(diffMin)+" late";
        return "On time";
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
            JSONObject session=new JSONObject();session.put("id",UUID.randomUUID().toString());session.put("title",a.optString("title"));session.put("category",a.optString("category","OTHER"));session.put("date",a.optString("date",date()));session.put("durationMs",duration);session.put("startAt",a.optLong("startAt",end));session.put("endAt",end);session.put("createdAt",dateTime());session.put("taskId",a.optString("taskId",""));session.put("scheduleId",a.optString("scheduleId",""));session.put("completed",complete);
            if(a.has("plannedStart")){session.put("plannedStart",a.optString("plannedStart"));session.put("adherenceMin",a.optInt("adherenceMin",0));}
            sessions.put(session);
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
        dlg().setTitle("Edit roadmap").setView(l).setPositiveButton("Save",(d,w)->{try{r.put("name",name.getText().toString().trim());r.put("description",desc.getText().toString().trim());r.put("icon",icon.getText().toString().trim());saveRoadmaps();refreshRoadmap();}catch(Exception e){toast("Could not edit roadmap");}}).setNegativeButton("Cancel",null).show();
    }
    void editPhaseDialog(JSONObject ph){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("Phase title");name.setText(ph.optString("title"));l.addView(name);EditText duration=new EditText(this);duration.setHint("Duration");duration.setText(ph.optString("duration"));l.addView(duration);dlg().setTitle("Edit phase").setView(l).setPositiveButton("Save",(d,w)->{try{ph.put("title",name.getText().toString().trim());ph.put("duration",duration.getText().toString().trim());saveRoadmaps();refreshRoadmap();}catch(Exception e){toast("Could not edit phase");}}).setNegativeButton("Cancel",null).show();}
    void editTopicDialog(JSONObject ph,JSONObject existing){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(8),dp(16),dp(8));

        addText(l,"Title / question",11);
        EditText name=new EditText(this);name.setHint("e.g. Lambda Expressions");name.setText(existing==null?"":existing.optString("title"));l.addView(name);

        LinearLayout row1=row();
        LinearLayout pCol=new LinearLayout(this);pCol.setOrientation(LinearLayout.VERTICAL);
        addText(pCol,"Priority",11); Spinner priority=prioritySpinner(existing==null?"MEDIUM":existing.optString("priority","MEDIUM")); pCol.addView(priority);
        row1.addView(pCol,new LinearLayout.LayoutParams(0,-2,1));
        addSpace(row1,12);
        LinearLayout dCol=new LinearLayout(this);dCol.setOrientation(LinearLayout.VERTICAL);
        addText(dCol,"Difficulty",11); Spinner difficulty=difficultySpinner(existing==null?"":existing.optString("difficulty","")); dCol.addView(difficulty);
        row1.addView(dCol,new LinearLayout.LayoutParams(0,-2,1));
        l.addView(row1);

        CheckBox interview=new CheckBox(this);interview.setText("Interview item");interview.setTextColor(Color.parseColor("#F4F6FB"));interview.setChecked(existing!=null&&existing.optBoolean("interview",false));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-2,-2); ip.topMargin=dp(6); interview.setLayoutParams(ip); l.addView(interview);

        addText(l,"Subtopics",11);
        EditText subs=new EditText(this);subs.setHint("Comma separated, e.g. filter, map, reduce");subs.setText(existing==null?"":joinJsonArray(existing.optJSONArray("subtopics")));l.addView(subs);

        addText(l,"Tags",11);
        EditText tags=new EditText(this);tags.setHint("Comma separated");tags.setText(existing==null?"":joinJsonArray(existing.optJSONArray("tags")));l.addView(tags);

        addText(l,"Primary resource link",11);
        EditText url=new EditText(this);url.setHint("https://...");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setText(existing==null?"":existing.optString("url",""));l.addView(url);

        addText(l,"Extra links (one per line: Title | https://...)",11);
        EditText links=new EditText(this);links.setHint("Docs | https://...");links.setMinLines(2);links.setText(existing==null?"":linksToText(existing.optJSONArray("links")));l.addView(links);

        ScrollView scroll=new ScrollView(this); scroll.addView(l);
        dlg().setTitle(existing==null?"Add roadmap item":"Edit roadmap item").setView(scroll).setPositiveButton("Save",(d,w)->{try{
            JSONObject t=existing==null?new JSONObject():existing;if(existing==null){t.put("id",UUID.randomUUID().toString());JSONArray ts=ph.optJSONArray("topics");if(ts==null){ts=new JSONArray();ph.put("topics",ts);}ts.put(t);}
            t.put("title",name.getText().toString().trim());
            t.put("priority",String.valueOf(priority.getSelectedItem()));
            String diff=String.valueOf(difficulty.getSelectedItem()); if("UNSET".equals(diff))t.remove("difficulty"); else t.put("difficulty",diff);
            t.put("interview",interview.isChecked());
            String raw=subs.getText().toString().trim();JSONArray a=new JSONArray();if(!raw.isEmpty())for(String x:raw.split(","))if(!x.trim().isEmpty())a.put(x.trim());if(a.length()>0)t.put("subtopics",a);else t.remove("subtopics");
            String tagRaw=tags.getText().toString().trim();JSONArray tagArray=new JSONArray();if(!tagRaw.isEmpty())for(String x:tagRaw.split(","))if(!x.trim().isEmpty())tagArray.put(x.trim());if(tagArray.length()>0)t.put("tags",tagArray);else t.remove("tags");
            String primary=url.getText().toString().trim();if(primary.isEmpty())t.remove("url");else t.put("url",primary);
            JSONArray extra=parseLinks(links.getText().toString());if(extra.length()>0)t.put("links",extra);else t.remove("links");
            saveRoadmaps();refreshRoadmap();
        }catch(Exception e){toast("Could not save item: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();
    }
    String linksToText(JSONArray links){if(links==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<links.length();i++){JSONObject l=links.optJSONObject(i);if(l==null)continue;if(b.length()>0)b.append("\n");b.append(l.optString("title","Resource")).append(" | ").append(l.optString("url",""));}return b.toString();}
    JSONArray parseLinks(String raw){JSONArray out=new JSONArray();if(raw==null||raw.trim().isEmpty())return out;for(String line:raw.split("\\n")){String[] p=line.split("\\|",2);if(p.length<2)continue;String title=p[0].trim(),url=p[1].trim();if(url.isEmpty())continue;JSONObject o=new JSONObject();try{o.put("title",title.isEmpty()?"Resource":title);o.put("url",url);out.put(o);}catch(Exception ignored){}}return out;}
    String joinJsonArray(JSONArray a){if(a==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)b.append(", ");b.append(a.optString(i));}return b.toString();}
    void addPhaseDialog(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setHint("Phase title");l.addView(name);EditText duration=new EditText(this);duration.setHint("Duration, e.g. Week 1");l.addView(duration);dlg().setTitle("Add phase").setView(l).setPositiveButton("Add",(d,w)->{try{JSONArray ps=roadmap.optJSONArray("phases");if(ps==null){ps=new JSONArray();roadmap.put("phases",ps);}JSONObject ph=new JSONObject();ph.put("id",UUID.randomUUID().toString());ph.put("number",ps.length()+1);ph.put("title",name.getText().toString().trim());ph.put("duration",duration.getText().toString().trim());ph.put("topics",new JSONArray());ps.put(ph);saveRoadmaps();refreshRoadmap();}catch(Exception e){toast("Could not add phase");}}).setNegativeButton("Cancel",null).show();}
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
    /** Groups recent sessions by day + activity, so repeat sessions of the same task (e.g. three short
     *  "Wake Up & Freshen Up" runs on one day) collapse into a single row with a combined total,
     *  rather than showing as separate near-duplicate lines. Newest group first. */
    ArrayList<JSONObject> groupedRecentSessions(int days){
        LinkedHashMap<String,JSONObject> map=new LinkedHashMap<>();
        for(JSONObject s:recentSessionObjects(days)){
            String day=s.optString("date");
            String key=day+"|"+(!s.optString("scheduleId","").isEmpty()?s.optString("scheduleId"):(!s.optString("taskId","").isEmpty()?s.optString("taskId"):s.optString("title","")+"|"+s.optString("category","OTHER")));
            JSONObject g=map.get(key);
            try{
                if(g==null){
                    g=new JSONObject();
                    g.put("date",day); g.put("title",s.optString("title")); g.put("category",s.optString("category","OTHER"));
                    g.put("durationMs",0L); g.put("sessionCount",0); g.put("sessions",new JSONArray());
                    if(s.has("plannedStart")){g.put("plannedStart",s.optString("plannedStart"));}
                    map.put(key,g);
                }
                g.put("durationMs",g.optLong("durationMs",0)+s.optLong("durationMs",0));
                g.put("sessionCount",g.optInt("sessionCount",0)+1);
                g.optJSONArray("sessions").put(s);
            }catch(Exception ignored){}
        }
        ArrayList<JSONObject> out=new ArrayList<>(map.values());
        Collections.reverse(out);
        return out;
    }
    /** Rebuilds Progress in place, keeping scroll position (used when expanding a session row). */
    void refreshProgress(){ int y=currentScrollY(); showProgress(); restoreScrollY(y); }
    void addSessionGroupRow(LinearLayout list,JSONObject g){
        LinearLayout c=card(); c.setPadding(dp(8),dp(5),dp(6),dp(5));
        String key=g.optString("date")+"|"+g.optString("title")+"|"+g.optString("category");
        boolean expanded=expandedSessionGroups.contains(key);

        LinearLayout head=row();
        Button chevron=microIconBtn(expanded?"▾":"▸","Toggle session details");
        chevron.setOnClickListener(v->{if(expanded)expandedSessionGroups.remove(key);else expandedSessionGroups.add(key);refreshProgress();});
        head.addView(chevron);
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setPadding(dp(6),0,0,0);
        TextView titleTv=tv(g.optString("title","Activity"),15); titleTv.setTypeface(null,1); mid.addView(titleTv);
        JSONArray sess=g.optJSONArray("sessions");
        JSONObject first=sess!=null&&sess.length()>0?sess.optJSONObject(0):null;

        StringBuilder line2=new StringBuilder();
        if(first!=null){
            if(g.has("plannedStart"))line2.append("Planned ").append(clockLabel(g.optString("plannedStart"))).append(" · ");
            line2.append("Started ").append(clockLabel(first.optLong("startAt",0)));
        }
        if(line2.length()>0){TextView l2=tv(line2.toString(),12); l2.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(l2);}

        StringBuilder line3=new StringBuilder();
        if(first!=null&&first.has("plannedStart"))line3.append(adherenceLabel(first.optInt("adherenceMin",0))).append(" · ");
        line3.append(formatDurationSmart(g.optLong("durationMs",0))).append(" tracked");
        int count=g.optInt("sessionCount",1);
        if(count>1)line3.append(" · ").append(count).append(" sessions");
        TextView l3=tv(line3.toString(),12); l3.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(l3);

        head.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        head.setOnClickListener(v->chevron.performClick());
        c.addView(head);

        if(expanded&&sess!=null){
            LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(32),dp(4),dp(4),dp(2));
            for(int i=0;i<sess.length();i++){
                JSONObject s=sess.optJSONObject(i); if(s==null)continue;
                StringBuilder d=new StringBuilder();
                d.append(clockLabel(s.optLong("startAt",0))).append(" – ").append(clockLabel(s.optLong("endAt",s.optLong("startAt",0))));
                d.append(" · ").append(formatDurationSmart(s.optLong("durationMs",0)));
                if(s.optBoolean("completed",false))d.append(" · ✓ Completed");
                TextView dv=tv(d.toString(),11); dv.setTextColor(Color.parseColor("#7A8296")); details.addView(dv);
            }
            c.addView(details);
        }
        list.addView(c);
    }
    /** Consecutive days (ending today, or yesterday if nothing tracked yet today) with any tracked time. */
    int currentStreak(){
        String d=date(); if(todaySessionMs(d)<=0)d=shiftDate(d,-1);
        int streak=0; int guard=0;
        while(todaySessionMs(d)>0&&guard<3650){streak++; d=shiftDate(d,-1); guard++;}
        return streak;
    }
    void addStreakCard(LinearLayout parent){
        LinearLayout c=card(); addText(c,"STREAK",11);
        int streak=currentStreak();
        addText(c,streak+(streak==1?" day":" days"),28);
        addText(c,streak==0?"Track something today to start a streak.":"Keep it going — tap a day for details.",12);
        LinearLayout daysRow=new LinearLayout(this); daysRow.setOrientation(LinearLayout.HORIZONTAL); daysRow.setPadding(0,dp(8),0,0);
        int show=14;
        for(int i=show-1;i>=0;i--){
            String d=shiftDate(date(),-i);
            boolean active=todaySessionMs(d)>0;
            boolean isToday=i==0;
            TextView chip=tv(new SimpleDateFormat("d",Locale.US).format(parseIso(d)),11);
            chip.setGravity(Gravity.CENTER); chip.setContentDescription(displayDate(d)+(active?", tracked":", no activity")+", tap for details");
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(32),1); lp.setMargins(dp(2),0,dp(2),0);
            android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable();
            gd.setCornerRadius(dp(7)); gd.setColor(Color.parseColor(active?"#34C77B":"#1B202B"));
            if(isToday)gd.setStroke(dp(2),Color.parseColor("#4F8EF7"));
            chip.setBackground(gd); chip.setTextColor(Color.parseColor(active?"#0B0F17":"#9AA4B8")); chip.setLayoutParams(lp);
            chip.setOnClickListener(v->showDayDetailDialog(d));
            daysRow.addView(chip);
        }
        c.addView(daysRow); parent.addView(c);
    }
    Date parseIso(String iso){try{return new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(iso);}catch(Exception e){return new Date();}}
    /** Shows a day's tracked activities as proper rows (title, category, duration, session
     *  count, and a completed check) instead of one long plain-text block — easier to scan,
     *  and consistent with how activities look everywhere else in the app. */
    void showDayDetailDialog(String day){
        ArrayList<JSONObject> groups=aggregateSessions(day);
        long total=todaySessionMs(day);

        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16),dp(4),dp(16),dp(4));
        addText(l,formatDuration(total)+" tracked",22);
        addSpace(l,10);

        if(groups.isEmpty()){
            addText(l,"No activities tracked this day.",13);
        } else {
            for(JSONObject g:groups){
                LinearLayout r=row(); r.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL); r.setMinimumHeight(0); r.setPadding(0,dp(6),0,dp(6));
                LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setMinimumHeight(0);
                TextView titleTv=tv(g.optString("title","Activity"),15); titleTv.setTypeface(null,1); mid.addView(titleTv);
                int cnt=g.optInt("sessionCount",0);
                String sub=g.optString("category","OTHER")+" · "+formatDurationSmart(g.optLong("durationMs",0))+" · "+cnt+(cnt==1?" session":" sessions");
                TextView subTv=tv(sub,12); subTv.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(subTv);
                r.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
                if(g.optBoolean("completed",false)){TextView check=tv("✓",16); check.setTextColor(Color.parseColor("#34C77B")); check.setGravity(Gravity.CENTER); r.addView(check,new LinearLayout.LayoutParams(dp(28),-2));}
                l.addView(r);
                View divider=new View(this); divider.setBackgroundColor(Color.parseColor("#22283A")); l.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            }
        }
        ScrollView sc=new ScrollView(this); sc.addView(l);
        dlg().setTitle(displayDate(day)).setView(sc).setPositiveButton("Close",null).show();
    }
    ArrayList<JSONObject> aggregateSessions(String day){
        LinkedHashMap<String,JSONObject> map=new LinkedHashMap<>();
        for(JSONObject s:sessionObjectsForDate(day)){
            String key=s.optString("scheduleId","");if(key.isEmpty())key=s.optString("taskId","");if(key.isEmpty())key=s.optString("title","")+"|"+s.optString("category","OTHER");
            JSONObject g=map.get(key);if(g==null){g=new JSONObject();try{g.put("key",key);g.put("title",s.optString("title"));g.put("category",s.optString("category","OTHER"));g.put("durationMs",0);g.put("sessionCount",0);g.put("taskId",s.optString("taskId",""));g.put("scheduleId",s.optString("scheduleId",""));g.put("completed",false);g.put("resumable",false);map.put(key,g);}catch(Exception ignored){}}
            try{g.put("durationMs",g.optLong("durationMs",0)+s.optLong("durationMs",0));g.put("sessionCount",g.optInt("sessionCount",0)+1);if(!s.optBoolean("completed",false)){g.put("resumable",true);g.put("completed",false);g.put("lastSession",s);}else if(!g.optBoolean("resumable",false)){g.put("completed",true);}}catch(Exception ignored){}
        }
        ArrayList<JSONObject> out=new ArrayList<>(map.values());
        for(JSONObject g:out){
            try{
                String sid=g.optString("scheduleId","");
                if(!sid.isEmpty()&&isPlanCompletedForDate(sid,day)){g.put("completed",true);g.put("resumable",false);continue;}
                // A task can be marked complete (e.g. via its checkbox) even though one of its
                // earlier, now-irrelevant timer sessions was never itself flagged "completed".
                // Without this check that stray session flag kept the group "resumable" and
                // showed a Resume button on an activity that's already done.
                String tid=g.optString("taskId","");
                if(!tid.isEmpty()){
                    JSONObject task=findTask(tid);
                    if(task!=null&&"completed".equals(task.optString("status"))){g.put("completed",true);g.put("resumable",false);}
                }
            }catch(Exception ignored){}
        }
        return out;
    }
    boolean hasOpenSessionForSchedule(JSONObject b){String id=b.optString("id","");if(id.isEmpty())return false;for(JSONObject s:sessionObjectsForDate(date()))if(id.equals(s.optString("scheduleId"))&&!s.optBoolean("completed",false))return true;return false;}

    // ---------- CR-003: explicit activity states ----------
    // "tracked" used to mean two different things in this app: (a) this schedule block's category is
    // configured to be included in time tracking, and (b) time was actually recorded for it today.
    // These helpers separate those two ideas: state = what's actually happening right now, and the
    // "track" boolean is only ever mentioned separately, as an opt-out note.
    boolean isScheduleRunning(JSONObject b){JSONObject a=getActiveTimer();return a!=null&&!b.optString("id","").isEmpty()&&b.optString("id","").equals(a.optString("scheduleId",""));}
    boolean isTaskRunning(JSONObject t){JSONObject a=getActiveTimer();return a!=null&&!t.optString("id","").isEmpty()&&t.optString("id","").equals(a.optString("taskId",""));}
    String scheduleStateLabel(JSONObject b){
        if(isPlanCompletedForDate(b.optString("id"),date()))return "✓ Completed";
        if(isScheduleRunning(b))return "🔴 Running";
        if(hasOpenSessionForSchedule(b))return "⏸ Paused";
        return "○ Not started";
    }
    String taskStateLabel(JSONObject t){
        if("completed".equals(t.optString("status")))return "✓ Completed";
        if(isTaskRunning(t))return "🔴 Running";
        if("in_progress".equals(t.optString("status")))return "⏸ Paused";
        return "○ Not started";
    }
    int stateColor(String label){
        if(label==null)return Color.parseColor("#9AA4B8");
        if(label.contains("Completed"))return Color.parseColor("#34C77B");
        if(label.contains("Running"))return Color.parseColor("#FF6B6B");
        if(label.contains("Paused"))return Color.parseColor("#FFB84F");
        return Color.parseColor("#9AA4B8");
    }
    /** Appends the relevant duration to a schedule state label (e.g. "🔴 Running · 24m"),
     *  so the state line itself distinguishes scheduled tracking from actual recorded time (CR-003). */
    String scheduleStateLabelWithMeta(JSONObject b){
        String state=scheduleStateLabel(b);
        if(state.contains("Running")){long ms=activeElapsedMsForDate(date());return state+" · "+formatDurationSmart(ms);}
        if(state.contains("Completed")){long ms=trackedMsForSchedule(b.optString("id",""),date());return ms>0?state+" · "+formatDurationSmart(ms):state;}
        return state;
    }
    String taskStateLabelWithMeta(JSONObject t){
        String state=taskStateLabel(t);
        if(state.contains("Running")){long ms=activeElapsedMsForDate(date());return state+" · "+formatDurationSmart(ms);}
        if(state.contains("Completed")){int min=t.optInt("actualMin",0);return min>0?state+" · "+formatMin(min):state;}
        return state;
    }
    long trackedMsForSchedule(String scheduleId,String day){
        if(scheduleId==null||scheduleId.isEmpty())return 0;
        long total=0;
        for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s!=null&&day.equals(s.optString("date"))&&scheduleId.equals(s.optString("scheduleId")))total+=s.optLong("durationMs",0);}
        return total;
    }
    String formatDurationSmart(long ms){if(ms<60000)return Math.max(1,ms/1000)+"s";return formatDuration(ms);}
    String sessionLine(JSONObject s){
        String line=s.optString("date")+" · "+s.optString("title")+" · "+formatDurationSmart(s.optLong("durationMs",0));
        if(s.has("plannedStart"))line+=" · "+adherenceLabel(s.optInt("adherenceMin",0));
        return line;
    }

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
        boolean overlap=blockOverlapsAny(b);
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row=row(); row.setPadding(0,dp(7),0,dp(7));
        View stripe=new View(this);int color;try{color=Color.parseColor(b.optString("color","#4F7CFF"));}catch(Exception e){color=Color.parseColor("#4F7CFF");}stripe.setBackgroundColor(color);row.addView(stripe,new LinearLayout.LayoutParams(dp(6),dp(88)));
        LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);mid.setPadding(dp(12),0,dp(8),0);
        addText(mid,b.optString("start")+" – "+b.optString("end"),12);
        TextView titleTv=tv(blockTitle(b),16); if(b.optString("title","").trim().isEmpty())titleTv.setTextColor(Color.parseColor("#9AA4B8")); mid.addView(titleTv,new LinearLayout.LayoutParams(-1,-2));
        mid.addView(labelWithDot(b.optString("category","OTHER")+(b.optBoolean("track",true)?" · in time tracking":""),categoryColor(b.optString("category","OTHER")),11));
        String stateLabel=scheduleStateLabelWithMeta(b); TextView stateTv=tv(stateLabel,12); stateTv.setTextColor(stateColor(stateLabel)); mid.addView(stateTv);
        if(overlap){TextView warn=tv("⚠ Overlaps another block",11);warn.setTextColor(Color.parseColor("#FFB84F"));mid.addView(warn);}
        row.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        JSONObject alarm=b.optJSONObject("alarm");if(alarm!=null&&alarm.optBoolean("enabled",false)){TextView al=tv("🔔",16);row.addView(al,new LinearLayout.LayoutParams(dp(34),-2));}
        boolean complete=isPlanCompletedForDate(b.optString("id"),date());
        Button st=primaryBtn(complete?"✓ Done":(hasOpenSessionForSchedule(b)?"▶ Resume":(isCurrentSchedule(b)?"▶ Start":"Start")));st.setOnClickListener(v->{if(!complete)startScheduleBlock(b);});row.addView(st);
        Button edit=iconBtn("✎","Edit schedule block");edit.setOnClickListener(v->scheduleBlockDialog(b,-1));row.addView(edit);
        Button del=dangerIconBtn("🗑","Delete schedule block");del.setOnClickListener(v->deleteScheduleBlock(b));row.addView(del);
        row.setOnLongClickListener(v->{scheduleBlockQuickMenu(b);return true;});
        outer.addView(row);
        if(overlap){View div=new View(this);div.setBackgroundColor(Color.parseColor("#332A1E"));outer.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));}
        parent.addView(outer);
    }
    void scheduleBlockQuickMenu(JSONObject b){
        String[] opts={"▶ Start","✎ Edit","🗑 Delete","Cancel"};
        dlg().setTitle(blockTitle(b)).setItems(opts,(d,w)->{
            if(w==0)startScheduleBlock(b);
            else if(w==1)scheduleBlockDialog(b,-1);
            else if(w==2)deleteScheduleBlock(b);
        }).show();
    }

    static final String[] SWATCH_COLORS={"#4F7CFF","#34C77B","#FFB84F","#B983FF","#FF6B6B","#4FD1FF","#FF8FB1","#A0A4AE","#FFD166","#7A7FEA","#6EE7B7","#F472B6"};
    static final String[] DAY_CODES={"MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"};
    static final String[] DAY_SHORT={"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
    static final String[] ALARM_MIN_OPTIONS={"5","10","15","30","45","60"};

    /** A read-only EditText that opens a native TimePickerDialog when tapped. Stores value as HH:mm (24h). */
    EditText timePickerField(String hint,String initial){ return timePickerField(hint,initial,null); }
    /** Same as above, but runs `onChanged` after a time is picked — used to auto-recalculate a
     *  dependent Duration field (CR-001) when Start/End change. */
    EditText timePickerField(String hint,String initial,Runnable onChanged){
        EditText e=new EditText(this); e.setHint(hint); e.setText(initial==null?"":initial);
        e.setFocusable(false); e.setClickable(true); e.setCursorVisible(false);
        e.setOnClickListener(v->{
            int h=8,m=0;
            String cur=e.getText().toString().trim();
            if(cur.matches("^\\d{1,2}:\\d{2}$")){String[] p=cur.split(":");try{h=Integer.parseInt(p[0]);m=Integer.parseInt(p[1]);}catch(Exception ignored){}}
            new TimePickerDialog(this,R.style.AppTheme_Dialog,(view,hh,mm)->{e.setText(String.format(Locale.US,"%02d:%02d",hh,mm));if(onChanged!=null)onChanged.run();},h,m,true).show();
        });
        return e;
    }
    /** A read-only EditText that opens a native DatePickerDialog when tapped. `holder[0]` carries the
     *  ISO yyyy-MM-dd value; the field itself displays a human-readable date (CR-001). */
    EditText datePickerField(String[] holder){
        if(holder[0]==null||holder[0].trim().isEmpty())holder[0]=date();
        EditText e=new EditText(this); e.setText(displayDate(holder[0]));
        e.setFocusable(false); e.setClickable(true); e.setCursorVisible(false);
        e.setOnClickListener(v->{
            Calendar c=Calendar.getInstance();
            try{c.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(holder[0]));}catch(Exception ignored){}
            new DatePickerDialog(this,R.style.AppTheme_Dialog,(view,y,mo,d)->{
                holder[0]=String.format(Locale.US,"%04d-%02d-%02d",y,mo+1,d);
                e.setText(displayDate(holder[0]));
            },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
        return e;
    }
    /** yyyy-MM-dd -> "28 Aug 2026" for display; raw ISO is still what's stored/exported (CR-055 groundwork). */
    String displayDate(String iso){
        try{return new SimpleDateFormat("d MMM yyyy",Locale.US).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(iso));}
        catch(Exception e){return iso==null?"":iso;}
    }
    /** "HH:mm" -> "10:00 AM" */
    String clockLabel(String hhmm){
        try{return new SimpleDateFormat("h:mm a",Locale.US).format(new SimpleDateFormat("HH:mm",Locale.US).parse(hhmm));}
        catch(Exception e){return hhmm==null?"":hhmm;}
    }
    /** epoch millis -> "2:28 AM" */
    String clockLabel(long millis){return new SimpleDateFormat("h:mm a",Locale.US).format(new Date(millis));}

    /** Toggleable day-of-week chips. Returns the container; selected days are tracked in `selected`. */
    LinearLayout dayChipsRow(boolean[] selected){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0,dp(6),0,dp(6));
        for(int i=0;i<7;i++){
            final int idx=i;
            TextView chip=tv(DAY_SHORT[i],12); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(4),dp(8),dp(4),dp(8));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1); lp.setMargins(dp(2),0,dp(2),0); chip.setLayoutParams(lp);
            Runnable refresh=()->{
                android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable();
                gd.setCornerRadius(dp(8)); gd.setColor(Color.parseColor(selected[idx]?"#4F8EF7":"#1B202B"));
                gd.setStroke(dp(1),Color.parseColor(selected[idx]?"#4F8EF7":"#303746")); chip.setBackground(gd);
                chip.setTextColor(Color.parseColor(selected[idx]?"#FFFFFF":"#9AA4B8"));
            };
            refresh.run();
            chip.setOnClickListener(v->{selected[idx]=!selected[idx];refresh.run();});
            r.addView(chip);
        }
        return r;
    }

    /** Color swatch picker. holder[0] carries the selected hex; returns the container view. */
    LinearLayout colorSwatchRow(String[] holder){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0,dp(6),0,dp(6));
        HorizontalScrollView sc=new HorizontalScrollView(this); sc.setHorizontalScrollBarEnabled(false);
        LinearLayout inner=new LinearLayout(this); inner.setOrientation(LinearLayout.HORIZONTAL);
        View[] swatchViews=new View[SWATCH_COLORS.length];
        Runnable[] refreshAll=new Runnable[1];
        for(int i=0;i<SWATCH_COLORS.length;i++){
            final String c=SWATCH_COLORS[i];
            View sw=new View(this);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(36),dp(36)); lp.setMargins(dp(4),dp(4),dp(4),dp(4)); sw.setLayoutParams(lp);
            swatchViews[i]=sw;
            sw.setOnClickListener(v->{holder[0]=c;refreshAll[0].run();});
            inner.addView(sw);
        }
        refreshAll[0]=()->{
            for(int i=0;i<SWATCH_COLORS.length;i++){
                boolean sel=SWATCH_COLORS[i].equalsIgnoreCase(holder[0]);
                android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL); gd.setColor(Color.parseColor(SWATCH_COLORS[i]));
                gd.setStroke(sel?dp(3):0,Color.WHITE);
                swatchViews[i].setBackground(gd);
            }
        };
        refreshAll[0].run();
        sc.addView(inner); r.addView(sc,new LinearLayout.LayoutParams(-1,-2));
        return r;
    }

    Spinner alarmMinutesSpinner(int selectedValue){
        Spinner sp=new Spinner(this);
        ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,ALARM_MIN_OPTIONS){
            @Override public View getView(int p,View cv,android.view.ViewGroup pr){TextView v=(TextView)super.getView(p,cv,pr);v.setText(getItem(p)+" min before");v.setTextColor(Color.parseColor("#F4F6FB"));v.setPadding(dp(12),dp(10),dp(12),dp(10));return v;}
            @Override public View getDropDownView(int p,View cv,android.view.ViewGroup pr){TextView v=(TextView)super.getDropDownView(p,cv,pr);v.setText(getItem(p)+" min before");v.setTextColor(Color.parseColor("#F4F6FB"));v.setBackgroundColor(Color.parseColor("#1B1F2A"));v.setPadding(dp(12),dp(10),dp(12),dp(10));return v;}
        };
        sp.setAdapter(ad);
        String target=String.valueOf(selectedValue);
        int best=1; for(int i=0;i<ALARM_MIN_OPTIONS.length;i++)if(ALARM_MIN_OPTIONS[i].equals(target)){best=i;break;}
        sp.setSelection(best);
        return sp;
    }

    void scheduleBlockDialog(JSONObject existing,int index){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(8),dp(16),dp(8));

        addText(l,"Title",11);
        EditText titleE=new EditText(this);titleE.setHint("e.g. Backend Stream API");titleE.setText(existing==null?"":existing.optString("title"));l.addView(titleE);
        TextView titleErr=tv("",11); titleErr.setTextColor(Color.parseColor("#FF6B6B")); titleErr.setVisibility(View.GONE); l.addView(titleErr);

        addText(l,"Time",11);
        LinearLayout timeRow=row();
        EditText startE=timePickerField("Start",existing==null?"20:00":existing.optString("start","20:00"));
        EditText endE=timePickerField("End",existing==null?"21:00":existing.optString("end","21:00"));
        timeRow.addView(startE,new LinearLayout.LayoutParams(0,-2,1));
        addSpace(timeRow,12);
        timeRow.addView(endE,new LinearLayout.LayoutParams(0,-2,1));
        l.addView(timeRow);
        TextView timeErr=tv("",11); timeErr.setTextColor(Color.parseColor("#FF6B6B")); timeErr.setVisibility(View.GONE); l.addView(timeErr);

        addText(l,"Category",11);
        Spinner cat=categorySpinner(existing==null?"LEARNING":existing.optString("category","OTHER"));l.addView(cat);

        addText(l,"Color",11);
        String[] colorHolder={existing==null?"#4F7CFF":existing.optString("color","#4F7CFF")};
        try{Color.parseColor(colorHolder[0]);}catch(Exception e){colorHolder[0]="#4F7CFF";}
        l.addView(colorSwatchRow(colorHolder));

        addText(l,"Repeats on",11);
        boolean[] selectedDays=new boolean[7];
        if(existing!=null){JSONArray ex=existing.optJSONArray("days"); if(ex!=null)for(int i=0;i<ex.length();i++){String dname=ex.optString(i).toUpperCase(Locale.US);for(int j=0;j<DAY_CODES.length;j++)if(DAY_CODES[j].equals(dname))selectedDays[j]=true;}}
        l.addView(dayChipsRow(selectedDays));
        addText(l,"No days selected = repeats every day",10);

        CheckBox track=new CheckBox(this);track.setText("Include in time tracking");track.setTextColor(Color.parseColor("#F4F6FB"));track.setChecked(existing==null||existing.optBoolean("track",true));l.addView(track);

        CheckBox alarm=new CheckBox(this);alarm.setText("Alarm");alarm.setTextColor(Color.parseColor("#F4F6FB"));alarm.setChecked(existing!=null&&existing.optJSONObject("alarm")!=null&&existing.optJSONObject("alarm").optBoolean("enabled",false));l.addView(alarm);
        int existingMins=existing!=null&&existing.optJSONObject("alarm")!=null?existing.optJSONObject("alarm").optInt("minutesBefore",5):5;
        Spinner minsSp=alarmMinutesSpinner(existingMins); minsSp.setVisibility(alarm.isChecked()?View.VISIBLE:View.GONE); l.addView(minsSp);
        alarm.setOnCheckedChangeListener((btn,checked)->minsSp.setVisibility(checked?View.VISIBLE:View.GONE));

        ScrollView scroll=new ScrollView(this); scroll.addView(l);
        AlertDialog dialog=dlg().setTitle(existing==null?"Add schedule block":"Edit schedule block").setView(scroll)
            .setPositiveButton("Save",(d,w)->{}) // overridden below to control dismissal
            .setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(dd->{
            Button saveBtn=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v->{
                titleErr.setVisibility(View.GONE); timeErr.setVisibility(View.GONE);
                String titleVal=titleE.getText().toString().trim();
                String startVal=startE.getText().toString().trim(), endVal=endE.getText().toString().trim();
                boolean valid=true;
                if(titleVal.isEmpty()){titleErr.setText("Title is required");titleErr.setVisibility(View.VISIBLE);valid=false;}
                int sm=toMinutes(startVal), em=toMinutes(endVal);
                if(sm<0||em<0){timeErr.setText("Please set both start and end time");timeErr.setVisibility(View.VISIBLE);valid=false;}
                else if(em<=sm){timeErr.setText("End time must be after start time");timeErr.setVisibility(View.VISIBLE);valid=false;}
                if(!valid)return;
                try{
                    JSONObject candidate=new JSONObject();
                    candidate.put("id",existing==null?UUID.randomUUID().toString():existing.optString("id",UUID.randomUUID().toString()));
                    candidate.put("title",titleVal); candidate.put("start",startVal); candidate.put("end",endVal);
                    candidate.put("category",String.valueOf(cat.getSelectedItem()));
                    candidate.put("color",colorHolder[0]); candidate.put("track",track.isChecked());
                    JSONArray days=new JSONArray(); for(int i=0;i<7;i++)if(selectedDays[i])days.put(DAY_CODES[i]);
                    candidate.put("days",days);
                    JSONObject al=new JSONObject(); al.put("enabled",alarm.isChecked());
                    al.put("minutesBefore",Integer.parseInt(ALARM_MIN_OPTIONS[Math.max(0,minsSp.getSelectedItemPosition())]));
                    al.put("sound","alarm"); candidate.put("alarm",al);

                    ArrayList<JSONObject> overlaps=findOverlappingBlocks(candidate,existing==null?null:existing.optString("id"));
                    Runnable commit=()->{
                        try{
                            JSONObject b=existing==null?new JSONObject():existing;
                            b.put("id",candidate.getString("id")); b.put("title",titleVal); b.put("start",startVal); b.put("end",endVal);
                            b.put("category",candidate.getString("category")); b.put("color",colorHolder[0]); b.put("track",track.isChecked());
                            b.put("days",days); b.put("alarm",al);
                            if(existing==null)schedules.put(b);
                            if(!al.optBoolean("enabled",false))ReminderReceiver.cancel(this,b.optString("id")); else ReminderReceiver.scheduleNext(this,b);
                            saveState(); dialog.dismiss(); showPlan();
                        }catch(Exception e){toast("Could not save schedule: "+safeMessage(e));}
                    };
                    if(!overlaps.isEmpty()){
                        StringBuilder names=new StringBuilder(); for(JSONObject o:overlaps){if(names.length()>0)names.append(", ");names.append(blockTitle(o)).append(" (").append(o.optString("start")).append("–").append(o.optString("end")).append(")");}
                        dlg().setTitle("Overlapping time")
                            .setMessage("This overlaps with: "+names+". Save anyway?")
                            .setPositiveButton("Save anyway",(dd2,ww2)->commit.run())
                            .setNegativeButton("Go back",null).show();
                    } else commit.run();
                }catch(Exception e){toast("Could not save schedule: "+safeMessage(e));}
            });
        });
        dialog.show();
    }

    String daysString(JSONObject b){StringBuilder x=new StringBuilder();JSONArray a=b.optJSONArray("days");if(a!=null)for(int i=0;i<a.length();i++){if(i>0)x.append(",");x.append(a.optString(i));}return x.toString();}
    void quickStartSchedule(JSONObject b){startScheduleBlock(b);}

    // ---------- Overlap detection ----------
    int toMinutes(String hhmm){try{String[] p=hhmm.split(":");return Integer.parseInt(p[0].trim())*60+Integer.parseInt(p[1].trim());}catch(Exception e){return -1;}}
    boolean daysShareOverlap(JSONArray a,JSONArray b){
        boolean aEvery=a==null||a.length()==0, bEvery=b==null||b.length()==0;
        if(aEvery||bEvery)return true;
        HashSet<String> setA=new HashSet<>(); for(int i=0;i<a.length();i++)setA.add(a.optString(i).toUpperCase(Locale.US));
        for(int i=0;i<b.length();i++)if(setA.contains(b.optString(i).toUpperCase(Locale.US)))return true;
        return false;
    }
    boolean timesOverlap(String s1,String e1,String s2,String e2){
        int m1=toMinutes(s1),n1=toMinutes(e1),m2=toMinutes(s2),n2=toMinutes(e2);
        if(m1<0||n1<0||m2<0||n2<0)return false;
        return m1<n2 && m2<n1;
    }
    ArrayList<JSONObject> findOverlappingBlocks(JSONObject candidate,String excludeId){
        ArrayList<JSONObject> out=new ArrayList<>();
        for(int i=0;i<schedules.length();i++){
            JSONObject b=schedules.optJSONObject(i); if(b==null)continue;
            if(excludeId!=null&&excludeId.equals(b.optString("id")))continue;
            if(daysShareOverlap(candidate.optJSONArray("days"),b.optJSONArray("days"))&&timesOverlap(candidate.optString("start"),candidate.optString("end"),b.optString("start"),b.optString("end")))
                out.add(b);
        }
        return out;
    }
    boolean blockOverlapsAny(JSONObject b){return !findOverlappingBlocks(b,b.optString("id")).isEmpty();}
    String blockTitle(JSONObject b){String t=b.optString("title","").trim();return t.isEmpty()?"Untitled block":t;}

    // ---------- Delete helpers ----------
    void confirmDelete(String title,String message,Runnable onConfirm){
        dlg().setTitle(title).setMessage(message)
            .setPositiveButton("Delete",(d,w)->onConfirm.run())
            .setNegativeButton("Cancel",null).show();
    }
    void deleteScheduleBlock(JSONObject b){
        confirmDelete("Delete schedule block?","\""+blockTitle(b)+"\" will be permanently removed.",()->{
            try{
                String id=b.optString("id","");
                JSONArray out=new JSONArray();
                for(int i=0;i<schedules.length();i++){JSONObject x=schedules.optJSONObject(i);if(x!=null&&!x.optString("id","").equals(id))out.put(x);}
                schedules=out;
                if(!id.isEmpty())ReminderReceiver.cancel(this,id);
                saveState(); toast("Schedule block deleted"); showPlan();
            }catch(Exception e){toast("Could not delete block: "+safeMessage(e));}
        });
    }
    void deleteTask(int index){
        JSONObject t=tasks.optJSONObject(index); if(t==null)return;
        confirmDelete("Delete task?","\""+t.optString("title","Task")+"\" will be permanently removed.",()->{
            try{
                JSONArray out=new JSONArray();
                for(int i=0;i<tasks.length();i++)if(i!=index)out.put(tasks.optJSONObject(i));
                tasks=out;
                JSONObject active=getActiveTimer();
                if(active!=null&&t.optString("id","").equals(active.optString("taskId","")))clearActiveTimer();
                saveState(); toast("Task deleted"); showPlan();
            }catch(Exception e){toast("Could not delete task: "+safeMessage(e));}
        });
    }
    void deleteTopic(JSONObject phase,JSONObject topic){
        confirmDelete("Delete item?","\""+topic.optString("title","This item")+"\" will be permanently removed.",()->{
            try{
                removeFromArray(phase.optJSONArray("topics"),topic);
                JSONArray cats=phase.optJSONArray("categories");
                if(cats!=null)for(int i=0;i<cats.length();i++){JSONObject cat=cats.optJSONObject(i);if(cat!=null)removeFromArray(cat.optJSONArray("topics"),topic);}
                HashSet<String> set=completedFor(currentRoadmapId); set.remove(topic.optString("id")); saveCompletedFor(currentRoadmapId,set);
                saveRoadmaps(); toast("Item deleted"); refreshRoadmap();
            }catch(Exception e){toast("Could not delete item: "+safeMessage(e));}
        });
    }
    void removeFromArray(JSONArray arr,JSONObject target){
        if(arr==null)return;
        for(int i=arr.length()-1;i>=0;i--)if(arr.optJSONObject(i)==target)arr.remove(i);
    }
    void deletePhase(JSONObject phase){
        confirmDelete("Delete phase?","\""+phase.optString("title","This phase")+"\" and all its items will be permanently removed.",()->{
            try{
                JSONArray topics=phaseTopics(phase); HashSet<String> set=completedFor(currentRoadmapId);
                for(int i=0;i<topics.length();i++){JSONObject t=topics.optJSONObject(i);if(t!=null)set.remove(t.optString("id"));}
                saveCompletedFor(currentRoadmapId,set);
                JSONArray ps=roadmap.optJSONArray("phases");
                if(ps!=null)removeFromArray(ps,phase);
                saveRoadmaps(); toast("Phase deleted"); refreshRoadmap();
            }catch(Exception e){toast("Could not delete phase: "+safeMessage(e));}
        });
    }
    void deleteRoadmap(JSONObject r){
        String id=r.optString("id",roadmapId(r));
        confirmDelete("Delete roadmap?","\""+roadmapName(r)+"\" and all its progress will be permanently removed.",()->{
            try{
                JSONArray out=new JSONArray();
                for(int i=0;i<roadmaps.length();i++){JSONObject x=roadmaps.optJSONObject(i);if(x!=null&&!id.equals(x.optString("id")))out.put(x);}
                roadmaps=out;
                try{SharedPreferences sp=getSharedPreferences(PREFS,0);JSONObject all=new JSONObject(sp.getString("completedByRoadmap","{}"));all.remove(id);sp.edit().putString("completedByRoadmap",all.toString()).apply();}catch(Exception ignored){}
                if(id.equals(currentRoadmapId)){
                    if(roadmaps.length()>0){currentRoadmapId=roadmaps.optJSONObject(0).optString("id");roadmap=findRoadmap(currentRoadmapId);}
                    else{currentRoadmapId=null;roadmap=null;}
                }
                saveRoadmaps(); toast("Roadmap deleted"); showLearn();
            }catch(Exception e){toast("Could not delete roadmap: "+safeMessage(e));}
        });
    }
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
        if(req==REQ_IMPORT){String raw=read(data.getData());JSONObject r=new JSONObject(raw);String fmt=r.optString("format");if(!"trackit-roadmap".equals(fmt)&&!"devtrack-roadmap".equals(fmt))throw new Exception("Unsupported roadmap format");normalizeRoadmap(r,UUID.randomUUID().toString(),"Imported Roadmap","📚");String id=r.optString("id");dlg().setTitle("Import roadmap").setMessage("Import "+roadmapName(r)+" as a new editable roadmap?").setPositiveButton("Import",(d,w)->{try{String finalId=findRoadmap(id)!=null?UUID.randomUUID().toString():id;r.put("id",finalId);roadmaps.put(r);setCurrentRoadmap(finalId);saveRoadmaps();showLearn();toast("Roadmap imported");}catch(Exception e){toast("Could not import roadmap: "+safeMessage(e));}}).setNegativeButton("Cancel",null).show();}
        else if(req==REQ_EXPORT){write(data.getData(),roadmap.toString(2));toast("Roadmap exported");}
        else if(req==REQ_SCHEDULE_IMPORT){
            String raw=read(data.getData()); JSONObject sch=new JSONObject(raw); validateSchedule(sch);
            JSONArray imported=sch.optJSONArray("blocks");
            dlg().setTitle("Import daily schedule").setMessage("Replace the current schedule with "+imported.length()+" blocks? Alarm settings included in the JSON will be applied.").setPositiveButton("Replace",(d,w)->{
                for(int i=0;i<schedules.length();i++){JSONObject old=schedules.optJSONObject(i);if(old!=null)ReminderReceiver.cancel(this,old.optString("id"));} schedules=imported; saveState(); scheduleAllImportedAlarms(); showPlan(); toast("Daily schedule imported");
            }).setNegativeButton("Cancel",null).show();
        }
        else if(req==REQ_SCHEDULE_EXPORT){ JSONObject sch=new JSONObject(); sch.put("format","devtrack-daily-schedule"); sch.put("version",1); sch.put("timezone",java.util.TimeZone.getDefault().getID()); sch.put("days",new JSONArray().put("MONDAY").put("TUESDAY").put("WEDNESDAY").put("THURSDAY").put("FRIDAY").put("SATURDAY").put("SUNDAY")); sch.put("blocks",schedules); write(data.getData(),sch.toString(2)); toast("Daily schedule exported"); }
        else if(req==300){String raw=read(data.getData());JSONObject s=new JSONObject(raw);if(s.has("roadmaps")){roadmaps=s.optJSONArray("roadmaps");if(roadmaps==null)roadmaps=new JSONArray();currentRoadmapId=s.optString("currentRoadmapId",roadmaps.length()>0?roadmaps.optJSONObject(0).optString("id"):null);roadmap=findRoadmap(currentRoadmapId);saveRoadmaps();}else if(s.has("roadmap")){JSONObject importedRoadmap=s.optJSONObject("roadmap");if(importedRoadmap!=null){normalizeRoadmap(importedRoadmap,"java-backend","Java Backend","☕");roadmaps=new JSONArray().put(importedRoadmap);currentRoadmapId=importedRoadmap.optString("id");roadmap=importedRoadmap;saveRoadmaps();}}if(s.has("completed")){completed.clear();JSONArray a=s.optJSONArray("completed");for(int i=0;i<a.length();i++)completed.add(a.optString(i));}tasks=s.optJSONArray("tasks");sessions=s.optJSONArray("sessions");schedules=s.optJSONArray("schedules");if(tasks==null)tasks=new JSONArray();if(sessions==null)sessions=new JSONArray();if(schedules==null)schedules=new JSONArray();saveState();scheduleAllImportedAlarms();toast("Backup imported");showHome();}
        else if(req==301){JSONObject s=new JSONObject();JSONArray c=new JSONArray();for(String id:completed)c.put(id);s.put("completed",c);s.put("tasks",tasks);s.put("sessions",sessions);s.put("schedules",schedules);s.put("roadmap",roadmap);s.put("roadmaps",roadmaps);s.put("currentRoadmapId",currentRoadmapId);write(data.getData(),s.toString(2));toast("Backup exported");}
    }catch(Exception e){toast("Import/export failed: "+e.getMessage());}}
    String read(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return b.toString("UTF-8");}
    void write(Uri u,String s)throws Exception{OutputStream out=getContentResolver().openOutputStream(u);out.write(s.getBytes(StandardCharsets.UTF_8));out.close();}
    void confirmReset(){dlg().setTitle("Reset all data?").setMessage("This deletes roadmap progress, tasks and study sessions from this device.").setPositiveButton("Delete",(d,w)->{completed.clear();tasks=new JSONArray();sessions=new JSONArray();schedules=new JSONArray();clearActiveTimer();getSharedPreferences(PREFS,0).edit().remove("roadmapOverride").remove(KEY_COMPLETED_PLANS).remove(KEY_ROADMAPS).remove(KEY_CURRENT_ROADMAP).remove("completedByRoadmap").apply();saveState();showHome();}).setNegativeButton("Cancel",null).show();}
}
