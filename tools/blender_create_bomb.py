"""
Runs INSIDE Blender (tools/make_bomb_model.py starts it, only when blender/bomb/bomb.blend is missing):
  blender --background --factory-startup --python tools/blender_create_bomb.py -- blender/bomb/bomb.blend
Builds the Bomb as two collections, bomb (the iron texture) and bomb_charged (the glowing one), sharing one mesh (see tools/blender_export_models.py); after that the .blend is the author's.

The shape (blocks; Blender +Z up, +Y north; the ball's middle at 0, 0, 0.5; it rolls about X, the axle): a sphere of radius 0.375 cut flat at x = +-0.334 where a skull plate (radius 0.17) closes
each end, a steel belt round its middle, and a collar on the -x end where the rope fuse comes out and arcs to the tip at (-0.55, 0.09, 0.31) in Minecraft's axes from the ball's middle (the
spark and flames of client/BombFx go there). Pictures: the 32 x 32 sheet of tools/make_bomb_model.py, as 16 x 16 and 16 x 8 pieces.
"""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector

TEX = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
R = 0.375
CUT = 0.334                                   # where the sphere is cut flat: sqrt(R^2 - 0.17^2)
PLATE_R = 0.17
MID_Z = 0.5
SEGMENTS = 16
RINGS = 10

# regions of the 32 x 32 sheet as (u0, v0, u1, v1), v up
IRON_UV = (0.0, 0.5, 0.5, 1.0)
PLATE_UV = (0.5, 0.5, 1.0, 1.0)
BELT_UV = (0.0, 0.375, 0.5, 0.5)
COLLAR_UV = (0.0, 0.0, 0.5, 0.125 + 0.125)
ROPE_UV = (0.5, 0.375, 1.0, 0.5)
TIP_UV = (0.5, 0.25, 1.0, 0.375)


def mc(p):
    """Minecraft axes relative to the ball's middle -> Blender: (x, y, z) -> (x, -z, y + MID_Z)."""
    return Vector((p[0], -p[2], p[1] + MID_Z))


def place(region, a, b):
    return (region[0] + (region[2] - region[0]) * a, region[1] + (region[3] - region[1]) * b)


def add_face(bm, uv_layer, verts, uvs):
    f = bm.faces.new(verts)
    for l, t in zip(f.loops, uvs):
        l[uv_layer].uv = t
    return f


def body(bm, uv):
    """The sphere cut flat at both ends: rings along X, the picture running round (u) and along (v)."""
    rings = []
    for i in range(RINGS + 1):
        x = -CUT + 2 * CUT * i / RINGS
        r = math.sqrt(max(R * R - x * x, 0.0))
        rings.append((x, r, [bm.verts.new((x, r * math.cos(2 * math.pi * s / SEGMENTS), MID_Z + r * math.sin(2 * math.pi * s / SEGMENTS))) for s in range(SEGMENTS)]))
    for i in range(RINGS):
        for s in range(SEGMENTS):
            t = (s + 1) % SEGMENTS
            a, b = rings[i][2], rings[i + 1][2]
            u0, u1 = s / SEGMENTS, (s + 1) / SEGMENTS
            v0, v1 = i / RINGS, (i + 1) / RINGS
            add_face(bm, uv, (a[s], a[t], b[t], b[s]), [place(IRON_UV, u0, v0), place(IRON_UV, u1, v0), place(IRON_UV, u1, v1), place(IRON_UV, u0, v1)])


def plate(bm, uv, side):
    """The skull plate closing an end of the ball: a 16-gon, the picture's circle."""
    x = side * CUT
    ring = [bm.verts.new((x, PLATE_R * math.cos(2 * math.pi * s / SEGMENTS), MID_Z + PLATE_R * math.sin(2 * math.pi * s / SEGMENTS))) for s in range(SEGMENTS)]
    if side < 0:
        ring.reverse()
    f = bm.faces.new(ring)
    for l in f.loops:
        y, z = l.vert.co.y, l.vert.co.z - MID_Z
        a, b = y / (2 * PLATE_R) + 0.5, z / (2 * PLATE_R) + 0.5
        if side > 0:
            a = 1.0 - a
        l[uv].uv = place(PLATE_UV, a, b)


