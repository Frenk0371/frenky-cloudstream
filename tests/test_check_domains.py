import json
from pathlib import Path
import tempfile
import unittest

from tools.check_domains import evaluate, host_stem, run, safe_url

class RegistryTests(unittest.TestCase):
    def test_registry_has_six_unique_sources(self):
        r = json.loads((Path(__file__).parents[1] / "config" / "domains.json").read_text())
        self.assertEqual(len(r["sources"]), 6)
        self.assertEqual(len({x["id"] for x in r["sources"]}), 6)
        for x in r["sources"]:
            self.assertTrue(safe_url(x["current_url"]))

    def test_rejects_unsafe_urls(self):
        for value in ("http://example.com", "https://localhost/", "https://127.0.0.1/",
                      "https://bob:pass@example.com/", "file:///etc/passwd",
                      "https://example.com:8443/", "https://example.com/#fragment"):
            self.assertFalse(safe_url(value), value)

    def test_auto_update_only_for_permanent_same_stem(self):
        old = "https://example.surf/"
        result = {"status": "ok", "final_url": "https://example.click/",
                  "hops": [{"from": old, "to": "https://example.click/", "status": 301}]}
        self.assertEqual(evaluate(old, result), ("auto_approved_redirect", "https://example.click/"))

    def test_temporary_redirect_needs_review(self):
        old = "https://example.surf/"
        result = {"status": "ok", "final_url": "https://example.click/",
                  "hops": [{"from": old, "to": "https://example.click/", "status": 302}]}
        self.assertEqual(evaluate(old, result)[0], "review_temporary_redirect")

    def test_different_name_needs_review(self):
        old = "https://example.surf/"
        result = {"status": "ok", "final_url": "https://unexpected.click/",
                  "hops": [{"from": old, "to": "https://unexpected.click/", "status": 301}]}
        self.assertEqual(evaluate(old, result)[0], "review_cross_domain_redirect")

    def test_does_not_promote_blocked_sites(self):
        self.assertEqual(evaluate("https://example.surf/",
            {"status": "blocked_or_error", "http_code": 403, "final_url": "https://example.click/",
             "hops": [{"status": 301}]})[0], "not_updated")

    def test_apply_changes_registry_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "domains.json"
            report_path = Path(tmp) / "report.json"
            p.write_text(json.dumps({"schema_version": 1, "updated_at": None, "sources": [
                {"id": "test", "name": "Test", "current_url": "https://example.surf/",
                 "original_url": "https://example.surf/"}]}))
            def fake_probe(url):
                return {"status": "ok", "http_code": 200, "final_url": "https://example.click/",
                        "hops": [{"from": url, "to": "https://example.click/", "status": 308}]}
            res = run(p, report_path, apply=True, prober=fake_probe)
            self.assertEqual(len(res["automatic_updates"]), 1)
            self.assertEqual(json.loads(p.read_text())["sources"][0]["current_url"], "https://example.click/")
            self.assertTrue(report_path.exists())

    def test_www_redirect_is_recognized(self):
        self.assertEqual(host_stem("https://www.example.click/"), "example")

    def test_co_uk_not_assumed_safe(self):
        self.assertEqual(host_stem("https://example.co.uk/"), "")

if __name__ == "__main__":
    unittest.main()
