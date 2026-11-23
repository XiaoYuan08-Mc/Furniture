import json, os, sys, zipfile, collections, glob
import yaml

root = r"G:\插件\家具"
res = os.path.join(root, "src", "main", "resources")
yml = os.path.join(res, "furniture.yml")
models_dir = os.path.join(res, "pack", "assets", "furniture", "models")
jar = os.path.join(root, "build", "libs", "Furniture-0.4.0.jar")

data = yaml.safe_load(open(yml, encoding="utf-8"))
furn = data["furniture"]
print("定义数量:", len(furn))

problems = []
for fid, d in furn.items():
    model = d.get("model", "furniture:" + fid)
    ns, _, key = model.partition(":")
    if ns != "furniture":
        problems.append(f"{fid}: 非 furniture 命名空间 {model}")
        continue
    path = os.path.join(models_dir, key + ".json")
    if not os.path.exists(path):
        problems.append(f"{fid}: 缺模型 {key}.json")
        continue
    try:
        json.load(open(path, encoding="utf-8"))
    except Exception as e:
        problems.append(f"{fid}: 模型 JSON 解析失败 {e}")
    mat = d.get("material")
    storage = d.get("storage", 0)
    if storage and storage not in (9, 18, 27, 36, 54):
        problems.append(f"{fid}: storage={storage} 不是标准格数")
    if d.get("seat") and "seat-y" not in d:
        problems.append(f"{fid}: seat 缺 seat-y")
    if d.get("seats") and not d.get("seat"):
        problems.append(f"{fid}: seats 但没 seat")
    if d.get("seats", 1) > max(1, d.get("size-width", 1)):
        problems.append(f"{fid}: seats={d['seats']} 超过宽度 {d.get('size-width',1)}")
print("模型/字段问题:", len(problems))
for p in problems:
    print("  ", p)

# 配方签名去重
sigs = collections.defaultdict(list)
for fid, d in furn.items():
    r = d.get("recipe")
    if not r:
        continue
    if r.get("shaped", True):
        shape = r.get("shape") or []
        ing = r.get("ingredients") or {}
        sig = ("S", tuple(shape), tuple(sorted((k, str(v)) for k, v in ing.items())))
    else:
        sig = ("U", tuple(sorted(str(x) for x in (r.get("ingredients") or []))))
    sigs[sig].append(fid)
dups = {k: v for k, v in sigs.items() if len(v) > 1}
print("配方数量:", sum(len(v) for v in sigs.values()), "重复签名:", len(dups))
for k, v in dups.items():
    print("  重复:", v, k)

# 材质合法性（用服务端 registry 粗略核对 material 名称是否存在于 jar 里太麻烦，这里只查空值）
empty_mat = [fid for fid, d in furn.items() if not d.get("material")]
print("缺 material:", empty_mat)

if os.path.exists(jar):
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        pack_entries = [n for n in names if n.startswith("pack/")]
        print("jar 内 pack/ 条目:", len(pack_entries))
        print("jar 内 furniture.yml:", "furniture.yml" in names, " config.yml:", "config.yml" in names)
        defines = [n for n in pack_entries if "/items/" in n]
        model_files = [n for n in pack_entries if "/models/" in n]
        print("jar items:", len(defines), "models:", len(model_files))
        missing = [f for f in furn if f"pack/assets/furniture/models/{furn[f].get('model','furniture:'+f).partition(':')[2]}.json" not in names]
        print("jar 缺模型:", missing)


# ---------------------------------------------------------------- 座位点核对
seat_bad = []
for fid, d in furn.items():
    pts = d.get("seat-points")
    if not pts:
        continue
    if not d.get("seat"):
        seat_bad.append(f"{fid}: 写了 seat-points 但 seat 不是 true")
        continue
    sw = d.get("size-width", 1)
    sd = d.get("size-depth", 1)
    if len(pts) > 1 and len(pts) != d.get("seats", 1):
        seat_bad.append(f"{fid}: seat-points {len(pts)} 个 / seats {d.get('seats', 1)}")
    for p in pts:
        if not isinstance(p, list) or len(p) != 3:
            seat_bad.append(f"{fid}: seat-points 有一项不是 [x, y, z]: {p}")
            continue
        x, y, z = p
        if not (0 <= x <= sw * 16 and 0 <= z <= sd * 16):
            seat_bad.append(f"{fid}: 座位点 {p} 超出占地 {sw}x{sd} 格")
        if not (0 < y <= 32):
            seat_bad.append(f"{fid}: 座位点 {p} 高度不在 0~2 格之间")
        if abs(y / 16.0 - d.get("seat-y", 0.6)) > 0.001:
            seat_bad.append(f"{fid}: seat-y {d.get('seat-y')} 和 seat-points 的 {y / 16.0:.4f} 对不上（以 seat-points 为准，只是提醒）")
print("座位点问题:", len(seat_bad))
for p in seat_bad:
    print("  ", p)

# ---------------------------------------------------------------- 贴图名核对
# 26.3 改过一批原版贴图文件名（比如 smooth_quartz 没了），引用对不上客户端就画紫黑方块。
client_jar = os.environ.get("MC_CLIENT_JAR", r"D:\mc263\client.jar")
if os.path.exists(client_jar):
    with zipfile.ZipFile(client_jar) as z:
        vanilla = {n[len("assets/minecraft/textures/block/"):-4]
                   for n in z.namelist()
                   if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")}
    tex_bad = {}
    for path in glob.glob(os.path.join(models_dir, "*.json")):
        try:
            model = json.load(open(path, encoding="utf-8"))
        except Exception:
            continue
        for key, value in (model.get("textures") or {}).items():
            if not isinstance(value, str):
                continue
            if value.startswith("minecraft:block/"):
                if value.split("/", 1)[1] not in vanilla:
                    tex_bad.setdefault(os.path.basename(path), []).append(value)
            elif not value.startswith("furniture:"):
                tex_bad.setdefault(os.path.basename(path), []).append(value)
    print("贴图缺失:", len(tex_bad))
    for name, refs in tex_bad.items():
        print("  ", name, "->", sorted(set(refs)))
else:
    print("找不到客户端 jar，跳过贴图核对（可用环境变量 MC_CLIENT_JAR 指定）")
