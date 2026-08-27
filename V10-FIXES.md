# DevTrack v10 fixes

## 1. Topic rows: everything collapsed into the heading row

Previously each roadmap topic was: checkbox+title row, then a separate row of
action buttons underneath (☆ save / ↺ later / ▶ track / ⋮ more). That second row
is gone. Now it's a single row:

`[checkbox] [title, flexes] [🔗 if it has a link] [☆/★] [↺] [▶] [⋮]`

- Buttons are much smaller now (`microIconBtn`: 28dp, 2dp padding, 12sp icon) —
  noticeably lighter than the previous `tinyIconBtn` (38dp) pass.
- ★ save / ↺ later tint gold/cyan when active instead of needing a second badge
  next to the title.
- Edit and Delete live in the "⋮" menu (unchanged from before) — they're used far
  less often than Save/Later/Track, so they don't need dedicated on-row buttons.
- Title truncates to one line with an ellipsis if it's long, so it never pushes the
  action icons off the edge of the screen.

Net effect: each topic is one row + up to 3 short meta/tag lines, instead of two
full button rows — meaningfully more topics fit on screen per scroll.

## 2. "Edit roadmap item" dialog — dropdowns instead of free text

- **Priority** is now a proper dropdown (`prioritySpinner`): LOW / MEDIUM / HIGH /
  CRITICAL, each with a colored dot matching how priority is shown elsewhere in the
  app, instead of a free-text field where you had to remember to type "high".
- **Difficulty** is the same treatment (`difficultySpinner`): UNSET / EASY / MEDIUM
  / HARD, colored dot, dropdown instead of a hint like "Difficulty: EASY / MEDIUM /
  HARD" that you had to type into by hand.
- Priority and Difficulty sit side-by-side in one row instead of stacked, so the
  dialog is shorter overall.
- Subtopics / Tags / Primary link / Extra links stay as text fields — those are
  genuinely free-form lists, not a fixed set of options, so a dropdown doesn't fit
  them.

## Validation performed

Same as v9: no Android SDK/network in this sandbox, so no APK build. Checked with a
full bracket/delimiter balance pass across the whole file and a manual read-through
of the changed methods (`addTopicRow`, `editTopicDialog`, `prioritySpinner`,
`difficultySpinner`, `dotAdapterRow`) end to end. Worth a real build via your CI
before shipping.
