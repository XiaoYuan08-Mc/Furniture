import json, os, sys, zipfile, re

root = r"G:\插件\家具"
models_dir = os.path.join(root, "src", "main", "resources", "pack", "assets", "furniture", "models")
client_jar = r"D:\我的世界\.minecraft\versions\2\2.jar"

# 客户端里可用的贴图与模型清单（含 mod 提供的？只算原版 assets/minecraft）
tex_avail = set()
model_avail = set()
with zipfile.ZipFile(client_jar) as z:
    for n in z.namelist():
        if n.startswith("assets/minecraft/textures/") and n.endswith(".png"):
            tex_avail.add("minecraft:" + n[len("assets/minecraft/textures/"):-4])
        elif n.startswith("assets/minecraft/models/") and n.endswith(".json"):
            model_avail.add("minecraft:" + n[len("assets/minecraft/models/"):-5])

print("jar 内贴图:", len(tex_avail), " 模型:", len(model_avail))


def load_model(ns_key):
    """返回 (json 或 None, 错误信息)"""
    ns, _, key = ns_key.partition(":")
    if not key:
        ns, key = "minecraft", ns
    if ns == "furniture":
        p = os.path.join(models_dir, key + ".json")
        if not os.path.exists(p):
            return None, f"缺少模型 furniture:{key}"
        try:
            return json.load(open(p, encoding="utf-8")), None
        except Exception as e:
            return None, f"JSON 解析失败 furniture:{key}: {e}"
    if ns == "minecraft":
        return None, None  # 原版父模型不深究
    return None, f"不支持命名空间 {ns_key}"


missing_tex = {}
missing_parent = {}
problems = []
refs = {}

for fn in sorted(os.listdir(models_dir)):
    if not fn.endswith(".json"):
        continue
    fid = fn[:-5]
    path = os.path.join(models_dir, fn)
    try:
        m = json.load(open(path, encoding="utf-8"))
    except Exception as e:
        problems.append(f"{fid}: JSON 解析失败 {e}")
        continue
    textures = m.get("textures", {}) or {}
    used = set()
    def walk(node):
        if isinstance(node, dict):
            for k, v in node.items():
                if k == "parent":
                    pv = v
                    if isinstance(pv, str) and not pv.startswith("minecraft:"):
                        pm, err = load_model(pv)
                        if err:
                            missing_parent.setdefault(err, []).append(fid)
                    elif isinstance(pv, str) and pv.startswith("minecraft:"):
                        key = pv[len("minecraft:"):]
                        if pv.startswith("minecraft:block/") and "block/" + key.split("/", 1)[1] not in model_avail:
                            missing_parent.setdefault(pv, []).append(fid)
                if isinstance(v, str) and v.startswith("#"):
                    used.add(v[1:])
                walk(v)
        elif isinstance(node, list):
            for x in node:
                walk(x)
    walk(m)
    for slot in used:
        if slot not in textures:
            problems.append(f"{fid}: 未定义的纹理槽 #{slot}")
    resolved = {}
    for slot, val in textures.items():
        if not isinstance(val, str):
            continue
        if val.startswith("#"):
            continue
        if val.startswith("minecraft:"):
            t = val
        elif val.startswith("furniture:"):
            t = val
        else:
            t = "furniture:" + val
        resolved[slot] = t
        refs.setdefault(t, set()).add(fid)
    for t in resolved.values():
        if t.startswith("furniture:"):
            key = t[len("furniture:"):]
            p = os.path.join(root, "src", "main", "resources", "pack", "assets", "furniture", "textures", key + ".png")
            if not os.path.exists(p):
                missing_tex.setdefault(t, set()).add(fid)
        else:
            key = t[len("minecraft:"):]
            if t not in tex_avail:
                missing_tex.setdefault(t, set()).add(fid)

print("结构问题:", len(problems))
for p in problems[:40]:
    print("  ", p)
print("\n缺失贴图条目:", len(missing_tex))
for t, fids in sorted(missing_tex.items(), key=lambda kv: -len(kv[1])):
    print(f"  {t}  <- {len(fids)} 个模型: {', '.join(sorted(fids)[:8])}")
print("\n父模型问题:", len(missing_parent))
for t, fids in missing_parent.items():
    print(f"  {t} <- {', '.join(sorted(fids))}")
