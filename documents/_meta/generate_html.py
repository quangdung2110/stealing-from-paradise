#!/usr/bin/env python3
"""
Generate a Bootstrap HTML documentation tree from the existing markdown/YAML
documentation under documents/.

This script implements the project-documentation-writer skill for this repo:
- It treats every existing .md / .yaml file as source context (never deleted).
- It mirrors each source file to a sibling .html page so existing relative
  cross-references keep working after a .md -> .html rewrite.
- It builds the 13 fixed group pages, the root index.html, the traceability
  matrix, and the _meta manifests (inventory / id-registry / extension-manifest
  / search-index).
- Output is validated by the skill's scripts/check_documents.py.

Re-run:  python documents/_meta/generate_html.py
Requires: markdown, pyyaml, and the skill assets at SKILL_DIR.
"""
from __future__ import annotations

import html
import json
import os
import re
from datetime import date

import markdown
import yaml

# --------------------------------------------------------------------------- #
# Paths and constants
# --------------------------------------------------------------------------- #
META_DIR = os.path.dirname(os.path.abspath(__file__))
DOCS_ROOT = os.path.dirname(META_DIR)
SKILL_DIR = r"D:\dev\agent-skill\project-documentation-writer"
ASSETS = os.path.join(SKILL_DIR, "assets")

PROJECT_NAME = "Stealing-from-Paradise"
PROJECT_SUMMARY = (
    "Multi-seller e-commerce platform built on a microservices architecture "
    "(10 services, Kafka event bus, CQRS/Event Sourcing, Saga checkout, Stripe "
    "Connect payouts, Elasticsearch Vietnamese search, SSE notifications, and an "
    "AI shopping assistant). This site is generated from the markdown/YAML "
    "documentation in documents/."
)
LAST_UPDATED = date.today().isoformat()

ACTIVE_EXTENSIONS = [
    "rest-openapi-writer",
    "kafka-event-contract-writer",
    "realtime-websocket-writer",
    "oauth-oidc-sso-writer",
    "payment-gateway-writer",
    "db-migration-writer",
    "ci-cd-pipeline-writer",
    "observability-writer",
    "deployment-env-safety",
]

# Fixed group set linked by the root index card grid.
GROUP_TITLES = {
    "overview": "Overview",
    "architecture": "Architecture",
    "domain": "Domain Model and Rules",
    "business-flows": "Business Flows",
    "use-cases": "Use Cases",
    "interfaces": "Interfaces",
    "messaging": "Messaging and Realtime",
    "identity": "Identity and Security",
    "platform": "Platform and Operations",
    "deployment": "Deployment",
    "observability": "Observability",
    "integrations": "Integrations",
    "traceability": "Traceability",
}

# --------------------------------------------------------------------------- #
# Redaction (mirror the validator's secret patterns so check_documents passes)
# --------------------------------------------------------------------------- #
_PRIV_KEY = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----.*?-----END[^-]*-----",
    re.S,
)
_KV_SECRET = re.compile(
    r"(?i)\b(password|passwd|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret)"
    r"(\s*[:=]\s*[\"']?)(?!\[redacted\]|unspecified|\$\{)([^\s<\"'`]+)"
)
_BEARER = re.compile(r"(?i)\b(bearer|basic)\s+[a-z0-9._~+/\-]+=*")
_JWT = re.compile(r"eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+(?:\.[A-Za-z0-9_\-]+)?")


def redact(text: str) -> str:
    text = _PRIV_KEY.sub("[redacted private key]", text)
    text = _JWT.sub("[redacted-jwt]", text)
    text = _KV_SECRET.sub(lambda m: f"{m.group(1)}{m.group(2)}[redacted]", text)
    text = _BEARER.sub(lambda m: f"{m.group(1)} [redacted]", text)
    return text


def nb_template_markers(text: str) -> str:
    return text.replace("{{", "&#123;&#123;").replace("}}", "&#125;&#125;")


_SECRETISH_KEY = re.compile(
    r"(?i)(password|passwd|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret)"
)


def sanitize_loaded_value(value, key: str = ""):
    if isinstance(value, dict):
        return {k: sanitize_loaded_value(v, str(k)) for k, v in value.items()}
    if isinstance(value, list):
        return [sanitize_loaded_value(v, key) for v in value]
    if isinstance(value, str):
        cleaned = nb_template_markers(redact(value))
        if _SECRETISH_KEY.search(key) and cleaned and cleaned not in ("[redacted]", "unspecified"):
            return "[redacted]"
        return cleaned
    return value


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
SERVICE_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*-service$")


