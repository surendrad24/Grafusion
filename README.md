<p align="center">
  <img src="assets/brand/png/grafusion-wordmark-horizontal.png" alt="Grafusion — Grafana mobile client by Fusionlancers" width="520">
</p>

<h1 align="center">Grafusion — Mobile Dashboards for Grafana</h1>

<p align="center">
  A native Android (and soon iOS) client for self-hosted Grafana — dashboards, panels, alerts and on-call, without a browser.
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
and on-call surfaces — without going through a browser or paying for Grafana Cloud Mobile.

The Android app is native (Kotlin + Jetpack Compose + Vico charts). A small Go relay is planned
to power push notifications and background sync for OSS Grafana users who don't have an on-prem
push gateway.

## Currently implemented (v0.1)

The Android app is a working preview against a self-hosted Grafana instance. What is landed today:

### Accounts
- Multi-account: add and switch between multiple Grafana instances.
- Auth: username/password *and* API token (`Bearer`) — pick per account.
- Credentials are stored in `EncryptedSharedPreferences` (AES-256-GCM master key).
- `/api/user` used to verify credentials on add.

### Dashboards
- Browse dashboards via `/api/search` (folders, tags, favorites-friendly).
- Room-backed offline cache of the dashboard list per account.
- Open a dashboard — panels are parsed from Grafana JSON (`/api/dashboards/uid/{uid}`),
  including one level of collapsed row containers and panels missing an `id` field (synthetic
  fallback IDs so nothing gets silently dropped).
- Responsive layout: on tablets (>600 dp wide) panels honor their `gridPos` and render
  **side-by-side** exactly like the Grafana web grid. On phones they stack.
- **Auto-refresh** selector in the top bar: Off / 5 s / 10 s / 30 s / 1 min / 5 min / 15 min.
- Time range selector: last 15 min / 1 h / 6 h / 24 h / 7 d.

### Panel renderers (native, no WebView)
Each dashboard runs `/api/ds/query` and native Compose renderers draw the results:
- **timeseries / graph** — Vico line chart, multi-series with a legend and unit-aware axes.
- **stat / gauge** — big number + label, with unit + decimals from `fieldConfig`.
- **barchart** — Vico column chart.
- **table** — horizontally-scrollable table with a sticky header, all columns preserved from the
  raw DataFrame (numeric + string).
- **logs** — auto-detects the `Line` / `Body` field and renders a scrollable log view.
- **row** — collapsible/flat row grouping.
- Unsupported types (e.g. geomap, text) fall through to a labeled placeholder instead of
  breaking the whole dashboard.

### Alerts
- Consumes `/api/v1/provisioning/alert-rules` and grouped state via `/api/alertmanager/*`.
- Firing / Pending / Normal state indicators.

### App shell
- Material 3 dark & light theming (system-aware, or user-forced Dark / Light).
- Splash: gradient background, logo, wordmark, tagline and version — renders instantly from an
  XML layout so it doesn't get eaten by Compose's first-frame cost.
- Foldable/tablet-aware navigation: side rail on wide screens, bottom bar on phones.

## Roadmap — native feature milestones

Modeled after Grafana Cloud Mobile + Grafana OnCall to give self-hosted users the same
experience natively.

| # | Milestone | Highlights |
|---|---|---|
| **M1** | Dashboard essentials *(shipped in v0.1)* | Search, open, native panel rendering, side-by-side layout, auto-refresh |
| **M2** | Explore & alerts polish | Ad-hoc query view (`/api/ds/query` with Prometheus/Loki/InfluxDB pickers), alert rule detail, silence / mute from mobile |
| **M3** | Push notifications | Go relay (`backend/`) subscribes to Grafana Alertmanager webhooks, forwards to FCM, per-account topics, quiet hours |
| **M4** | On-call | Grafana OnCall schedule view, current on-call badge, acknowledge / resolve incidents |
| **M5** | Full panel coverage | canvas, geomap (MapLibre), state timeline, status history, heatmap, pie chart, alert list panel |
| **M6** | Editing & sharing | Move / resize panels from the tablet, save dashboard variants, share panel snapshots as PNG |

## Architecture

```
android/         — Kotlin + Jetpack Compose app
  data/          — Room DB, retrofit API, DataStore prefs, EncryptedSharedPreferences vault
  ui/            — Compose screens (dashboards, alerts, accounts, splash)
backend/         — Go relay: Alertmanager webhook receiver → FCM (planned, M3)
```

Non-obvious constraints worth knowing before hacking:

- Android build requires **JDK 17** (Android Gradle Plugin 8.x is incompatible with JDK 25).
  Run gradle with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew ...`.
- Grafana panels sometimes come back without an `id` field (Security dashboards especially) —
  parser assigns synthetic IDs so the dashboard still renders.
- Compose cold-start on mid-range Android devices drops ~1.6 s of frames — that's why the
  splash is a plain XML `Activity`, not a Compose overlay.

## Getting started (development)

```bash
# Clone
git clone https://github.com/surendrad24/Grafusion.git
cd Grafusion/android

# Build a debug APK
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then launch the app, tap **Accounts → Add**, and point it at your Grafana URL with either
username/password or an API token.

## Screenshots

_Coming soon — will be added under `docs/images/` once the M1 UI stabilizes._

## Branding

The Grafusion identity uses an original **Data Fusion Node** symbol — a geometric "G" combining
a rounded orbit with a sharp data path. The mark does not reproduce Grafana's logo.

Primary colors:

- Fusion Navy — `#0B1739`
- Fusion Blue — `#142B5F`
- Energy Orange — `#FF8A00`
- Data Purple — `#7C3AED`

## Contributing

Bug reports, dashboards that don't render correctly, and pull requests are all welcome.
For dashboards that don't render, please include the panel JSON (right-click → Inspect →
Panel JSON in Grafana) so we can add a fixture.

## Trademark notice

Grafana is a trademark of Raintank Inc. dba Grafana Labs. Grafusion is an independent open-source
client and is not endorsed by or affiliated with Grafana Labs.

## License

Copyright © 2026 **Fusionlancers Technologies Pvt. Ltd.**

Add a formal software license (Apache-2.0 recommended) before accepting external contributions.

## Company

Built by **Fusionlancers Technologies Pvt. Ltd.** — https://fusionlancers.com/
