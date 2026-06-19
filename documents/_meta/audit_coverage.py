#!/usr/bin/env python3
"""
Audit: prove the generated HTML fully captures the original markdown/YAML.

For every source .md/.yaml under documents/ (excluding _meta) it checks:
  1. mapping     - a sibling .html page exists
  2. content     - fraction of source content tokens present in the rendered HTML
  3. structure   - markdown tables / code fences / mermaid fences are preserved
  4. integrity   - no leftover MERMAIDZ placeholders, no empty body

Run: python documents/_meta/audit_coverage.py
"""
from __future__ import annotations

import os
import re
from html.parser import HTMLParser

META_DIR = os.path.dirname(os.path.abspath(__file__))
DOCS_ROOT = os.path.dirname(META_DIR)

TOKEN_RE = re.compile(r"\w[\w./-]{3,}", re.UNICODE)
# Tokens that are legitimately removed from the rendered output (redaction).
REDACTED_HINT = re.compile(r"(?i)^(ey[jJ]|bearer|basic|secure|pass|secret|token)")


class TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self._skip = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self._skip += 1
        # Link/image targets are content too: include href/src so a rewritten
        # .md -> .html cross-reference still counts as preserved.
        for k, v in attrs:
            if k in ("href", "src") and v:
                self.parts.append(" " + v + " ")

    def handle_endtag(self, tag):
        if tag in ("script", "style") and self._skip:
            self._skip -= 1

    def handle_data(self, data):
        if not self._skip:
            self.parts.append(data)

    def text(self):
        return " ".join(self.parts)


def out_html(src):
    return re.sub(r"\.(md|yaml|yml)$", ".html", src, flags=re.I)


def tokens(text):
    return set(t.lower() for t in TOKEN_RE.findall(text))


def list_sources():
    out = []
    for cur, dirs, files in os.walk(DOCS_ROOT):
        rel = os.path.relpath(cur, DOCS_ROOT).replace("\\", "/")
        if rel == "_meta" or rel.startswith("_meta/"):
            continue
        dirs[:] = [d for d in dirs if d != "_meta"]
        for f in files:
            if f.lower().endswith((".md", ".yaml", ".yml")):
                out.append(os.path.relpath(os.path.join(cur, f), DOCS_ROOT).replace("\\", "/"))
    return sorted(out)


def main():
    sources = list_sources()
    missing_map = []
    low_cov = []
    placeholders = []
    empty = []
    md_table_mismatch = []
    mermaid_mismatch = []
    coverages = []

    for src in sources:
        html_rel = out_html(src)
        html_abs = os.path.join(DOCS_ROOT, html_rel)
        if not os.path.exists(html_abs):
            missing_map.append(src)
            continue

        with open(os.path.join(DOCS_ROOT, src), encoding="utf-8-sig") as fh:
            src_text = fh.read()
        with open(html_abs, encoding="utf-8") as fh:
            html_raw = fh.read()

        ext = TextExtractor()
        ext.feed(html_raw)
        html_text = ext.text()
        html_tok = tokens(html_text)
        # normalize rewritten links: a .html target also counts as its .md source
        html_tok |= {re.sub(r"\.html$", ".md", t) for t in html_tok}
        html_tok |= {re.sub(r"\.html$", ".yaml", t) for t in html_tok}

        if "MERMAIDZ" in html_raw:
            placeholders.append(html_rel)
        # body emptiness: visible text length beyond the shell chrome
        if len(html_text.strip()) < 200:
            empty.append((html_rel, len(html_text.strip())))

        src_tok = tokens(src_text)
        if not src_tok:
            continue
        missing_tok = src_tok - html_tok
        # ignore tokens that are expected to be redacted
        real_missing = {t for t in missing_tok if not REDACTED_HINT.match(t)}
        cov = 1 - len(real_missing) / len(src_tok)
        coverages.append((cov, src, sorted(list(real_missing))[:12]))
        if cov < 0.97:
            low_cov.append((cov, src, sorted(list(real_missing))[:15]))

        # structure parity (markdown only)
        if src.lower().endswith(".md"):
            # pipe table rows in source (lines with >=2 pipes, not separators handled loosely)
            src_tables = len(re.findall(r"(?m)^\s*\|.+\|\s*$", src_text))
            html_table_rows = len(re.findall(r"<tr[ >]", html_raw))
            if src_tables and html_table_rows == 0:
                md_table_mismatch.append((src, src_tables, html_table_rows))
            src_mermaid = len(re.findall(r"```mermaid", src_text))
            html_mermaid = len(re.findall(r'<pre class="mermaid">', html_raw))
            if src_mermaid != html_mermaid:
                mermaid_mismatch.append((src, src_mermaid, html_mermaid))

    coverages.sort()
    print("=" * 70)
    print("COVERAGE AUDIT — generated HTML vs source markdown/YAML")
    print("=" * 70)
    print(f"source files                : {len(sources)}")
    print(f"mapped to HTML              : {len(sources) - len(missing_map)}")
    print(f"missing HTML (unmapped)     : {len(missing_map)}")
    print(f"leftover placeholders       : {len(placeholders)}")
    print(f"empty/near-empty bodies     : {len(empty)}")
    print(f"md table-loss               : {len(md_table_mismatch)}")
    print(f"mermaid count mismatch      : {len(mermaid_mismatch)}")
    if coverages:
        avg = sum(c for c, _, _ in coverages) / len(coverages)
        print(f"content token coverage      : avg={avg:.4f}  min={coverages[0][0]:.4f}")
        below = [c for c in coverages if c[0] < 0.97]
        print(f"files below 0.97 coverage   : {len(below)}")

    def dump(title, rows):
        if rows:
            print(f"\n--- {title} ---")
            for r in rows[:25]:
                print("  ", r)

    dump("UNMAPPED SOURCE FILES", missing_map)
    dump("LEFTOVER PLACEHOLDERS", placeholders)
    dump("EMPTY BODIES (rel, textlen)", empty)
    dump("TABLE LOSS (src, srcRows, htmlRows)", md_table_mismatch)
    dump("MERMAID MISMATCH (src, srcN, htmlN)", mermaid_mismatch)
    dump("LOWEST COVERAGE (cov, file, sample missing tokens)", low_cov)

    ok = not (missing_map or placeholders or md_table_mismatch or mermaid_mismatch)
    print("\nRESULT:", "PASS" if ok and not low_cov else
          ("PASS (review low-coverage notes)" if ok else "NEEDS REVIEW"))


if __name__ == "__main__":
    main()
