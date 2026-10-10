#!/usr/bin/env python3
"""Diagnostic of HTML catalogue *metadata*, not video/playback links."""
from __future__ import annotations
from concurrent.futures import ThreadPoolExecutor, as_completed
from html.parser import HTMLParser
from pathlib import Path
import json
import re
from urllib.parse import urljoin, urlsplit, urlencode
from urllib.request import urlopen, Request
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config" / "domains.json"
REPORT = ROOT / "reports" / "catalogue_probe.json"
USER_AGENT = "Mozilla/5.0 (compatible; FrenkyCatalogueMetadataProbe/1.0)"
SEARCH = "reacher"

class CatalogueParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.forms = []
        self._form = None
        self._anchor = None
        self.anchors = []
        self.scripts = 0
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form":
            self._form = {"method": attrs.get("method", "get"), "action": attrs.get("action", ""),
                          "inputs": []}
        elif tag == "input" and self._form is not None:
            self._form["inputs"].append({"name": attrs.get("name", ""),
                                          "placeholder": attrs.get("placeholder", "")})
        elif tag == "a":
            self._anchor = {"url": attrs.get("href", ""),
                            "class": attrs.get("class", ""),
                            "title": attrs.get("title", ""), "text": ""}
        elif tag == "script":
            self.scripts += 1
    def handle_data(self, data):
        if self._anchor is not None:
            self._anchor["text"] += data.strip()[:150] + " "
    def handle_endtag(self, tag):
        if tag == "form" and self._form is not None:
            self.forms.append(self._form)
            self._form = None
        elif tag == "a" and self._anchor is not None:
            a = self._anchor
            a["text"] = " ".join(a["text"].split())[:150]
            if a["url"]:
                self.anchors.append(a)
            self._anchor = None

def request(url, method='GET', fields=None):
    try:
        data = urlencode(fields).encode("utf-8") if method == "POST" and fields else None
        req = Request(url, data=data, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
        with urlopen(req, timeout=10) as response:
            raw = response.read(250000)
            final = response.url
            status = response.status
            charset = response.headers.get_content_charset() or "utf-8"
            text = raw.decode(charset, errors="replace")
            parser = CatalogueParser()
            parser.feed(text)
            return {"http_status": status, "final_url": final,
                    "content_length_sample": len(raw),
                    "forms": parser.forms[:4],
                    "script_tags": parser.scripts,
                    "links_total_sample": len(parser.anchors),
                    "matching_titles": [a for a in parser.anchors if SEARCH in (a["title"]+" "+a["text"]).lower()][:10]}
    except (HTTPError, URLError, TimeoutError, ValueError, OSError) as exc:
        return {"error":type(exc).__name__, "http_status":getattr(exc, "code", None)}

def probe(src):
    base = src["current_url"].rstrip("/") + "/"
    from urllib.parse import urlsplit
    parts = urlsplit(base)
    origin = f"{parts.scheme}://{parts.netloc}/"
    variants = [
        ("homepage", base),
        ("wordpress_s", base + "?" + urlencode({"s": SEARCH})),
        ("query_search", base + "?" + urlencode({"search": SEARCH})),
        ("dle_get", base + "?" + urlencode({"do":"search","subaction":"search","story":SEARCH})),
        ("dle_post", base),
        ("dle_story_get", base + "?" + urlencode({"story": SEARCH})),
        ("root_wordpress_s", origin + "?" + urlencode({"s": SEARCH})),
    ]
    return {"id":src["id"], "name":src["name"],
            "variants":{name:request(url, "POST" if name == "dle_post" else "GET",
              {"do":"search","subaction":"search","story":SEARCH} if name == "dle_post" else None)
              for name, url in variants}}

def main():
    sources = json.loads(CONFIG.read_text(encoding="utf-8"))["sources"]
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(probe, src) for src in sources]
        results = [f.result() for f in as_completed(futures)]
    results.sort(key=lambda x:x["id"])
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({"query":SEARCH, "results":results}, ensure_ascii=False, indent=2) + "\n",encoding="utf-8")
    for r in results:
        print(r["id"],[(k,x.get("http_status"),len(x.get("matching_titles",[]))) for k,x in r["variants"].items()])

if __name__ == "__main__":
    main()
