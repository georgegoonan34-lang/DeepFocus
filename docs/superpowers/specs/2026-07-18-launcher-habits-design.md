# Launcher Redesign + Habit Tracker — Design

Date: 2026-07-18
Status: Approved (user waived detailed section review; decisions confirmed via Q&A)

## Goal

Bring back DeepFocus as the phone's home screen, replacing the removed
text-only alphabetical launcher with a two-level layout that is faster to
navigate but still calm. Add a simple habit tracker one swipe away.

## Decisions (from user Q&A)

1. **Home screen role**: DeepFocus is the HOME launcher again (as before
   commit debb07f), not just a screen inside the app.
2. **Important apps**: user pins/unpins apps on the phone by long-pressing
   them. Pins persist in the local database.
3. **Habit tracker**: lives inside the launcher as a swipe page, not a
   separate app.
4. **Habit grid**: columns are the current week Mon–Sun; chevrons navigate
   to past weeks; past days (including yesterday) stay tappable; future
   days are disabled.
5. **Architecture**: single LauncherActivity hosting swipeable pages,
   Room + repository + ViewModel (Hilt), matching project MVVM direction.

## UI Structure

One activity, `HorizontalPager` with three pages, starting on Home:

```
[0 Habits]  ←→  [1 Home]  ←→  [2 All apps]
```

- **Home**: big clock + date (as the old launcher), then a 4-column grid
  of pinned apps with real icons and labels. Bottom row: "Other apps ›"
  which animates to the All Apps page. Long-press a pinned icon to unpin.
  Blocked pinned apps render dimmed and unclickable.
- **All apps**: alphabetical list with icons; non-blocked apps first,
  blocked apps dimmed at the bottom with their status label (same ordering
  rules as the old launcher). Tap launches; long-press pins/unpins.
- **Habits**: table with day columns (M–S with date numbers) and habit
  rows. Tap a cell to toggle. Today's column highlighted; future cells
  disabled. Chevrons step weeks; forward chevron disabled on the current
  week. "+" adds a habit (dialog); long-press a habit name to rename or
  delete.
- Back button: returns to Home page if elsewhere, otherwise no-op (it is
  the home screen).
- Style: existing pure black/white theme; app icons are the only color.

## Data (Room, new database — none existed before)

`DeepFocusDatabase` v1:

- `pinned_apps(packageName PK, position, pinnedAt)`
- `habits(id PK autogen, name, position, createdAt)`
- `habit_checks(habitId, epochDay)` composite PK, FK → habits ON DELETE
  CASCADE. `epochDay` = `LocalDate.toEpochDay()`.

DAOs → thin repositories (`PinnedAppsRepository`, `HabitsRepository`) →
`LauncherViewModel` (installed apps + pins) and `HabitsViewModel`
(week window, checks, habit CRUD). No domain/usecase layer — YAGNI for
this size.

## Behaviors kept from the old launcher

- Start BlockingService (foreground) + ScreenTimeService in onCreate.
- Refresh the installed-app list on ON_RESUME.
- Blocked = `BlockedApps.isBlocked(pkg)` or scheduled-out via
  `ScheduledApps`; blocked rows show a status label and don't launch.
  The accessibility service remains the enforcement backstop.

## Manifest

Restore the pre-debb07f launcher entry (MAIN + HOME + DEFAULT + LAUNCHER,
singleTask, stateNotNeeded) pointing at the new activity. SetupActivity
keeps its own LAUNCHER entry as the setup/status screen; its manifest
comment is updated since "the custom launcher was removed" is obsolete.

## Error handling

- Pinned app uninstalled → filtered out of Home (pin row remains until
  unpinned; harmless).
- Habit toggles are idempotent (insert-or-ignore / delete).
- Icon loading happens off the main thread in the ViewModel.

## Testing

Manual on-device verification (no test infrastructure exists in the repo):
set as default launcher, pin/unpin, launch apps, blocked apps stay
blocked, habit check-off persists across reinstall.
