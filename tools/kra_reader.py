"""Minimal Krita (.kra) reader for flat RGBA8 paint layers (needs Pillow).

Reads files saved by Krita itself (LZF-compressed tiles) as well as files from kra_writer.py.

    from kra_reader import read_kra
    doc = read_kra("file.kra")          # doc["width"], doc["height"], doc["layers"] (top first)
    layer = doc["layers"][0]            # dict: name, visible, opacity, blend, x, y, image (RGBA PIL image)
"""
import re
import zipfile
import xml.etree.ElementTree as ET

from PIL import Image

TILE = 64


def _lzf_decompress(data: bytes, out_len: int) -> bytes:
    out = bytearray()
    i = 0
    n = len(data)
    while i < n:
        ctrl = data[i]
        i += 1
        if ctrl < 32:                      # literal run
            out += data[i:i + ctrl + 1]
            i += ctrl + 1
        else:                              # back reference
            length = ctrl >> 5
            if length == 7:
                length += data[i]
                i += 1
            ref = len(out) - ((ctrl & 0x1F) << 8) - data[i] - 1
            i += 1
            length += 2
            for _ in range(length):        # may overlap, so copy byte by byte
                out.append(out[ref])
                ref += 1
    if len(out) != out_len:
        raise ValueError(f"LZF decode gave {len(out)} bytes, expected {out_len}")
    return bytes(out)


def _decode_layer(blob: bytes, planar_hint=None) -> Image.Image:
    m = re.match(rb"VERSION 2\nTILEWIDTH (\d+)\nTILEHEIGHT (\d+)\nPIXELSIZE (\d+)\nDATA (\d+)\n", blob)
    if not m:
        raise ValueError("unsupported layer data header")
    tw, th, px, count = (int(g) for g in m.groups())
    if px != 4:
        raise ValueError("only RGBA8 layers are supported")
    pos = m.end()
    tiles = []
    for _ in range(count):
        e = blob.index(b"\n", pos)
        x, y, comp, size = blob[pos:e].decode().split(",")
        x, y, size = int(x), int(y), int(size)
        payload = blob[e + 1:e + 1 + size]
        pos = e + 1 + size
        flag, body = payload[0], payload[1:]
        raw = _lzf_decompress(body, tw * th * px) if flag == 1 else body
        tiles.append((x, y, comp, raw))
    if not tiles:
        return Image.new("RGBA", (1, 1), (0, 0, 0, 0))
    minx = min(t[0] for t in tiles); miny = min(t[1] for t in tiles)
    maxx = max(t[0] for t in tiles) + tw; maxy = max(t[1] for t in tiles) + th
    img = Image.new("RGBA", (maxx - minx, maxy - miny), (0, 0, 0, 0))
    for x, y, comp, raw in tiles:
        n = tw * th
        if comp == "LZF":                  # Krita stores compressed tiles planar: all B, all G, all R, all A
            b, g, r, a = raw[0:n], raw[n:2 * n], raw[2 * n:3 * n], raw[3 * n:4 * n]
            tile = Image.merge("RGBA", [Image.frombytes("L", (tw, th), p) for p in (r, g, b, a)])
        else:                              # NONE: interleaved BGRA
            tile = Image.frombytes("RGBA", (tw, th), raw, "raw", "BGRA")
        img.paste(tile, (x - minx, y - miny))
    img.info["origin"] = (minx, miny)
    return img


def read_kra(path: str) -> dict:
    with zipfile.ZipFile(path) as z:
        root = ET.fromstring(z.read("maindoc.xml"))
        ns = {"k": "http://www.calligra.org/DTD/krita"}
        image = root.find("k:IMAGE", ns)
        name = image.get("name")
        layers = []
        skipped = []   # nodes this reader can't use (groups, masks, filter/vector/text layers)
        for node in image.find("k:layers", ns):
            if node.get("nodetype") != "paintlayer":
                skipped.append((node.get("name"), node.get("nodetype")))
                continue
            img = _decode_layer(z.read(f"{name}/layers/{node.get('filename')}"))
            ox, oy = img.info.get("origin", (0, 0))
            layers.append({
                "name": node.get("name"),
                "visible": node.get("visible") == "1",
                "opacity": int(node.get("opacity")),
                "blend": node.get("compositeop"),
                "x": int(node.get("x")) + ox,
                "y": int(node.get("y")) + oy,
                "channelflags": node.get("channelflags") or "",
                "image": img,
            })
        return {"name": name, "width": int(image.get("width")), "height": int(image.get("height")),
                "layers": layers, "skipped": skipped}


def layer_on_canvas(layer: dict, width: int, height: int) -> Image.Image:
    """The layer pasted onto a canvas-sized transparent image."""
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    out.paste(layer["image"], (layer["x"], layer["y"]))
    return out
