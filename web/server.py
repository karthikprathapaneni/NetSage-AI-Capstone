import http.server
import socketserver
import json
import socket
import time
import urllib.request
import urllib.error
import math
import os
import threading

PORT = 8080
PROBE_URL = "https://www.google.com/generate_204"
DNS_PROBE_HOST = "dns.google"
THROUGHPUT_URL = "https://speed.cloudflare.com/__down?bytes=150000" # 150KB sample

class NetSageDecisionEngine:
    """
    Python implementation matching NetSage AI's Kotlin AdaptiveDecisionEngine.
    Computes Quality Score (0..100), Stability Index (0..100), Adaptive Action, and ML Feature Vector.
    """
    def __init__(self):
        self.history = []

    def compute_single_quality(self, obs):
        score = 0
        latency = obs.get("latency_ms")
        if latency is None:
            score += 20
        elif latency < 35:
            score += 35
        elif latency < 75:
            score += 28
        elif latency < 140:
            score += 20
        elif latency < 260:
            score += 12
        else:
            score += 4

        level = obs.get("signal_level", 0)
        if level == 4:
            score += 35
        elif level == 3:
            score += 28
        elif level == 2:
            score += 20
        elif level == 1:
            score += 10
        else:
            score += 15

        tp = obs.get("throughput_mbps")
        down_est = obs.get("downstream_kbps", 0)
        if tp and tp > 50:
            score += 20
        elif tp and tp > 15:
            score += 16
        elif tp and tp > 5:
            score += 12
        elif down_est > 50000:
            score += 18
        elif down_est > 10000:
            score += 14
        else:
            score += 8

        if obs.get("is_validated", False):
            score += 10

        return max(0, min(100, score))

    def evaluate(self, current):
        if not current.get("is_connected", False) or current.get("network_type") == "NONE":
            return {
                "quality_score": 0,
                "stability_index": 0,
                "action_title": "Poor Coverage / Offline Alert",
                "action_key": "POOR_COVERAGE_ALERT",
                "action_rationale": "No active network route detected. Offline-first local caching activated.",
                "confidence": 0.95,
                "latency_trend": "No active network route.",
                "signal_trend": "Transceiver disconnected.",
                "ml_status": "Heuristic Baseline Active (ML Hook Ready)",
                "feature_vector": [0.0] * 8
            }

        samples = [current] + self.history[:10]
        sample_scores = [self.compute_single_quality(s) for s in samples]
        if len(sample_scores) > 1:
            avg_hist = sum(sample_scores[1:]) / len(sample_scores[1:])
            weighted_score = int(round((sample_scores[0] * 0.4) + (avg_hist * 0.6)))
        else:
            weighted_score = sample_scores[0]

        # Stability index based on variance
        valid_latencies = [s.get("latency_ms") for s in self.history if s.get("latency_ms") is not None]
        if len(valid_latencies) >= 2:
            mean = sum(valid_latencies) / len(valid_latencies)
            variance = sum((x - mean) ** 2 for x in valid_latencies) / len(valid_latencies)
            std_dev = math.sqrt(variance)
            latency_penalty = min(50.0, std_dev / 4.0)
        else:
            latency_penalty = 5.0

        stability = int(round(max(10, min(100, 100.0 - latency_penalty))))

        # Determine Adaptive Action
        net_type = current.get("network_type", "WIFI")
        sig_dbm = current.get("signal_dbm")
        lat = current.get("latency_ms")

        if net_type == "WIFI":
            if (sig_dbm and sig_dbm < -83) and (lat and lat > 280 or stability < 45):
                action_title = "Switch Traffic to Cellular"
                action_key = "SWITCH_TO_CELLULAR"
                action_rationale = "Wi-Fi link is severely degraded with high latency jitter. Immediate failover recommended."
            elif (sig_dbm and sig_dbm < -80) or (lat and lat > 200):
                action_title = "Prepare Wi-Fi to Cellular Handoff"
                action_key = "PREPARE_WIFI_HANDOFF"
                action_rationale = "Wi-Fi RSSI degradation detected. Calibrating cellular route for seamless socket transition."
            elif weighted_score >= 65:
                action_title = "Maintain Current Link"
                action_key = "MAINTAIN_CURRENT"
                action_rationale = "Current network connection exhibits optimal throughput, low latency, and stable signal parameters."
            else:
                action_title = "Maintain Current Link"
                action_key = "MAINTAIN_CURRENT"
                action_rationale = "Link operating within tolerable parameters."
        elif "CELLULAR" in net_type:
            if current.get("is_metered", False) and weighted_score < 45:
                action_title = "Constrain Background Traffic"
                action_key = "THROTTLE_BACKGROUND_TASKS"
                action_rationale = "Cellular metering constraint detected under degraded link conditions. Prioritizing critical packets."
            elif sig_dbm and sig_dbm < -115:
                action_title = "Poor Coverage Alert"
                action_key = "POOR_COVERAGE_ALERT"
                action_rationale = "Low cellular reception detected. Prepare offline buffer."
            else:
                action_title = "Maintain Current Link"
                action_key = "MAINTAIN_CURRENT"
                action_rationale = "Cellular carrier link is stable and validated."
        else:
            action_title = "Maintain Current Link"
            action_key = "MAINTAIN_CURRENT"
            action_rationale = "Connection is stable."

        # Trends
        if lat:
            if self.history:
                past_lats = [s.get("latency_ms") for s in self.history if s.get("latency_ms") is not None]
                avg_l = sum(past_lats) / len(past_lats) if past_lats else lat
                diff = int(round(lat - avg_l))
                if diff > 30:
                    lat_trend = f"Latency elevated: {lat}ms (+{diff}ms vs baseline)"
                elif diff < -20:
                    lat_trend = f"Latency improved: {lat}ms ({diff}ms vs baseline)"
                else:
                    lat_trend = f"Latency stable: {lat}ms (avg {int(round(avg_l))}ms)"
            else:
                lat_trend = f"Latency: {lat}ms (initial probe)"
        else:
            lat_trend = "Awaiting active probe execution."

        sig_desc = f"{current.get('signal_desc', 'Good')} ({sig_dbm} dBm, {current.get('signal_level', 3)}/4 bars)"

        # 8-dimensional ML Feature Vector
        feature_vector = [
            round(min(1.0, (lat or 120) / 500.0), 3),
            round(min(1.0, (current.get("dns_ms") or 25) / 200.0), 3),
            round(min(1.0, max(0.0, ((sig_dbm or -75) + 120) / 80.0)), 3),
            round(current.get("signal_level", 3) / 4.0, 3),
            round(min(1.0, (current.get("downstream_kbps", 40000)) / 150000.0), 3),
            0.5, # Network type normalized
            round(stability / 100.0, 3),
            1.0 if current.get("is_metered", False) else 0.0
        ]

        return {
            "quality_score": weighted_score,
            "stability_index": stability,
            "action_title": action_title,
            "action_key": action_key,
            "action_rationale": action_rationale,
            "confidence": round(min(0.98, 0.55 + (len(self.history) / 20.0) * 0.40), 2),
            "latency_trend": lat_trend,
            "signal_trend": sig_desc,
            "ml_status": "Heuristic Baseline Active (ML Hook Ready)",
            "feature_vector": feature_vector
        }

