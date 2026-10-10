#!/usr/bin/env python3
"""Conservative domain checker: only public HTTPS hosts and trusted-form redirects."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import ipaddress
import json
from pathlib import Path
import socket
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "config" / "domains.json"
DEFAULT_REPORT = ROOT / "reports" / "latest.json"
REDIRECT_CODES = {301, 302, 303, 307, 308}
PERMANENT_CODES = {301, 308}
MAX_REDIRECTS = 6

def safe_url(value: str, check_dns: bool = False) -> bool:
    try:
        u = urllib.parse.urlsplit(value)
        host = u.hostname
        if (u.scheme.lower() != "https" or not host or u.username or u.password
                or u.port not in (None, 443) or u.fragment):
            return False
        host = host.lower().rstrip(".")
        if host in {"localhost"} or host.endswith((".local", ".internal", ".localhost")):
            return False
        if "." not in host or len(host) > 253:
            return False
        try:
            ipaddress.ip_address(host)
            return False
        except ValueError:
            pass
        if check_dns:
            addrs = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
            if not addrs:
                return False
            for addr in addrs:
                ip = ipaddress.ip_address(addr[4][0])
                if not ip.is_global:
                    return False
        return True
    except (ValueError, OSError, UnicodeError):
        return False

def host_stem(url: str) -> str:
    """Conservative label heuristic; never proves domain ownership."""
    host = (urllib.parse.urlsplit(url).hostname or "").lower().rstrip(".")
    labels = host.split(".")
    if len(labels) == 2:
        return labels[0]
    if len(labels) == 3 and labels[0] == "www":
        return labels[1]
    return ""

class TrackingRedirect(urllib.request.HTTPRedirectHandler):
    def __init__(self) -> None:
        self.hops: list[dict] = []
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        next_url = urllib.parse.urljoin(req.full_url, newurl)
        if code not in REDIRECT_CODES or not safe_url(next_url, check_dns=True):
            raise ValueError("Unsafe redirect destination")
        if len(self.hops) >= MAX_REDIRECTS:
            raise ValueError("Redirect chain too long")
        self.hops.append({"from": req.full_url, "to": next_url, "status": code})
        return super().redirect_request(req, fp, code, msg, headers, next_url)

def probe(url: str, timeout: int = 10) -> dict:
    if not safe_url(url, check_dns=True):
        return {"status": "unsafe_url", "final_url": url, "hops": [], "http_code": None}
    redirects = TrackingRedirect()
    opener = urllib.request.build_opener(redirects)
    request = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (compatible; DomainHealthChecker/1.0)",
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
        "Range": "bytes=0-1024",
    }, method="GET")
    try:
        with opener.open(request, timeout=timeout) as response:
            response.read(1024)
            code = response.status
            final_url = response.url
        if not safe_url(final_url):
            return {"status": "unsafe_url", "final_url": url, "hops": redirects.hops, "http_code": code}
        return {"status": "ok" if 200 <= code < 400 else "unavailable",
                "final_url": final_url, "hops": redirects.hops, "http_code": code}
    except urllib.error.HTTPError as e:
        return {"status": "blocked_or_error", "http_code": e.code, "final_url": e.url, "hops": redirects.hops}
    except (urllib.error.URLError, ValueError, TimeoutError, OSError) as e:
        return {"status": "connection_error", "final_url": url, "hops": redirects.hops,
                "http_code": None, "error": type(e).__name__}

def evaluate(original_url: str, result: dict) -> tuple[str, str | None]:
    if result.get("status") != "ok":
        return "not_updated", None
    final = result.get("final_url", "")
    if not safe_url(final):
        return "unsafe_destination", None
    if final.rstrip("/") == original_url.rstrip("/"):
        return "unchanged", None
    hops = result.get("hops") or []
    if not hops:
        return "no_redirect", None
    if any(h.get("status") not in PERMANENT_CODES for h in hops):
        return "review_temporary_redirect", final
    if host_stem(original_url) and host_stem(original_url) == host_stem(final):
        return "auto_approved_redirect", final
    return "review_cross_domain_redirect", final

def run(config: Path, report_path: Path, apply: bool, prober=probe) -> dict:
    registry = json.loads(config.read_text(encoding="utf-8"))
    entries = registry.get("sources", [])
    if len({s["id"] for s in entries}) != len(entries):
        raise ValueError("Duplicate source IDs")
    results = []
    changes = []
    for source in entries:
        url = source["current_url"]
        status = prober(url)
        decision, suggestion = evaluate(url, status)
        entry = {"id": source["id"], "name": source["name"], "checked_url": url,
                 "status": status["status"], "http_code": status.get("http_code"),
                 "decision": decision, "suggested_url": suggestion,
                 "redirects": status.get("hops", [])}
        results.append(entry)
        if apply and decision == "auto_approved_redirect" and suggestion:
            source["current_url"] = suggestion
            changes.append({"id": source["id"], "previous": url, "new": suggestion})
    now = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    report = {"checked_at_utc": now, "automatic_updates": changes,
              "needs_review": [r for r in results if r["decision"].startswith("review_")
                               or r["status"] != "ok"],
              "results": results}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if changes:
        registry["updated_at"] = now
        config.write_text(json.dumps(registry, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return report

def main() -> None:
    parser = argparse.ArgumentParser(description="Check a registry of HTTPS source domains")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--apply", action="store_true")
    opts = parser.parse_args()
    report = run(opts.config, opts.report, opts.apply)
    print(json.dumps({"checked": len(report["results"]),
                      "updated": len(report["automatic_updates"]),
                      "review_needed": len(report["needs_review"]),
                      "report_file": str(opts.report)}, ensure_ascii=False))

if __name__ == "__main__":
    main()
