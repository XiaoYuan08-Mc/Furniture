import json, os, sys, zipfile, collections
import yaml

root = r"G:\插件\家具"
res = os.path.join(root, "src", "main", "resources")
yml = os.path.join(res, "furniture.yml")
models_dir = os.path.join(res, "pack", "assets", "furniture", "models")
jar = os.path.join(root, "build", "libs", "Furniture-0.3.1.jar")

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

