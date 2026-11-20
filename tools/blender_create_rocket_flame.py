"""
Runs INSIDE Blender (tools/make_rocket_flame.py starts it, only when blender/rocket/missile_flame.blend is missing):
  blender --background --factory-startup --python tools/blender_create_rocket_flame.py -- blender/rocket/missile_flame.blend
Builds the Rocket's exhaust flame as one collection, missile_flame (see tools/blender_export_models.py); after that the .blend is the author's.

The original missile's flame is a few fans of light streaming from its tail, drawn added to the screen. Same here: three flat tapering tongues crossed round the rocket's axis (60 degrees
apart), from the rocket's tail back along it, widest a little behind the nozzle and ending in a point. The picture runs along each tongue (u: white-hot at the nozzle, yellow, orange, red and
gone at the tip) and soft across it (v). In the rocket's frame (client/MissileRenderer; tools/make_missile_model.py): the rocket's middle is the block's middle, its nose points to Minecraft's -z,
so its tail is at +z, which is Blender -Y; the nozzle ends 0.41 block behind the middle. The renderer flickers its length and turns it about the axis.
"""
import math
import os
import sys

import bmesh
import bpy

TEX = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
START = 0.40                       # blocks behind the middle where the flame begins (the nozzle)
LENGTH = 1.05                      # blocks, nozzle to the tip
HALF = 0.20                        # half width at the widest
MID_Z = 0.5
STEPS = 8


def tongue(bm, uv, angle):
    ca, sa = math.cos(angle), math.sin(angle)
    rows = []
    for i in range(STEPS + 1):
        s = i / STEPS
        w = HALF * (0.45 + 0.55 * math.sin(min(1.0, s * 2.2) * math.pi / 2)) * (1.0 - s ** 1.6)       # opens fast, then narrows to a point
        y = -(START + LENGTH * s)                                                                  # Blender -Y is back
        rows.append((bm.verts.new((-w * ca, y, MID_Z - w * sa)), bm.verts.new((w * ca, y, MID_Z + w * sa)), s))
    for (a0, a1, s0), (b0, b1, s1) in zip(rows, rows[1:]):
        f = bm.faces.new((a0, a1, b1, b0))
        for l in f.loops:
            s = s0 if l.vert in (a0, a1) else s1
            l[uv].uv = (s, 0.0 if l.vert in (a0, b0) else 1.0)


def main():
    out = sys.argv[sys.argv.index("--") + 1]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    me = bpy.data.meshes.new("missile_flame")
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    for k in range(3):
        tongue(bm, uv, math.pi * k / 3.0)
    bm.to_mesh(me)
    bm.free()
    m = bpy.data.materials.new("missile_flame")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = bpy.data.images.load(os.path.abspath(os.path.join(TEX, "missile_flame.png")), check_existing=True)
    tx.interpolation = "Closest"
    nt.links.new(tx.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], o.inputs["Surface"])
    me.materials.append(m)
    c = bpy.data.collections.new("missile_flame")
    c["texture"] = "missile_flame"
    bpy.context.scene.collection.children.link(c)
    c.objects.link(bpy.data.objects.new("missile_flame", me))
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out), relative_remap=True)
    print("saved", out)


main()
