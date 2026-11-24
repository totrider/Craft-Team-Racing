"""
Runs INSIDE Blender (tools/export_blender_models.py starts it):  blender --background <file.blend> --python tools/blender/blender_export_models.py -- <assets/craftteamracing folder>

Every top-level collection of the scene is one model, named as the collection (collections whose name starts with "_" are skipped: helpers, references). All the mesh objects in it (modifiers
applied) are written to models/item/<collection>.obj with its .mtl, and a models/item/pickup_<collection>.json loader entry. The collection's custom property "texture" names the picture,
textures/item/<texture>.png (default: the collection's name).

Conventions: 1 Blender unit is 1 block; Blender's origin is the middle of the block's floor; +X is east, +Y is north, +Z is up (Blender -> Minecraft: x, z, -y, plus half a block in x and z,
because a block model is 0..1). UVs as Blender has them (origin bottom left; the model JSON says flip_v). Faces are written as triangles, all with the normal (0, 1, 0) (except a collection with the custom property "smooth_normals", and the beakers: their mesh's own vertex normals): the game's entity renderer
lights a quad by the axis its normal is nearest to, so true normals make the triangles of one slanted face differ in brightness (the mod's models are lit by their texture, not by their normals);
front faces wind counter-clockwise outside, as Blender's do (use Recalculate Outside).
"""
import json
import os
import sys

import bpy

DISPLAY = {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def corners(coll):
    """(position, uv) of every corner of every triangle of every mesh object in the collection, already in Minecraft's axes."""
    dg = bpy.context.evaluated_depsgraph_get()
    out = []
    for obj in sorted(coll.all_objects, key=lambda o: o.name):
        if obj.type != "MESH":
            continue
        ev = obj.evaluated_get(dg)
        mesh = ev.to_mesh()
        mesh.calc_loop_triangles()
        uvs = mesh.uv_layers.active.data if mesh.uv_layers.active else None
        m = obj.matrix_world
        nm = m.to_3x3().inverted_safe().transposed()
        normals = mesh.corner_normals
        for tri in mesh.loop_triangles:
            for loop in tri.loops:
                p = m @ mesh.vertices[mesh.loops[loop].vertex_index].co
                uv = uvs[loop].uv if uvs else (0.0, 0.0)
                n = (nm @ normals[loop].vector).normalized()
                fn = (nm @ tri.normal).normalized()
                out.append(((p.x + 0.5, p.z, -p.y + 0.5), (uv[0], uv[1]), (n.x, n.z, -n.y), (fn.x, fn.z, -fn.y), tri.area))
        ev.to_mesh_clear()
    return out


def smoothed(cs, degrees=60.0):
    """Vertex normals from the geometry: at each position, the normals of the faces that meet there (those within {degrees} of this corner's own face) averaged, weighted by area.
    A mesh that is flat shaded in Blender (the generated ones are) has only face normals to give; this rounds a curved surface and keeps a hard edge hard."""
    import math
    limit = math.cos(math.radians(degrees))
    at = {}
    for (x, y, z), _uv, _n, fn, area in cs:
        at.setdefault((round(x, 4), round(y, 4), round(z, 4)), []).append((fn, area))
    out = []
    for (x, y, z), uv, _n, fn, area in cs:
        sx = sy = sz = 0.0
        for gn, ga in at[(round(x, 4), round(y, 4), round(z, 4))]:
            if fn[0] * gn[0] + fn[1] * gn[1] + fn[2] * gn[2] >= limit:
                sx += gn[0] * ga
                sy += gn[1] * ga
                sz += gn[2] * ga
        length = math.sqrt(sx * sx + sy * sy + sz * sz) or 1.0
        out.append(((x, y, z), uv, (sx / length, sy / length, sz / length), fn, area))
    return out


def write(root, name, texture, cs, smooth=False):
    if smooth:
        cs = smoothed(cs)
    models = os.path.join(root, "models", "item")
    os.makedirs(models, exist_ok=True)
    with open(os.path.join(models, name + ".obj"), "w", newline="\n") as f:
        f.write("# %s: exported by tools/blender/blender_export_models.py from %s. Do not edit; change the .blend.\n" % (name, os.path.basename(bpy.data.filepath)))
        f.write("mtllib %s.mtl\no %s\nusemtl atlas\n" % (name, name))
        if not smooth:
            f.write("vn 0 1 0\n")
        for (x, y, z), _, _n, _f, _a in cs:
            f.write("v %.5f %.5f %.5f\n" % (x, y, z))
        for _, (u, v), _n, _f, _a in cs:
            f.write("vt %.5f %.5f\n" % (u, v))
        if smooth:
            for _, _uv, (nx, ny, nz), _f, _a in cs:
                f.write("vn %.4f %.4f %.4f\n" % (nx, ny, nz))
        for i in range(0, len(cs), 3):
            f.write("f " + " ".join("%d/%d/%d" % (i + k + 1, i + k + 1, (i + k + 1) if smooth else 1) for k in range(3)) + "\n")
    with open(os.path.join(models, name + ".mtl"), "w", newline="\n") as f:
        f.write("newmtl atlas\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\nmap_Kd #atlas\n")
    with open(os.path.join(models, "pickup_" + name + ".json"), "w", newline="\n") as f:
        json.dump({"loader": "neoforge:obj", "model": "craftteamracing:models/item/%s.obj" % name, "flip_v": True, "automatic_culling": False, "shade_quads": False,
                   "textures": {"atlas": "craftteamracing:item/" + texture, "particle": "craftteamracing:item/" + texture}, "display": DISPLAY}, f, indent=2)


def main():
    root = sys.argv[sys.argv.index("--") + 1]
    n = 0
    for coll in bpy.context.scene.collection.children:
        if coll.name.startswith("_"):
            continue
        cs = corners(coll)
        if not cs:
            print("collection %s has no mesh: skipped" % coll.name)
            continue
        # the bottles are drawn with the vertex normals of the mesh (smooth where Blender shades it smooth), so a shader pack can light them smoothly; the others keep the one normal
        write(root, coll.name, coll.get("texture", coll.name), cs, smooth=bool(coll.get("smooth_normals", coll.name.startswith("beaker"))))
        n += 1
    print("blender export: %d models from %s" % (n, os.path.basename(bpy.data.filepath)))


main()
