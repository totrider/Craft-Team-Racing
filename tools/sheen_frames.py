"""
The Power Shield's sheen, as eight baked models (an approximation of the footage, not the game's own method).

The game draws the sheen (`highlight`) as a separate mesh that turns 22.5 degrees a frame for eight frames, and in the footage it only ever shows on the bubble, but the two meshes
do not coincide in the game's data (the sheen's vertices are 22 to 31 units from the cone's surface), and the game has no mask for it. So here every one of the eight visible frames
of the sweep is baked as its own model: the sheen mesh is turned to that frame's angle (the pose fitted by hand: scaled, moved, turned about z round its outer end), cut into
smaller triangles (the pictures' uv follows) and every vertex is moved onto the nearest point of the bubble's surface, measured in the bubble's DISPLAYED proportions (the renderer
stretches the cone differently along each axis). The renderer then draws frame i of the model with the bubble's own pose and nothing else.

Used by tools/extract_ctr_textures.py on the files that mesh_pack_files made for the shield layers (the sheen's picture atlas is shared, only the positions change).
"""
import math

import numpy as np

# the pose of the sheen inside the bubble's, as fitted by hand against the footage (blocks of the baked model, centred): scaled, then the offset, turned about z
SHEEN_SCALE = np.array([1.0, 1.0, 0.7])
SHEEN_OFFSET = np.array([0.0, 0.0, -0.12])
# the bubble's displayed proportions along the model's x, y, z (client/DebugCommands: shieldScale 1.65, 4.25, 0.5 on the turned axes: x -> x, z -> up (4.25), y -> depth (0.5))
DISPLAY = np.array([1.65, 0.5, 4.25])
# the turn's pivot is the sheen's OUTER end, the tip at the middle of the bubble's base (raw x 0, y -127: the point marked in the author's Blender file, where the cone's base middle and the
# sheen's tip meet). The sheen swings round it like a clock hand, so the crescent's tip does not swing the opposite way; the turn is about z, so only x and y matter. The baked models are
# centred on the pack's pivot (0, 33.5, 58.5) at a scale of 0.4375 / 72.53 blocks a unit.
PIVOT = np.array([0.0, (-127.04 - 33.5) * 0.4375 / 72.53, 0.0])
FRAMES = 8
FIRST_ANGLE = 270.0
STEP = 22.5
SUBDIVIDE = 2                 # each level splits a triangle in four


def _parse(obj_text):
    v, vt, f = [], [], []
    for line in obj_text.splitlines():
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            v.append([float(x) for x in p[1:4]])
        elif p[0] == "vt":
            vt.append([float(x) for x in p[1:3]])
        elif p[0] == "f":
            f.append([int(x.split("/")[0]) - 1 for x in p[1:4]])
    return np.array(v), np.array(vt), f


def _split(tri):
    """tri: (positions 3x3, uvs 3x2) -> four triangles (corner, midpoints), the winding kept."""
    p, t = tri
    pm = [(p[0] + p[1]) / 2, (p[1] + p[2]) / 2, (p[2] + p[0]) / 2]
    tm = [(t[0] + t[1]) / 2, (t[1] + t[2]) / 2, (t[2] + t[0]) / 2]
    return [(np.array([p[0], pm[0], pm[2]]), np.array([t[0], tm[0], tm[2]])),
            (np.array([pm[0], p[1], pm[1]]), np.array([tm[0], t[1], tm[1]])),
            (np.array([pm[2], pm[1], p[2]]), np.array([tm[2], tm[1], t[2]])),
            (np.array([pm[0], pm[1], pm[2]]), np.array([tm[0], tm[1], tm[2]]))]


