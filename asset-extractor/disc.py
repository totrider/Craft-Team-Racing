"""
Identifies a game disc image from the image itself (no file names, no hashes): the ISO 9660 volume of a PlayStation disc names its boot file in SYSTEM.CNF
("BOOT = cdrom:\\SCUS_944.26;1"), and that is the game's serial number; the volume and system identifiers come with it. Handles raw 2352-byte images (.bin,
Mode 1 or Mode 2) and plain 2048-byte images (.iso / .img), and a .cue sheet pointing at a .bin.

  from disc import identify
  info = identify("iso/game.bin")      # {"serial": "SCUS-94426", "boot": "SCUS_944.26", "volume": "CRASH", "platform": "ps1", "layout": "raw", ...}  or None

Read only; nothing is written. Used by extract.py to pick the game profile in games/.
"""
import os
import re
import struct

RAW = 2352
DATA = 2048


class Image:
    """A raw (2352-byte sectors) or cooked (2048-byte) disc image with an ISO 9660 volume."""

    def __init__(self, path):
        self.path = path
        self.f = open(path, "rb")
        self.f.seek(16 * RAW)
        raw = self.f.read(64)
        self.f.seek(16 * DATA)
        cooked = self.f.read(64)
        sync = bytes([0] + [255] * 10 + [0])
        if raw[:12] == sync and raw[25:30] == b"CD001":
            self.layout, self.off = "raw", 24            # Mode 2 Form 1 (PlayStation)
        elif raw[:12] == sync and raw[17:22] == b"CD001":
            self.layout, self.off = "raw", 16            # Mode 1
        elif cooked[1:6] == b"CD001":
            self.layout, self.off = "cooked", 0
        else:
            self.f.close()
            raise ValueError("no ISO 9660 volume found")

    def close(self):
        self.f.close()

    def sectors(self, lba, count):
        out = bytearray()
        for i in range(count):
            if self.layout == "cooked":
                self.f.seek((lba + i) * DATA)
            else:
                self.f.seek((lba + i) * RAW + self.off)
            out += self.f.read(DATA)
        return bytes(out)

    def read(self, lba, size):
        return self.sectors(lba, -(-size // DATA))[:size]

    def find(self, path):
        """The (lba, size) of a file of the root directory tree, or None."""
        pvd = self.sectors(16, 1)
        lba, size = struct.unpack_from("<I", pvd, 158)[0], struct.unpack_from("<I", pvd, 166)[0]
        for part in [p for p in path.replace("\\", "/").split("/") if p]:
            data = self.read(lba, size)
            found = None
            pos = 0
            while pos < len(data):
                n = data[pos]
                if n == 0:
                    pos = (pos // DATA + 1) * DATA
                    continue
                rec = data[pos: pos + n]
                name = rec[33: 33 + rec[32]].decode("latin-1")
                if name not in ("\x00", "\x01") and name.split(";")[0].rstrip(".").upper() == part.upper():
                    found = (struct.unpack_from("<I", rec, 2)[0], struct.unpack_from("<I", rec, 10)[0])
                    break
                pos += n
            if not found:
                return None
            lba, size = found
        return found

    def volume(self):
        pvd = self.sectors(16, 1)
        return pvd[8:40].decode("latin-1").strip(), pvd[40:72].decode("latin-1").strip()


def serial_of(boot):
    """'SCUS_944.26' -> 'SCUS-94426' (the number printed on the case)."""
    m = re.match(r"([A-Za-z]{4})[_\-]?(\d{3})[.\-]?(\d{2})", boot)
    return "%s-%s%s" % (m.group(1).upper(), m.group(2), m.group(3)) if m else boot.upper()


def image_files(path):
    """The image files a path stands for: a .cue sheet names its .bin files, anything else is itself."""
    if path.lower().endswith(".cue"):
        out = []
        try:
            with open(path, encoding="latin-1") as f:
                for line in f:
                    m = re.match(r'\s*FILE\s+"?([^"]+?)"?\s+\w+\s*$', line, re.I)
                    if m:
                        out.append(os.path.join(os.path.dirname(path), m.group(1)))
        except OSError:
            pass
        return out
    return [path]


def identify(path):
    """What the disc image is, or None when it is not a PlayStation-style disc image. Reads the first data track of a .cue."""
    for p in image_files(path):
        if not os.path.isfile(p):
            continue
        try:
            img = Image(p)
        except (ValueError, OSError):
            continue
        try:
            system, volume = img.volume()
            where = img.find("SYSTEM.CNF")
            if where is None:
                return {"file": p, "serial": None, "boot": None, "volume": volume, "system": system, "platform": "unknown", "layout": img.layout}
            text = img.read(*where).decode("latin-1")
            boot = None
            platform = "ps1"
            for line in text.splitlines():
                key = line.split("=")[0].strip().upper()
                if key in ("BOOT", "BOOT2"):
                    boot = line.split("\\")[-1].split(":")[-1].split(";")[0].strip()
                    platform = "ps2" if key == "BOOT2" else "ps1"
                    break
            return {"file": p, "serial": serial_of(boot) if boot else None, "boot": boot, "volume": volume, "system": system, "platform": platform, "layout": img.layout}
        finally:
            img.close()
    return None


if __name__ == "__main__":
    import sys
    for arg in sys.argv[1:]:
        print(arg, "->", identify(arg))
