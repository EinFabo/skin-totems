"""Legt Skin Totems auf Modrinth als Entwurf an und lädt alles hoch: Icon, Beschreibung, Links,
Galerie und die Version mit Jar. Mehrfach ausführbar: schon Vorhandenes wird übersprungen.

  MODRINTH_TOKEN=mrp_... python3 tools/modrinth_publish.py

Token: modrinth.com → Settings → Personal access tokens. Benötigte Rechte: Create projects,
Read projects, Write projects, Create versions, Read versions.

Das Projekt bleibt ein Entwurf. Einreichen geht danach auf der Projektseite mit
„Submit for review“ (oder mit --submit).
"""
import argparse
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

API = "https://api.modrinth.com/v2"
USER_AGENT = "EinFabo/skin-totems-publisher (github.com/EinFabo/skin-totems)"
ROOT = Path(__file__).resolve().parent.parent
RELEASE = ROOT / "release"
MODRINTH = RELEASE / "modrinth"

SLUG = "skin-totems"
VERSION = "1.0.0+26.2"
JAR = RELEASE / f"skintotems-{VERSION}.jar"
FABRIC_API = "P7dR8mSH"
MOD_MENU = "mOgUt4GM"

GALLERY = [
    ("01_plushies.png", "Plushies for every skin", "Rendered from the in-game plushie model with five different skins.", True),
    ("02_plushie_activation.png", "Plushie totem pop", "Your own plushie in the totem activation animation.", False),
    ("03_plushie_first_person.png", "Plushies in your hands", "First person with the plushie style.", False),
    ("04_totem_everyone.png", "Everyone holds their own", "Every player holds a totem with their own skin.", False),
    ("05_totem_first_person.png", "Skin totems", "First person with the flat totem style.", False),
    ("06_totem_activation.png", "Skin totem pop", "Your skin totem in the activation animation.", False),
    ("07_settings.png", "Settings with live preview", "Style, where to show it, layers and size.", False),
    ("08_inventory.png", "Inventory", "Your plushie in the hotbar, inventory and on the player preview.", False),
    ("09_advancement.png", "Advancements", "Even advancement toasts show your skin.", False),
]


class Api:
    def __init__(self, token):
        self.token = token

    def request(self, method, path, body=None, content_type=None, query=None):
        url = API + path + ("?" + urllib.parse.urlencode(query) if query else "")
        headers = {"Authorization": self.token, "User-Agent": USER_AGENT}
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req) as resp:
                data = resp.read()
                return resp.status, json.loads(data) if data else None
        except urllib.error.HTTPError as e:
            text = e.read().decode("utf-8", "replace")
            try:
                return e.code, json.loads(text)
            except ValueError:
                return e.code, text

    def json(self, method, path, payload):
        return self.request(method, path, json.dumps(payload).encode(), "application/json")

    def multipart(self, method, path, fields, files):
        boundary = uuid.uuid4().hex
        parts = []
        for name, value in fields.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n'
                         f"Content-Type: application/json\r\n\r\n".encode() + value.encode() + b"\r\n")
        for name, path_ in files.items():
            mime = mimetypes.guess_type(path_.name)[0] or "application/java-archive"
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{path_.name}"\r\n'
                         f"Content-Type: {mime}\r\n\r\n".encode() + path_.read_bytes() + b"\r\n")
        body = b"".join(parts) + f"--{boundary}--\r\n".encode()
        return self.request(method, path, body, f"multipart/form-data; boundary={boundary}")


def check(status, data, what):
    if status >= 300:
        sys.exit(f"✗ {what} fehlgeschlagen (HTTP {status}): {data}")
    print(f"✓ {what}")
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--submit", action="store_true", help="Projekt danach zur Prüfung einreichen")
    args = ap.parse_args()
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        sys.exit("MODRINTH_TOKEN fehlt (siehe Kopf dieser Datei).")
    if not JAR.exists():
        sys.exit(f"{JAR} fehlt – erst ./gradlew build und die Jar nach release/ kopieren.")
    api = Api(token)

    status, project = api.request("GET", f"/project/{SLUG}")
    body = (MODRINTH / "description.md").read_text()
    summary = (MODRINTH / "summary.txt").read_text().strip()
    if status == 404:
        data = {
            "slug": SLUG,
            "title": "Skin Totems",
            "description": summary,
            "body": body,
            "project_type": "mod",
            "categories": ["decoration", "social"],
            "additional_categories": ["equipment"],
            "client_side": "required",
            "server_side": "unsupported",
            "license_id": "MIT",
            "source_url": "https://github.com/EinFabo/skin-totems",
            "issues_url": "https://github.com/EinFabo/skin-totems/issues",
            "initial_versions": [],
            "is_draft": True,
        }
        project = check(*api.multipart("POST", "/project", {"data": json.dumps(data)}, {"icon": MODRINTH / "icon.png"}),
                        "Projekt angelegt (Entwurf)")
    else:
        check(status, project, "Projekt gefunden")
        check(*api.json("PATCH", f"/project/{project['id']}", {"description": summary, "body": body}), "Beschreibung aktualisiert")
        check(*api.request("PATCH", f"/project/{project['id']}/icon", (MODRINTH / "icon.png").read_bytes(), "image/png", {"ext": "png"}),
              "Icon aktualisiert")
    project_id = project["id"]

    status, project = api.request("GET", f"/project/{project_id}")
    existing = {g.get("title") for g in project.get("gallery", [])}
    for ordering, (file, title, description, featured) in enumerate(GALLERY):
        if title in existing:
            print(f"· Galerie: {title} (schon da)")
            continue
        query = {"ext": "png", "featured": str(featured).lower(), "title": title, "description": description, "ordering": ordering}
        status, data = api.request("POST", f"/project/{project_id}/gallery", (MODRINTH / "gallery" / file).read_bytes(),
                                   "image/png", query)
        check(status, data, f"Galerie: {title}")

    status, versions = api.request("GET", f"/project/{project_id}/version")
    if any(v["version_number"] == VERSION for v in versions or []):
        print(f"· Version {VERSION} (schon da)")
    else:
        data = {
            "project_id": project_id,
            "name": "Skin Totems 1.0.0",
            "version_number": VERSION,
            "changelog": (MODRINTH / "changelog.md").read_text(),
            "dependencies": [
                {"project_id": FABRIC_API, "dependency_type": "required"},
                {"project_id": MOD_MENU, "dependency_type": "optional"},
            ],
            "game_versions": ["26.2"],
            "version_type": "release",
            "loaders": ["fabric"],
            "featured": True,
            "file_parts": ["file"],
            "primary_file": "file",
        }
        check(*api.multipart("POST", "/version", {"data": json.dumps(data)}, {"file": JAR}), f"Version {VERSION} hochgeladen")

    if args.submit:
        check(*api.json("PATCH", f"/project/{project_id}", {"status": "processing"}), "Zur Prüfung eingereicht")
    print(f"\nFertig: https://modrinth.com/mod/{SLUG}")


if __name__ == "__main__":
    main()