engine = NetSageDecisionEngine()

# Current state
current_observation = {
    "network_type": "WIFI",
    "transport_name": "Wi-Fi (802.11ax / 5 GHz)",
    "is_connected": True,
    "is_validated": True,
    "interface_name": "wlan0",
    "latency_ms": None,
    "dns_ms": None,
    "http_ms": None,
    "throughput_mbps": None,
    "downstream_kbps": 78000,
    "upstream_kbps": 32000,
    "signal_dbm": -62,
    "signal_level": 4,
    "signal_desc": "Excellent",
    "wifi_ssid": "NetSage-Research-5G",
    "wifi_freq_mhz": 5180,
    "wifi_link_speed_mbps": 866,
    "carrier": None,
    "is_metered": False,
    "timestamp": int(time.time() * 1000)
}

def execute_real_probe():
    """Executes a real DNS resolution and HTTP 204 latency probe."""
    dns_time_ms = None
    http_time_ms = None
    err = None

    # Real DNS probe
    try:
        t0 = time.time()
        socket.gethostbyname(DNS_PROBE_HOST)
        dns_time_ms = int(round((time.time() - t0) * 1000))
    except Exception as e:
        err = f"DNS error: {str(e)}"

    # Real HTTP 204 probe
    try:
        req = urllib.request.Request(
            PROBE_URL,
            headers={"User-Agent": "NetSage-AI-WebTelemetry/1.0", "Cache-Control": "no-cache"}
        )
        t0 = time.time()
        with urllib.request.urlopen(req, timeout=3.5) as resp:
            if resp.status == 204 or resp.status == 200:
                http_time_ms = int(round((time.time() - t0) * 1000))
    except Exception as e:
        err = f"HTTP error: {str(e)}"

    effective_latency = http_time_ms or dns_time_ms or 45

    # Real throughput sample probe (small chunk)
    throughput_mbps = None
    try:
        t0 = time.time()
        req_tp = urllib.request.Request(
            THROUGHPUT_URL,
            headers={"User-Agent": "NetSage-AI-WebTelemetry/1.0", "Cache-Control": "no-cache"}
        )
        with urllib.request.urlopen(req_tp, timeout=4.0) as resp:
            data = resp.read()
            elapsed = time.time() - t0
            if elapsed > 0 and len(data) > 0:
                megabits = (len(data) * 8.0) / 1000000.0
                throughput_mbps = round(megabits / elapsed, 2)
    except Exception:
        # Fallback to link estimate calculation
        throughput_mbps = round((current_observation.get("downstream_kbps", 50000) / 1000.0) * 0.72, 1)

    current_observation["dns_ms"] = dns_time_ms
    current_observation["http_ms"] = http_time_ms
    current_observation["latency_ms"] = effective_latency
    current_observation["throughput_mbps"] = throughput_mbps
    current_observation["timestamp"] = int(time.time() * 1000)

    # Save to history
    obs_copy = dict(current_observation)
    engine.history.insert(0, obs_copy)
    if len(engine.history) > 50:
        engine.history.pop()

    return current_observation

