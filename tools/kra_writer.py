"""Minimal Krita (.kra) writer: layered RGBA8 documents straight from Python (needs Pillow).   [kra-pipeline 1.0.0]

    from kra_writer import Layer, write_kra
    write_kra("out.kra", 64, 32, [
        Layer("guide", guide_img, opacity=128),   # top of the stack first
        Layer("base",  base_img),
    ], name="my_doc", info="Generated from make_thing.py, stamp 1a2b3c4d5e6f")

Layers are listed TOP FIRST. Each Layer holds a PIL RGBA image the size of the canvas
(or smaller, with an x/y offset >= 0). The sRGB profile is read from the local Krita install (or KRITA_ICC).
Tested by opening the result with Krita 5.2.2.

DETERMINISTIC: the same layers always give the same bytes. Layer ids are derived from the document name, the layer's
position and its name (not random), and every zip entry has a fixed timestamp and compression level. So rebuilding an
unchanged document produces a byte-identical file, and git sees no change.
write_kra() also compares with the file already on disk and writes ONLY when the bytes differ; it returns True when it wrote.
(Byte-identity holds for one Python/zlib version. A different Python build may compress differently once, then be stable.)
"""
import io
import os
import uuid
import zipfile
from dataclasses import dataclass
from xml.sax.saxutils import escape, quoteattr

from PIL import Image

_ICC_DEFAULT = r"C:\Program Files\Krita (x64)\share\color\icc\krita\sRGB-elle-V2-srgbtrc.icc"
ICC = os.environ.get("KRITA_ICC") or _ICC_DEFAULT
if not os.environ.get("KRITA_ICC") and os.environ.get("KRITA_BIN"):          # KRITA_BIN: the folder with krita.exe, as the guides say
    _alt = os.path.join(os.path.dirname(os.environ["KRITA_BIN"].rstrip("\\/")), "share", "color", "icc", "krita", "sRGB-elle-V2-srgbtrc.icc")
    if os.path.exists(_alt):
        ICC = _alt
PROFILE = "sRGB-elle-V2-srgbtrc.icc"
TILE = 64
_NS = uuid.UUID("6f1c2a52-3b0e-4a0e-9d7a-5d0c6f7a2b11")      # fixed namespace for the derived layer ids
_STAMP_TIME = (2020, 1, 1, 0, 0, 0)                          # fixed zip timestamp
_LEVEL = 6                                                   # fixed deflate level


@dataclass
class Layer:
    name: str
    image: Image.Image
    visible: bool = True
    opacity: int = 255        # 0-255
    blend: str = "normal"     # Krita composite op id: normal, multiply, overlay, ...
    locked: bool = False
    x: int = 0
    y: int = 0
    channelflags: str = ""    # e.g. "1110" = alpha channel off = "Inherit Alpha" (clip to layers below)


def _layer_blob(img: Image.Image) -> bytes:
    """Tile data. Tiles are stored uncompressed (tag NONE, flag byte 0) as interleaved BGRA, which
    Krita reads fine and re-compresses when you save. (The zip container deflates it anyway.)"""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    tiles = []
    for ty in range(0, h, TILE):
        for tx in range(0, w, TILE):
            raw = bytearray(TILE * TILE * 4)
            empty = True
            for yy in range(TILE):
                for xx in range(TILE):
                    X, Y = tx + xx, ty + yy
                    if X < w and Y < h:
                        r, g, b, a = px[X, Y]
                        if a:
                            empty = False
                            i = (yy * TILE + xx) * 4
                            raw[i:i + 4] = bytes((b, g, r, a))
            if empty:
                continue
            tiles.append((tx, ty, bytes([0]) + bytes(raw)))
    head = ("VERSION 2\nTILEWIDTH %d\nTILEHEIGHT %d\nPIXELSIZE 4\nDATA %d\n" % (TILE, TILE, len(tiles))).encode()
    body = b"".join(("%d,%d,NONE,%d\n" % (tx, ty, len(p))).encode() + p for tx, ty, p in tiles)
    return head + body


def _png(im: Image.Image) -> bytes:
    b = io.BytesIO()
    im.save(b, "PNG")
    return b.getvalue()


