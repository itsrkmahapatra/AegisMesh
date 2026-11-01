# AegisMesh: Offline Cyclone & Disaster Alert Radar

[![Android CI Build](https://github.com/itsrkmahapatra/AegisMesh/actions/workflows/android_ci.yml/badge.svg)](https://github.com/itsrkmahapatra/AegisMesh/actions)
[![Emergency Bundle CDN Cron](https://github.com/itsrkmahapatra/AegisMesh/actions/workflows/cron_emergency_bundle.yml/badge.svg)](https://github.com/itsrkmahapatra/AegisMesh/actions)
[![License: Proprietary | All Rights Reserved](https://img.shields.io/badge/License-Proprietary%20%7C%20All%20Rights%20Reserved-red.svg)](LICENSE)
[![Architecture: Clean Compose](https://img.shields.io/badge/Architecture-Clean%20Compose-06B6D4.svg)](https://developer.android.com/jetpack/compose)

> **Architected & Developed by Raj Kishor Mahapatra**  
> **Package Name**: `com.itsrkmahapatra.aegismesh`  
> **Mission**: An industry-dominating, 2-page offline-first hybrid emergency radar app engineered to rank **#1 globally for emergency resilience, disaster tracking, and cyclone safety**.

> [!IMPORTANT]
> ### 🔒 PROPRIETARY INTELLECTUAL PROPERTY & END-USER LICENSE
> **Copyright © 2026 Raj Kishor Mahapatra. All Rights Reserved.**  
> AegisMesh is provided strictly for personal and community emergency resilience **USE ONLY**.  
> - **NO COPYING**: You may not copy, clone, mirror, or duplicate the source code, architecture, or assets.  
> - **NO MODIFICATION**: You may not modify, alter, fork, adapt, or create derivative works.  
> - **NO COMMERCIAL USE OR SALE**: You may not sell, resell, lease, sublicense, commercialize, or monetize this software in any form.  
> - **NO REVERSE ENGINEERING**: Decompilation, disassembly, or extraction of the underlying mesh algorithms is strictly prohibited.  
> See the [LICENSE](LICENSE) file for complete legally binding terms.

---

## 🏛️ Evaluated Through 4 Critical Lenses

### 1. Flawless Android Architecture
- **Tech Stack**: 100% Kotlin, Jetpack Compose Material 3, AndroidX Lifecycle, Kotlin Coroutines & Flow.
- **Strict 2-Page Topology**: 
  - **Page 1: Live Radar** (MapLibre vector map + 168-hour wind animation scrubber).
  - **Page 2: Official Alerts** (NDMA Sachet CAP RSS alerts + Government Do's & Don'ts).
  - *No user-to-user messaging chat to maintain 100% legal, telecom, and national regulatory compliance.*
- **Offline-First Room DB**: Reactive SQLite persistence with automatic timestamp-based "Latest Wins" resolution.
- **R8 / ProGuard Minification**: Fully optimized rules stripping unused symbols to minimize APK download footprint.

### 2. Elite UI/UX & Localization
- **High-Contrast Dark Emergency Theme**: Designed for maximum readability in torrential storm environments and low battery consumption on OLED panels.
- **Interactive 168-Hour Timeline Scrubber**: Smooth Play/Pause loop advancing through 7 days of hourly wind speed, gale gusts, and precipitation intensity.
- **50 km Live GPS Radius Filter**: Instant client-side Haversine filtering removing distant noise and prioritizing local threats.
- **Coastal Multi-Language Support**: Designed for rapid deployment across high-risk coastal zones in Odia, Bengali, Telugu, Tamil, Gujarati, and Hindi.

### 3. Infinite Infrastructure Scaling (100% Keyless & Serverless)
- **Direct On-Device Fetching**: The user's device directly fetches and parses the public domain NDMA Sachet CAP RSS feed and Open-Meteo weather APIs with **zero intermediary servers and zero operational costs**.
- **Automated Cron CDN**: A lightweight Node.js script executed every 15 minutes via free GitHub Actions compiles, signs, and compresses the All-India emergency bundle (`latest.json.gz`, ~55 KB), distributed over GitHub Pages CDN.
- **Peer-to-Peer Mesh Distribution**: Offline devices automatically synchronize over Bluetooth Low Energy (BLE) and Wi-Fi Direct using Google Nearby Connections API (`Strategy.P2P_CLUSTER`).

### 4. Aggressive Growth & ASO Strategy
- Built with targeted keyword density for high-intent emergency searches (`cyclone tracker`, `disaster alert`, `offline weather radar`).
- Viral K-factor amplification mechanism: 1 connected device in an evacuation shelter automatically updates hundreds of offline peer devices within minutes.

---

## 📡 Core System Architecture

```
                                  [ INTERNET ONLINE ]
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    ▼                                             ▼
       [ NDMA Sachet CAP RSS ]                           [ Open-Meteo 7-Day ]
   (sachet.ndma.gov.in/rss/rss_india.xml)             (api.open-meteo.com/v1/forecast)
                    │                                             │
                    │ Direct OkHttp Fetch                         │ Direct OkHttp Fetch
                    │ On-Device CapXmlParser                      │ On-Device JSON Parse
                    │                                             │
                    └──────────────────────┬──────────────────────┘
                                           ▼
                                 [ Local Room Database ]
                                (aegis_emergency.db)
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    ▼                                             ▼
          [ Page 1: Live Radar ]                     [ Page 2: Government Alerts ]
     MapLibre Native + OpenFreeMap               NDMA CAP Warnings (Extreme / Severe)
    168h Wind/Gust Vector Overlay                 50 km Live GPS Radius Filtering
   Compose Timeline Scrubber (0..167)             Official Do's and Don'ts Cards
                    │
                    ▼ [ CELL TOWERS COLLAPSE / OFFLINE ]
    [ Google Nearby Connections P2P Cluster ]
       (BLE & Wi-Fi Direct Multi-Hop Mesh)
  ◄─────────────────────────────────────────────►
  Device A (Latest Sync) ──> Device B (Offline)
        Compressed GZIP Bundle (<55 KB)
```

---

## 🔋 Battery & Outage Optimization

1. **Cellular Collapse Detection**:
   Monitors baseband telephony events (`ServiceState.STATE_OUT_OF_SERVICE`). When towers fail during cyclone landfall, prompts the user to switch to **Airplane Mode + Bluetooth**, preventing frantic modem search loops that drain battery.
2. **Duty-Cycling Mesh Engine**:
   Runs background discovery in alternating cycles: **15 seconds active scanning**, followed by **45 seconds sleep**. Extends emergency device runtime during multi-day power blackouts.

---

## 🛠️ Project Structure

```
AegisMesh/
├── .github/workflows/
│   ├── cron_emergency_bundle.yml      # Free 15-min GitHub Actions compiler & CDN publisher
│   └── android_ci.yml                # Automated CI lint & build workflow
├── scripts/
│   └── compile_emergency_bundle.js   # Zero-cost Node.js compiler for GitHub Pages CDN
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # Foreground Service, Nearby BLE/Wi-Fi permissions
│   │   ├── assets/
│   │   │   └── india_base_spec.json  # Preloaded vector basemap & offline cache spec
│   │   ├── java/com/itsrkmahapatra/aegismesh/
│   │   │   ├── AegisApplication.kt   # MapLibre & database initialization
│   │   │   ├── MainActivity.kt       # Permission coordinator & GPS binding
│   │   │   ├── battery/              # Cellular outage & battery protection
│   │   │   ├── data/
│   │   │   │   ├── local/            # Room Database, DAO, and Entities
│   │   │   │   ├── remote/           # Direct NDMA XML & Open-Meteo fetchers
│   │   │   │   └── repository/       # Data reconciliation & GZIP export/ingest
│   │   │   ├── domain/model/         # Domain models (Alert, Weather, Mesh Payload)
│   │   │   ├── mesh/                 # P2P Cluster, Handshake protocol, Duty cycle
│   │   │   ├── ui/
│   │   │   │   ├── AegisRootScreen.kt# 2-Page Bottom Navigation
│   │   │   │   ├── map/              # MapLibre View & 168-hour timeline scrubber
│   │   │   │   └── alerts/           # Official NDMA warnings & Do's/Don'ts
│   │   │   └── util/                 # Haversine distance, Bounding box, GZIP
│   │   └── res/                      # High-contrast emergency themes & icons
├── ASO_STRATEGY.md                   # Global #1 ranking growth & ASO blueprint
└── README.md
```

---

## 🚀 Getting Started & Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34 (UpsideDownCake)

### Build Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/itsrkmahapatra/AegisMesh.git
   cd AegisMesh
   ```
2. Open in Android Studio or build via Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
3. Test emergency bundle compiler locally:
   ```bash
   node scripts/compile_emergency_bundle.js
   ```

---

## 📜 Regulatory & Civic Compliance
- **No User-to-User Chat**: AegisMesh is strictly a broadcast recipient and relay reader app. It does not provide end-user chat or arbitrary text messaging, ensuring 100% compliance with Indian Telecom Regulations, NDMA disaster broadcasting norms, and Google Play communications policies.
- **Data Attribution**: All emergency alerts originate from official public feeds issued by the **National Disaster Management Authority (NDMA)**, **India Meteorological Department (IMD)**, **Central Water Commission (CWC)**, and **INCOIS**.

---

## 🔒 License & Intellectual Property Terms
**Copyright © 2026 Raj Kishor Mahapatra. All Rights Reserved.**  
This software is proprietary. Permission is granted solely to install and execute the application for personal safety and disaster response. Copying, altering, modifying, reverse engineering, selling, renting, or redistributing this repository or derivative works is strictly prohibited. For complete terms, see [LICENSE](LICENSE).

---

## 👨‍💻 Developer & Maintainer
**Raj Kishor Mahapatra**  
Lead Architect & Developer  
GitHub: [@itsrkmahapatra](https://github.com/itsrkmahapatra)  
Contact: `raju.chikitigada@gmail.com`

