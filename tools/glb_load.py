"""Minimal GLB reader: returns world-space triangles per node with their material, no external libraries."""
import json, struct
import numpy as np

COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def load(path):
    b = open(path, "rb").read()
    jl = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + jl])
    off = 20 + jl
    bl = struct.unpack("<I", b[off:off + 4])[0]
    binary = b[off + 8:off + 8 + bl]

    def accessor(i):
        a = j["accessors"][i]
        bv = j["bufferViews"][a["bufferView"]]
        dt = COMP[a["componentType"]]
        n = NCOMP[a["type"]]
        start = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        stride = bv.get("byteStride", 0)
        itemsize = np.dtype(dt).itemsize * n
        if stride and stride != itemsize:
            arr = np.frombuffer(binary, dtype=np.uint8, count=a["count"] * stride, offset=start).reshape(a["count"], stride)
            arr = arr[:, :itemsize].copy().view(dt).reshape(a["count"], n)
        else:
            arr = np.frombuffer(binary, dtype=dt, count=a["count"] * n, offset=start).reshape(a["count"], n)
        return arr

    def local(n):
        if "matrix" in n:
            return np.array(n["matrix"], dtype=np.float64).reshape(4, 4).T
        t = np.array(n.get("translation", [0, 0, 0]), dtype=np.float64)
        q = n.get("rotation", [0, 0, 0, 1])
        s = np.array(n.get("scale", [1, 1, 1]), dtype=np.float64)
        x, y, z, w = q
        r = np.array([
            [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
        m = np.eye(4)
        m[:3, :3] = r * s
        m[:3, 3] = t
        return m

    out = []

    def walk(ni, parent):
        n = j["nodes"][ni]
        world = parent @ local(n)
        if "mesh" in n:
            for prim in j["meshes"][n["mesh"]]["primitives"]:
                pos = accessor(prim["attributes"]["POSITION"]).astype(np.float64)
                idx = accessor(prim["indices"]).reshape(-1).astype(np.int64) if "indices" in prim else np.arange(len(pos))
                uv = accessor(prim["attributes"]["TEXCOORD_0"]).astype(np.float64) if "TEXCOORD_0" in prim["attributes"] else None
                w = (np.c_[pos, np.ones(len(pos))] @ world.T)[:, :3]
                out.append({"node": n.get("name"), "material": prim.get("material"), "pos": w, "idx": idx.reshape(-1, 3), "uv": uv})
        for c in n.get("children", []):
            walk(c, world)

    scene = j["scenes"][j.get("scene", 0)]
    for r in scene["nodes"]:
        walk(r, np.eye(4))
    return j, out, binary


if __name__ == "__main__":
    import sys
    j, prims, _ = load(sys.argv[1])
    for p in prims:
        lo, hi = p["pos"].min(0), p["pos"].max(0)
        mat = j["materials"][p["material"]] if p["material"] is not None else {}
        print("%-22s mat=%-18s tris=%5d  size(cm)=%s center(cm)=%s" % (
            p["node"], mat.get("name"), len(p["idx"]), np.round((hi - lo) * 100, 1), np.round((hi + lo) * 50, 1)))
