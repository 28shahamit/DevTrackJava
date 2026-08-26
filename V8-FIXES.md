# DevTrack v8 fixes (v2.1.1 review pass)

## Additional fixes found and applied during review

1. **Hardware Back button trap on the Learn tab** — `page==1` was true both for the
   Learn list and the roadmap detail screen, and the old check also relied on
   `roadmap!=null` (which is basically always true). That meant pressing the
   system Back button while simply browsing the Learn list re-rendered Learn
   instead of exiting/backgrounding the app, silently swallowing every back
   press. Added a dedicated `roadmapDetailOpen` flag that is only true while a
   roadmap's phase/topic detail view is open, and wired it into every screen
   transition (`showHome`, `showLearn`, `showRoadmap`, `showPlan`,
   `showProgress`). Back now only intercepts on the actual detail screen.

2. **Roadmap JSON import was over-restrictive** — `Import Roadmap JSON` used to
   hard-reject any file whose `format` field wasn't exactly
   `trackit-roadmap` or `devtrack-roadmap`, so a roadmap exported from
   somewhere else (or hand-written) with the same shape but no/different
   format tag would fail with "Unsupported roadmap format." Import now just
   checks for a `phases` array (the actual structural requirement) and lets
   `normalizeRoadmap` fill in `format`/`id`/`name`/`icon` as before. This is
   what makes "import JSON for any roadmap" actually work end-to-end —
   Java Backend, DSA, or a custom roadmap all go through the same generic
   importer already wired to the Learn tab.



## Fixed in one pass

1. **Roadmap navigation**
   - Roadmap detail has a visible Back button.
   - Android system Back also returns from roadmap detail to Learn.

2. **Edit controls**
   - Large Edit buttons were replaced by compact pencil controls in roadmap cards, phases, roadmap items, schedule rows and task rows.
   - Accessibility descriptions are included for the icon controls.

3. **Today's Plan is single-source**
   - The Plan tab is the only place that renders the complete daily plan/timeline.
   - Home no longer duplicates the complete plan.
   - Home only provides a shortcut to open today's plan.
   - Tracking remains separate from planning.

4. **Tracked-activity layout**
   - Today's tracked activities use wrap-content rows/card height.
   - The previous giant empty vertical area is removed.
   - Empty state is compact and says `No activities tracked today.`

5. **Dynamic roadmaps**
   - Java Backend and DSA use the same generic roadmap engine.
   - Roadmap names/descriptions/icons are editable.
   - Imported placeholder names can be replaced by metadata titles.
   - Progress labels adapt to `topics` or `questions`.

6. **DSA support**
   - Bundled DSA roadmap contains 421 questions across 15 categories.
   - Question `url` is supported for direct opening.
   - `links[]` supports multiple resources.
   - Tags are displayed and editable.
   - DONE / SAVE / LATER / TRACK remain supported.

7. **Progress isolation**
   - Completion is stored by roadmap ID.
   - Backend completion cannot increase DSA completion and vice versa.
   - Progress totals come from the actual imported roadmap data.
   - Time tracking remains independent of roadmap completion.

8. **Timer/session behavior**
   - Stop & Save creates a resumable session.
   - Resume starts the same activity again.
   - Complete closes the activity.
   - Zero-duration sessions are discarded.
   - Daily tracked activities are grouped by activity.

## Validation performed

- Java source delimiter balance checked.
- All bundled JSON files parse successfully.
- DSA JSON verified at **421 questions**, with **53 questions containing links**.
- No Home call remains that renders the complete `Today's Plan` card.
- Compact Edit controls are present in the affected row types.

## Build

This checkout does not contain a Gradle wrapper and the current environment does not have Gradle/Android SDK installed, so an APK build could not be executed here. The project remains configured for the existing GitHub Actions Android build workflow.
