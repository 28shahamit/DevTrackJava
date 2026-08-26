# DevTrack v8 — Dynamic Roadmaps + Unified Plan + Real Productivity Tracking

DevTrack is a local-first Android productivity and learning tracker.

## v7 fixes

### Roadmaps
- Multiple independent roadmaps are supported.
- Java Backend and DSA use the same generic roadmap engine.
- Roadmaps have editable name, icon and description.
- Import/export roadmap JSON.
- Each roadmap has independent completion/progress.
- Phases/categories can contain normal learning topics or question-style items.

### DSA
The bundled DSA roadmap is question-oriented with 15 categories and 421 practice questions:

Array, Matrix, String, Search and Sort, Linked List, Binary Trees, BST, Greedy, BackTracking, Stacks and Queues, Heap, Graph, Trie, Dynamic Programming and Bit Manipulation.

Each question supports:
- `url` — primary coding/problem link
- `links[]` — optional multiple resources
- DONE
- SAVE
- LATER
- TRACK

### Productivity tracking
- Daily schedule is shown directly in Today's Plan.
- Automatic mode selects the current/next scheduled activity.
- Manual mode lets the user choose the activity.
- Start → live timer → Stop & Save or Complete.
- Stop & Save keeps the activity resumable.
- Resume continues the same activity and combines sessions.
- Complete closes the activity and removes Resume.
- Zero-duration sessions are not stored/displayed as `0m` records.
- Today's tracking is grouped by activity instead of showing duplicate cards.
- Progress counts only real saved sessions.


## v8 fixes

### UI and navigation
- Added a visible Back button on roadmap detail screens; Android back also returns to Learn.
- Replaced large Edit buttons in roadmap, phase, schedule and task rows with compact pencil controls.
- Today's full plan is shown in one place only: the Plan tab is the source of truth. Home no longer duplicates the full daily plan.
- Home provides a single shortcut to open today's plan instead of rendering a second copy.

### Tracking list layout
- Today's tracked activities now use content-sized rows/card height.
- Removed the large empty vertical area that could appear between tracked activities.
- Empty state now reads `No activities tracked today.` and the card ends immediately.

### Roadmap data
- Imported roadmaps with an `Imported Roadmap` placeholder name now use their JSON metadata title when available.
- Roadmap progress labels are dynamic (`topics` vs `questions`) instead of assuming every roadmap is a topic roadmap.
- DSA tags are displayed and editable in addition to subtopics.

## Roadmap JSON

Minimum structure:

```json
{
  "format": "devtrack-roadmap",
  "version": 1,
  "id": "dsa",
  "name": "DSA",
  "icon": "🧠",
  "description": "Interview preparation",
  "kind": "questions",
  "phases": [
    {
      "id": "array",
      "number": 1,
      "title": "Array",
      "color": "#E67E5F",
      "topics": [
        {
          "id": "array-001",
          "title": "Reverse the array",
          "difficulty": "EASY",
          "url": "https://example.com/problem",
          "links": [
            {"title": "LeetCode", "url": "https://example.com"},
            {"title": "Explanation", "url": "https://example.com"}
          ]
        }
      ]
    }
  ]
}
```

The app accepts either a single `url` or multiple `links`.

## Daily schedule JSON

The existing `devtrack-daily-schedule` format remains supported, including `color`, `track`, `days` and alarm settings.

## Build

The project intentionally does not include generated APK/build output, and this checkout does not include a Gradle wrapper.

Push to `main` (or run the workflow manually) and GitHub Actions will build the debug APK automatically — see `.github/workflows/build-apk.yml`. Download it from the Actions tab under the `app-debug-apk` artifact.

To build locally, install Gradle 8.7+ and the Android SDK (platform 35, build-tools 35.0.0), then run:

```bash
gradle assembleDebug
```
