"""校验最终资源包：模型纹理引用（含 particle 槽）是否能解析到真实贴图。
模拟客户端叠加：vanilla jar 的 assets/minecraft + 本包 assets。"""
import json, os, glob, collections, zipfile

root = r"G:\插件\家具"
pack = os.path.join(root, "src", "main", "resources", "pack")
client_jar = r"D:\我的世界\.minecraft\versions\2\2.jar"

vanilla_tex, vanilla_models = set(), set()
with zipfile.ZipFile(client_jar) as z:
    for n in z.namelist():
        if n.startswith("assets/minecraft/textures/") and n.endswith(".png"):
            vanilla_tex.add(n[len("assets/minecraft/textures/"):-4])
        elif n.startswith("assets/minecraft/models/") and n.endswith(".json"):
            vanilla_models.add(n[len("assets/minecraft/models/"):-5])

pack_files = set()
for p in glob.glob(os.path.join(pack, "**", "*"), recursive=True):
    if os.path.isfile(p):
        pack_files.add(os.path.relpath(p, pack).replace("\\", "/"))

pack_tex = set(n[len("assets/"):] for n in pack_files
               if n.startswith("assets/") and "/textures/" in n and n.endswith(".png"))

def resolve_tex(ref):
    ns, _, key = ref.partition(":")
    if not key:
        ns, key = "minecraft", ref
    path = f"assets/{ns}/textures/{key}.png"
    if ns == "minecraft":
        return path in pack_files or key in vanilla_tex
    return path in pack_files

bad = collections.defaultdict(list)
no_particle = []
model_count = 0

for p in sorted(glob.glob(os.path.join(pack, "assets", "*", "models", "**", "*.json"), recursive=True)):
    rel = os.path.relpath(p, pack).replace("\\", "/")
    ns = rel.split("/")[1]
    key = rel[len(f"assets/{ns}/models/"):-5]
    try:
        m = json.load(open(p, encoding="utf-8"))
    except Exception as e:
        bad["JSON 解析失败"].append(f"{ns}:{key} {e}")
        continue
    model_count += 1
    tex = m.get("textures") or {}
    if ns == "furniture":
        if "particle" not in tex:
            no_particle.append(key)
    # 解析 # 链
    def tex_value(slot, depth=0):
        if depth > 6:
            return None
        v = tex.get(slot)
        if v is None:
            return None
        if isinstance(v, str) and v.startswith("#"):
            return tex_value(v[1:], depth + 1)
        return v
    missing_slots = []
    for el in m.get("elements", []):
        for face in (el.get("faces", {}) or {}).values():
            t = face.get("texture")
            if isinstance(t, str) and t.startswith("#"):
                val = tex_value(t[1:])
                if val is None:
                    missing_slots.append(t)
                elif isinstance(val, str) and not resolve_tex(val):
                    bad[val].append(f"{ns}:{key}")
    for slot, v in tex.items():
        if isinstance(v, str) and not v.startswith("#") and not resolve_tex(v):
            bad[v].append(f"{ns}:{key}#{slot}")

print(f"扫描模型 {model_count} 个")
print("家具模型缺 particle:", len(no_particle), no_particle)
print("无法解析的贴图引用:", len(bad))
for t, fids in list(bad.items())[:30]:
    print(f"  {t} <- {len(fids)}: {', '.join(sorted(set(fids))[:5])}")