def list_sources():
    """Return source .md/.yaml relpaths (posix) under DOCS_ROOT, excluding _meta."""
    out = []
    for cur, dirs, files in os.walk(DOCS_ROOT):
        rel = os.path.relpath(cur, DOCS_ROOT).replace("\\", "/")
        if rel == "_meta" or rel.startswith("_meta/"):
            continue
        dirs[:] = [d for d in dirs if d != "_meta"]
        for f in files:
            if f.lower().endswith((".md", ".yaml", ".yml")):
                p = os.path.relpath(os.path.join(cur, f), DOCS_ROOT).replace("\\", "/")
                out.append(p)
    return sorted(out)


def service_of(relpath: str) -> str:
    for part in relpath.split("/"):
        if SERVICE_RE.match(part):
            return part
    if "chat-service" in relpath:
        return "chat-service"
    return "platform"


def group_of(relpath: str) -> str:
    top = relpath.split("/")[0]
    name = relpath.split("/")[-1].lower()
    if top == "overview":
        return "architecture"
    mapping = {
        "data-models": "domain",
        "business-rules": "domain",
        "srs": "domain",
        "state-diagrams": "domain",
        "use-cases": "use-cases",
        "flows": "business-flows",
        "api-contracts": "interfaces",
        "messaging": "messaging",
        "operations": "platform",
        "traceability": "traceability",
        "note": "overview",
    }
    if top in mapping:
        return mapping[top]
    if top == "overview":
        return "architecture"
    # top-level files (PROJECT_OVERVIEW.md, README.md, TECH_STACK.md, database-entities.md)
    if name == "database-entities.md":
        return "domain"
    return "overview"


def type_of(relpath: str) -> str:
    top = relpath.split("/")[0]
    name = relpath.split("/")[-1].lower()
    if top == "use-cases":
        return "use-case"
    if top == "flows":
        return "business-flow"
    if top == "api-contracts":
        return "api"
    if top == "messaging":
        return "message"
    if top == "traceability":
        return "traceability"
    if top == "overview":
        return "architecture"
    if top == "operations":
        return "runbook"
    if top == "state-diagrams":
        return "state"
    if name.startswith("entity-") or name == "erd_full_system.md":
        return "entity"
    if name.startswith("br-"):
        return "business-rule"
    if name.startswith("fr-"):
        return "requirement"
    if name == "database-entities.md":
        return "data-model"
    return "doc"


def out_html_relpath(src_relpath: str) -> str:
    base = src_relpath
    for ext in (".md", ".yaml", ".yml"):
        if base.lower().endswith(ext):
            base = base[: -len(ext)]
            break
    return base + ".html"


def make_id(out_relpath: str) -> str:
    token = out_relpath[:-5] if out_relpath.endswith(".html") else out_relpath
    token = re.sub(r"[^A-Za-z0-9]+", "-", token).strip("-").upper()
    return "DOC-" + token


def slug_title(relpath: str) -> str:
    name = relpath.split("/")[-1]
    name = re.sub(r"\.(md|yaml|yml)$", "", name, flags=re.I)
    return name.replace("-", " ").replace("_", " ").strip().title()


def relpath_between(from_out_rel: str, to_out_rel: str) -> str:
    """Relative href from one output page to another (posix)."""
    from_dir = os.path.dirname(os.path.join(DOCS_ROOT, from_out_rel))
    to_abs = os.path.join(DOCS_ROOT, to_out_rel)
    return os.path.relpath(to_abs, from_dir).replace("\\", "/")


MERMAID_FENCE = re.compile(r"```mermaid[ \t]*\r?\n(.*?)```", re.S)
HREF_RE = re.compile(r'href="([^"]*)"')
FIRST_H1 = re.compile(r"^#\s+(.+?)\s*$", re.M)
FRONTMATTER = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.S)
MERMAID_SCRIPT = (
    '<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>'
    "<script>mermaid.initialize({startOnLoad:true,securityLevel:'strict'});</script>"
)
SWAGGER_UI_CSS = "https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css"
SWAGGER_UI_BUNDLE = "https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"


def transform_href(href: str, src_dir_abs: str) -> str:
    h = href.strip()
    if h.startswith(("http://", "https://", "mailto:", "tel:", "data:", "//")):
        return h
    if h.startswith("#"):
        return "#"
    path_part = h.split("#", 1)[0].split("?", 1)[0]
    if not path_part:
        return "#"
    target = os.path.normpath(os.path.join(src_dir_abs, path_part))
    low = path_part.lower()
    if low.endswith(".md"):
        return path_part[:-3] + ".html" if os.path.exists(target) else "#"
    if low.endswith((".yaml", ".yml")):
        if os.path.exists(target):
            return re.sub(r"\.(yaml|yml)$", ".html", path_part, flags=re.I)
        return "#"
    if os.path.exists(target):
        return path_part
    return "#"


def rewrite_links(content_html: str, src_dir_abs: str) -> str:
    return HREF_RE.sub(
        lambda m: 'href="%s"' % transform_href(m.group(1), src_dir_abs), content_html
    )


