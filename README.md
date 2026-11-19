# NetSage AI — Intelligent Mobile Network Quality Prediction & Adaptive Connectivity Decision Support System

> **B.E. CSE–AI · Mobile Computing Capstone Project**

[![Android](https://img.shields.io/badge/Android-API%2035-green?logo=android)](android/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)](android/app/src/main/java/com/netsage/ai/)
[![Python](https://img.shields.io/badge/Python-3.14-blue?logo=python)](ml/)
[![ML](https://img.shields.io/badge/ML-Random%20Forest%2099.17%25-orange)](ml/train_model.py)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## 📌 Overview

**NetSage AI** is a mobile-first, ML-augmented system that:

- **Collects real network telemetry** — latency (DNS + HTTP), throughput, Wi-Fi/cellular signal — directly from Android device APIs
- **Runs a pure-Kotlin Random Forest** on-device for quality classification (no server round-trip)
- **Provides explainable adaptive decisions** — Maintain / Handoff / Switch / Throttle — with confidence scores
- **Persists observations** in Room SQLite with full history and trend analysis
- **Visualises everything** in a live web dashboard with smooth charts, gauges and scenario simulation

---

## 🗂️ Project Structure

```
NetSage-AI-Capstone/
├── android/          # Kotlin + Jetpack Compose Android app
│   └── app/src/main/java/com/netsage/ai/
│       ├── domain/       # Models, Decision Engine, ML Classifier
│       ├── measurement/  # TelemetryCollector, ActiveProbeEngine
│       ├── storage/      # Room DB, DAO, Repository
│       └── ui/           # Compose screens, ViewModel
├── ml/               # Python ML training pipeline
│   ├── train_model.py         # Trains Random Forest, exports JSON model
│   ├── netsage_model.json     # Trained model (58 KB)
│   └── evaluation_report.json # Accuracy, F1, confusion matrix
└── web/              # Python web dashboard
    ├── server.py     # HTTP server + Decision Engine (Python mirror)
    ├── index.html    # Professional light-theme SaaS dashboard
    └── test_server.py # 4 integration tests
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│           Android Application           │
│  ┌─────────────┐   ┌──────────────────┐ │
│  │ Measurement │   │   Domain Layer   │ │
│  │   Layer     │──▶│ AdaptiveDecision │ │
│  │ (Telemetry, │   │ MlQualityClassif │ │
│  │  Probes)    │   │ NetworkRepository│ │
│  └─────────────┘   └────────┬─────────┘ │
│                             │            │
│  ┌──────────────────────────▼──────────┐ │
│  │     Room SQLite (Persistence)       │ │
│  └──────────────────────────┬──────────┘ │
│                             │            │
│  ┌──────────────────────────▼──────────┐ │
│  │   Jetpack Compose UI + ViewModel    │ │
│  └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘

┌────────────────────────────────┐
│  Python Web Dashboard (:8080)  │
│  server.py ◀──▶ index.html    │
│  Decision Engine (Python mirror│
│  of Kotlin AdaptiveEngine)    │
└────────────────────────────────┘
```

---

## 🚀 Quick Start

### Web Dashboard (runs anywhere with Python)

```powershell
cd web
python server.py
# Open http://localhost:8080
```

### ML Training Pipeline

```powershell
cd ml
python train_model.py
# Outputs: netsage_model.json, evaluation_report.json
```

### Android App

> Requires Android Studio with SDK Build-Tools 34

1. Open `android/` in Android Studio
2. Tools → SDK Manager → Install **Android SDK Build-Tools 34**
3. Connect Android phone (USB Debugging on)
4. Click ▶ Run

---

## 📊 ML Performance

| Model | Accuracy | Macro F1 | Poor-class Recall | Inference |
|-------|----------|----------|-------------------|-----------|
| Random Forest (NetSage) | **99.17%** | **99.11%** | **98.68%** | 4.7 µs |
| Heuristic Baseline | 60.42% | 59.42% | — | — |

- **Training set:** 1,800 samples · 30 simulated sessions · session-based train/test split
- **Features (8):** normalised latency, DNS, HTTP, throughput, signal, metering, stability, network type
- **Labels:** `POOR`, `FAIR`, `GOOD`, `EXCELLENT`
- **Model format:** pure JSON (no native libraries) — runs in Kotlin via `MlQualityClassifier.kt`

---

## 📡 Real Measurements Collected

| Metric | Method | Unit |
|--------|--------|------|
| DNS resolution time | Socket connect to `dns.google:53` | ms |
| HTTP round-trip | GET `https://www.google.com/generate_204` | ms |
| Download throughput | Timed 150 KB payload download | Mbps |
| Wi-Fi RSSI | `WifiManager.connectionInfo` | dBm |
| Cellular signal | `SignalStrength` API | dBm / ASU |
| Connection validation | `NetworkCapabilities.NET_CAPABILITY_VALIDATED` | bool |
| Metering state | `NetworkCapabilities.NET_CAPABILITY_NOT_METERED` | bool |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Android UI | Jetpack Compose, Material 3 |
| Language | Kotlin 2.0, Coroutines, Flow |
| Persistence | Room SQLite |
| Networking | Android ConnectivityManager, NetworkCallback |
| ML (Android) | Pure Kotlin Random Forest (no JNI) |
| ML (Training) | Python 3.14, scikit-learn 1.9, numpy |
| Web Server | Python `http.server`, TCPServer |
| Web UI | Vanilla HTML/CSS/JS, Canvas API |
| Build | Gradle 9.8 (KTS), AGP 8.7 |

---

## 🔑 Key Design Decisions

- **No fabricated measurements** — every metric comes from real device APIs or is explicitly labelled `Simulated`
- **Missing = null**, never zero — avoids misleading quality calculations
- **ML predictions include confidence** and are labelled `ML-assisted` vs `Heuristic` in every `PredictionDecision`
- **Non-destructive DB migrations** — schema changes use explicit Room migrations
- **Bounded probe traffic** — download limited to 150 KB; probes have timeouts and de-overlap guards

---

## 📋 API Endpoints (Web Dashboard)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/status` | Current observation + decision + history |
| `POST` | `/api/probe` | Trigger live active probe |
| `POST` | `/api/simulate` | Simulate a scenario (`wifi_normal`, `wifi_handoff_trigger`, `cellular_5g`, `cellular_metered_poor`, `offline`) |
| `POST` | `/api/clear` | Clear measurement history |

---

## 👨‍💻 Author

**B.E. CSE-AI — Third Year**  
Mobile Computing Subject · Capstone Project  
Built with Antigravity IDE · November 2026

---

## 📄 License

MIT — free to use, fork and extend for academic and personal projects.
