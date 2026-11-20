"""
Runs INSIDE Blender (tools/make_beaker_models.py starts it, only when blender/beaker/beaker.blend is missing):
  blender --background --factory-startup --python tools/blender_create_beaker.py -- blender/beaker/beaker.blend
Builds N. Brio's Beaker as two collections, beaker_green and beaker_red (see tools/blender_export_models.py); after that the .blend is the author's.

A lathe round the vertical axis, 14 sides, with the profile measured from the original's flask (blocks): a flat base 0.28 across, a bulb that is widest (0.49) a third of the way up and curves
in to a narrow neck (0.18) between 0.38 and 0.57, and a flared lip 0.30 across at 0.64 with an open mouth 0.20 across at the rim, a funnel of glass narrowing to 0.11 and ending 0.056 below the rim (no floor). The bulb holds the liquid (its lowest rings in the shade cell, then the liquid, then the
light one at its surface); the rest is glass. The colours are the cells of the 16 x 16 palette textures/item/beaker.png (4 x 4 texel cells): row 0 green, row 1 red, columns liquid, shade, light,
glass. Blender's origin is the middle of the flask's base.
"""
import math
import os
import sys

import bmesh
import bpy

TEX = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item", "beaker.png")
SIDES = 14
# (height, radius) from the base up
PROFILE = [(0.0, 0.142), (0.03, 0.185), (0.10, 0.228), (0.21, 0.243), (0.29, 0.205), (0.35, 0.130), (0.385, 0.090), (0.57, 0.090), (0.60, 0.125), (0.644, 0.151)]
LIQUID_TOP = 0.33
R_IN = 0.100                                  # the mouth's radius at the rim
# the wall of the mouth, rim down: it narrows as it goes and ends a good way up the neck (0.07 deep), so it reads as a funnel, not a straight tube
MOUTH = [(0.628, 0.092), (0.612, 0.080), (0.598, 0.066), (0.588, 0.056)]

COLS = {"liquid": 0, "dark": 1, "light": 2, "glass": 3}


def cell_uv(column, row):
    """The inside of a palette cell, as (u0, v0, u1, v1) with v up."""
    return ((column * 4 + 0.5) / 16.0, 1.0 - (row * 4 + 3.5) / 16.0, (column * 4 + 3.5) / 16.0, 1.0 - (row * 4 + 0.5) / 16.0)


def band(y0, y1):
    """Which cell a band of the flask is in."""
    mid = (y0 + y1) / 2.0
    if mid > LIQUID_TOP:
        return "glass"
    if mid < 0.07:
        return "dark"
    if mid > 0.27:
        return "light"
    return "liquid"


def flask(name, row):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    rings = [[bm.verts.new((r * math.cos(2 * math.pi * k / SIDES), r * math.sin(2 * math.pi * k / SIDES), y)) for k in range(SIDES)] for y, r in PROFILE]

    def face(verts, cell):
        u0, v0, u1, v1 = cell_uv(COLS[cell], row)
        f = bm.faces.new(verts)
        corners = ((u0, v0), (u1, v0), (u1, v1), (u0, v1))
        for i, l in enumerate(f.loops):                                  # every corner of the face gets a place in the cell (a many sided face too)
            l[uv].uv = corners[i % 4]

    for i in range(len(PROFILE) - 1):
        cell = band(PROFILE[i][0], PROFILE[i + 1][0])
        for k in range(SIDES):
            m = (k + 1) % SIDES
            face((rings[i][k], rings[i][m], rings[i + 1][m], rings[i + 1][k]), cell)
    # the opening: the lip's top is a ring, and inside it the neck's wall runs down to a floor, so looking into the mouth shows a clean hole and not the see-through glass of the far side
    y_top = PROFILE[-1][0]
    funnel = [(y_top, R_IN)] + MOUTH
    wall = [[bm.verts.new((r * math.cos(2 * math.pi * k / SIDES), r * math.sin(2 * math.pi * k / SIDES), y)) for k in range(SIDES)] for y, r in funnel]
    for k in range(SIDES):
        m = (k + 1) % SIDES
        face((rings[-1][k], rings[-1][m], wall[0][m], wall[0][k]), "glass")                 # the rim
        for i in range(len(wall) - 1):
            face((wall[i][k], wall[i][m], wall[i + 1][m], wall[i + 1][k]), "glass")         # the wall, facing in, narrowing down; open at its bottom (no floor)
    bottom = list(reversed(rings[0]))
    face(bottom, "dark")
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    return me


def material():
    m = bpy.data.materials.new("beaker")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = bpy.data.images.load(os.path.abspath(TEX), check_existing=True)
    tx.interpolation = "Closest"
    nt.links.new(tx.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    return m


def main():
    out = sys.argv[sys.argv.index("--") + 1]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    mat = material()
    for name, row in (("beaker_green", 0), ("beaker_red", 1)):
        me = flask(name, row)
        me.materials.append(mat)
        c = bpy.data.collections.new(name)
        c["texture"] = "beaker"
        bpy.context.scene.collection.children.link(c)
        c.objects.link(bpy.data.objects.new(name, me))
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out), relative_remap=True)
    print("saved", out)


main()