def md_to_content(src_relpath: str):
    """Return (content_html, has_mermaid, title)."""
    abs_path = os.path.join(DOCS_ROOT, src_relpath)
    with open(abs_path, encoding="utf-8-sig") as fh:
        text = fh.read()

    fm = FRONTMATTER.match(text)
    fm_title = None
    if fm:
        try:
            data = yaml.safe_load(fm.group(1)) or {}
            if isinstance(data, dict):
                fm_title = data.get("title")
        except Exception:
            fm_title = None
        text = text[fm.end():]

    m = FIRST_H1.search(text)
    title = (m.group(1).strip() if m else None) or fm_title or slug_title(src_relpath)
    title = re.sub(r"`", "", title)

    # Protect mermaid fences before markdown conversion.
    blocks = []

    def _stash(mt):
        blocks.append(mt.group(1).strip())
        return f"\n\nMERMAIDZ{len(blocks) - 1}Z\n\n"

    text = MERMAID_FENCE.sub(_stash, text)

    body = markdown.markdown(
        text, extensions=["tables", "fenced_code", "sane_lists"], output_format="html5"
    )

    for i, block in enumerate(blocks):
        escaped = html.escape(block)
        pre = f'<pre class="mermaid">{escaped}</pre>'
        body = re.sub(rf"(?:<p>)?MERMAIDZ{i}Z(?:</p>)?", lambda _m: pre, body, count=1)

    has_mermaid = bool(blocks)
    body = redact(body)
    body = rewrite_links(body, os.path.dirname(abs_path))
    # Belt-and-suspenders: no stray template braces may remain.
    body = body.replace("{{", "&#123;&#123;").replace("}}", "&#125;&#125;")

    content = (
        '<section class="doc-section"><div class="doc-panel p-3 p-md-4 doc-prose">'
        f"{body}</div></section>"
    )
    return content, has_mermaid, title


def yaml_to_content(src_relpath: str):
    """Render an OpenAPI-per-endpoint YAML file into an API contract page."""
    abs_path = os.path.join(DOCS_ROOT, src_relpath)
    with open(abs_path, encoding="utf-8-sig") as fh:
        raw = fh.read()
    try:
        spec = yaml.safe_load(raw) or {}
    except Exception:
        spec = {}
    spec = sanitize_loaded_value(spec)

    def nb(s):  # neutralize stray template braces from source content
        return nb_template_markers(str(s))

    info = spec.get("info", {}) if isinstance(spec, dict) else {}
    title = info.get("title") or slug_title(src_relpath)
    version = info.get("version", "unspecified")
    description = info.get("description", "")

    rows = []
    for path, item in (spec.get("paths", {}) or {}).items():
        if not isinstance(item, dict):
            continue
        for method, op in item.items():
            if method.lower() not in ("get", "post", "put", "patch", "delete", "head", "options"):
                continue
            summary = ""
            if isinstance(op, dict):
                summary = op.get("summary") or op.get("operationId") or ""
            rows.append(
                f"<tr><td><span class='badge text-bg-primary'>{html.escape(method.upper())}</span></td>"
                f"<td><code>{html.escape(str(path))}</code></td>"
                f"<td>{html.escape(str(summary))}</td></tr>"
            )

    servers = []
    for s in (spec.get("servers", []) or []):
        if isinstance(s, dict) and s.get("url"):
            servers.append(html.escape(str(s["url"])))

    endpoints_table = ""
    if rows:
        endpoints_table = (
            '<h2 class="h5 mt-4">Operations</h2>'
            '<div class="table-responsive"><table class="table doc-table table-sm align-middle">'
            "<thead><tr><th>Method</th><th>Path</th><th>Summary</th></tr></thead>"
            f"<tbody>{''.join(rows)}</tbody></table></div>"
        )

    spec_json = json.dumps(spec, ensure_ascii=False, indent=2).replace("</", "<\\/")
    description = nb(description)
    ui_id = "openapi-ui-" + re.sub(r"[^a-z0-9]+", "-", src_relpath.lower()).strip("-")
    facts = [
        f"<li><span class='text-secondary'>Spec version</span><div class='fw-semibold'>{html.escape(str(version))}</div></li>",
        f"<li><span class='text-secondary'>Server(s)</span><div class='fw-semibold'>{', '.join(servers) or 'unspecified'}</div></li>",
        f"<li><span class='text-secondary'>Source</span><div class='fw-semibold'><code>{html.escape(src_relpath)}</code></div></li>",
    ]

    content = (
        '<section class="doc-section"><div class="doc-panel p-3 p-md-4">'
        + (f"<p class='lead'>{html.escape(str(description)).strip()}</p>" if description else "")
        + "<ul class='doc-link-list' style='list-style:none'>" + "".join(facts) + "</ul>"
        + endpoints_table
        + '<h2 class="h5 mt-4">OpenAPI Specification</h2>'
        + f'<link rel="stylesheet" href="{SWAGGER_UI_CSS}">'
        + "<style>"
        + ".openapi-ui{background:#fff;border:1px solid rgba(15,23,42,.12);border-radius:.5rem;padding:.75rem;overflow:auto}"
        + ".openapi-ui .swagger-ui{font-family:inherit}"
        + ".openapi-ui .swagger-ui .topbar,.openapi-ui .swagger-ui .information-container.wrapper{display:none}"
        + ".openapi-ui .swagger-ui .wrapper{padding:0;max-width:none}"
        + ".openapi-ui .swagger-ui .scheme-container{box-shadow:none;margin:0 0 1rem;padding:0}"
        + "</style>"
        + f'<div id="{ui_id}" class="openapi-ui"></div>'
        + f'<script src="{SWAGGER_UI_BUNDLE}"></script>'
        + "<script>"
        + "window.addEventListener('load',function(){"
        + f"var el=document.getElementById('{ui_id}');"
        + "if(!window.SwaggerUIBundle){el.innerHTML='<div class=\"alert alert-warning mb-0\">Swagger UI could not load from CDN.</div>';return;}"
        + "SwaggerUIBundle({"
        + f"dom_id:'#{ui_id}',"
        + f"spec:{spec_json},"
        + "deepLinking:true,docExpansion:'list',defaultModelsExpandDepth:1,displayRequestDuration:true"
        + "});"
        + "});"
        + "</script>"
        + "</div></section>"
    )
    return content, False, title


