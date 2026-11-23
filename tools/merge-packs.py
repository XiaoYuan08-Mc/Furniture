"""把 集成/ 下四个资源包的 assets 合并进插件内置 pack，
统一出一个资源包，客户端只装一个。家具命名空间 furniture 不参与覆盖。"""
import os, zipfile, collections

root = r"G:\插件\家具"
pack = os.path.join(root, "src", "main", "resources", "pack")
integr = os.path.join(root, "集成")
order = ["Anti_Xray_Full_26.3.zip", "EnderEyes_26.3_v3.1.zip",
         "EvenBetterEnchants_v3_1.21.5+.zip", "Clearer Slot Highlight.zip"]

skip_prefix = ("__MACOSX/",)
skip_exact = {"pack.mcmeta", "pack.png", "README.txt", ".DS_Store"}

written = collections.Counter()
conflicts = []
for zname in order:
    zpath = os.path.join(integr, zname)
    if not os.path.exists(zpath):
        print("缺失:", zname)
        continue
    with zipfile.ZipFile(zpath) as z:
        for e in z.infolist():
            name = e.filename
            name = name.replace("\\", "/")
            if name.endswith("/") or name in skip_exact or name.startswith(skip_prefix):
                continue
            if not name.startswith("assets/"):
                continue
            if name.startswith("assets/furniture/"):
                conflicts.append((zname, name, "家具命名空间跳过"))
                continue
            dst = os.path.join(pack, *name.split("/"))
            if os.path.exists(dst):
                conflicts.append((zname, name, "覆盖"))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with z.open(e) as src, open(dst, "wb") as out:
                out.write(src.read())
            written[zname] += 1

for z, n in written.items():
    print(f"{z}: 写入 {n}")
print("冲突/跳过:", len(conflicts))
for c in conflicts[:20]:
    print("  ", c)
