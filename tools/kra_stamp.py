"""Decides whether a script-owned .kra has to be regenerated.

The art scripts are the source of truth: script -> layered .kra -> PNG (tools/build_art.py). A script that owns its
.kra rewrites the WHOLE file from its drawing code, but only when something it depends on has changed, so a small
change does not rebuild every .kra. The state is kept in krita/.stamps.json: for each .kra the hash of the script,
its helper modules and any data/model file it reads, taken when the file was last written.

In a script that owns a .kra:

    import kra_stamp
    INPUTS = [__file__, "tools/kra_writer.py", "tools/sprite_outline.py"]     # relative to the project folder, or absolute
    if kra_stamp.stale("krita/items/care_items.kra", INPUTS):
        ...build the layers and write_kra(..., info=kra_stamp.provenance(INPUTS, __file__))...
        kra_stamp.record("krita/items/care_items.kra", INPUTS)

  python tools/kra_stamp.py --status    list every .kra in the manifest: scripted (and up to date / stale) or not yet scripted
  FORCE_KRA=1 (environment) or --force  makes stale() always return True, to rebuild everything

A .kra with no entry in .stamps.json is "not yet scripted": it still holds hand-painted art that no script produces,
so nothing may overwrite it until its script has taken that art over.
"""
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
STAMPS = os.path.join(ROOT, "krita", ".stamps.json")


def _abs(p):
    return p if os.path.isabs(p) else os.path.join(ROOT, p)


def _load():
    if os.path.exists(STAMPS):
        with open(STAMPS, encoding="utf-8") as f:
            return json.load(f)
    return {}


def digest(inputs):
    h = hashlib.sha256()
    for p in sorted({os.path.normpath(_abs(i)) for i in inputs}):
        try:
            name = os.path.relpath(p, ROOT)
        except ValueError:                      # another drive (e.g. a system font): use the full path
            name = p
        h.update(name.replace("\\", "/").encode())
        with open(p, "rb") as f:
            h.update(f.read())
    return h.hexdigest()


def provenance(inputs, script=None):
    """The text stored in a generated .kra's document info: which script made it and the stamp it was made from, so anyone can tell whether a .kra is current
    without running anything (compare the stamp with `python tools/kra_stamp.py --status`). Deliberately NO version number, date or machine path: those would
    change every release and make git store a new copy of every .kra. Only the script's file name goes in. `script` is the owning script's path (the caller of
    write_if_stale); `inputs` is sorted, so its first entry is NOT reliably the script."""
    script = os.path.basename(script) if script else "?"
    return f"Generated template. Script: {script}. Stamp: {digest(inputs)[:12]}. Edit a copy of it, not this file."


def stale(kra_rel, inputs):
    """True when the .kra is missing, was never stamped, or an input changed since it was written."""
    if os.environ.get("FORCE_KRA") or "--force" in sys.argv:
        return True
    if not os.path.exists(_abs(kra_rel)):
        return True
    return _load().get(kra_rel.replace("\\", "/")) != digest(inputs)


def record(kra_rel, inputs):
    """Call right after the script wrote the .kra."""
    stamps = _load()
    stamps[kra_rel.replace("\\", "/")] = digest(inputs)
    os.makedirs(os.path.dirname(STAMPS), exist_ok=True)
    with open(STAMPS, "w", encoding="utf-8", newline="\n") as f:
        json.dump(stamps, f, indent=2, sort_keys=True)


def inputs_for(script, extra=()):
    """The script, the tools/*.py modules it imports (transitively) and any extra files: the usual INPUTS list."""
    import re
    seen, todo = set(), [os.path.abspath(script)]
    while todo:
        f = todo.pop()
        if f in seen or not os.path.exists(f):
            continue
        seen.add(f)
        with open(f, encoding="utf-8") as fh:
            text = fh.read()
        for m in re.finditer(r"^\s*(?:import|from)\s+([A-Za-z_]\w*)", text, re.M):
            cand = os.path.join(HERE, m.group(1) + ".py")
            if os.path.exists(cand):
                todo.append(cand)
    return sorted(seen) + list(extra)


def rel(path):
    """A .kra path (absolute or relative to the project) as the project-relative key used in .stamps.json."""
    return os.path.relpath(_abs(path), ROOT).replace("\\", "/")


def is_scripted(kra_rel):
    return kra_rel.replace("\\", "/") in _load()


def _status():
    manifest = json.load(open(os.path.join(ROOT, "krita", "manifest.json"), encoding="utf-8"))
    stamps = _load()
    for a in manifest["assets"]:
        k = a["kra"]
        state = "not yet scripted" if k not in stamps else "scripted"
        print(f"  {state:17} {stamps.get(k, '')[:12]:12} {k}")     # the stamp matches the one in the .kra's document info


if __name__ == "__main__":
    if "--status" in sys.argv:
        _status()
    else:
        print(__doc__)
