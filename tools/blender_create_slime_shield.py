"""
Runs INSIDE Blender (tools/make_slime_shield.py starts it, only when blender/shield/slime_shield.blend is missing):
  blender --background --factory-startup --python tools/blender_create_slime_shield.py -- blender/shield/slime_shield.blend
Builds the Slime Shield's layers as collections (one model each, see tools/blender_export_models.py) and saves the file; after that the .blend is the author's.

The shape is in the frame the original's shield models use (client/ShieldBubble poses them): a squashed pyramid whose AXIS is the model's y (Blender +Z), wide in x (1.94 blocks) and
shallow in z (Blender y, 0.71 blocks); the game turns the apex toward the camera and stretches the short side back, so from the camera it is a square-ish slime block with rounded corners:
a flat face, a slanted rim. The picture is mapped straight along the axis, so the rim is the picture's outer band.
  shield_green / shield_blue   the slime's shell (the same mesh, linked), added to the screen
  shield_dark                  the nucleus, taken from the screen
  shield_sheen_0..7            the glint's eight steps across the face, added to the screen
"""
import os
import sys

import bmesh
import bpy

TEX = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
C = 0.55                                    # the octagon's chamfer: a square with its corners cut
HX, HY = 0.97, 0.355                        # half width / half depth at the base (the original's cone: 1.94 x 0.71)


def octagon(hx, hy, z):
    pts = [(1, -C), (1, C), (C, 1), (-C, 1), (-1, C), (-1, -C), (-C, -1), (C, -1)]      # counter-clockwise seen from above
    return [(x * hx, y * hy, z) for x, y in pts]


def planar_uv(p):
    return (p[0] / (2 * HX) + 0.5, p[1] / (2 * HY) + 0.5)


def frustum(name, rings, cap=True):
    """A mesh of stacked octagon rings (lowest first), outward-facing, with a flat cap on the top ring; UVs projected along the axis."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    vs = [[bm.verts.new(p) for p in r] for r in rings]
    faces = []
    for a, b in zip(vs, vs[1:]):
        for i in range(8):
            j = (i + 1) % 8
            faces.append(bm.faces.new((a[i], a[j], b[j], b[i])))
    if cap:
        faces.append(bm.faces.new(vs[-1]))
    uv = bm.loops.layers.uv.new("UVMap")
    for f in faces:
        for l in f.loops:
            l[uv].uv = planar_uv(l.vert.co)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    return me


def quad(name, pts):
    """A flat quad with its picture across it (the glint)."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    f = bm.faces.new([bm.verts.new(p) for p in pts])
    uv = bm.loops.layers.uv.new("UVMap")
    for l, t in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
        l[uv].uv = t
    bm.to_mesh(me)
    bm.free()
    return me


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


def collection(name, texture):
    c = bpy.data.collections.new(name)
    c["texture"] = texture
    bpy.context.scene.collection.children.link(c)
    return c


def add(coll, name, mesh, mat):
    if not mesh.materials:
        mesh.materials.append(mat)
    o = bpy.data.objects.new(name, mesh)
    coll.objects.link(o)
    return o


def main():
    out = sys.argv[sys.argv.index("--") + 1]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    # the original cone is 1.94 long along its axis (-0.47 .. 1.47) with the pivot at 0.5: the same here, so the game's poses fit
    shell = frustum("slime_shell", [octagon(HX, HY, -0.47), octagon(0.80, 0.29, 0.85), octagon(0.64, 0.23, 1.47)])
    for name in ("shield_green", "shield_blue"):
        add(collection(name, "slime_" + name), name, shell, material(name, "slime_" + name + ".png"))
    nucleus = frustum("slime_nucleus", [octagon(0.50, 0.18, 0.05), octagon(0.44, 0.16, 0.95)])
    add(collection("shield_dark", "slime_shield_dark"), "shield_dark", nucleus, material("shield_dark", "slime_shield_dark.png"))
    mat = material("shield_sheen", "slime_shield_sheen.png")
    for i in range(8):
        x = -0.42 + 0.84 * i / 7.0
        z = 1.47 + 0.02
        pts = [(x - 0.20, -0.20, z), (x - 0.06, -0.20, z), (x + 0.20, 0.20, z), (x + 0.06, 0.20, z)]
        add(collection("shield_sheen_%d" % i, "slime_shield_sheen"), "shield_sheen_%d" % i, quad("sheen_%d" % i, pts), mat)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out), relative_remap=True)
    print("saved", out)


main()
