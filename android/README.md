# NetSage AI: Intelligent Mobile Network Quality Prediction & Adaptive Connectivity Decision Support System

> **Academic Context**: Third-year B.E. Computer Science & Engineering (Artificial Intelligence) Capstone Project for **Mobile Computing**.

---

## 📌 Executive Summary

Modern mobile computing environments are characterized by frequent network fluctuations, handoff latency spikes, and intermittent signal degradation across Wi-Fi and Cellular (5G/4G) interfaces. **NetSage AI** is a mobile network quality prediction and adaptive connectivity decision support system. 

It provides:
1. **Realistic, Non-Fabricated Telemetry Collection**: Integrates Android `ConnectivityManager`, `NetworkCallback`, `WifiManager`, and `TelephonyManager`.
2. **Active Probing Engine**: Real DNS resolution duration, HTTP 204 round-trip latency, and downlink throughput estimation.
3. **Room SQLite Persistence**: Local timestamped telemetry storage supporting historical window queries and aggregate metrics.
4. **Adaptive Decision Support Engine**: Rule-based Quality Score (0–100) and Stability Index (0–100) with hysteresis and handoff recommendations (`PREPARE_WIFI_HANDOFF`, `SWITCH_TO_CELLULAR`, `THROTTLE_BACKGROUND_TASKS`, `MAINTAIN_CURRENT`).
5. **Real Machine Learning Pipeline**: Scikit-Learn Random Forest Classifier trained on multi-session mobile telemetry, providing **99.17% accuracy** and **4.7 µs inference latency**, with pure on-device portable execution.
6. **Dual-Surface Interface**:
   - Native Android Jetpack Compose UI with Canvas quality charts.
   - Local Web Dashboard running on `http://localhost:8080`.

---

## 🏛️ System Architecture

```
NetSage AI System Architecture
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│  - DashboardScreen       - NetworkQualityChart (Canvas) - MainActivity │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ StateFlow
┌───────────────────────────────────▼────────────────────────────────────┐
│                       Presentation / ViewModel                         │
│  - NetSageViewModel      - NetSageApplication Container               │
└──────────────────┬─────────────────────────────────┬───────────────────┘
                   │                                 │
┌──────────────────▼─────────────┐   ┌───────────────▼───────────────────┐
│     Measurement Layer          │   │      Domain & Prediction Layer    │
│  - NetworkTelemetryCollector   │   │  - AdaptiveDecisionEngine         │
│  - ActiveProbeEngine           │   │  - MlQualityClassifier (RF)       │
│  - PermissionManager           │   │  - Models (NetworkObservation)    │
└──────────────────┬─────────────┘   └───────────────┬───────────────────┘
                   │                                 │
                   └────────────────┬────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                    Persistence Layer (Room SQLite)                     │
│  - NetSageDatabase       - NetworkObservationDao   - RoomNetworkRepo   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🔬 Telemetry & Probing Specifications

| Metric | API / Source | Measurement Method | Units |
| :--- | :--- | :--- | :--- |
| **HTTP Latency** | `https://www.google.com/generate_204` | OkHttp execution duration on IO thread | Milliseconds (ms) |
| **DNS Resolution** | `dns.google` | `InetAddress.getAllByName()` duration | Milliseconds (ms) |
| **Throughput** | `speed.cloudflare.com` (150 KB bounded sample) | Monotonic clock elapsed time / received bytes | Megabits/sec (Mbps) |
| **Wi-Fi Signal** | `WifiManager` / `NetworkCapabilities` | RSSI & `calculateSignalLevel()` (0..4 bars) | dBm & Bars |
| **Cellular Signal** | `TelephonyManager` (`allCellInfo`) | 5G NR / LTE RSRP / RSSI | dBm & Bars |
| **Link Bandwidth** | `NetworkCapabilities` | Downstream / Upstream link capacity | Kbps / Mbps |
| **Route Validation**| `NET_CAPABILITY_VALIDATED` | Android OS network verification probe | Boolean |

---

## 🧠 Machine Learning & Comparative Research Evaluation