def tube(bm, uv, points, radii, region, sides=8, cap_end=True, cap_start=False):
    """A tube along a polyline of Blender points, the picture running along it (u) and round it (v)."""
    frames = []
    for i, p in enumerate(points):
        d = (points[min(i + 1, len(points) - 1)] - points[max(i - 1, 0)]).normalized()
        ref = Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((1, 0, 0))
        n = d.cross(ref).normalized()
        b = d.cross(n).normalized()
        frames.append([bm.verts.new(p + (n * math.cos(2 * math.pi * k / sides) + b * math.sin(2 * math.pi * k / sides)) * radii[i]) for k in range(sides)])
    for i in range(len(points) - 1):
        for k in range(sides):
            m = (k + 1) % sides
            a, b = frames[i], frames[i + 1]
            u0, u1 = i / (len(points) - 1), (i + 1) / (len(points) - 1)
            v0, v1 = k / sides, (k + 1) / sides
            add_face(bm, uv, (a[k], b[k], b[m], a[m]), [place(region, u0, v0), place(region, u1, v0), place(region, u1, v1), place(region, u0, v1)])
    for ring, want in ((frames[-1], cap_end), (frames[0], cap_start)):
        if want:
            f = bm.faces.new(ring)
            for l in f.loops:
                l[uv].uv = place(region, 0.5, 0.5)


def material(name, image):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = bpy.data.images.load(os.path.abspath(os.path.join(TEX, image)), check_existing=True)
    tx.interpolation = "Closest"
    nt.links.new(tx.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    return m


def main():
    out = sys.argv[sys.argv.index("--") + 1]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    me = bpy.data.meshes.new("bomb")
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    body(bm, uv)
    plate(bm, uv, 1)
    plate(bm, uv, -1)
    # the steel belt: a short band a hair proud of the ball, round its middle
    rb = R + 0.012
    for s in range(SEGMENTS):
        t = (s + 1) % SEGMENTS
        ring = []
        for x in (-0.05, 0.05):
            ring.append([bm.verts.new((x, rb * math.cos(2 * math.pi * k / SEGMENTS), MID_Z + rb * math.sin(2 * math.pi * k / SEGMENTS))) for k in (s, t)])
        add_face(bm, uv, (ring[0][0], ring[0][1], ring[1][1], ring[1][0]),
                 [place(BELT_UV, s / SEGMENTS, 0), place(BELT_UV, (s + 1) / SEGMENTS, 0), place(BELT_UV, (s + 1) / SEGMENTS, 1), place(BELT_UV, s / SEGMENTS, 1)])
    # the collar, on the ball where the fuse leaves it, and the fuse arcing out to the tip
    base = mc((-0.30, 0.07, 0.22))
    normal = (base - Vector((0, 0, MID_Z))).normalized()
    tube(bm, uv, [base - normal * 0.015, base + normal * 0.075], [0.06, 0.06], COLLAR_UV, sides=8, cap_end=True)
    p0, p1, p2 = base + normal * 0.07, mc((-0.46, 0.20, 0.22)), mc(TIP := (-0.55, 0.09, 0.31))
    pts = [(p0 * (1 - t) ** 2 + p1 * 2 * t * (1 - t) + p2 * t * t) for t in [i / 7 for i in range(8)]]
    tube(bm, uv, pts, [0.032 - 0.0025 * i / 7 for i in range(8)], ROPE_UV, sides=6, cap_end=False)
    tube(bm, uv, [pts[-1], pts[-1] + (pts[-1] - pts[-2]).normalized() * 0.03], [0.03, 0.026], TIP_UV, sides=6, cap_end=True)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    mats = {"bomb": material("bomb_iron", "bomb_iron.png"), "bomb_charged": material("bomb_hot", "bomb_hot.png")}
    for name, tex in (("bomb", "bomb_iron"), ("bomb_charged", "bomb_hot")):
        c = bpy.data.collections.new(name)
        c["texture"] = tex
        bpy.context.scene.collection.children.link(c)
        if not me.materials:
            me.materials.append(mats[name])
        o = bpy.data.objects.new(name, me)
        o.material_slots[0].link = "OBJECT"
        o.material_slots[0].material = mats[name]
        c.objects.link(o)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out), relative_remap=True)
    print("saved", out)


main()