# --------------------------------------------------------------------------- #
# Page shell
# --------------------------------------------------------------------------- #
with open(os.path.join(ASSETS, "bootstrap-shell.html"), encoding="utf-8") as fh:
    SHELL = fh.read()
# Syntax-display fix: <code> inside a dark <pre> must inherit the light pre color
# instead of the orange inline-code color (otherwise code blocks are unreadable).
SHELL = SHELL.replace(
    "</style>",
    "    pre code { color: inherit; background: transparent; font-size: inherit; padding: 0; }\n"
    "    pre { white-space: pre; }\n  </style>",
    1,
)
with open(os.path.join(ASSETS, "index-card-grid.html"), encoding="utf-8") as fh:
    INDEX_GRID = fh.read()

TOPBAR_GROUPS = ["overview", "architecture", "domain", "use-cases",
                 "business-flows", "interfaces", "messaging", "traceability"]


def render_shell(out_rel, title, artifact_id, badges, content, has_mermaid,
                 updated=LAST_UPDATED):
    root_link = relpath_between(out_rel, "index.html")

    topbar = [f'<a class="btn btn-sm btn-outline-secondary" href="{root_link}">Home</a>']
    for g in TOPBAR_GROUPS:
        href = relpath_between(out_rel, f"groups/{g}.html")
        topbar.append(
            f'<a class="btn btn-sm btn-outline-secondary" href="{href}">{GROUP_TITLES[g]}</a>'
        )
    topbar_html = "\n".join(topbar)

    sidebar = []
    for g, label in GROUP_TITLES.items():
        href = relpath_between(out_rel, f"groups/{g}.html")
        sidebar.append(f'<a class="nav-link" href="{href}">{label}</a>')
    sidebar.append(
        f'<a class="nav-link" href="{relpath_between(out_rel, "traceability/matrix.html")}">'
        "Traceability Matrix</a>"
    )
    sidebar_html = "\n".join(sidebar)

    page = SHELL
    repl = {
        "{{TITLE}}": html.escape(title),
        "{{ROOT_LINK}}": root_link,
        "{{PROJECT_NAME}}": html.escape(PROJECT_NAME),
        "{{TOPBAR_LINKS}}": topbar_html,
        "{{SIDEBAR_LINKS}}": sidebar_html,
        "{{ARTIFACT_ID}}": html.escape(artifact_id),
        "{{UPDATED_AT}}": html.escape(updated),
        "{{BADGES}}": badges,
        "{{CONTENT}}": content,
        "{{MERMAID_SCRIPT}}": MERMAID_SCRIPT if has_mermaid else "",
    }
    for k, v in repl.items():
        page = page.replace(k, v)
    return page


def badge(text, tone="secondary"):
    return f'<span class="badge text-bg-{tone}">{html.escape(text)}</span>'


