# Furniture 0.2.0

基于 **Display 实体 + 自动资源包** 的家具框架，当前自带 **52 件家具 / 景观**，全部可在工作台合成。这一版起支持**多格占用、旋转、坐下、灯具开关**。

- 服务端：Purpur/Paper **26.3**（Java 25）
- 家具外观：`ItemDisplay` 渲染 `item_model` 自定义模型（`ItemDisplayTransform.NONE`，模型 1 单位 = 1 像素，16 = 1 格）
- 交互碰撞：`Interaction` 实体（尺寸按家具定义，多格家具的交互箱会覆盖整个占地）
- 资源包：插件启动时把内置 `pack/` 打成 zip，并起一个内置 HTTP 服务（默认 8123），玩家登录后自动下发
- 合成：`furniture.yml` 的 `recipe` 节点注册成真正的工作台配方，进服自动解锁配方书

## 快速测试

1. 把 `build/libs/Furniture-0.2.0.jar` 丢进 `plugins/`，重启服务端。
2. 控制台确认出现下面几行：
   - `已加载家具定义 52 个, 合成配方 52 条`
   - `已注册合成配方 52 条`
   - `资源包已生成: ...\plugins\Furniture\pack\FurniturePack.zip (… 字节, sha1=…)`
   - `资源包 HTTP 服务已启动，监听 0.0.0.0:8123 (/pack.zip)`
3. 进服 → 接受资源包（提示语可在 `config.yml` 改），控制台出现 `资源包已加载: <玩家名>`。
4. 批量拿家具看外观（比一件件点快得多）：
   ```
   /furniture list
   /furniture give chair 8           # 给自己 8 件木椅
   /furniture give couch XiaoYuan08 4
   ```
5. 放置与交互：
   ```
   手持家具右键方块      # 放置，朝向自动跟随你的视角
   左键                  # 拆除（默认掉落家具物品）
   潜行右键              # 原地旋转 90°
   右键椅子              # 坐上去（潜行 / 受击自动起身）
   右键灯具              # 开灯 / 关灯
   /furniture place table 10 64 10 90
   /furniture remove
   /furniture rotate 10 64 10 90
   /furniture light 10 64 10
   ```

## 家具清单（52 件）

| 分类 | ID |
| --- | --- |
| 座椅 | `chair` 木椅、`chair_spruce` 云杉椅、`stool` 圆凳、`bench` 长凳、`rocking_chair` 摇椅、`armchair` 扶手椅、`couch` 沙发、`couch_blue` 蓝色沙发、`office_chair` 办公椅 |
| 桌台 | `table` 木桌、`coffee_table` 茶几、`desk` 书桌、`dining_table` 餐桌（2 格宽）、`side_table` 边几、`picnic_table` 野餐桌（2 格宽） |
| 储物 | `cabinet` 木柜、`drawer` 抽屉柜、`shelf` 书架、`wardrobe` 衣柜（2 格高）、`nightstand` 床头柜、`shoe_rack` 鞋架、`tv_stand` 电视柜、`crate` 木箱、`vanity` 梳妆台 |
| 床 | `bed` 单人床、`crib` 婴儿床 |
| 灯具 | `lamp` 落地灯、`table_lamp` 台灯、`street_lamp` 路灯（3 格高）、`lantern_post` 灯笼柱（2 格高） |
| 绿植 | `plant` 盆栽、`flower_pot` 花盆、`planter_box` 花箱、`hedge` 绿篱、`birdhouse` 鸟屋、`mailbox` 信箱、`fence` 栅栏段、`well` 水井 |
| 景观 | `garden_bench` 花园长椅、`fountain` 喷泉、`arch` 花园拱门（2 格高）、`scarecrow` 稻草人 |
| 厨房 | `kitchen_counter` 厨房台面、`sink` 水槽、`fridge` 冰箱（2 格高）、`stove` 灶台 |
| 其他 | `clock` 落地钟（3 格高）、`coat_rack` 衣帽架、`rug` 地毯、`tv` 电视、`fireplace` 壁炉（2 格高）、`piano` 钢琴 |

## 工作台合成

配方写在 `furniture.yml` 的 `recipe` 节点里，插件启动 / `/furniture reload` 时注册成真正的工作台配方。

```yaml
    recipe:
      shaped: true        # 有序配方
      shape:
        - " P "
        - "PPP"
      ingredients:
        P: OAK_PLANKS
      amount: 1
```

