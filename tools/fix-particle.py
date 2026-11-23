import json, os, re

root = r"G:\插件\家具"
models_dir = os.path.join(root, "src", "main", "resources", "pack", "assets", "furniture", "models")

fixed, already, errors = [], [], []

def pick_particle(tex):
    # 优先用模型自己的贴图（非 particle 自身），保持粒子外观接近模型
    for v in tex.values():
        if isinstance(v, str) and v.startswith("#"):
            continue
        if isinstance(v, str) and v:
            return v
    return "minecraft:block/oak_planks"

for fn in sorted(os.listdir(models_dir)):
    if not fn.endswith(".json"):
        continue
    p = os.path.join(models_dir, fn)
    raw = open(p, encoding="utf-8").read()
    try:
        m = json.loads(raw)
    except Exception as e:
        errors.append(f"{fn}: JSON 解析失败 {e}")
        continue
    tex = m.setdefault("textures", {})
    changed = False
    # 1. 26.x 要求 particle 槽必须存在且可解析，否则模型按缺失模型渲染（紫黑棋盘）
    if "particle" not in tex or not isinstance(tex.get("particle"), str) or tex["particle"].startswith("#"):
        tex["particle"] = pick_particle(tex)
        changed = True
    # 2. 所有面引用的槽必须定义
    used = set()
    for el in m.get("elements", []):
        for face in (el.get("faces", {}) or {}).values():
            t = face.get("texture")
            if isinstance(t, str) and t.startswith("#"):
                used.add(t[1:])
    for s in sorted(used):
        if s not in tex:
            errors.append(f"{fn}: 面引用了未定义槽 #{s}")
    if changed:
        text = json.dumps(m, ensure_ascii=False, indent=2) + "\n"
        open(p, "w", encoding="utf-8", newline="\n").write(text)
        fixed.append(fn[:-5])
    else:
        already.append(fn[:-5])

print("补 particle:", len(fixed), fixed)
print("本来就有 particle:", len(already))
print("错误:", len(errors))
for e in errors:
    print("  ", e)
