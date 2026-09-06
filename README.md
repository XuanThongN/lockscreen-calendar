# LockCal: All-in-One Android Lock Screen Calendar

**LockCal** is a native Android application and companion utility designed to display all your synchronized calendars (Google Calendar, Microsoft Outlook, Apple iCloud, and custom iCal feeds) directly on your Android **Lock Screen**.

---

## 🌟 Key Features

1. **Persistent Lock Screen Notification Service**:
   - Uses `NotificationCompat.VISIBILITY_PUBLIC` and `PRIORITY_DEFAULT/HIGH` to display your agenda directly on your lock screen without unlocking your phone.
   - **Compact View**: Displays the immediate upcoming event and countdown badge.
   - **Expanded View**: Shows upcoming events for today and this week with start times, titles, and color badges.
   - Includes **"Sync Now"** action directly from the lock screen.

2. **Lock Screen & Home Screen Widget**:
   - Native Android `AppWidgetProvider` flagged with `android:widgetCategory="home_screen|keyguard"`.
   - Compatible with **Samsung LockStar (Good Lock)**, **Xiaomi HyperOS Lock Screen Widgets**, and widget hosts like **Lockscreen Widgets**.

3. **Glance Fullscreen Lock Screen Mode**:
   - Native activity utilizing `setShowWhenLocked(true)` and `setTurnScreenOn(true)` to present a clean AMOLED black dashboard of your calendar agenda over the lock screen.

4. **Universal iCalendar (RFC 5545) Sync Engine**:
   - Parses Google Calendar, Outlook, iCloud, Nextcloud, and webcal feeds.
   - Handles time zones, all-day events, recurrent schedules, and offline caching.

5. **Instant Termux Live Companion**:
   - A ready-to-run Python CLI tool (`./lockcal`) running directly inside Termux to immediately test and post calendar notifications to your lock screen right now!

---

## 🚀 Instant Quickstart (Termux Companion)

You can immediately start displaying your calendar on your lock screen right on this device:

### 1. Add your Calendar iCal URL:
```bash
./lockcal add "Work" "https://calendar.google.com/calendar/ical/your_id/private-xyz/basic.ics"
```

### 2. Trigger Sync & Display on Lock Screen:
```bash
./lockcal sync
```

### 3. Run in Background (Auto-refresh every 30 minutes):
```bash
./lockcal daemon &
```

---

## 📅 How to Get Your Google Calendar iCal URL

1. Open **Google Calendar** on a web browser: [calendar.google.com](https://calendar.google.com)
2. In the top right, click the **Settings (Gear icon) -> Settings**.
3. In the left sidebar under **"Settings for my calendars"**, click your calendar name.
4. Scroll down to the **"Integrate calendar"** section.
5. Copy the link in the box labeled **"Secret address in iCal format"** (starts with `https://calendar.google.com/calendar/ical/...`).
   *(Note: For Outlook or iCloud, go to Calendar Sharing > Publish Calendar and copy the ICS/Webcal link).*

---

## 📱 How to Build the Native Android APK

### Option A: Free Cloud Build via GitHub Actions (Easiest)
1. Initialize a git repository and push this folder to your GitHub:
   ```bash
   cd ~/lockscreen-calendar
   git init
   git add .
   git commit -m "Initial commit of LockCal"
   git remote add origin https://github.com/<your-username>/lockscreen-calendar.git
   git branch -M main
   git push -u origin main
   ```
2. The included `.github/workflows/build-apk.yml` workflow will automatically run.
3. Once completed, download the signed debug APK directly from the **Actions** tab on your phone and tap to install!

### Option B: Android Studio
1. Copy or clone this folder to your PC/Mac.
2. Open **Android Studio** and choose **Open Project**.
3. Select `~/lockscreen-calendar`.
4. Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## ⚙️ Xiaomi HyperOS / MIUI Lockscreen Configuration

Since this device is a **Xiaomi** running HyperOS:
1. Open phone **Settings > Notifications & Status Bar**.
2. Tap **Lock screen notifications** -> Set to **"Show all notifications and their contents"**.
3. Under App Notifications, find **LockCal** (or **Termux** if using the companion):
   - Enable **Allow notifications**.
   - Enable **Show on Lock screen**.
   - Enable **Allow vibration / badges**.
4. In **Settings > Battery > Battery Saver**, set LockCal to **"No restrictions"** so background sync isn't killed by Xiaomi memory management.

---

## 📂 Project Architecture

```
lockscreen-calendar/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/lockcal/app/
│       │   ├── LockCalApp.kt                     # Application & WorkManager init
│       │   ├── model/
│       │   │   ├── CalendarFeed.kt               # Feed metadata & color tags
│       │   │   └── CalendarEvent.kt              # Event model with date formatting
│       │   ├── data/
│       │   │   ├── IcsParser.kt                  # RFC 5545 iCalendar stream parser
│       │   │   └── CalendarRepository.kt         # Feed storage & OkHttp fetcher
│       │   ├── service/
│       │   │   ├── CalendarSyncWorker.kt         # WorkManager background worker
│       │   │   ├── LockscreenNotificationManager.kt # Persistent lockscreen RemoteViews
│       │   │   └── BootReceiver.kt               # Re-post notification after restart
│       │   ├── widget/
│       │   │   └── LockCalendarWidgetProvider.kt # Lockscreen & Homescreen AppWidget
│       │   └── ui/
│       │       ├── MainActivity.kt               # Feeds & settings UI
│       │       ├── FeedAdapter.kt                # Recycler adapter for feeds
│       │       ├── EventAdapter.kt               # Recycler adapter for events
│       │       └── LockScreenGlanceActivity.kt   # Fullscreen glance dashboard
│       └── res/
│           ├── layout/                           # Modern Material3 & RemoteViews layouts
│           ├── drawable/                         # Vectors & shape drawables
│           ├── values/                           # Strings, colors, styles
│           └── xml/                              # Widget metadata & backup configs
├── termux-companion/
│   └── lockcal.py                               # Live Termux CLI companion
├── .github/workflows/
│   └── build-apk.yml                            # GitHub Actions cloud APK builder
├── gradlew                                      # Gradle wrapper script
└── settings.gradle.kts                          # Project configuration
```