def _closest(p, tri):
    """The closest point of a triangle to p (Ericson, Real-Time Collision Detection)."""
    a, b, c = tri
    ab, ac, ap = b - a, c - a, p - a
    d1, d2 = ab @ ap, ac @ ap
    if d1 <= 0 and d2 <= 0:
        return a
    bp = p - b
    d3, d4 = ab @ bp, ac @ bp
    if d3 >= 0 and d4 <= d3:
        return b
    vc = d1 * d4 - d3 * d2
    if vc <= 0 and d1 >= 0 and d3 <= 0:
        return a + ab * (d1 / (d1 - d3))
    cp = p - c
    d5, d6 = ab @ cp, ac @ cp
    if d6 >= 0 and d5 <= d6:
        return c
    vb = d5 * d2 - d1 * d6
    if vb <= 0 and d2 >= 0 and d6 <= 0:
        return a + ac * (d2 / (d2 - d6))
    va = d3 * d6 - d5 * d4
    if va <= 0 and (d4 - d3) >= 0 and (d5 - d6) >= 0:
        return b + (c - b) * ((d4 - d3) / ((d4 - d3) + (d5 - d6)))
    denom = 1.0 / (va + vb + vc)
    return a + ab * (vb * denom) + ac * (vc * denom)


def frames(files, cone_name="shield_green", sheen_name="shield_highlight", prefix="shield_sheen"):
    """The files of the eight sheen models (obj, mtl, model json) for a pack's {files}, from the cone's and the sheen's own files in it."""
    cone_v, _, cone_f = _parse(files["models/item/%s.obj" % cone_name])
    sheen_v, sheen_vt, sheen_f = _parse(files["models/item/%s.obj" % sheen_name])
    # the bubble's triangles in displayed proportions, centred (the renderer draws everything with a translate of -0.5)
    cone = [(cone_v[[a, b, c]] - 0.5) * DISPLAY for a, b, c in cone_f]
    # the sheen's triangles (positions in the baked model, uv), cut up
    tris = [(sheen_v[f] - 0.5, sheen_vt[f]) for f in sheen_f]
    for _ in range(SUBDIVIDE):
        tris = [piece for t in tris for piece in _split(t)]
    out = {}
    base_json = files["models/item/pickup_%s.json" % sheen_name]
    for i in range(FRAMES):
        angle = math.radians(FIRST_ANGLE + STEP * i)
        c, s = math.cos(angle), math.sin(angle)
        rot = np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])
        cache = {}
        v_lines, vt_lines, vn_lines, f_lines = [], [], [], []
        for p, t in tris:
            ids = []
            vn_lines.append("vn 0 1 0")
            for k in range(3):
                q = rot @ ((p[k] + SHEEN_OFFSET) * SHEEN_SCALE - PIVOT) + PIVOT               # offset, scale, then the turn about z round the outer end
                key = tuple(np.round(q, 6))
                if key not in cache:
                    d = q * DISPLAY
                    best, best_d = None, 1e18
                    for tri in cone:
                        x = _closest(d, tri)
                        dd = float(((x - d) ** 2).sum())
                        if dd < best_d:
                            best, best_d = x, dd
                    cache[key] = best / DISPLAY + 0.5
                v_lines.append("v %.5f %.5f %.5f" % tuple(cache[key]))
                vt_lines.append("vt %.5f %.5f" % tuple(t[k]))
                ids.append(len(v_lines))
            f_lines.append("f %d/%d/%d %d/%d/%d %d/%d/%d" % (ids[0], ids[0], len(vn_lines), ids[1], ids[1], len(vn_lines), ids[2], ids[2], len(vn_lines)))
        name = "%s_%d" % (prefix, i)
        obj = ["# CTR sheen frame %d from the owner's own disc (local use only)" % i, "mtllib %s.mtl" % sheen_name, "o %s" % name, "usemtl atlas"] + v_lines + vt_lines + vn_lines + f_lines
        out["models/item/%s.obj" % name] = "\n".join(obj) + "\n"
        js = dict(base_json)
        js["model"] = "craftteamracing:models/item/%s.obj" % name
        out["models/item/pickup_%s.json" % name] = js
    return out
