"""把模型里对 minecraft:block/... 的贴图引用改成家具包自带的副本，
避免客户端装了别的资源包、或后续版本贴图改名时家具变紫黑/变色。"""
import json, os, shutil, zipfile

root = r"E:\繁花梦\03-插件\家具"
pack = os.path.join(root, "src", "main", "resources", "pack")
models_dir = os.path.join(pack, "assets", "furniture", "models")
tex_dir = os.path.join(pack, "assets", "furniture", "textures", "block")
client_jar = r"D:\我的世界\.minecraft\versions\2\2.jar"

os.makedirs(tex_dir, exist_ok=True)
copied, remapped, missing = [], [], []

with zipfile.ZipFile(client_jar) as z:
    names = set(z.namelist())
    for fn in sorted(os.listdir(models_dir)):
        if not fn.endswith(".json"):
            continue
        p = os.path.join(models_dir, fn)
        m = json.load(open(p, encoding="utf-8"))
        changed = False
        tex = m.get("textures") or {}
        for slot, val in list(tex.items()):
            if not isinstance(val, str) or val.startswith("#"):
                continue
            if val.startswith("minecraft:block/"):
                key = val[len("minecraft:"):]          # block/xxx
                src = "assets/minecraft/textures/" + key + ".png"
                meta = "assets/minecraft/textures/" + key + ".png.mcmeta"
                if src not in names:
                    missing.append(val)
                    continue
                dst = os.path.join(tex_dir, key.split("/", 1)[1] + ".png")
                if not os.path.exists(dst):
                    with z.open(src) as fsrc, open(dst, "wb") as fdst:
                        fdst.write(fsrc.read())
                    copied.append(os.path.basename(dst))
                if meta in names:
                    mdst = dst + ".mcmeta"
                    if not os.path.exists(mdst):
                        with z.open(meta) as fsrc, open(mdst, "wb") as fdst:
                            fdst.write(fsrc.read())
                        copied.append(os.path.basename(mdst))
                tex[slot] = "furniture:" + key
                changed = True
        if changed:
            open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(m, ensure_ascii=False, indent=2) + "\n")
            remapped.append(fn[:-5])

print("改引用模型:", len(remapped))
print("复制贴图:", len(copied), sorted(set(copied)))
print("原版 jar 里找不到:", sorted(set(missing)))