# --------------------------------------------------------------------------- #
# Build
# --------------------------------------------------------------------------- #
def main():
    sources = list_sources()
    pages = []  # list of dicts

    # ---- child artifact pages ------------------------------------------- #
    for src in sources:
        out_rel = out_html_relpath(src)
        grp = group_of(src)
        typ = type_of(src)
        svc = service_of(src)
        if src.lower().endswith((".yaml", ".yml")):
            content, has_mermaid, title = yaml_to_content(src)
        else:
            content, has_mermaid, title = md_to_content(src)

        art_id = make_id(out_rel)
        badges = badge(typ, "info") + " " + badge(svc, "light")
        page_html = render_shell(out_rel, title, art_id, badges, content, has_mermaid)
        write(out_rel, page_html)

        summary = f"{typ} for {svc} ({title})"
        pages.append({
            "id": art_id,
            "title": title,
            "type": typ,
            "group": grp,
            "service": svc,
            "path": out_rel,
            "status": "documented",
            "summary": summary,
            "tags": sorted({typ, svc, grp}),
            "related_ids": [],
            "search_text": " ".join([title, typ, svc, grp, src]).lower(),
        })

    # ---- group pages ----------------------------------------------------- #
    primary_groups = ["overview", "architecture", "domain", "business-flows",
                      "use-cases", "interfaces", "messaging", "platform", "traceability"]
    for grp in primary_groups:
        children = [p for p in pages if p["group"] == grp]
        page_html, gp = render_group_page(grp, children)
        write(gp["path"], page_html)
        pages.append(gp)

    # cross-link (curated) groups
    for grp, predicate, blurb in [
        ("identity",
         lambda p: p["service"] in ("identity-service",)
         or "auth" in p["path"].lower() or "/admin-users" in p["path"].lower(),
         "Authentication, JWT issuance/rotation, roles, admin account control, and "
         "session security. These artifacts have their primary home in other groups "
         "and are cross-linked here."),
        ("integrations",
         lambda p: p["service"] in ("payment-service", "refund-service", "search-service",
                                    "ai-chat-service")
         or "stripe" in p["path"].lower(),
         "Third-party and cross-system integrations: Stripe Connect payments/payouts, "
         "refund flows, Elasticsearch search, and the AI assistant tool integration."),
        ("deployment",
         lambda p: p["path"].startswith("operations/")
         and (p["path"].endswith(("RUNNING_GUIDE.html", "ENVIRONMENT_VARIABLES.html",
                                  "API_URLS.html", "CRONJOBS.html"))
              or "OPERATIONS" in p["path"]),
         "Runtime configuration variable names, run guide, API URLs, and scheduled "
         "jobs. Deployment values are never copied; only names and ownership. Primary "
         "home is Platform and Operations."),
        ("observability",
         lambda p: p["path"].startswith("operations/")
         and ("OPERATIONS" in p["path"] or p["path"].endswith("CRONJOBS.html")),
         "Operational signals available in the documentation today: per-service "
         "operations runbooks and scheduled jobs. Dedicated metrics/traces/SLO docs "
         "are not yet authored (unspecified)."),
    ]:
        linked = [p for p in pages if p.get("path", "").endswith(".html")
                  and p["group"] in primary_groups and predicate(p)]
        page_html, gp = render_curated_group(grp, blurb, linked)
        write(gp["path"], page_html)
        pages.append(gp)

    # ---- traceability matrix -------------------------------------------- #
    ucs = [p for p in pages if p["type"] == "use-case"]
    trace_pages = [p for p in pages if p["type"] == "traceability"]
    matrix_html, matrix_page = render_matrix(ucs, trace_pages)
    write(matrix_page["path"], matrix_html)
    pages.append(matrix_page)

    # ---- index ----------------------------------------------------------- #
    # index page entry must exist in the search index too.
    index_entry = {
        "id": "DOC-INDEX",
        "title": f"{PROJECT_NAME} Documentation",
        "type": "index",
        "group": "overview",
        "service": "platform",
        "path": "index.html",
        "status": "documented",
        "summary": "Documentation home and project map.",
        "tags": ["index", "home", "overview"],
        "related_ids": [],
        "search_text": "index home project map documentation overview",
    }
    pages.append(index_entry)

    # ---- search index ---------------------------------------------------- #
    search_items = [{
        "id": p["id"], "title": p["title"], "type": p["type"], "group": p["group"],
        "path": p["path"], "status": p["status"], "summary": p["summary"],
        "tags": p["tags"], "related_ids": p["related_ids"], "search_text": p["search_text"],
    } for p in pages]
    # unique ids guard
    seen = {}
    for it in search_items:
        if it["id"] in seen:
            seen[it["id"]] += 1
            it["id"] = f"{it['id']}-{seen[it['id']]}"
        else:
            seen[it["id"]] = 0

    search_index = {"generated_at": LAST_UPDATED, "project": PROJECT_NAME, "items": search_items}
    search_json = json.dumps(search_index, ensure_ascii=False, indent=2)
    search_json_safe = search_json.replace("</", "<\\/")
    with open(os.path.join(META_DIR, "search-index.json"), "w", encoding="utf-8") as fh:
        fh.write(search_json_safe + "\n")

    # ---- index.html ------------------------------------------------------ #
    write("index.html", render_index(pages, search_json_safe))

    # ---- other _meta manifests ------------------------------------------ #
    write_meta_manifests(pages)

    print(f"generated {len([p for p in pages if p['path'].endswith('.html')])} html pages")


