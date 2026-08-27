# DevTrack v11 fixes

## 1. Missing resource: `nav_item_tint.xml` → `@color/nav_selected` / `@color/nav_unselected`

Those two colors didn't exist anywhere in `colors.xml`, and there was no
`nav_item_tint.xml` color-state-list either — that's what was breaking your build.
Fixed by:

- Adding `nav_selected` (`#4F8EF7`, matches the app's accent color) and
  `nav_unselected` (`#8890A0`) to `res/values/colors.xml`.
- Adding `res/color/nav_item_tint.xml`, a color-state-list that resolves to
  `nav_selected` when a view is `state_selected="true"` and `nav_unselected`
  otherwise.
- Wiring `NavButton` style's `android:textColor` to `@color/nav_item_tint` instead
  of the flat `@color/text` it was using before.
- Actually driving that selected state from code: added `setNavSelected(int id)` in
  `MainActivity`, called from `showHome()`, `showLearn()`, `showPlan()`,
  `showProgress()`, and `showRoadmap()` (which keeps the Learn tab highlighted since
  that's where roadmaps are reached from). Previously the bottom nav never showed
  which tab was active at all — now it does, as a side effect of actually fixing the
  missing resource properly instead of just stubbing empty colors in.

## 2. Roadmap topic rows are now collapsible

This is the bigger change. Each topic is now a single compact heading row by
default:

`[▸/▾ expand] [checkbox] [title, truncates to one line] [★/↺ badge if set] [▶ track] [⋮ more]`

Tapping the chevron (or the title itself) expands the row to reveal, directly
underneath: priority/difficulty/interview meta, subtopics, tags, and an "🔗 Open
resource" link if the item has one — the same details that used to always take up
space, now shown only when you actually want them. Collapsed is the default, so a
phase with 49 items now shows 49 one-line rows instead of 49 multi-line cards.

- Buttons are smaller again: `microIconBtn` went from 28dp/12sp to 24dp/11sp with
  1dp padding. Phase-level Edit/Delete icons got the same treatment
  (`dangerMicroIconBtn` added alongside).
- Save (★) and Later (↺) moved into the "⋮" menu (Save for later / Mark for later,
  next to Edit/Delete) since they're not something you tap every time you look at a
  row — a small colored badge next to the title still shows at a glance whether
  either is set.
- Expand/collapse state is kept in memory for the session (not written into the
  roadmap JSON), so it doesn't bloat your saved roadmap data with UI state.

## Validation performed

No Android SDK/network in this sandbox, so still no real build here. Checked with a
full bracket/delimiter balance pass across the whole file, confirmed
`openTopicLinks` (used by the new "Open resource" line) still exists, and manually
read through `addTopicRow`, `topicOverflowMenu`, `setNavSelected`, and the four
`showX()` call sites end to end. Please run an actual build before installing —
this is a meaningful structural change to the roadmap screen.
