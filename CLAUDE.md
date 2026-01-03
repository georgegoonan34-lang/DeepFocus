# DeepFocus - Distraction Blocking App for Android

## Project Overview
DeepFocus is a native Android application designed to permanently block distracting apps and create an undistracting phone experience. Similar to Cold Turkey for desktop, but built specifically for Android (Samsung Galaxy S23 and other Android devices).

## Tech Stack
- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: MVVM with Clean Architecture
- **UI**: Jetpack Compose (Material 3)
- **DI**: Hilt (Dagger)
- **Database**: Room
- **Async**: Kotlin Coroutines + Flow
- **Testing**: JUnit 5, Espresso, MockK

---

# CRITICAL RULES

## Git Workflow - MANDATORY
- **ALWAYS commit and push after ANY code changes**
- **NEVER skip git workflow** - Every change must be committed and pushed
- Format: `git add . && git commit -m "type: description" && git push`
- Commit types: `feat:`, `fix:`, `refactor:`, `docs:`, `style:`, `chore:`, `test:`

## Fixing Features - NON-NEGOTIABLE
- **NEVER add error messages as a substitute for fixing bugs**
- **NEVER add debug logging instead of fixing** - Fix the actual issue
- **NEVER manually insert data as the solution** - Fix the code so it works automatically
- **ONE ATTEMPT, ONE FIX** - Ultrathink, find root cause, fix it properly
- If you lack context, ASK or use tools to gather it

## Android Security Rules
- **Request only necessary permissions** - Don't over-request
- **Handle permissions gracefully** - Always check before using
- **Never store sensitive data in plain text** - Use EncryptedSharedPreferences
- **Validate all user inputs** - Prevent injection attacks

---

# PLUGINS & SKILLS ACTIVATION

## When to Use Each Plugin

### `/frontend-design` - UI Development
**Trigger phrases**: "create UI", "build component", "design screen", "make it look", "improve the design"
**Use for**:
- Creating new Compose screens/components
- Improving visual design
- Building Material 3 components
- Any user-facing interface work

### `/feature-dev` - Feature Development
**Trigger phrases**: "build feature", "implement", "add functionality", "create flow", "new feature"
**Use for**:
- Multi-file feature implementation
- New Android services
- Integration work
- Any feature touching multiple parts of codebase

### `/code-review` - Code Quality
**Trigger phrases**: "review code", "check PR", "audit", "review changes"
**Use for**:
- Before pushing major changes
- Reviewing refactored code
- Security checks

## MCP Tools - When to Use

### GitHub MCP
**Use for**: Issue management, PR operations, branch management
- Creating issues for bugs found
- Managing branches

### Playwright MCP
**Use for**: Testing web-based admin panels (if any)

### Context7 MCP
**Use for**: Looking up Android/Kotlin documentation
- Jetpack Compose docs
- Android API references
- Kotlin language features

---

# CODE STYLE

## General Principles
- Clean, simple, modular code
- Short sentences in comments
- Explain thought process, not syntax
- NEVER delete existing comments (update if obsolete)
- Follow Android best practices and Google's style guide

## Kotlin
- Use Kotlin idioms (apply, let, also, run, with)
- Prefer immutability (val over var)
- Use data classes for models
- Use sealed classes for state management
- Explicit nullability handling (avoid !!)
- Extension functions for reusable logic

## Jetpack Compose
- Single responsibility composables
- Hoist state to appropriate level
- Use remember and derivedStateOf properly
- Preview annotations for all UI components
- Use Material 3 theming system

## Architecture
```
app/
├── data/
│   ├── local/          # Room database, DAOs
│   ├── repository/     # Repository implementations
│   └── model/          # Data models
├── domain/
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic use cases
├── presentation/
│   ├── ui/
│   │   ├── screens/    # Compose screens
│   │   ├── components/ # Reusable composables
│   │   └── theme/      # Material 3 theme
│   └── viewmodel/      # ViewModels
├── service/            # Android services
│   ├── blocking/       # App blocking service
│   ├── accessibility/  # Accessibility service
│   └── device_admin/   # Device admin receiver
├── di/                 # Hilt modules
└── util/               # Utility classes
```

---

# CORE FEATURES

## 1. App Blocking System
**Priority: HIGH**
- Block apps permanently or on schedule
- Prevent uninstallation of DeepFocus
- Whitelist essential apps (Phone, Messages, etc.)
- Multiple blocking profiles (Work, Sleep, Focus)

### Implementation
- Use `DevicePolicyManager` for device admin capabilities
- Use `AccessibilityService` to detect and block app launches
- Use `UsageStatsManager` to track app usage
- Foreground service for persistent blocking

## 2. Undistracting UI Mode
**Priority: HIGH**
- Grayscale mode toggle
- Hide notification badges
- Simplified home screen launcher
- Remove app suggestions

### Implementation
- `AccessibilityService` for UI modifications
- Custom launcher activity (optional)
- Secure Settings modifications (with ADB grant)

