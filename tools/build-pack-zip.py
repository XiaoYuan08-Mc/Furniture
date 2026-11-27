"""模拟插件运行时生成的资源包（jar 内 pack/ + items/*.json + pack.mcmeta），
输出到 build/FurniturePack.zip 供本地校验/单独分发。"""
import json, os, zipfile, hashlib, glob, re

root = r"E:\繁花梦\03-插件\家具"
jar_path = max(glob.glob(os.path.join(root, "build", "libs", "Furniture-*.jar")), key=os.path.getmtime)
ver = re.search(r"Furniture-(\d+\.\d+\.\d+)\.jar", os.path.basename(jar_path)).group(1)
pack_root = os.path.join(root, "src", "main", "resources", "pack")
out = os.path.join(root, "build", f"FurniturePack-{ver}.zip")

# furniture.yml 里的 model 名 → items/<id>.json
defs = {}
for line in open(os.path.join(root, "src", "main", "resources", "furniture.yml"), encoding="utf-8"):
    m = re.match(r"^  ([a-z0-9_]+):\s*$", line)
    if m:
        current = m.group(1)
    m2 = re.match(r"^    model:\s*(\S+)", line)
    if m2:
        defs[current] = m2.group(1).strip('"\'')

assets = {}
import yaml
cfg = yaml.safe_load(open(os.path.join(root, "src", "main", "resources", "config.yml"), encoding="utf-8"))["resource-pack"]
desc = cfg["description"]
fmt_major = cfg["pack-format"]["major"]
fmt_minor = cfg["pack-format"]["minor"]
fmt_max_major = max(fmt_major, cfg["pack-format"]["max-major"])
fmt_max_minor = max(fmt_minor, cfg["pack-format"]["max-minor"])
with zipfile.ZipFile(jar_path) as z:
    for n in z.namelist():
        if n.startswith("pack/") and not n.endswith("/"):
            assets[n[len("pack/"):]] = z.read(n)

count_items = 0
for fid, model in defs.items():
    ns, _, key = model.partition(":")
    if ns != "furniture":
        continue
    assets[f"assets/furniture/items/{key}.json"] = json.dumps(
        {"model": {"type": "minecraft:model", "model": f"furniture:{key}"}}, ensure_ascii=False, indent=2
    ).encode() + b"\n"
    count_items += 1

meta = json.dumps({"pack": {"description": desc,
                            "min_format": [fmt_major, fmt_minor], "max_format": [fmt_max_major, fmt_max_minor]}}, ensure_ascii=False, indent=2)

with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    info = zipfile.ZipInfo("pack.mcmeta", (1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(info, meta.encode())
    for name in sorted(assets):
        info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, assets[name])

data = open(out, "rb").read()
print("条目:", len(assets) + 1, "items 定义:", count_items)
print("大小:", len(data), "字节")
print("sha1:", hashlib.sha1(data).hexdigest())
print("输出:", out)