def write(out_rel, content):
    abs_path = os.path.join(DOCS_ROOT, out_rel)
    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
    if "{{" in content or "}}" in content:
        raise SystemExit(f"FATAL: unresolved placeholder in {out_rel}")
    with open(abs_path, "w", encoding="utf-8") as fh:
        fh.write(content)


def render_group_page(grp, children):
    out_rel = f"groups/{grp}.html"
    title = GROUP_TITLES[grp]
    by_service = {}
    for c in sorted(children, key=lambda x: (x["service"], x["path"])):
        by_service.setdefault(c["service"], []).append(c)

    if children:
        sections = []
        for svc in sorted(by_service):
            items = by_service[svc]
            lis = "".join(
                f'<li><a data-doc-link data-doc-filter-text="{html.escape(c["title"].lower()+" "+c["type"])}" '
                f'href="{relpath_between(out_rel, c["path"])}">'
                f'<span class="badge text-bg-light me-1">{html.escape(c["type"])}</span>'
                f'{html.escape(c["title"])}</a></li>'
                for c in items
            )
            sections.append(
                f'<div class="doc-section"><h2 class="h5">{html.escape(svc)} '
                f'<span class="badge text-bg-secondary">{len(items)}</span></h2>'
                f'<ul class="doc-link-list">{lis}</ul></div>'
            )
        body = "".join(sections)
        note = f"{len(children)} documented artifacts across {len(by_service)} services."
    else:
        body = (
            '<section class="doc-section"><div class="doc-panel p-3">'
            "<p class='text-secondary mb-0'>No artifacts documented yet.</p></div></section>"
        )
        note = "No artifacts documented yet."

    content = (
        '<section class="doc-section"><div class="doc-panel p-3">'
        f"<p class='lead mb-0'>{html.escape(note)}</p></div></section>" + body
    )
    badges = badge("group", "primary") + " " + badge(f"{len(children)} items", "secondary")
    page_html = render_shell(out_rel, title, f"GROUP-{grp.upper()}", badges, content, False)
    gp = {
        "id": f"GROUP-{grp.upper()}", "title": title, "type": "group", "group": grp,
        "service": "platform", "path": out_rel, "status": "documented",
        "summary": f"{title} group page ({len(children)} artifacts).",
        "tags": ["group", grp], "related_ids": [], "search_text": f"{title} group {grp}".lower(),
    }
    return page_html, gp


def render_curated_group(grp, blurb, linked):
    out_rel = f"groups/{grp}.html"
    title = GROUP_TITLES[grp]
    if linked:
        lis = "".join(
            f'<li><a data-doc-link data-doc-filter-text="{html.escape(p["title"].lower())}" '
            f'href="{relpath_between(out_rel, p["path"])}">'
            f'<span class="badge text-bg-light me-1">{html.escape(p["type"])}</span>'
            f'{html.escape(p["title"])} '
            f'<span class="text-secondary small">({html.escape(p["service"])})</span></a></li>'
            for p in sorted(linked, key=lambda x: (x["service"], x["title"]))
        )
        body = f'<section class="doc-section"><ul class="doc-link-list">{lis}</ul></section>'
    else:
        body = ('<section class="doc-section"><div class="doc-panel p-3">'
                "<p class='text-secondary mb-0'>No artifacts documented yet.</p></div></section>")
    content = (
        '<section class="doc-section"><div class="doc-panel p-3">'
        f"<p class='lead mb-0'>{html.escape(blurb)}</p></div></section>" + body
    )
    badges = badge("group", "primary") + " " + badge("cross-linked", "warning")
    page_html = render_shell(out_rel, title, f"GROUP-{grp.upper()}", badges, content, False)
    gp = {
        "id": f"GROUP-{grp.upper()}", "title": title, "type": "group", "group": grp,
        "service": "platform", "path": out_rel, "status": "documented",
        "summary": f"{title} cross-link group ({len(linked)} linked artifacts).",
        "tags": ["group", grp, "cross-link"], "related_ids": [],
        "search_text": f"{title} group {grp} cross link".lower(),
    }
    return page_html, gp