无序配方写 `shaped: false`，`ingredients` 直接给材质列表。查看当前所有配方（含字符图例）：`/furniture recipes`。

合成产物是**带 `item_model` 和 PDC 的家具物品**，由 `PrepareItemCraftEvent` 按 3x3 合成格重新匹配后写进结果槽，不依赖服务端对配方产物的序列化行为，所以能正常摆放、也能在背包里显示家具模型。

自带这 52 条配方特意避开了原版形状（8 木板环形 = 箱子、3 羊毛 + 3 木板 = 床、6 木板 + 3 书 = 书架、6 木板 2x3 = 门/活板门），自己加配方时也要注意别撞，撞了会优先匹配到原版配方。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/furniture list` | 家具定义列表 + 已放置数量 |
| `/furniture recipes` | 查看工作台合成配方（字符图例 + 材料） |
| `/furniture give <ID> [玩家] [数量]` | 获取家具（默认给自己 1 个，最多 256） |
| `/furniture place <ID>` | 准星处放置（玩家） |
| `/furniture place <ID> <x> <y> <z> [yaw]` | 指定坐标放置（控制台可用） |
| `/furniture remove` | 拆除附近最近一件 |
| `/furniture remove <x> <y> <z>` | 拆除指定坐标 |
| `/furniture rotate [x y z] [角度]` | 原地旋转（默认 90°，控制台带坐标） |
| `/furniture light [x y z]` | 开关灯具 |
| `/furniture nearest` | 看身边最近一件家具的 yaw / lit / 占地 |
| `/furniture pack` | 重新下发资源包 |
| `/furniture reload` | 重载 `config.yml` / `furniture.yml`、重建资源包、重注册配方 |

## 配置

`plugins/Furniture/config.yml`

- `resource-pack.mode`：`builtin`（插件自己分发）/ `external`（你自己托管 zip，填 `public-url`）
- `resource-pack.port`：内置 HTTP 端口（被占用时自动顺延，日志里打实际端口）
- `resource-pack.public-url`：留空时自动用本机局域网 IPv4；**正式服请填公网 IP / 域名**
- `resource-pack.pack-format`：默认 `97.1`（26.3 的资源包格式）
- `place.consume-item`：放置是否消耗物品（测试期建议 `false`）
- `place.drop-item`：拆除是否掉落
- `interact.sneak-rotate`：潜行右键是否 = 旋转 90°（关掉就只剩拆除以外什么都不做）
- `interact.seat-offset`：坐姿高度微调（格）。人浮在椅面上就填负数（-0.1），陷进椅面就填正数（+0.1），`/furniture reload` 生效
- `interact.seat-marker`：座位用 marker 隐形盔甲架（不挡碰撞），坐不上去时改 `false`

`plugins/Furniture/furniture.yml`

`name / model / material / hitbox-width / hitbox-height / view-range / transform / scale / offset-y / size-width / size-depth / seat / seat-y / light / recipe`，含义写在文件注释里。
`model` 即资源包中的模型 ID，形如 `furniture:chair`：客户端先读 `assets/furniture/items/chair.json`（物品模型定义，**由插件在重建包时按定义自动生成**），再由它指向 `assets/furniture/models/chair.json`。

加新家具的最小步骤：往 `pack/assets/furniture/models/` 放一个模型 json，在 `furniture.yml` 加一条 `model: furniture:xxx`，`/furniture reload`。`items/xxx.json` 不用手写。
若 `model` 写成别的命名空间（例如 `minecraft:block/stone`），插件不会生成定义文件（避免覆盖原版），需要你自己提供对应的 `assets/<命名空间>/items/<路径>.json`，控制台会告警。

## 多格 / 坐下 / 开关灯怎么配

```yaml
  dining_table:
    size-width: 2      # 占地 2 格（模型局部 +x 方向）
    size-depth: 2      # 占地 2 格（模型局部 +z 方向），床/婴儿床用这个
  chair:
    seat: true
    seat-y: 0.62       # 人坐在离脚下方块底部 0.62 格的位置
  lamp:
    light: true        # 右键切换模型亮度（15/15 自发光）
