<p align="center">
  <img src="assets/brand/png/grafusion-wordmark-horizontal.png" alt="Grafusion - Grafana mobile client by Fusionlancers" width="520">
</p>

<h1 align="center">Grafusion - Mobile Dashboards for Grafana</h1>

<p align="center">
  A native Android (and soon iOS) client for self-hosted Grafana - dashboards, panels, alerts and on-call, without a browser.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84?logo=android&logoColor=white">
  <img alt="iOS" src="https://img.shields.io/badge/iOS-planned-000000?logo=apple&logoColor=white">
  <img alt="Grafana" src="https://img.shields.io/badge/Grafana-self--hosted-F46800?logo=grafana&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue">
  <img alt="Status" src="https://img.shields.io/badge/status-active-2563EB">
</p>

## Overview

**Grafusion** is an open-source mobile and tablet client for **self-hosted Grafana**, built by
[Fusionlancers Technologies Pvt. Ltd.](https://fusionlancers.com/). It targets teams who run
their own Grafana stack and want first-class mobile access to their dashboards, panels, alerts
and on-call surfaces - without going through a browser or paying for Grafana Cloud Mobile.

The Android app is native (Kotlin + Jetpack Compose + Vico charts). A Go relay in `backend/`
receives Grafana Alertmanager webhooks and forwards them to devices via FCM for OSS users who
don't have an on-prem push gateway.

## Currently implemented

The Android app runs against any self-hosted Grafana instance today. What is shipped:

### Accounts & security
- Multi-account: add and switch between multiple Grafana instances.
- Auth: username/password **or** API token (`Bearer`) - pick per account.
- Credentials stored in `EncryptedSharedPreferences` (AES-256-GCM master key).
- `/api/user` used to verify credentials on add.
- **App lock**: optional PIN (PBKDF2-hashed) + `BiometricPrompt` fingerprint gate on launch.
- Runtime **permissions onboarding** screen for `POST_NOTIFICATIONS` (Android 13+).

### Dashboards
- Browse via `/api/search` with **search bar**, **folder chips**, **starred filter** and
  **pull-to-refresh**.
- Room-backed **offline cache** of the dashboard list per account - the app is usable without
  network.
- Open a dashboard - panels parsed from `/api/dashboards/uid/{uid}`, including one level of
  row containers and panels missing an `id` (synthetic IDs so nothing gets silently dropped).
- Responsive layout: on tablets (>600 dp) panels honor their `gridPos` and render
  **side-by-side** exactly like the Grafana web grid; phones stack.
- **Auto-refresh** in the top bar: Off / 5 s / 10 s / 30 s / 1 min / 5 min / 15 min.
- **Time-range selector**: last 15 min / 1 h / 6 h / 24 h / 7 d.
- **Row collapse / expand** with `type=row` headers preserved from the dashboard JSON.

### Dashboard edit mode
Tap the pencil to enter an in-place editor that persists back through
`/api/dashboards/db?overwrite=true`:

- **Long-press drag** to reorder panels within a row.
- **Resize** via w/h steppers and preset chips (half / full / quarter).
- **Rename** panels inline.
- **Duplicate** or **delete** panels from a per-panel menu.
- **Add panel** bottom sheet - pick from all supported renderer types.
- Dirty-count badge on the save icon; discard confirm on close when unsaved.

### Panel renderers (native, no WebView)
Each dashboard runs `/api/ds/query` and native Compose renderers draw the results:

- **timeseries / graph** - Vico line chart, multi-series with legend and unit-aware axes.
- **stat / gauge** - big number + label, unit + decimals from `fieldConfig`.
- **bargauge** - horizontal bar-gauge for multi-metric summaries.
- **barchart** - Vico column chart.
- **piechart** - donut / pie with legend.
- **heatmap** - bucketed 2D heatmap.
- **state-timeline / status-history** - per-series state bands over time.
- **table** - horizontally-scrollable, sticky-header, all columns preserved from the raw
  DataFrame (numeric + string).
- **logs** - auto-detects `Line` / `Body` field, scrollable log view.
- **text** - markdown/plain text panels.
- **geomap / worldmap-panel** - map with pinned points (MapLibre-style renderer).
- **row** - collapsible / flat row grouping.
- Unsupported types fall through to a labeled placeholder instead of breaking the dashboard.

### Alerts
- Live alert list backed by Grafana **Alertmanager v2** (`/api/alertmanager/*`) plus
  provisioning rules from `/api/v1/provisioning/alert-rules`.
- Firing / Pending / Normal state indicators.
- **Tap an alert** for a detail sheet with labels, annotations and the source rule.
- **Silence from mobile** for 30 minutes / 2 hours / 24 hours.

### Push notifications (backend relay)
`backend/` is a Go service that turns Grafana Alertmanager webhooks into FCM pushes:

- `POST /v1/devices` - register/refresh an FCM token per (account, device).
- `POST /v1/webhook/grafana` - webhook contact point receiver.
- SQLite-backed device registry; noop-mode when `FCM_CREDENTIALS_JSON` is unset (dev-friendly).
- Dockerfile included.

### App shell
- Material 3 dark & light theming (system-aware, or user-forced Dark / Light).
- Splash: gradient background, logo, wordmark, tagline, version - renders instantly from an
  XML layout so it doesn't get eaten by Compose's first-frame cost.
- Foldable/tablet-aware navigation: side rail on wide screens, bottom bar on phones.

## Roadmap

Modeled after Grafana Cloud Mobile + Grafana OnCall to give self-hosted users the same
experience natively.

| # | Milestone | Status | Highlights |
|---|---|---|---|
| **M1** | Dashboard essentials | shipped | Search, open, native panel rendering, side-by-side layout, auto-refresh |
| **M2** | Dashboard polish | shipped | Search / star / folder chips, pull-to-refresh, expanded panel renderer coverage |
| **M3** | Panel editing | shipped | Move / resize / rename / duplicate / delete, add panel, row collapse |
| **M4** | Alerts + push + security | shipped | Live Alertmanager list, silence 30m/2h/24h, Go relay -> FCM, biometric app lock, offline cache |
| **M5** | On-call | planned | Grafana OnCall schedule view, current on-call badge, acknowledge / resolve incidents |
| **M6** | Explore & sharing | planned | Ad-hoc `/api/ds/query` view, share panel snapshots as PNG, dashboard variants |

## Architecture

```
android/         - Kotlin + Jetpack Compose app
  data/
    db/          - Room DAOs (dashboard cache, ...)
    prefs/       - DataStore + EncryptedSharedPreferences (theme, app lock, notifications)
    repo/        - Grafana API repos: Account, Dashboard, Alert, Notifications, PanelParser
    model/       - Panel, PanelGroup, Series, RawFrame, ...
  ui/
    accounts/    - add / switch / delete Grafana accounts
    alerts/      - alert list + detail sheet + silence controls
    dashboards/  - list + detail + edit mode + native panel renderers
    lock/        - PIN + biometric unlock gate
    permissions/ - runtime permissions onboarding
    nav/         - adaptive scaffold (rail / bottom bar)
    splash/, theme/, auth/
backend/         - Go relay: Alertmanager webhook -> FCM (SQLite device registry)
  cmd/relay/     - entrypoint
  internal/      - HTTP handlers, storage, FCM client
```

Non-obvious constraints worth knowing before hacking:

- Android build requires **JDK 17** (Android Gradle Plugin 8.x is incompatible with JDK 25).
  Run gradle with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew ...`.
- Grafana panels sometimes come back without an `id` field (Security dashboards especially) -
  parser assigns synthetic IDs so the dashboard still renders.
- `BiometricPrompt` requires the host activity to be a `FragmentActivity`, not a plain
  `ComponentActivity` - `MainActivity` extends `FragmentActivity` for that reason.
- Compose cold-start on mid-range Android devices drops ~1.6 s of frames - that's why the
  splash is a plain XML `Activity`, not a Compose overlay.
- Dashboard edit persistence uses `/api/dashboards/db` with `overwrite=true`; row containers
  are round-tripped verbatim so nested layouts survive save.

## Getting started (development)

### Android app

```bash
git clone https://github.com/surendrad24/Grafusion.git
cd Grafusion/android

# Build a debug APK
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app, tap **Accounts -> Add**, and point it at your Grafana URL with either
username/password or an API token.

### Push relay (optional, for M4 push)

```bash
cd backend
go run ./cmd/relay
```

Then in Grafana: **Alerting -> Contact points -> New -> Webhook**, URL
`https://your-relay.example.com/v1/webhook/grafana`, attach it to a notification policy.
See `backend/README.md` for env vars.

### Firebase Cloud Messaging (optional, for real push delivery)

The Android app compiles fine without Firebase - it falls back to a device-ID stub for the
relay's routing key and simply won't receive pushes. To turn on real FCM delivery:

1. Create a Firebase project and register an Android app with package
   `com.fusionlancers.grafusion`.
2. Download `google-services.json` and drop it into `android/app/`. The Gradle build
   detects the file and enables the `google-services` plugin automatically.
3. In the relay, set `FCM_CREDENTIALS_JSON` to a service-account key from the same
   Firebase project (see `backend/README.md`).
4. Rebuild and reinstall. On first launch `GrafusionMessagingService.onNewToken` writes
   the FCM token into `NotificationPreferences`, and `NotificationsRepository`
   re-registers the device with the relay so subsequent Grafana webhooks are delivered as
   system notifications on the "Grafana alerts" channel.

## Screenshots

_Coming soon - will be added under `docs/images/` once the M5 UI stabilizes._

## Branding

The Grafusion identity uses an original **Data Fusion Node** symbol - a geometric "G" combining
a rounded orbit with a sharp data path. The mark does not reproduce Grafana's logo.

Primary colors:

- Fusion Navy - `#0B1739`
- Fusion Blue - `#142B5F`
- Energy Orange - `#FF8A00`
- Data Purple - `#7C3AED`

## Contributing

Bug reports, dashboards that don't render correctly, and pull requests are all welcome.
For dashboards that don't render, please include the panel JSON (right-click -> Inspect ->
Panel JSON in Grafana) so we can add a fixture.

## Trademark notice

Grafana is a trademark of Raintank Inc. dba Grafana Labs. Grafusion is an independent open-source
client and is not endorsed by or affiliated with Grafana Labs.

## License

Copyright (c) 2026 **Fusionlancers Technologies Pvt. Ltd.**

Add a formal software license (Apache-2.0 recommended) before accepting external contributions.

## Company

Built by **Fusionlancers Technologies Pvt. Ltd.** - https://fusionlancers.com/
