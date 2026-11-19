"""
NetSage AI — Machine Learning Pipeline & Research Evaluation
Trains a Random Forest classifier on multi-session mobile network telemetry to predict QoS states.
Evaluates model vs. a threshold-based baseline and exports parameters for on-device Android inference.
"""

import os
import json
import time
import math
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, classification_report

FEATURE_NAMES = [
    "rtt_latency_norm",    # [0] 0..1 normalized (RTT / 500ms)
    "dns_latency_norm",    # [1] 0..1 normalized (DNS / 200ms)
    "signal_strength_norm",# [2] 0..1 normalized ((dBm + 120) / 80)
    "signal_bars_norm",    # [3] 0..1 (bars / 4)
    "downlink_bw_norm",    # [4] 0..1 normalized (downlink_kbps / 150000)
    "network_type_norm",   # [5] 0..1 (Wi-Fi=0.2, 5G=0.4, 4G=0.6, 3G=0.8, Other=1.0)
    "stability_index_norm",# [6] 0..1 (stability / 100)
    "is_metered"           # [7] binary (0.0 = unmetered, 1.0 = metered)
]

TARGET_LABELS = {
    0: "POOR",       # Quality degraded, high latency, poor signal -> Handoff / Throttle
    1: "MODERATE",   # Medium quality, acceptable for web browsing
    2: "EXCELLENT"   # High speed, low latency, unmetered -> Maintain link
}

def generate_telemetry_dataset(num_sessions=30, samples_per_session=60, random_seed=42):
    """
    Generates structured multi-session telemetry simulating realistic mobile computing conditions:
    - Session types: Optimal Wi-Fi, Fringe Wi-Fi, 5G NR, Congested LTE, Weak Coverage.
    - Prevents data leakage by partitioning strictly by session ID.
    """
    np.random.seed(random_seed)
    X, y, session_ids = [], [], []

    session_types = ["wifi_excellent", "wifi_degrading", "cellular_5g_optimal", "cellular_lte_congested", "cellular_weak_coverage"]

    for s_id in range(num_sessions):
        s_type = session_types[s_id % len(session_types)]

        for t in range(samples_per_session):
            if s_type == "wifi_excellent":
                rtt = np.random.normal(20, 4)
                dns = np.random.normal(12, 3)
                signal_dbm = np.random.normal(-52, 3)
                bars = 4
                downlink_kbps = np.random.normal(85000, 8000)
                net_type_val = 0.2
                stability = np.random.normal(92, 4)
                metered = 0.0
                label = 2 # EXCELLENT

            elif s_type == "wifi_degrading":
                # Signal drops over time, latency spikes
                drift = t / float(samples_per_session)
                rtt = np.random.normal(40 + drift * 260, 20)
                dns = np.random.normal(20 + drift * 120, 15)
                signal_dbm = np.random.normal(-65 - drift * 24, 4)
                bars = max(1, int(round(4 - drift * 3)))
                downlink_kbps = max(1000, np.random.normal(50000 - drift * 45000, 5000))
                net_type_val = 0.2
                stability = max(15, np.random.normal(85 - drift * 60, 8))
                metered = 0.0
                label = 0 if drift > 0.45 or rtt > 180 or signal_dbm < -82 else 1

            elif s_type == "cellular_5g_optimal":
                rtt = np.random.normal(28, 5)
                dns = np.random.normal(18, 4)
                signal_dbm = np.random.normal(-75, 4)
                bars = 4
                downlink_kbps = np.random.normal(160000, 20000)
                net_type_val = 0.4
                stability = np.random.normal(90, 5)
                metered = 0.0
                label = 2 # EXCELLENT

            elif s_type == "cellular_lte_congested":
                rtt = np.random.normal(110, 25)
                dns = np.random.normal(45, 12)
                signal_dbm = np.random.normal(-88, 5)
                bars = 2
                downlink_kbps = np.random.normal(12000, 3000)
                net_type_val = 0.6
                stability = np.random.normal(68, 10)
                metered = 1.0
                label = 1 # MODERATE

            else: # cellular_weak_coverage
                rtt = np.random.normal(320, 60)
                dns = np.random.normal(160, 40)
                signal_dbm = np.random.normal(-114, 4)
                bars = 1
                downlink_kbps = np.random.normal(1800, 600)
                net_type_val = 0.6
                stability = np.random.normal(35, 12)
                metered = 1.0
                label = 0 # POOR

            # Feature normalization
            norm_rtt = float(np.clip(rtt / 500.0, 0.0, 1.0))
            norm_dns = float(np.clip(dns / 200.0, 0.0, 1.0))
            norm_signal = float(np.clip((signal_dbm + 120.0) / 80.0, 0.0, 1.0))
            norm_bars = float(np.clip(bars / 4.0, 0.0, 1.0))
            norm_downlink = float(np.clip(downlink_kbps / 150000.0, 0.0, 1.0))
            norm_stability = float(np.clip(stability / 100.0, 0.0, 1.0))

            features = [
                round(norm_rtt, 4),
                round(norm_dns, 4),
                round(norm_signal, 4),
                round(norm_bars, 4),
                round(norm_downlink, 4),
                round(net_type_val, 4),
                round(norm_stability, 4),
                float(metered)
            ]

            X.append(features)
            y.append(label)
            session_ids.append(s_id)

    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32), np.array(session_ids)

