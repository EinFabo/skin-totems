"""Rendert das Plushie aus plushie.json mit einem Skin als scharfes PNG (z. B. fürs Modrinth-Icon).

Gleiche Geometrie wie im Spiel (ModelPart-Quader, Skin-UVs wie beim Spielermodell), orthografische
Ansicht, Minecraft-artiges Licht, Supersampling für weiche Kanten.

  python3 tools/render_plushie.py SKIN.png OUT.png --size 512 --yaw -30 --pitch 12 [--slim]
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GEOMETRY = ROOT / "src/client/resources/assets/skintotems/plushie/plushie.json"


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], float)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], float)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], float)


def translate(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale(x, y, z):
    return np.diag([x, y, z, 1.0])


def cube_faces(u, v, x, y, z, w, h, d, grow, mirror, tw, th):
    """Wie ModelPart.Cube: Liste von (4 Ecken, 4 UVs)."""
    x0, y0, z0 = x - grow, y - grow, z - grow
    x1, y1, z1 = x + w + grow, y + h + grow, z + d + grow
    if mirror:
        x0, x1 = x1, x0
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    faces = [
        ([l1, l0, t0, t1], u1, v0, u2, v1),
        ([t2, t3, l3, l2], u2, v1, u22, v0),
        ([t0, l0, l3, t3], u0, v1, u1, v2),
        ([t1, t0, t3, t2], u1, v1, u2, v2),
        ([l1, t1, t2, l2], u2, v1, u3, v2),
        ([l0, l1, l2, l3], u3, v1, u4, v2),
    ]
    out = []
    for verts, U0, V0, U1, V1 in faces:
        quad = [(verts[0], (U1, V0)), (verts[1], (U0, V0)), (verts[2], (U0, V1)), (verts[3], (U1, V1))]
        if mirror:
            quad.reverse()
        out.append(([np.array(p, float) for p, _ in quad], [(a / tw, b / th) for _, (a, b) in quad]))
    return out


def collect(parts, matrix, slim, tw, th, out):
    for part in parts.values():
        pv = part.get("pivot", [0, 0, 0])
        r = [math.radians(a) for a in part.get("rotation", [0, 0, 0])]
        s = part.get("scale", [1, 1, 1])
        m = matrix @ translate(pv[0] / 16, pv[1] / 16, pv[2] / 16) @ rot_z(r[2]) @ rot_y(r[1]) @ rot_x(r[0]) @ scale(*s)
        cubes = part["slim_cubes"] if slim and "slim_cubes" in part else part.get("cubes", [])
        for c in cubes:
            for verts, uvs in cube_faces(*c["uv"], *c["from"], *c["size"], c.get("inflate", 0), c.get("mirror", False), tw, th):
                world = [(m @ np.append(p / 16, 1.0))[:3] for p in verts]
                out.append((world, uvs))
        collect(part.get("children", {}), m, slim, tw, th, out)


def render(skin_path, size, yaw, pitch, slim, supersample=4, margin=0.08):
    geometry = json.loads(GEOMETRY.read_text())
    tw, th = geometry.get("texture_width", 64), geometry.get("texture_height", 64)
    skin = np.asarray(Image.open(skin_path).convert("RGBA"), float) / 255.0
    faces = []
    collect(geometry["parts"], np.eye(4), slim, tw, th, faces)

    # Ansicht: Gesicht zeigt nach -Z (zur Kamera), Y nach unten wie im Bild
    view = rot_x(math.radians(-pitch)) @ rot_y(math.radians(yaw))
    pts = [[(view @ np.append(p, 1.0))[:3] for p in quad] for quad, _ in faces]
    allp = np.array([p for q in pts for p in q])
    lo, hi = allp.min(0), allp.max(0)
    n = size * supersample
    extent = max(hi[0] - lo[0], hi[1] - lo[1])
    k = n * (1 - 2 * margin) / extent
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    color = np.zeros((n, n, 4))
    depth = np.full((n, n), np.inf)
    light0 = np.array([0.2, -1.0, -0.7]) / np.linalg.norm([0.2, -1.0, -0.7])
    light1 = np.array([-0.2, -1.0, 0.7]) / np.linalg.norm([-0.2, -1.0, 0.7])

    for quad, (_, uvs) in zip(pts, faces):
        sp = [np.array([(p[0] - cx) * k + n / 2, (p[1] - cy) * k + n / 2, p[2]]) for p in quad]
        e1, e3 = sp[1] - sp[0], sp[3] - sp[0]
        det = e1[0] * e3[1] - e1[1] * e3[0]
        if abs(det) < 1e-9:
            continue
        normal = np.cross(quad[1] - quad[0], quad[3] - quad[0])
        normal /= np.linalg.norm(normal) or 1
        if normal[2] > 0:
            normal = -normal  # zur Kamera drehen (sichtbare Seite)
        shade = min(1.0, 0.4 + 0.6 * (max(0, normal @ -light0) + max(0, normal @ -light1)))
        xs = [p[0] for p in sp]
        ys = [p[1] for p in sp]
        x0, x1 = max(int(min(xs)), 0), min(int(max(xs)) + 1, n)
        y0, y1 = max(int(min(ys)), 0), min(int(max(ys)) + 1, n)
        if x0 >= x1 or y0 >= y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = gx - sp[0][0], gy - sp[0][1]
        a = (dx * e3[1] - dy * e3[0]) / det
        b = (e1[0] * dy - e1[1] * dx) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        z = sp[0][2] + a * e1[2] + b * e3[2]
        uv0, uv1, uv3 = np.array(uvs[0]), np.array(uvs[1]), np.array(uvs[3])
        u = uv0[0] + a * (uv1[0] - uv0[0]) + b * (uv3[0] - uv0[0])
        v = uv0[1] + a * (uv1[1] - uv0[1]) + b * (uv3[1] - uv0[1])
        umin, umax = sorted((uv0[0], uv1[0], uv3[0]))[0], sorted((uv0[0], uv1[0], uv3[0]))[-1]
        vmin, vmax = sorted((uv0[1], uv1[1], uv3[1]))[0], sorted((uv0[1], uv1[1], uv3[1]))[-1]
        px = np.clip((u * skin.shape[1]).astype(int), int(umin * skin.shape[1]), max(int(umax * skin.shape[1]) - 1, 0))
        py = np.clip((v * skin.shape[0]).astype(int), int(vmin * skin.shape[0]), max(int(vmax * skin.shape[0]) - 1, 0))
        tex = skin[py, px]
        sub = depth[y0:y1, x0:x1]
        mask = inside & (tex[..., 3] >= 0.1) & (z < sub)
        sub[mask] = z[mask]
        rgb = tex[..., :3] * shade
        col = color[y0:y1, x0:x1]
        col[mask, :3] = rgb[mask]
        col[mask, 3] = 1.0

    img = Image.fromarray((color * 255).round().astype(np.uint8), "RGBA")
    return img.resize((size, size), Image.LANCZOS) if supersample > 1 else img


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("skin")
    ap.add_argument("out")
    ap.add_argument("--size", type=int, default=512)
    ap.add_argument("--yaw", type=float, default=-30)
    ap.add_argument("--pitch", type=float, default=12)
    ap.add_argument("--slim", action="store_true")
    args = ap.parse_args()
    render(args.skin, args.size, args.yaw, args.pitch, args.slim).save(args.out)