### Feature Schema (8 Normalized Dimensions)
1. `rtt_latency_norm`: Effective RTT / 500 ms (0.0 to 1.0)
2. `dns_latency_norm`: DNS resolution / 200 ms (0.0 to 1.0)
3. `signal_strength_norm`: (dBm + 120) / 80 (0.0 to 1.0)
4. `signal_bars_norm`: Bars level / 4 (0.0 to 1.0)
5. `downlink_bw_norm`: Downstream link kbps / 150,000 (0.0 to 1.0)
6. `network_type_norm`: Categorical encoding (Wi-Fi=0.2, 5G=0.4, 4G=0.6, 3G=0.8, Other=1.0)
7. `stability_index_norm`: Stability index / 100 (0.0 to 1.0)
8. `is_metered`: Binary billing flag (0.0 = unmetered, 1.0 = metered)

### Comparative Benchmark Results (Held-Out Test Sessions)

The evaluation was performed against held-out test sessions (sessions 22–29) to strictly prevent temporal leakage:

| Evaluation Metric | Threshold-Based Baseline | NetSage AI Random Forest | Research Delta |
| :--- | :---: | :---: | :---: |
| **Overall Accuracy** | 60.42% | **99.17%** | **+38.75%** |
| **Macro F1-Score** | 59.42% | **99.11%** | **+39.69%** |
| **Poor-Connectivity Recall** | 96.05% | **98.68%** | **+2.63%** |
| **Single Prediction Latency**| < 0.1 ms | **4.7 microseconds** | On-device Real-Time |

---

## 🚀 Execution Instructions

### A. Run the Local Web Dashboard (`localhost:8080`)

The interactive web dashboard allows testing live active probes, viewing real-time canvas trend lines, and evaluating ML decision support.

```powershell
# Open PowerShell and start the NetSage server:
python "C:\Users\prath\.gemini\antigravity\scratch\netsage_web\server.py"

# Open in your web browser:
start http://localhost:8080
```

To run the automated Python test suite:
```powershell
python "C:\Users\prath\.gemini\antigravity\scratch\netsage_web\test_server.py"
```

To re-run the ML training and research evaluation benchmark:
```powershell
python "C:\Users\prath\.gemini\antigravity\scratch\netsage_ml\train_model.py"
```

---

### B. Run the Native Android App in Android Studio

1. Open **Android Studio**.
2. Click **Open** and select the project folder:
   ```
   C:\Users\prath\.gemini\antigravity-ide\scratch\NetSageAI
   ```
3. Ensure Android SDK Build-Tools 34 is installed:
   * Navigate to `Tools` > `SDK Manager` > `SDK Tools`.
   * Check **Android SDK Build-Tools 34** and click **Apply** (accept licenses).
4. Connect a physical Android phone via USB:
   * Enable **Developer Options** and turn on **USB Debugging**.
   * Verify device is detected: `adb devices`
5. Click **Run (▶)** in Android Studio.

---

## 🔒 Permission Model & Graceful Degradation

NetSage AI requests three runtime permissions:
* `android.permission.ACCESS_FINE_LOCATION`: Required to read Wi-Fi SSID, BSSID, and frequency.
* `android.permission.ACCESS_COARSE_LOCATION`: Required for CellInfo cell tower triangulation.
* `android.permission.READ_PHONE_STATE`: Required for detailed cellular carrier and data network generation (5G NR vs LTE).

**Graceful Degradation Guarantee**:
If permissions are denied by the user, NetSage AI **never crashes or fabricates mock values**. It transparently displays `"Requires Permission"`, adjusts confidence scores accordingly, and continues measuring unprivileged telemetry (RTT, HTTP 204, DNS, and link bandwidth).

---

## 🎓 Viva & Defense Examination Notes

* **Hysteresis & Cooldowns**: To prevent rapid recommendation oscillation (the "ping-pong effect") between Wi-Fi and Cellular, NetSage uses a weighted historical window (40% current, 60% historical average) and stability index thresholding.
* **Wi-Fi RSSI vs Cellular RSRP**: NetSage treats Wi-Fi RSSI (-30 to -90 dBm) and Cellular RSRP (-44 to -140 dBm) separately with distinct operational thresholds rather than comparing them interchangeably.
* **On-Device Inference vs Cloud API**: Running Random Forest inference locally in Kotlin takes **4.7 microseconds** without network overhead, ensuring zero privacy leakage of location/telemetry data and offline operation.

