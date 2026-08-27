# DevTrack v14 fixes — Phase 1: UX Foundation (CR-001 to CR-005)

Implemented against `DevTrack_UI_UX_Change_Requests.md`, following the doc's own
recommended order (V14 = CR-001..CR-005). All changes are in `MainActivity.java`.

## CR-001 — Redesign Add Task dialog ✅ (was not yet done)

The Add Task dialog previously used plain, unlabeled `EditText` fields for date
(`"Date YYYY-MM-DD"`), start/end (`"Start HH:mm"` free text), a manually-typed
"Estimate minutes" number, and free-text Priority/Category boxes where you had to
remember to type e.g. `"high"` or `"LEARNING"` correctly.

`taskDialog()` is now rebuilt to match the schedule-block dialog's pattern
(reusing the same helpers added for that dialog in earlier versions):

- **Task title** — labeled `EditText`, inline "Task title is required" error.
- **Date** — `datePickerField()`, opens the native date picker, displays as
  `28 Aug 2026` (CR-055 groundwork) instead of requiring typed ISO text.
- **Start / End** — `timePickerField()`, native time picker, side-by-side row.
  Inline "Please set both start and end time" / "End time must be after start
  time" errors, matching the schedule dialog's validation.
- **Duration** — new, read-only, calculated automatically from Start/End
  (`recompute()` runs whenever either time picker changes) instead of being a
  separately-typed number that could disagree with the times.
- **Priority** — dropdown (`prioritySpinner`, same LOW/MEDIUM/HIGH/CRITICAL with
  colored dots used elsewhere in the app) instead of free text.
- **Category** — dropdown (`categorySpinner`) instead of free text.
- **Overlap validation** — new `findOverlappingTasks()` checks the task being
  saved against every other task on the same date; if any overlap, Save shows
  "This overlaps with: X (20:00–21:00). Save anyway?" before committing, same
  UX as the existing schedule-block overlap check.
- **Editing preserves data** — the dialog still edits the existing `JSONObject`
  in place and only fills in defaults for genuinely new tasks.

`estimateMin` continues to be the field other screens already read as the
task's planned duration, so the effort/remaining-time tracking elsewhere in the
app (Home, Plan, Progress) keeps working unchanged.

## CR-002 — Dark dialog theme ✅ (already done, verified)

All dialogs already go through the single `dlg()` factory
(`AlertDialog.Builder(this, R.style.AppTheme_Dialog)`), and `AppTheme.Dialog` /
`dialog_bg.xml` / `colors.xml` already define a dark surface, border, and accent
consistent with the rest of the app. Confirmed no call site constructs a raw
`new AlertDialog.Builder(this)` that would fall back to the platform's gray
Material dialog. No changes needed.

## CR-003 — Clarify activity tracking states ✅ (helpers existed but were unused — now wired in)

`scheduleStateLabel()` / `taskStateLabel()` / `stateColor()` already existed in
the codebase with exactly the states this CR asks for (`○ Not started`,
`🔴 Running`, `⏸ Paused`, `✓ Completed`), but **nothing actually called them** —
every row still only showed a Start/Resume/Done *button* with no separate status
line, so a user had no explicit state indicator to read.

- Added `scheduleStateLabelWithMeta()` / `taskStateLabelWithMeta()`, which append
  the relevant duration to the label (`🔴 Running · 24m`, `✓ Completed · 1h 22m`),
  matching the CR-003 example exactly. Added `trackedMsForSchedule()` to sum a
  schedule block's actual recorded session time for that duration.
- Wired a colored state line into every row that shows a plan/task item:
  `addCompactPlanRow` (Home), `addScheduleBlockRow` (Plan), `addTaskRow` (Home),
  and `taskCard` (Plan). All four now read state from the same two functions, so
  Home, Plan, and Progress can no longer disagree about what state an item is in.
- Renamed the schedule row's `"· tracked"` suffix (which describes whether a
  block is *configured* to count toward time tracking) to `"· in time tracking"`
  so it reads as a setting, not a state — it sits right next to the new
  `🔴 Running` / `✓ Completed` state line, so the two ideas needed to look
  visibly different instead of both using the bare word "tracked".

## CR-004 — Improve Start / Stop / Completed UX ✅ (mostly done; running state made more visually distinct)

Start/Stop/Complete, live elapsed time, activity title + category, and
"prevent accidental multiple active timers" (every start path already checks
`getActiveTimer()!=null` and asks you to stop the current one first) were
already implemented in `addTrackingCard`. The one gap: the "RUNNING" indicator
was plain body-colored text, not visually distinct from the rest of the card.

- `"● RUNNING"` → `"🔴 RUNNING"`, colored `#FF6B6B` (the same red used for the
  Running state elsewhere) and bolded, so the active state is unmistakable at a
  glance — consistent with the new state-line coloring from CR-003.

## CR-005 — Improve icon clarity ✅ (already done, verified)

`iconBtn` / `dangerIconBtn` / `tinyIconBtn` / `microIconBtn` already set
`setContentDescription()` and, on API 26+, `setTooltipText()`. High-traffic
buttons (Start, Stop & Save, Complete, Delete, Edit) already use visible text
rather than bare icons. Destructive actions already read "Delete" via
`dangerBtn`/`confirmDelete()` rather than a bare 🗑. No changes needed beyond
what CR-003/CR-004 already added.

## Validation performed

Same constraints as v8–v11: no Android SDK/network in this sandbox, so no real
Gradle/APK build. Checked:
- Full parenthesis/brace/bracket balance across the whole file (matched).
- No leftover references to the old `taskDialog` fields (`estE`, `remE`,
  `priorityE`, `categoryE`) anywhere else in the file.
- Manual end-to-end read-through of `taskDialog`, `findOverlappingTasks`,
  `scheduleStateLabelWithMeta`, `taskStateLabelWithMeta`, `trackedMsForSchedule`,
  and every call site that now renders a state line.

Please run a real build via your CI/Android Studio before installing.
