"""Turns a numpy painter into layers: the picture is drawn into one array as before, and after each drawing step the script calls snap(name, array). The pixels that step changed
become a layer (with their new values), so the stack flattens to exactly the finished array, and each layer is one step of the drawing.

    stages = kra_stage.Stages(128, 128)
    ...draw into arr...
    stages.snap("body panel", arr)
    ...
    kra_layers.place(sheet, stages.layers, (0, 0))          # or sheet.add(name, image) for each

A step may only add or recolour opaque pixels (a pixel that is cleared again would need a hole in the layers below it; snap refuses it).
"""
import numpy as np
from PIL import Image


class Stages:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.prev = np.zeros((height, width, 4), dtype=np.uint8)
        self.layers = []                         # (name, RGBA image), bottom first

    def snap(self, name, arr):
        a = np.clip(arr, 0, 255).astype(np.uint8)
        assert not np.any((self.prev[:, :, 3] > 0) & (a[:, :, 3] == 0)), "stage '%s' clears pixels: cut a hole in the layers below instead" % name
        changed = np.any(a != self.prev, axis=2) & (a[:, :, 3] > 0)
        if changed.any():
            layer = np.zeros_like(a)
            layer[changed] = a[changed]
            self.layers.append((name, Image.fromarray(layer, "RGBA")))
        self.prev = a.copy()
