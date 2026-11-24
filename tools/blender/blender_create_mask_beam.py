"""
Runs INSIDE Blender (tools/make_mask_beam.py starts it, only when blender/mask/mask_beam.blend is missing):
  blender --background --factory-startup --python tools/blender/blender_create_mask_beam.py -- blender/mask/mask_beam.blend
Builds the light that swirls round a kart under the Totem (Mask) power-up, as 24 collections per mask (mask_beam_aku_0..23, mask_beam_uka_0..23: one model each, see
tools/blender/blender_export_models.py). After that the .blend is the author's.

The original's beam (akubeam / ukabeam) is a ribbon of light round the kart, 24 animation frames, drawn added to the screen (Aku Aku) or taken from it (Uka Uka) and turned with the head
(client/MaskHead). Same here: two tapering ribbons wind up the ring of the original's beam (radius 0.85 blocks; the head orbits a little outside it) over the height of the original's beam; each frame turns the
helix 7.5 degrees, which is a shift of the whole pattern up the ring, and after 24 frames it is the same pattern again (180 degrees is one ribbon spacing), so the loop is seamless. Blender
+Z is up; the model's middle (Blender z = 0.5 is the head's height, as in the original's frame) is where the head is.
"""
import math
import os
import sys

import bmesh
import bpy

TEX = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
FRAMES = 24
STRANDS = 2
RADIUS = 0.85
Z_LOW, Z_HIGH = 0.5 - 0.66, 0.5 + 0.22          # the original's beam spans -0.66 .. +0.22 blocks about its pivot (the head's height)
TURNS = 0.8                                      # how far round each ribbon winds between its ends
STEPS = 32
MAX_WIDTH = 0.24


def ribbon(frame):
    me = bpy.data.meshes.new("beam_%d" % frame)
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    spin = math.radians(7.5 * frame)
    for strand in range(STRANDS):
        base = 2.0 * math.pi * strand / STRANDS - spin
        row = []
        for i in range(STEPS + 1):
            s = i / STEPS
            th = base + TURNS * 2.0 * math.pi * s
            z = Z_LOW + (Z_HIGH - Z_LOW) * s
            w = MAX_WIDTH * math.sin(math.pi * s) ** 0.7 * 0.5
            r = RADIUS * (1.0 + 0.04 * math.sin(2.0 * math.pi * s))
            x, y = r * math.cos(th), r * math.sin(th)
            row.append((bm.verts.new((x, y, z - w)), bm.verts.new((x, y, z + w)), s))
        for (a0, a1, s0), (b0, b1, s1) in zip(row, row[1:]):
            f = bm.faces.new((a0, b0, b1, a1))
            for l in f.loops:
                s = s0 if l.vert in (a0, a1) else s1
                l[uv].uv = (s, 0.0 if l.vert in (a0, b0) else 1.0)
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


def main():
    out = sys.argv[sys.argv.index("--") + 1]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    mats = {who: material("mask_beam_" + who, "mask_beam_%s.png" % who) for who in ("aku", "uka")}
    for frame in range(FRAMES):
        mesh = ribbon(frame)
        mesh.materials.append(mats["aku"])
        for who in ("aku", "uka"):
            c = bpy.data.collections.new("mask_beam_%s_%d" % (who, frame))
            c["texture"] = "mask_beam_" + who
            bpy.context.scene.collection.children.link(c)
            o = bpy.data.objects.new("beam_%s_%d" % (who, frame), mesh)
            if who == "uka":
                o.material_slots[0].link = "OBJECT"
                o.material_slots[0].material = mats["uka"]
            c.objects.link(o)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out), relative_remap=True)
    print("saved", out)


main()
