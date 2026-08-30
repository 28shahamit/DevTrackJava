# DevTrack v17 fixes

Four issues reported against v16. All changes are in `MainActivity.java`.

## 1. Screen "blink" when toggling a chevron (expand/collapse)

`base()` — called by every top-level screen render (`showHome`, `showLearn`,
`showPlan`, `showProgress`) and by the in-place refresh helpers
(`refreshRoadmap()`, `refreshProgress()`) — used to do
`content.removeAllViews()` and build a **brand-new** `ScrollView` every single
time, even for a simple chevron toggle. Destroying and recreating the whole
view tree on every tap is what produced the flash: the window briefly has no
content view attached while the new tree is measured and laid out.

`base()` now reuses the existing `ScrollView`/body `LinearLayout` when one is
already mounted, and only clears + rebuilds its children. The `ScrollView`
itself is never torn down for an in-place refresh, so there's no longer a
frame with nothing attached to redraw.

## 2. "Resume" button showing on an already-completed task

In "Today's Tracked Activities", `aggregateSessions()` grouped a task's
sessions and set `resumable = true` if *any* session in the group had
`completed = false` — including a stray earlier session that was superseded
once the task itself got marked complete (e.g. via its checkbox). The
schedule-block case already had a safety check (`isPlanCompletedForDate`) to
force `completed = true / resumable = false`, but the equivalent check was
missing for task-based groups.

Added the matching check: if a group's `taskId` points at a task whose
`status` is `"completed"`, the group is now forced to
`completed = true, resumable = false` — no more Resume button on a task
that's already done.

## 3. Day-log dialog redesign

Tapping a day in the streak calendar used to show a single `AlertDialog`
message — one long unstyled text blob dumping every activity's title,
category, duration and session count on top of each other. `showDayDetailDialog()`
now builds a real list of rows (bold title, muted category/duration/session-count
line, a green ✓ for completed activities, thin dividers between entries) in a
scrollable custom view, matching how activities look everywhere else in the
app instead of a wall of text.

## 4. Tracking Settings moved out of the Progress tab

"Tracking Settings" (automatic/manual mode, manual-activity start, alarm
test/permission, import/export/reset) was living at the bottom of the
Progress tab, mixed in with your stats. It's now its own `showSettings()`
screen (split into TRACKING MODE / ALARMS / DATA cards), reachable via a new
⚙ icon that `base()` adds to the heading row of every top-level tab. Settings
remembers which tab you opened it from and returns you there.