# Perform initial probe at startup
try:
    execute_real_probe()
except Exception:
    pass

class NetSageHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/status":
            decision = engine.evaluate(current_observation)
            data = {
                "observation": current_observation,
                "decision": decision,
                "history": engine.history[:30]
            }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(data).encode("utf-8"))
            return

        if self.path == "/" or self.path.startswith("/index"):
            self.path = "/index.html"

        super().do_GET()

    def do_POST(self):
        if self.path == "/api/probe":
            updated_obs = execute_real_probe()
            decision = engine.evaluate(updated_obs)
            data = {
                "observation": updated_obs,
                "decision": decision,
                "history": engine.history[:30]
            }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(data).encode("utf-8"))
            return

        if self.path == "/api/simulate":
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            payload = json.loads(body) if body else {}
            scenario = payload.get("scenario", "wifi_normal")

            if scenario == "wifi_normal":
                current_observation.update({
                    "network_type": "WIFI",
                    "transport_name": "Wi-Fi (802.11ax / 5 GHz)",
                    "is_connected": True,
                    "is_validated": True,
                    "interface_name": "wlan0",
                    "latency_ms": 22,
                    "dns_ms": 11,
                    "http_ms": 22,
                    "signal_dbm": -56,
                    "signal_level": 4,
                    "signal_desc": "Excellent",
                    "wifi_ssid": "NetSage-Campus-5G",
                    "carrier": None,
                    "is_metered": False
                })
            elif scenario == "wifi_handoff_trigger":
                current_observation.update({
                    "network_type": "WIFI",
                    "transport_name": "Wi-Fi (Weak Fringe / 2.4 GHz)",
                    "is_connected": True,
                    "is_validated": True,
                    "interface_name": "wlan0",
                    "latency_ms": 310,
                    "dns_ms": 145,
                    "http_ms": 310,
                    "signal_dbm": -87,
                    "signal_level": 1,
                    "signal_desc": "Poor (High Jitter)",
                    "wifi_ssid": "NetSage-Campus-5G",
                    "carrier": None,
                    "is_metered": False
                })
            elif scenario == "cellular_5g":
                current_observation.update({
                    "network_type": "CELLULAR_5G",
                    "transport_name": "Cellular 5G (NR-SA Mid-Band)",
                    "is_connected": True,
                    "is_validated": True,
                    "interface_name": "rmnet_data0",
                    "latency_ms": 32,
                    "dns_ms": 16,
                    "http_ms": 32,
                    "throughput_mbps": 184.5,
                    "downstream_kbps": 195000,
                    "upstream_kbps": 48000,
                    "signal_dbm": -78,
                    "signal_level": 4,
                    "signal_desc": "Excellent",
                    "wifi_ssid": None,
                    "carrier": "NetSage Telecommunications",
                    "is_metered": False
                })
            elif scenario == "cellular_metered_poor":
                current_observation.update({
                    "network_type": "CELLULAR_4G",
                    "transport_name": "Cellular 4G (LTE Band 20)",
                    "is_connected": True,
                    "is_validated": True,
                    "interface_name": "rmnet_data1",
                    "latency_ms": 240,
                    "dns_ms": 95,
                    "http_ms": 240,
                    "throughput_mbps": 3.2,
                    "downstream_kbps": 4000,
                    "upstream_kbps": 900,
                    "signal_dbm": -116,
                    "signal_level": 1,
                    "signal_desc": "Poor",
                    "wifi_ssid": None,
                    "carrier": "Mobile Carrier LTE",
                    "is_metered": True
                })
            elif scenario == "offline":
                current_observation.update({
                    "network_type": "NONE",
                    "transport_name": "Offline / Disconnected",
                    "is_connected": False,
                    "is_validated": False,
                    "interface_name": None,
                    "latency_ms": None,
                    "dns_ms": None,
                    "http_ms": None,
                    "throughput_mbps": None,
                    "signal_dbm": None,
                    "signal_level": 0,
                    "signal_desc": "Disconnected",
                    "wifi_ssid": None,
                    "carrier": None,
                    "is_metered": False
                })

            current_observation["timestamp"] = int(time.time() * 1000)
            engine.history.insert(0, dict(current_observation))
            decision = engine.evaluate(current_observation)

            data = {
                "observation": current_observation,
                "decision": decision,
                "history": engine.history[:30]
            }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(data).encode("utf-8"))
            return

        if self.path == "/api/clear":
            engine.history.clear()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"success": True}).encode("utf-8"))
            return

        self.send_error(404, "Endpoint not found")

if __name__ == "__main__":
    web_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(web_dir)
    print(f"Starting NetSage AI Web Server on http://localhost:{PORT}")
    with socketserver.TCPServer(("", PORT), NetSageHandler) as httpd:
        httpd.serve_forever()

