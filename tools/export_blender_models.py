"""
Exports the mod's Blender models: every blender/**/*.blend is opened in Blender (in the background) and tools/blender/blender_export_models.py writes each of its collections as a model.

  python tools/export_blender_models.py

blender/ sits next to blockbench/: Blockbench for block-style models, Blender for anything modelled as a mesh. The .blend files are the SOURCE (never hand-edit the OBJ and the model JSON that come
out). See blender/README.md for the conventions. Finds Blender in the BLENDER environment variable (the path to blender.exe), else the newest install in C:\\Program Files\\Blender Foundation;
if there is none, the committed models are used as they are and this does nothing. Run from the project folder.
"""
import glob
import os
import re
import subprocess


def find_blender():
    env = os.environ.get("BLENDER")
    if env and os.path.exists(env):
        return env
    found = glob.glob(r"C:\Program Files\Blender Foundation\Blender *\blender.exe")
    found.sort(key=lambda p: [int(n) for n in re.findall(r"\d+", os.path.basename(os.path.dirname(p)))])
    return found[-1] if found else None


def export_all():
    blends = sorted(glob.glob(os.path.join("blender", "**", "*.blend"), recursive=True))
    if not blends:
        return
    exe = find_blender()
    if not exe:
        print("Blender not found: the committed models under src/main/resources are used as they are")
        return
    for blend in blends:
        subprocess.run([exe, "--background", "--factory-startup", blend, "--python", os.path.join("tools", "blender", "blender_export_models.py"), "--",
                        os.path.join("src", "main", "resources", "assets", "craftteamracing")], check=True)


if __name__ == "__main__":
    export_all()