## 3. Block Bypass Prevention
**Priority: CRITICAL**
- Password/PIN protection for settings
- Time-locked blocks (can't disable for X hours/days)
- No easy uninstall (device admin)
- Anti-circumvention measures

### Implementation
- Device Admin for uninstall protection
- Encrypted settings storage
- Hash-based password verification
- Cooldown timers for changes

## 4. Usage Statistics
**Priority: MEDIUM**
- Track time spent in apps
- Daily/weekly reports
- Screen time goals
- Streak tracking

### Implementation
- `UsageStatsManager` for usage data
- Room database for historical data
- WorkManager for daily aggregation

## 5. Focus Sessions
**Priority: MEDIUM**
- Pomodoro-style focus timers
- Block everything except whitelist during session
- Break reminders
- Session history

---

# ANDROID PERMISSIONS REQUIRED

```xml
<!-- Core permissions -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- For accessibility service -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- For device admin -->
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />

<!-- Optional: Grayscale mode -->
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
```

**Note**: `WRITE_SECURE_SETTINGS` requires ADB grant:
```bash
adb shell pm grant com.deepfocus.app android.permission.WRITE_SECURE_SETTINGS
```

---

# DATABASE SCHEMA

## Room Entities

### BlockedApp
```kotlin
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val blockType: BlockType, // PERMANENT, SCHEDULED, TEMPORARY
    val scheduleStart: Long?, // For scheduled blocks
    val scheduleEnd: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
```

### UsageRecord
```kotlin
@Entity(tableName = "usage_records")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val date: Long, // Day timestamp
    val totalTimeMs: Long,
    val launchCount: Int
)
```

### FocusSession
```kotlin
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val plannedDurationMs: Long,
    val actualDurationMs: Long?,
    val completed: Boolean
)
```

### BlockProfile
```kotlin
@Entity(tableName = "block_profiles")
data class BlockProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean,
    val blockedPackages: List<String>, // TypeConverter
    val schedule: Schedule? // TypeConverter
)
```

---

# DESIGN SYSTEM

## Color Palette (Calm Focus Theme)
```kotlin
// Light Theme
val md_theme_light_primary = Color(0xFF006B5E)        // Teal - calming
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF7AF8E2)
val md_theme_light_secondary = Color(0xFF4A635D)
val md_theme_light_background = Color(0xFFFAFDFB)
val md_theme_light_surface = Color(0xFFFAFDFB)
val md_theme_light_error = Color(0xFFBA1A1A)

// Dark Theme
val md_theme_dark_primary = Color(0xFF5CDBC6)
val md_theme_dark_onPrimary = Color(0xFF003730)
val md_theme_dark_primaryContainer = Color(0xFF005046)
val md_theme_dark_secondary = Color(0xFFB1CCC4)
val md_theme_dark_background = Color(0xFF191C1B)
val md_theme_dark_surface = Color(0xFF191C1B)

// Status Colors
val blocked_red = Color(0xFFE53935)
val allowed_green = Color(0xFF43A047)
val warning_amber = Color(0xFFFFA000)
```

## Typography
- Use system font for readability
- Headlines: Bold, larger sizes
- Body: Regular weight, comfortable reading size
- Monospace for timers/statistics

## Design Principles
1. **Minimal & Calm**: The app itself should not be distracting
2. **Clear Status**: Always show what's blocked/allowed
3. **Friction for Changes**: Make it intentionally difficult to disable blocking
4. **Encouraging**: Positive reinforcement for focus time

---

# THINKING TRIGGERS

Use these phrases for complex tasks:
- `"think"` - Basic reasoning
- `"think hard"` - More analysis
- `"think harder"` - Deep analysis
- `"ultrathink"` - Maximum reasoning (architecture, complex Android APIs)

---

# DEVELOPMENT WORKFLOW

## Initial Setup
1. Create project in Android Studio
2. Configure Gradle with all dependencies
3. Set up Hilt dependency injection
4. Create Room database
5. Implement core services

## Testing on Device
```bash
# Install debug build
./gradlew installDebug

# Grant special permissions via ADB
adb shell pm grant com.deepfocus.app android.permission.WRITE_SECURE_SETTINGS

# View logs
adb logcat -s DeepFocus
```

## Building Release
```bash
./gradlew assembleRelease
```

---

# KNOWN CHALLENGES

## Samsung-Specific
- Samsung has aggressive battery optimization - need to guide users to disable
- One UI may interfere with accessibility services
- Samsung Knox may block some device admin features

## Android Restrictions
- Background service limitations on Android 12+
- Accessibility service may be killed by system
- Device admin removal requires user action

## Solutions
- Use WorkManager for periodic checks
- Implement service restart on kill
- Guide users through Samsung battery optimization settings
- Use foreground service with persistent notification

---

# RESOURCES

## Android Documentation
- [Device Admin](https://developer.android.com/guide/topics/admin/device-admin)
- [Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/service)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

## Similar Apps (Research)
- Cold Turkey Blocker (Desktop)
- Freedom
- AppBlock
- Stay Focused

---

# COMMANDS

## Useful ADB Commands
```bash
# List installed packages
adb shell pm list packages

# Get app usage stats
adb shell dumpsys usagestats

# Check device admin
adb shell dumpsys device_policy

# Force stop app
adb shell am force-stop <package>

# Check accessibility services
adb shell settings get secure enabled_accessibility_services
```
