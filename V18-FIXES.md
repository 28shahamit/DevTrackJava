# DevTrack v18 — Roadmap-to-Schedule Linking with Time Tracking

## Problem

A 1:1 auto-complete between a schedule block and a roadmap topic ("finish this
60-minute block → topic is 100% done") is wrong for anything that takes more
than one session, and it throws away exactly the data worth keeping: how long
things actually took, and which parts got covered.

## What changed

All changes are in `MainActivity.java`.

### 1. Roadmap topics — and now subtopics — can carry an optional time estimate
`editTopicDialog()` has "Estimated time to master (minutes, optional)" for the
topic (`estimateMin`), same as before. The **Subtopics** field now also
accepts a per-subtopic estimate: `filter:20, map:20, reduce` — `name:minutes`
where minutes is given, plain `name` where it isn't. Subtopics are stored as
either a plain string (no estimate) or `{"name":"filter","estimateMin":20}` —
both forms read interchangeably everywhere (`subName()` / `subEstimate()`
helpers), so existing plain-string subtopic lists keep working untouched.

### 2. Schedule blocks link to a roadmap topic explicitly
`scheduleBlockDialog()` gets a "Link to roadmap topic (optional)" picker
(`pickRoadmapThenTopic()` → `pickTopicWithinRoadmap()`). It stores
`linkedRoadmapId` + `linkedTopicId` on the block. This is a deliberate choice
over fuzzy title-matching ("Backend: Stream API with Collections" vs.
"Streams API" would never match reliably) — you pick the topic once when you
set up the block.

### 3. Session stop updates the linked topic, not a binary flag
`stopActiveTimer()` now checks `linkedTopicId` the same way it already checked
`taskId` for tasks: it adds the session's minutes to the topic's `actualMin`
and recomputes `remainingMin` against `estimateMin`. No auto-complete.

### 4. End-of-session confirmation states remaining/overbooked time explicitly
A schedule block's own start/end clock times are never touched by roadmap
estimates — they're independent, as before. What changed is what the
confirmation says. `showSessionReviewDialog()` now leads with:
- `"Xm remaining of Ym estimate"` (green) if under, or
- `"Xm over the Ym estimate"` (amber) if over
- if the topic has no estimate: `"Xm logged so far · no estimate set"`

Then, if the topic has subtopics, a checklist to confirm which ones you
actually covered — each showing its own `logged/estimate` if it has one — and
finally an explicit **✓ Completed / ○ In progress / Skip** choice. This *is*
the "pop up to confirm while ending the task" — nothing is auto-decided; you
confirm status and coverage every time a linked session ends.

### 5. Progress bars and a Time vs Estimate report
- Roadmap topic rows now show `actual / estimate` with a progress bar when an
  estimate is set (`addTopicRow`), and expanded topics show logged minutes
  per subtopic.
- Progress tab has a new **⏱ Time vs Estimate** button → `showTimeReport()`:
  every topic across every roadmap that has time logged or estimated,
  sorted worst-overrun-first, with its subtopic time breakdown.

### 6. Import daily schedule applies pre-set links automatically — no prompt
`onActivityResult()` for `REQ_SCHEDULE_IMPORT` replaces the schedule as
before. If a block in the imported JSON already has `linkedRoadmapId` +
`linkedTopicId`, that's it — it's linked, silently, no dialog. Blocks with no
link fields stay unlinked. Nothing is required or forced; the import toast
just reports how many blocks came in pre-linked.

This means the linking decision happens once, when you (or a script/LLM)
author the daily-schedule JSON — not as an interactive step you have to click
through every time you import.

## Data format additions

Roadmap topic (optional, additive):
```json
{
  "id": "streams",
  "title": "Streams API",
  "estimateMin": 240,
  "actualMin": 95,
  "remainingMin": 145,
  "subtopics": [
    { "name": "filter", "estimateMin": 20 },
    "map"
  ],
  "subtopicMinutes": { "filter": 20, "map": 35 }
}
```

Schedule block (optional, additive):
```json
{
  "id": "backend",
  "title": "Backend: Stream API with Collections",
  "linkedRoadmapId": "java-backend",
  "linkedTopicId": "streams"
}
```

Both are backward compatible — existing roadmap/schedule JSON files import
and work exactly as before with no link and no estimate.
