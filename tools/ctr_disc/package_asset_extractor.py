"""
Copies the scripts the asset extractor needs into asset-extractor/ctr/ (the folder is git-ignored; it is what makes asset-extractor/ a folder you can drop into an instance on its own).

  python tools/ctr_disc/package_asset_extractor.py

The three extractors (extract_ctr_sounds, extract_ctr_music, extract_ctr_textures) are the entry points; every module of tools/ they import, and the ones those import, goes along.
Then zip asset-extractor/ (without output/ and iso/'s contents) to hand it to somebody.
"""
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))                 # tools/ctr_disc
TOOLS = os.path.dirname(HERE)                                      # tools/
OUT = os.path.join(os.path.dirname(TOOLS), "asset-extractor", "ctr")
FOLDERS = (HERE, TOOLS)
ENTRY = ["extract_ctr_sounds", "extract_ctr_music", "extract_ctr_textures"]


def find(name):
    """The path of module {name} in tools/ctr_disc or tools/, or None."""
    for folder in FOLDERS:
        path = os.path.join(folder, name + ".py")
        if os.path.exists(path):
            return path
    return None


def imports(name):
    with open(find(name), encoding="utf-8") as f:
        text = f.read()
    found = set()
    for m in re.finditer(r"^\s*(?:import|from)\s+([A-Za-z_][A-Za-z0-9_]*)", text, re.M):
        if find(m.group(1)):
            found.add(m.group(1))
    return found


def main():
    todo = list(ENTRY)
    seen = set()
    while todo:
        n = todo.pop()
        if n in seen:
            continue
        seen.add(n)
        todo.extend(imports(n))
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    for n in sorted(seen):
        shutil.copy2(find(n), os.path.join(OUT, n + ".py"))
    print("asset-extractor/ctr: %d scripts (%s)" % (len(seen), ", ".join(sorted(seen))))


if __name__ == "__main__":
    sys.exit(main())
