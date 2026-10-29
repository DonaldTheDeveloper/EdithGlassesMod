import bpy, math, sys, os
from mathutils import Vector

ang_z = float(os.environ.get("ANG_Z", "35"))    # camera azimuth around the glasses
ang_e = float(os.environ.get("ANG_E", "12"))    # elevation
dist = float(os.environ.get("DIST", "0.42"))
out = os.environ.get("OUT", os.path.join(os.path.dirname(os.path.abspath(__file__)), "test.png"))
res = int(os.environ.get("RES", "512"))
transparent = os.environ.get("TRANSPARENT", "0") == "1"

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools", "edith_blenderkit.glb"))
sc = bpy.context.scene
hide = ("Bottom-Case", "Top-Case", "Bottom-Hinge", "Top-Hinge", "Stark Industries")
for o in sc.objects:
    if o.name in hide:
        o.hide_render = True
        o.hide_viewport = True

# centre of the visible glasses
pts = []
for o in sc.objects:
    if o.type == 'MESH' and not o.hide_render:
        for c in o.bound_box:
            pts.append(o.matrix_world @ Vector(c))
mn = Vector((min(p[i] for p in pts) for i in range(3)))
mx = Vector((max(p[i] for p in pts) for i in range(3)))
centre = (mn + mx) / 2
size = mx - mn
print("BBOX", tuple(round(x, 3) for x in size), tuple(round(x, 3) for x in centre))

# make the lens and the glow emit a little so the glasses look powered on
for m in bpy.data.materials:
    if m.use_nodes:
        bsdf = next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is None:
            continue
        if m.name == "glass-edith":
            bsdf.inputs["Base Color"].default_value = (0.02, 0.14, 0.4, 1)
            bsdf.inputs["Roughness"].default_value = 0.04
            bsdf.inputs["Emission Color"].default_value = (0.05, 0.5, 1.0, 1)
            bsdf.inputs["Emission Strength"].default_value = 0.85
        if m.name == "Glow":
            bsdf.inputs["Emission Color"].default_value = (0.2, 0.75, 1.0, 1)
            bsdf.inputs["Emission Strength"].default_value = 6.0
        if m.name in ("gloss-metal", "Material.006"):
            bsdf.inputs["Metallic"].default_value = 1.0
            bsdf.inputs["Roughness"].default_value = 0.18

# camera
cam_data = bpy.data.cameras.new("cam")
cam_data.lens = 85
cam = bpy.data.objects.new("cam", cam_data)
sc.collection.objects.link(cam)
az, el = math.radians(ang_z), math.radians(ang_e)
# glasses look towards -Y (front). Camera swings around them.
cam.location = centre + Vector((math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el))) * dist
direction = centre - cam.location
cam.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
sc.camera = cam

# lights: warm key, cool rim from behind, soft fill
def area(name, loc, energy, color, size_m):
    d = bpy.data.lights.new(name, 'AREA')
    d.energy = energy
    d.color = color
    d.size = size_m
    o = bpy.data.objects.new(name, d)
    o.location = centre + Vector(loc)
    o.rotation_euler = (centre + Vector((0, 0, 0)) - o.location).to_track_quat('-Z', 'Y').to_euler()
    sc.collection.objects.link(o)

area("key", (-0.3, -0.3, 0.45), 28, (1.0, 0.93, 0.85), 0.35)
area("rim", (0.3, 0.35, 0.2), 70, (0.25, 0.7, 1.0), 0.3)
area("rim2", (-0.35, 0.3, 0.1), 45, (0.2, 0.55, 1.0), 0.3)
area("fill", (0.4, -0.3, 0.3), 14, (0.8, 0.9, 1.0), 0.5)

# world: deep blue gradient
world = bpy.data.worlds.new("w")
sc.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.01, 0.03, 0.06, 1)
bg.inputs[1].default_value = 1.0

sc.render.engine = 'BLENDER_EEVEE'
sc.render.resolution_x = res
sc.render.resolution_y = res
sc.render.film_transparent = True
sc.render.image_settings.file_format = 'PNG'
sc.render.image_settings.color_mode = 'RGBA'
sc.render.filepath = out
try:
    sc.eevee.taa_render_samples = 128
except Exception:
    pass
sc.view_settings.view_transform = 'Filmic' if 'Filmic' in [v.identifier for v in sc.view_settings.bl_rna.properties['view_transform'].enum_items] else 'AgX'

bpy.ops.render.render(write_still=True)
print("RENDERED", out)