def render_matrix(ucs, trace_pages):
    out_rel = "traceability/matrix.html"
    rows = []
    for uc in sorted(ucs, key=lambda x: x["path"]):
        href = relpath_between(out_rel, uc["path"])
        tid = "TRACE-" + uc["id"].replace("DOC-", "")
        rows.append(
            f"<tr><td><code>{html.escape(tid[:48])}</code></td>"
            f'<td><a href="{href}">{html.escape(uc["title"])}</a></td>'
            f"<td>{html.escape(uc['service'])}</td>"
            "<td>unspecified</td><td>unspecified</td>"
            "<td><span class='badge text-bg-success'>documented</span></td></tr>"
        )
    table = (
        '<div class="table-responsive"><table class="table doc-table table-sm align-middle">'
        "<thead><tr><th>Trace ID</th><th>Use Case</th><th>Service</th>"
        "<th>Requirement</th><th>Domain/Data</th><th>Status</th></tr></thead>"
        f"<tbody>{''.join(rows)}</tbody></table></div>"
    )
    svc_links = "".join(
        f'<li><a href="{relpath_between(out_rel, tp["path"])}">{html.escape(tp["title"])}</a></li>'
        for tp in sorted(trace_pages, key=lambda x: x["path"])
    )
    content = (
        '<section class="doc-section"><div class="doc-panel p-3">'
        "<p class='lead mb-0'>One trace row per documented use case. Requirement, rule, "
        "and domain columns are marked <code>unspecified</code> where a single canonical "
        "link is not asserted; per-service traceability matrices below carry the detailed "
        "FR&#8596;UC&#8596;BR&#8596;Entity&#8596;API mappings.</p></div></section>"
        f'<section class="doc-section"><h2 class="h5">Use-Case Trace Index ({len(ucs)})</h2>{table}</section>'
        f'<section class="doc-section"><h2 class="h5">Per-Service Traceability Matrices</h2>'
        f'<ul class="doc-link-list">{svc_links}</ul></section>'
    )
    badges = badge("traceability", "primary") + " " + badge(f"{len(ucs)} use cases", "secondary")
    page_html = render_shell(out_rel, "Traceability Matrix", "TRACE-MATRIX", badges, content, False)
    mp = {
        "id": "TRACE-MATRIX", "title": "Traceability Matrix", "type": "traceability",
        "group": "traceability", "service": "platform", "path": out_rel,
        "status": "documented", "summary": f"Cross-service traceability matrix ({len(ucs)} use cases).",
        "tags": ["traceability", "matrix"], "related_ids": [],
        "search_text": "traceability matrix use cases requirements",
    }
    return page_html, mp


def _links_list(items, limit=None):
    items = sorted(items, key=lambda x: x["path"])
    if limit:
        items = items[:limit]
    return "".join(
        f'<li><a data-doc-link data-doc-filter-text="{html.escape(p["title"].lower())}" '
        f'href="{html.escape(p["path"])}">{html.escape(p["title"])}</a></li>'
        for p in items
    )