```

- 占地格子的方向跟着 `yaw` 转：`yaw=0` 时 `size-width` 往世界 `+x` 铺，`yaw=90` 时往 `+z` 铺，旋转后会被重新校验碰撞并自动拒绝转不开的位置。
- 放置/旋转时只要有任意一格被实体方块或别的家具占着就拒绝，提示写在聊天栏。
- 当前标好的：`dining_table` / `picnic_table` 宽 2，`bed` / `crib` 深 2；10 件座椅 `seat: true`（木椅 0.62 / 圆凳 0.5 / 扶手椅 0.42 / 蓝色沙发 0.35 / 办公椅 0.65 / 床 0.26 …）；`lamp`、`table_lamp`、`street_lamp`、`lantern_post`、`fireplace` 可开关灯。
- 存档 `placed.yml` 会记 `yaw` 和 `lit`，重启后按原朝向恢复，老存档（没有 yaw / hitbox 字段）也能读，交互箱会自动重新绑上。

## 改模型 / 加家具更快的办法

`tools/gen-catalog.ps1` 是这个目录的生成器：里面用「一行一个盒子」的紧凑写法描述所有家具（坐标 + 贴图键 + 配方 + `sw/sd/seat/seatY/light`），跑一次就重新生成 42 个新模型和整份 `furniture.yml`。

```
powershell -File tools/gen-catalog.ps1
```

改外形就在 `$items` 里改 `box`，加家具就复制一条改 `id` / `name` / `box` / `recipe`。已有的 10 件（`skipModel=$true`）是手调过的模型，不会被覆盖。

## 模型约定

- 坐标 `0~16` = 1 格，`y=0` 是家具底部，模型正面朝南（`+z`）；`y` 超过 16 就是 2 格高（衣柜、路灯、落天钟等）。
- 放置时锚点取方块中心 `y+0.5`，模型底面正好贴地；朝向为「玩家视角 yaw + 180」，即正面朝向你。
- 改模型不需要重做别的：换模型文件后 `/furniture reload` 重建包即可。
- 贴图全部复用原版方块贴图（`minecraft:block/oak_planks` 之类），所以 52 件的包只有 38 KB，不需要额外 png。

## 已知限制（这一版故意不做）

- 没有实体碰撞：家具是纯显示 + 交互箱，玩家能穿过去（后续可用撤销碰撞 / 隐形方块方案）。
- 不能储物（柜子 / 冰箱 / 箱子都只是外壳，没有库存）。
- 拆除后掉落物品，但没有「只能被自己放置的家具拆」之类的权限控制。
- 无玩家在线时区块不加载，控制台放置的家具要等有人靠近才看得见（实体生命周期跟区块走，属正常现象）。
- 坐标放置/旋转/开关灯的命令面向调试，正式服建议走近使用。

## 这一版已实机验证的（无头服 + 控制台命令）

- 餐桌 `yaw=0` 占 `(x,z)+(x+1,z)`，在第二格放别的家具会被拒；`yaw=90` 改占 `(x,z)+(x,z+1)`
- 床 `yaw=0` 占 `(x,z)+(x,z+1)`，婴儿床同
- 连续旋转 90° / 180° / 270° 正常，占地跟着转并按新格子校验
- 落地灯连续开关：`已开灯 → 已关灯 → 已开灯`，`placed.yml` 里 `lit` 同步
- 非灯具右键提示 `xxx 不是灯具`
- 重启恢复 5~6 件，`yaw` / `lit` 保留；把某条记录的 `yaw`/`hitbox` 字段删掉模拟老存档，仍能正常恢复并找回交互箱
- 资源包 105 条目（52 items + 52 models + pack.mcmeta），38,101 字节

## 构建

```
gradle build --offline
```

编译期直接用本机已有的 `G:/服务端/Purpur-26.3/purpur-api/build/libs/purpur-api-26.3.local-SNAPSHOT.jar`，
以及服务端 `libraries/` 里的 Adventure / Guava / BungeeChat / JOML，**离线可编译**；没有本地 jar 时回退到
`org.purpurmc.purpur:purpur-api:26.3.build.2660-stable`。

## 下一步可以接着做

1. 柜子 / 冰箱 / 木箱挂真实库存（`InventoryHolder` 或 `ItemDisplay` + 虚拟库存 GUI）。
2. 实体碰撞（`barrier` 撤销 或 隐形方块方案），避免玩家穿模。
3. 双格床的「躺下」姿态、沙发多人同坐。
4. 资源包合并：把已有的服务器资源包和家具包 zip 合并，只下发一个。
5. 家具 GUI 图标单独做 2D 模型（现在背包里是 3D 模型按方块比例显示）。
6. 家具数量继续扩（现在 52 件，目标是模拟 mod 家具包的量级）。

