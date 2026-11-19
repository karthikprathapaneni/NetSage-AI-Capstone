"""
NetSage AI — Automated Python Test Suite
Validates server endpoints, active probing, simulation scenarios, decision logic, and ML model outputs.
"""

import unittest
import urllib.request
import urllib.error
import json
import time

SERVER_URL = "http://localhost:8080"

class TestNetSageWebServer(unittest.TestCase):

    def test_01_status_endpoint(self):
        req = urllib.request.Request(f"{SERVER_URL}/api/status")
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))

            self.assertIn("observation", data)
            self.assertIn("decision", data)
            self.assertIn("history", data)

            obs = data["observation"]
            dec = data["decision"]

            self.assertIn("network_type", obs)
            self.assertIn("quality_score", dec)
            self.assertIn("stability_index", dec)
            self.assertIn("action_title", dec)
            self.assertIn("feature_vector", dec)
            self.assertEqual(len(dec["feature_vector"]), 8)

    def test_02_simulation_scenarios(self):
        scenarios = ["wifi_normal", "wifi_handoff_trigger", "cellular_5g", "cellular_metered_poor", "offline"]

        for sc in scenarios:
            req = urllib.request.Request(
                f"{SERVER_URL}/api/simulate",
                data=json.dumps({"scenario": sc}).encode("utf-8"),
                headers={"Content-Type": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                self.assertEqual(resp.status, 200)
                data = json.loads(resp.read().decode("utf-8"))

                obs = data["observation"]
                dec = data["decision"]

                if sc == "offline":
                    self.assertFalse(obs["is_connected"])
                    self.assertEqual(dec["quality_score"], 0)
                    self.assertEqual(dec["action_key"], "POOR_COVERAGE_ALERT")
                elif sc == "wifi_handoff_trigger":
                    is_handoff = "HANDOFF" in dec["action_key"] or "CELLULAR" in dec["action_key"]
                    self.assertTrue(is_handoff, f"Expected handoff or cellular switch, got: {dec['action_key']}")
                elif sc == "cellular_5g":
                    self.assertTrue(dec["quality_score"] >= 70)

    def test_03_active_probe_endpoint(self):
        req = urllib.request.Request(
            f"{SERVER_URL}/api/probe",
            data=b"",
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=6.0) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            obs = data["observation"]

            self.assertIsNotNone(obs["latency_ms"])
            self.assertTrue(obs["latency_ms"] > 0)

    def test_04_clear_history(self):
        req = urllib.request.Request(
            f"{SERVER_URL}/api/clear",
            data=b"",
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertTrue(data.get("success", False))

if __name__ == "__main__":
    unittest.main()