def kra_bytes(width: int, height: int, layers: list, name: str = "Untitled", info: str = "") -> bytes:
    """The whole .kra file as bytes (deterministic: see the module doc)."""
    if not os.path.exists(ICC):
        raise SystemExit(f"error: the sRGB profile {ICC} is missing (a normal Krita install has it): set the KRITA_ICC environment variable or kra_writer.ICC")
    with open(ICC, "rb") as f:
        icc = f.read()
    nodes = [(f"layer{i + 2}", L) for i, L in enumerate(layers)]  # first = top of the stack

    layer_xml = []
    for i, (fn, L) in enumerate(nodes):
        layer_id = uuid.uuid5(_NS, f"{name}/{i}/{L.name}")
        layer_xml.append(
            f'   <layer filename="{fn}" nodetype="paintlayer" name={quoteattr(L.name)} '
            f'x="{L.x}" y="{L.y}" opacity="{L.opacity}" visible="{int(L.visible)}" '
            f'locked="{int(L.locked)}" compositeop="{L.blend}" colorspacename="RGBA" '
            f'uuid="{{{layer_id}}}" collapsed="0" colorlabel="0" onionskin="0" intimeline="1" '
            f'channellockflags="" channelflags="{L.channelflags}"/>')
    nl = "\n"
    maindoc = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE DOC PUBLIC '-//KDE//DTD krita 2.0//EN' 'http://www.calligra.org/DTD/krita-2.0.dtd'>
<DOC xmlns="http://www.calligra.org/DTD/krita" syntaxVersion="2.0" editor="Krita" kritaVersion="5.2.2">
 <IMAGE profile="{PROFILE}" y-res="72" colorspacename="RGBA" width="{width}" height="{height}" description="" x-res="72" mime="application/x-kra" name={quoteattr(name)}>
  <layers>
{nl.join(layer_xml)}
  </layers>
  <ProjectionBackgroundColor ColorData="AAAAAA=="/>
  <GlobalAssistantsColor SimpleColorData="176,176,176,255"/>
  <Palettes/>
  <resources/>
  <animation>
   <framerate type="value" value="24"/>
   <range to="100" type="timerange" from="0"/>
   <currentTime type="value" value="0"/>
  </animation>
 </IMAGE>
</DOC>
"""
    docinfo = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE document-info PUBLIC '-//KDE//DTD document-info 1.1//EN' 'http://www.calligra.org/DTD/document-info-1.1.dtd'>
<document-info xmlns="http://www.calligra.org/DTD/document-info"><about><title></title><abstract>{escape(info)}</abstract></about></document-info>
"""
    # flattened merged image + preview thumbnail
    flat = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for _, L in reversed(nodes):
        if L.visible:
            layer = L.image.convert("RGBA")
            if L.opacity < 255:
                layer.putalpha(layer.getchannel("A").point(lambda v, o=L.opacity: v * o // 255))
            flat.alpha_composite(layer, (L.x, L.y))
    scale = max(1, 256 // max(width, height))
    prev = flat.resize((width * scale, height * scale), Image.NEAREST)

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        def put(entry, data, stored=False):
            zi = zipfile.ZipInfo(entry, date_time=_STAMP_TIME)
            zi.external_attr = 0o644 << 16
            zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
            z.writestr(zi, data, compress_type=zi.compress_type, compresslevel=None if stored else _LEVEL)

        put("mimetype", b"application/x-krita", stored=True)   # stored, first entry
        put("maindoc.xml", maindoc)
        put("documentinfo.xml", docinfo)
        put("mergedimage.png", _png(flat))
        put("preview.png", _png(prev))
        put(f"{name}/annotations/icc", icc)
        for fn, L in nodes:
            put(f"{name}/layers/{fn}", _layer_blob(L.image))
            put(f"{name}/layers/{fn}.defaultpixel", bytes(4))
            put(f"{name}/layers/{fn}.icc", icc)
    return buf.getvalue()


def write_kra(path: str, width: int, height: int, layers: list, name: str = "Untitled", info: str = "") -> bool:
    """Writes the .kra, but only when its bytes differ from the file already there. True when it wrote, False when it was already identical."""
    data = kra_bytes(width, height, layers, name, info)
    if os.path.exists(path):
        with open(path, "rb") as f:
            if f.read() == data:
                return False
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    return True
