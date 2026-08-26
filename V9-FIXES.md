# DevTrack v9 fixes

All changes are in `MainActivity.java`. No new files/screens were needed — everything
uses the same programmatic-view approach as the rest of the app.

## 1. Delete, everywhere data can be created (the critical bug)

Added a 🗑 delete icon next to every ✎ edit control, plus a confirmation dialog
("Delete X? ... will be permanently removed.") before anything is actually removed:

- **Schedule blocks** — `deleteScheduleBlock()`. Removes the block, cancels its alarm
  (`ReminderReceiver.cancel`), saves state. This fixes the stray "abccccccc" block —
  it's now removable from the Plan tab.
- **Tasks** — `deleteTask()`, on both the Home shortcut row and the Plan tab task card.
  Clears the active timer first if it was tracking the deleted task.
- **Roadmaps** — `deleteRoadmap()` on the roadmap card in Learn. Removes its stored
  completion data too. If you delete the roadmap you're currently viewing, it falls
  back to another roadmap (or to the empty "import a roadmap" state if none are left).
- **Phases** — `deletePhase()` next to "✎ Edit phase". Removes the phase and clears
  completion for every item that was inside it.
- **Roadmap items/topics/questions** — `deleteTopic()` next to "✎ Edit roadmap item".
  Handles both the flat `topics` array and the nested `categories[].topics` shape.

## 2. Add/Edit schedule block dialog — rebuilt UX

- **Time fields** are now tap-to-open native `TimePickerDialog`s instead of raw text
  (`timePickerField()`). No more mistyped "2000" or "8:0".
- **Color** is a swatch picker (`colorSwatchRow()`) — 12 preset colors, tap to select,
  selected swatch gets a white ring. No more raw hex text box.
- **Days** are toggle chips (Mon–Sun, `dayChipsRow()`) instead of a comma-separated
  text field. Empty selection still means "every day", same as before.
- **Alarm minutes-before** is now a dropdown (`alarmMinutesSpinner()`: 5/10/15/30/45/60)
  instead of a bare number field, and it only shows once the Alarm checkbox is on.
- **Category dropdown** now shows a colored dot per category, both in the closed
  spinner and the open list (`categoryRowView()` / `categoryAdapter()`).
- **Validation** — the Save button no longer auto-dismisses the dialog. It checks:
  - title isn't empty (inline red error under the field)
  - both start and end are set, and end is after start (inline red error)
  Invalid input keeps the dialog open with the error shown instead of silently failing
  or saving bad data. (Free-text hex is gone entirely, so that failure mode no longer
  applies.)

## 3. Overlap detection

- `findOverlappingBlocks()` checks the block being saved against every other block
  that shares at least one day (blank days = every day counts as shared) for a
  start/end time intersection.
- If it finds one, Save shows "This overlaps with: X (20:00–21:00), ... Save anyway?"
  before committing — you can back out or confirm.
- Any block currently overlapping another is flagged inline in the Plan list with
  "⚠ Overlaps another block" under its title, plus a subtle divider under the row, so
  the conflict is visible without opening anything.

## 4. Schedule list UX

- **Untitled blocks** now render as a dimmed "Untitled block" placeholder instead of
  blank space, so an empty-title row is never invisible/unreachable.
- **Long-press** on a schedule row opens a quick action sheet (Start / Edit / Delete)
  so common actions don't require opening the full edit dialog first. (True
  swipe-to-dismiss would need moving the list to a `RecyclerView` + `ItemTouchHelper`,
  which is a bigger structural change than this pass covers — long-press is the
  equivalent quick-action affordance within the current plain-`LinearLayout` list.)
- Category labels across Home, Plan, and schedule rows now show a small colored dot
  matching the category, for quicker visual scanning.

## 5. UI density pass (buttons too big, topics hard to scan)

- **Roadmap topic rows rebuilt** — each item used to be a checkbox+"DONE" row, then a
  Save/Later pill row, then a Track pill + edit/delete icon row: 3 rows of full-width
  buttons per topic. Now it's: checkbox + title (with a small ★/↺ badge if
  saved/marked later) → meta line → one compact row of small icon buttons
  (🔗 open / ☆ save / ↺ later / ▶ track / ⋮ more). Edit and Delete moved into the
  "⋮" overflow menu since they're used far less often than Save/Later/Track. This
  turns a ~3-line-of-buttons card into one, so far more topics fit on screen and it's
  actually scannable — this is what image 3 (Roadmap - Experienced Track) was
  showing.
- **Buttons shrunk app-wide** — base button height 44dp→40dp, icon buttons 44dp→40dp,
  padding tightened to match, and a new smaller `tinyIconBtn` (38dp) for dense rows
  like topic actions. Card padding and margins tightened slightly too. Still within
  reasonable tap-target size, just less visually heavy.
- Saved/Later state now shows as a small colored ★/↺ badge next to the title instead
  of only being visible by re-reading button label text.


- Delimiter/bracket balance checked across the whole file (parens, braces, brackets
  all match).
- Manual review of every new lambda for effectively-final capture correctness.
- No Gradle/Android SDK is available in this environment (as in v8), so an actual
  APK build could not be run here — the project still targets the existing GitHub
  Actions Android build workflow for that.
