# DevTrack — Native Android Java Test Build

This is the native Java/XML implementation of DevTrack.

## Stack

- Java 17
- Android SDK
- XML layouts/views
- Gradle
- Native Android AlarmManager + NotificationManager
- SharedPreferences + JSON for offline data
- No AI
- No backend
- No Capacitor

## Application identity

Display name: DevTrack

Application ID: `com.javaroadmap.tracker`

The application ID is intentionally kept compatible with the existing app.

## Build on GitHub

Push this repository to GitHub. The included GitHub Actions workflow builds:

`app/build/outputs/apk/debug/app-debug.apk`

The artifact is named `devtrack-debug-apk`.

## Current native Java MVP

Implemented:

- Home dashboard
- Roadmap with topic percentages
- Existing Java backend roadmap data
- Roadmap topic completion
- Roadmap JSON import/export
- Tasks
- Estimate / remaining / actual effort
- Planner
- Task timer
- Study sessions
- Daily study time
- Progress
- Full JSON backup/restore
- Reset data
- Native Android reminder notifications
- Background alarm scheduling
- Alarm rescheduling after device boot
- Test alarm
- No AI code

This is deliberately a clean native baseline for comparison against the existing Capacitor implementation.

## Important Android behavior

Android 13+ requires notification permission. Exact alarms can also be restricted by Android; the app falls back to `setAndAllowWhileIdle` if exact-alarm permission is unavailable.

The notification channel is `task_reminders_v2`.

## Reliability / polish update

- Defensive startup and roadmap validation
- Safe handling when imported roadmap data is missing or malformed
- Startup recovery screen instead of a null-data crash
- Branded DevTrack launcher icon with adaptive Android icon support
- Native reminder channel uses the device alarm ringtone where available
- Cleaner native bottom navigation styling

## Daily schedule JSON

DevTrack supports importing and exporting a daily schedule from **Plan → Import Daily Schedule JSON**.
Each block can define its own `color` and alarm configuration:

```json
{
  "id": "java",
  "title": "Java Backend",
  "start": "20:00",
  "end": "21:30",
  "category": "LEARNING",
  "color": "#7C5CFC",
  "track": true,
  "days": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "alarm": {
    "enabled": true,
    "minutesBefore": 10,
    "sound": "alarm"
  }
}
```

The schedule UI uses the block color as a visual timeline accent. Alarm settings are applied when the schedule is imported. Alarms use the device alarm ringtone through the native Android notification channel and are rescheduled after reboot.