class ThresholdBaseline:
    """Documented rule-based baseline for benchmarking against the ML model."""
    def predict(self, X):
        preds = []
        for row in X:
            norm_rtt, norm_dns, norm_sig, norm_bars, norm_bw, _, norm_stab, _ = row
            # If latency > 180ms (norm_rtt > 0.36) or signal < -85 dBm (norm_sig < 0.43) or stability < 45% -> POOR
            if norm_rtt > 0.36 or norm_sig < 0.43 or norm_stab < 0.45:
                preds.append(0) # POOR
            elif norm_rtt < 0.12 and norm_sig > 0.65 and norm_stab > 0.75:
                preds.append(2) # EXCELLENT
            else:
                preds.append(1) # MODERATE
        return np.array(preds)

def export_model_to_json(rf_model, output_path):
    """
    Exports Random Forest decision trees into a compact, human-readable JSON file.
    This enables 100% portable, pure-Kotlin on-device inference without ABI/NDK runtime dependencies!
    """
    trees = []
    for estimator in rf_model.estimators_:
        tree_dict = {
            "children_left": estimator.tree_.children_left.tolist(),
            "children_right": estimator.tree_.children_right.tolist(),
            "feature": estimator.tree_.feature.tolist(),
            "threshold": estimator.tree_.threshold.tolist(),
            "value": estimator.tree_.value.tolist()
        }
        trees.append(tree_dict)

    model_payload = {
        "model_type": "RandomForestClassifier",
        "version": "1.0.0",
        "n_estimators": len(trees),
        "n_features": rf_model.n_features_in_,
        "classes": [int(c) for c in rf_model.classes_],
        "class_labels": TARGET_LABELS,
        "feature_names": FEATURE_NAMES,
        "trees": trees
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(model_payload, f)
    print(f"Exported Random Forest model to: {output_path} ({os.path.getsize(output_path)} bytes)")

def run_ml_pipeline():
    output_dir = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(output_dir, exist_ok=True)

    print("==================================================================")
    print("NetSage AI — Machine Learning Pipeline & Comparative Evaluation")
    print("==================================================================")

    # 1. Dataset Generation
    X, y, session_ids = generate_telemetry_dataset(num_sessions=30, samples_per_session=60)
    print(f"Generated Dataset: {X.shape[0]} samples across {len(np.unique(session_ids))} multi-condition sessions.")

    # 2. Session-based Train / Test Split (Prevent temporal/session leakage)
    unique_sessions = np.unique(session_ids)
    test_session_count = int(round(len(unique_sessions) * 0.25))
    test_sessions = unique_sessions[-test_session_count:]
    train_sessions = unique_sessions[:-test_session_count]

    train_mask = np.isin(session_ids, train_sessions)
    test_mask = np.isin(session_ids, test_sessions)

    X_train, y_train = X[train_mask], y[train_mask]
    X_test, y_test = X[test_mask], y[test_mask]

    print(f"Train Set: {X_train.shape[0]} samples (Sessions: {train_sessions.tolist()})")
    print(f"Test Set:  {X_test.shape[0]} samples (Held-out Sessions: {test_sessions.tolist()})")

    # 3. Train ML Model
    rf = RandomForestClassifier(n_estimators=30, max_depth=6, min_samples_split=4, random_state=42)
    rf.fit(X_train, y_train)

    # Measure inference latency
    t0 = time.time()
    y_pred_rf = rf.predict(X_test)
    inference_duration_us = ((time.time() - t0) / len(X_test)) * 1_000_000

    # 4. Baseline Evaluation
    baseline = ThresholdBaseline()
    y_pred_base = baseline.predict(X_test)

    # Metrics computation
    acc_rf = accuracy_score(y_test, y_pred_rf)
    acc_base = accuracy_score(y_test, y_pred_base)

    f1_rf = f1_score(y_test, y_pred_rf, average="macro")
    f1_base = f1_score(y_test, y_pred_base, average="macro")

    rec_poor_rf = recall_score(y_test, y_pred_rf, labels=[0], average="macro")
    rec_poor_base = recall_score(y_test, y_pred_base, labels=[0], average="macro")

    cm_rf = confusion_matrix(y_test, y_pred_rf).tolist()
    cm_base = confusion_matrix(y_test, y_pred_base).tolist()

    print("\n--- Comparative Research Evaluation Results ---")
    print(f"{'Metric':<30} | {'Threshold Baseline':<20} | {'NetSage ML (Random Forest)':<25}")
    print("-" * 80)
    print(f"{'Accuracy':<30} | {acc_base*100:>18.2f}% | {acc_rf*100:>23.2f}%")
    print(f"{'Macro F1-Score':<30} | {f1_base*100:>18.2f}% | {f1_rf*100:>23.2f}%")
    print(f"{'Poor-Connectivity Recall':<30} | {rec_poor_base*100:>18.2f}% | {rec_poor_rf*100:>23.2f}%")
    print(f"{'Single Prediction Latency':<30} | {'< 0.1 ms':>20} | {f'{inference_duration_us:.1f} microseconds':>25}")
    print("-" * 80)

    # Save Evaluation Report
    report = {
        "timestamp": time.time(),
        "train_samples": int(X_train.shape[0]),
        "test_samples": int(X_test.shape[0]),
        "features": FEATURE_NAMES,
        "metrics": {
            "baseline": {
                "accuracy": round(float(acc_base), 4),
                "macro_f1": round(float(f1_base), 4),
                "poor_recall": round(float(rec_poor_base), 4),
                "confusion_matrix": cm_base
            },
            "random_forest": {
                "accuracy": round(float(acc_rf), 4),
                "macro_f1": round(float(f1_rf), 4),
                "poor_recall": round(float(rec_poor_rf), 4),
                "prediction_latency_us": round(float(inference_duration_us), 2),
                "confusion_matrix": cm_rf
            }
        }
    }

    report_path = os.path.join(output_dir, "evaluation_report.json")
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print(f"Saved evaluation metrics to: {report_path}")

    # Export Model to JSON for Android Kotlin integration
    model_export_path = os.path.join(output_dir, "netsage_model.json")
    export_model_to_json(rf, model_export_path)

    return report

if __name__ == "__main__":
    run_ml_pipeline()