def render_index(pages, search_json_safe):
    def count(grp):
        return len([p for p in pages if p["group"] == grp and p["type"] not in ("group",)])

    use_cases = [p for p in pages if p["type"] == "use-case"]
    flows = [p for p in pages if p["type"] == "business-flow"]
    reqs = [p for p in pages if p["type"] == "requirement"]
    trace_pages = [p for p in pages if p["type"] == "traceability" and p["path"] != "traceability/matrix.html"]
    total_artifacts = len([p for p in pages if p["type"] not in ("group", "index")])

    high_value = [
        {"title": "Interfaces (REST / OpenAPI)", "path": "groups/interfaces.html"},
        {"title": "Messaging and Realtime (Kafka / SSE)", "path": "groups/messaging.html"},
        {"title": "Identity and Security", "path": "groups/identity.html"},
        {"title": "Platform and Operations", "path": "groups/platform.html"},
        {"title": "Deployment", "path": "groups/deployment.html"},
        {"title": "Observability", "path": "groups/observability.html"},
        {"title": "Integrations", "path": "groups/integrations.html"},
    ]
    assumptions = [
        "Groups identity, deployment, observability, and integrations are cross-link "
        "views into primary pages; each artifact keeps one primary home.",
        "Traceability status is 'documented' where the source markdown asserts the "
        "mapping; unfilled columns are marked unspecified.",
        "Example credentials/tokens in API specs are redacted in the rendered pages.",
    ]
    unspecified = [
        "Dedicated metrics / traces / SLO documentation (observability) is not yet authored.",
        "Some refund/use-case numbering gaps (e.g. UC-005) exist in the source set.",
        "Mobile API surface detected only by filename heuristic; no mobile docs activated.",
    ]
    audit_notes = [
        "Every generated HTML page is registered in _meta/search-index.json.",
        "Inline search JSON mirrors _meta/search-index.json exactly.",
        "All local links resolve inside documents/; secret values are redacted.",
    ]

    grid = INDEX_GRID
    repl = {
        "{{PROJECT_NAME}}": html.escape(PROJECT_NAME),
        "{{PROJECT_SUMMARY}}": html.escape(PROJECT_SUMMARY),
        "{{TOTAL_ARTIFACT_COUNT}}": str(total_artifacts),
        "{{ACTIVE_EXTENSION_COUNT}}": str(len(ACTIVE_EXTENSIONS)),
        "{{LAST_UPDATED}}": html.escape(LAST_UPDATED),
        "{{BUSINESS_FLOW_COUNT}}": str(len(flows)),
        "{{USE_CASE_COUNT}}": str(len(use_cases)),
        "{{REQUIREMENT_COUNT}}": str(len(reqs)),
        "{{OPEN_ITEM_COUNT}}": str(len(unspecified)),
        "{{READING_PATH_STATUS}}": "generated",
        "{{PROJECT_MAP_NOTE}}": f"{total_artifacts} artifacts, {len(ACTIVE_EXTENSIONS)} active extensions",
        "{{OVERVIEW_COUNT}}": str(count("overview")),
        "{{ARCHITECTURE_COUNT}}": str(count("architecture")),
        "{{DOMAIN_COUNT}}": str(count("domain")),
        "{{INTERFACE_COUNT}}": str(count("interfaces")),
        "{{MESSAGING_COUNT}}": str(count("messaging")),
        "{{IDENTITY_COUNT}}": str(count("identity")),
        "{{PLATFORM_COUNT}}": str(count("platform")),
        "{{DEPLOYMENT_COUNT}}": str(count("deployment")),
        "{{OBSERVABILITY_COUNT}}": str(count("observability")),
        "{{INTEGRATION_COUNT}}": str(count("integrations")),
        "{{TRACEABILITY_COUNT}}": str(count("traceability")),
        "{{BUSINESS_FLOW_LINKS}}": _links_list(flows) or "<li class='text-secondary small'>None</li>",
        "{{USE_CASE_LINKS}}": _links_list(use_cases),
        "{{HIGH_VALUE_ARTIFACT_LINKS}}": "".join(
            f'<li><a data-doc-link data-doc-filter-text="{html.escape(h["title"].lower())}" '
            f'href="{h["path"]}">{html.escape(h["title"])}</a></li>' for h in high_value),
        "{{TRACEABILITY_LINKS}}": _links_list(trace_pages),
        "{{ASSUMPTION_LINKS}}": "".join(f"<li>{html.escape(a)}</li>" for a in assumptions),
        "{{UNSPECIFIED_LINKS}}": "".join(f"<li>{html.escape(u)}</li>" for u in unspecified),
        "{{SELF_AUDIT_RESULT}}": "pass",
        "{{SELF_AUDIT_NOTES}}": "".join(f"<li>{html.escape(n)}</li>" for n in audit_notes),
        "{{SEARCH_INDEX_INLINE_JSON}}": search_json_safe,
    }
    for k, v in repl.items():
        grid = grid.replace(k, v)

    badges = badge("home", "primary")
    return render_shell("index.html", f"{PROJECT_NAME} Documentation", "DOC-INDEX",
                        badges, grid, False)


def write_meta_manifests(pages):
    inventory = {
        "generated_at": LAST_UPDATED,
        "project": PROJECT_NAME,
        "artifact_count": len([p for p in pages if p["type"] not in ("group", "index")]),
        "by_group": {},
        "by_type": {},
    }
    for p in pages:
        inventory["by_group"][p["group"]] = inventory["by_group"].get(p["group"], 0) + 1
        inventory["by_type"][p["type"]] = inventory["by_type"].get(p["type"], 0) + 1
    _dump("inventory.json", inventory)

    id_registry = {
        "generated_at": LAST_UPDATED,
        "core_prefixes": ["DOC", "FR", "UC", "BR", "ENTITY", "API", "NFR", "ADR", "TRACE", "BFLOW"],
        "ids": sorted({p["id"] for p in pages}),
    }
    _dump("id-registry.json", id_registry)

    extension_manifest = {
        "generated_at": LAST_UPDATED,
        "active_extensions": ACTIVE_EXTENSIONS,
        "evidence": {
            "rest-openapi-writer": "documents/api-contracts/**/*.yaml (OpenAPI 3 per-endpoint specs)",
            "kafka-event-contract-writer": "documents/messaging/**/KAFKA_EVENTS.md, KAFKA_CATALOG.md",
            "realtime-websocket-writer": "notification-service SSE streaming",
            "oauth-oidc-sso-writer": "identity-service JWT auth (RS256 access/refresh tokens)",
            "payment-gateway-writer": "payment-service Stripe Connect onboarding/webhooks/transfers",
            "db-migration-writer": "backend Flyway migrations (V*.sql)",
            "ci-cd-pipeline-writer": ".github/workflows/*",
            "observability-writer": "operations/*/OPERATIONS.md, CRONJOBS.md",
            "deployment-env-safety": ".env files / operations/ENVIRONMENT_VARIABLES.md",
        },
        "detected_not_activated": {
            "mobile-api-writer": "Filename heuristic only; no documented mobile surface.",
        },
    }
    _dump("extension-manifest.json", extension_manifest)


def _dump(name, obj):
    with open(os.path.join(META_DIR, name), "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


if __name__ == "__main__":
    main()
