# Furniture

用 Display 实体做家具，资源包自动生成。自带 76 件家具和景观，都能在工作台合成。跑在 Purpur / Paper 26.3、Java 25 上。

- 服务端：Purpur/Paper **26.3**（Java 25）
- 家具外观：`ItemDisplay` 渲染 `item_model` 自定义模型（`ItemDisplayTransform.NONE`，模型 1 单位 = 1 像素，16 = 1 格）
- 交互碰撞：`Interaction` 实体（尺寸按家具定义，多格家具的交互箱覆盖整个占地）
- 资源包：插件启动时把内置 `pack/` 打成 zip，并起一个内置 HTTP 服务（默认 8123，被占用自动顺延），玩家登录后自动下发
- 合成：`furniture.yml` 的 `recipe` 节点注册成真正的工作台配方

## 0.3.1 修了什么

1. **容器槽位保序**：界面里物品放在第 20 格，关掉重开后还在第 20 格（以前会全部挤到最前面）。存档用空串占位。
2. **世界未加载的记录不再被清掉**：多世界服里 world 没加载时先把记录 park 住并原样回写，不会被 `saveAll` 当成垃圾删掉。
3. **显示实体丢失重新生成时保留容器内容**：以前重生成只补模型和交互箱，柜子里的东西会没。
4. **容器界面身份校验**：只认当初打开的那个界面，别的界面关闭不会把内容误写进家具。
5. **关服刷新容器**：`onDisable` 先把还在看界面的玩家内容写回再存盘。
6. **开箱失败清理映射**：被别的插件拦下没打开时不会留下脏占用，挡住其他人。
7. **座位加固**：`seats` 自动钳制不超过家具宽度；清理座位实体已消失的脏记录；跨世界 / 传送到空目标不再 NPE。
8. **旋转先请人起身**：命令旋转和交互旋转都会先把坐着的人请起来，座位不会停在旧朝向。
9. **灯具光源方块补回**：区块重新加载时如果外部把 `light` 方块改掉了，会自动补回来。
10. **资源包 hash 稳定**：zip 条目固定时间戳，内容没改时 `sha1` 不变，`/furniture reload` 不会让所有玩家重下资源包。
11. **资源包 HTTP 线程池回收**：每次 reload 都会关掉旧线程池，不再泄漏。
12. **`/furniture reload` 也重读 `placed.yml`**：手工改过存档能直接生效，已有实体会被复用不会重复生成。

## 0.3.0 相对 0.2.0 的变化

1. **真实照明**：灯具开灯时在锚点格放一个不可见的 `minecraft:light`（亮度 15），真的照亮地面和墙；关灯 / 拆除自动清掉。`config.yml` 的 `light.place-block` 可关。
2. **多人座位**：`seats: 2/3` 让长条沙发、野餐桌、秋千能同时坐多人，沿家具宽度自动等分落座，坐满会提示。
3. **储物容器内容持久化**：改成 Base64 物品字节存 `placed.yml`（原来的 YAML ItemStack 序列化在 26.3 会 `Material cannot be null`，已废弃）。
4. **新增 `/furniture fill`**：不用开 GUI 就能往储物家具里塞物品，方便验收和补货。
5. **`/furniture info`** 增加人数、锚点方块与亮度，验收灯和容器一眼看清。
6. **`/furniture list` 支持中文名过滤**（`/furniture list 沙发` 直接列出 4 件沙发）。
7. 目录 52 → **76 件**，新增 24 件（含樱桃木、玻璃、沙岩材质）。

## 快速测试

1. 把 `build/libs/Furniture-0.3.1.jar` 丢进 `plugins/`（**删掉旧的 0.3.0 jar**，同版本名会报 Ambiguous plugin name）。
2. 控制台确认：
   - `已加载家具定义 76 个, 合成配方 76 条`
   - `资源包已生成: ...\plugins\Furniture\pack\FurniturePack.zip (56199 字节, sha1=…)`
   - `资源包 HTTP 服务已启动，监听 0.0.0.0:8123 (/pack.zip)`
3. 进服接受资源包，控制台出现 `资源包已加载: <玩家名>`。
4. 拿家具与放置：
   ```
   /furniture list 沙发
   /furniture give sofa_long 2
   手持家具右键方块      # 放置，朝向跟随视角
   左键                  # 拆除（默认掉落家具物品）
   潜行右键              # 原地旋转 90°
   右键椅子              # 坐上去（潜行 / 受击 / 传送 / 死亡自动起身）
   右键柜子              # 打开储物界面
   右键灯具              # 开灯 / 关灯
   ```

## 家具清单（76 件）

| 分类 | ID |
| --- | --- |
| 座椅 | `chair` 木椅、`chair_spruce` 云杉椅、`stool` 圆凳、`bench` 长凳、`rocking_chair` 摇椅、`armchair` 扶手椅、`couch` 沙发、`couch_blue` 蓝色沙发、`office_chair` 办公椅、`bar_stool` 吧台凳、`piano_stool` 琴凳、`sofa_long` 长条沙发（2 格宽 / 2 人）、`bean_bag` 懒人沙发 |
| 桌台 | `table` 木桌、`coffee_table` 茶几、`desk` 书桌、`dining_table` 餐桌（2 格宽）、`side_table` 边几、`picnic_table` 野餐桌（2 格宽 / 2 人）、`round_table` 圆桌、`bar_counter` 吧台、`computer_desk` 电脑桌、`workbench_table` 工作台 |
| 储物 | `cabinet` 木柜(27)、`drawer` 抽屉柜(27)、`shelf` 书架(27)、`wardrobe` 衣柜(54)、`nightstand` 床头柜(9)、`shoe_rack` 鞋架(9)、`tv_stand` 电视柜(27)、`crate` 木箱(27)、`vanity` 梳妆台(27)、`bookshelf_tall` 高书柜(54)、`safe` 保险柜(27)、`medicine_cabinet` 药柜(18)、`wine_rack` 酒架(18)、`fridge` 冰箱(54)、`kitchen_counter` 厨房台面(9) |
| 床 | `bed` 单人床、`crib` 婴儿床 |
| 灯具 | `lamp` 落地灯、`table_lamp` 台灯、`street_lamp` 路灯、`lantern_post` 灯笼柱、`ceiling_lamp` 吊灯、`wall_lamp` 壁灯、`candle` 蜡烛、`neon_sign` 霓虹灯牌、`stone_lantern` 石灯笼 |
| 绿植 | `plant` 盆栽、`flower_pot` 花盆、`planter_box` 花箱、`hedge` 绿篱、`bush` 灌木、`cherry_bonsai` 樱花盆景、`birdhouse` 鸟屋、`mailbox` 信箱、`fence` 栅栏段、`well` 水井 |
| 景观 | `garden_bench` 花园长椅、`fountain` 喷泉、`arch` 花园拱门、`scarecrow` 稻草人、`swing` 秋千（2 格宽 / 2 人） |
| 厨房 | `sink` 水槽、`stove` 灶台、`oven` 烤箱、`coffee_machine` 咖啡机 |
| 其他 | `clock` 落地钟、`coat_rack` 衣帽架、`rug` 地毯、`tv` 电视、`fireplace` 壁炉、`piano` 钢琴、`mirror` 落地镜、`painting_frame` 画框 |

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/furniture list [关键字]` | 列表，可按 ID 或中文名过滤 | 所有人 |
| `/furniture recipes` | 工作台配方（字符图例 + 材料） | 所有人 |
| `/furniture nearest` | 身边最近一件的状态 | 所有人 |
| `/furniture help` | 用法 | 所有人 |
| `/furniture give <ID> [玩家] [数量]` | 拿家具 | `furniture.admin` |
| `/furniture place <ID> [x y z yaw]` | 准星处 / 指定坐标放置 | `furniture.admin` |
| `/furniture remove [x y z]` | 拆除（掉家具物品 + 掉容器物品） | `furniture.admin` |
| `/furniture rotate [x y z] [角度]` | 旋转（自动规整到 90° 倍数） | `furniture.admin` |
| `/furniture light [x y z]` | 开关灯 | `furniture.admin` |
| `/furniture info [x y z]` | id / 位置 / yaw / lit / 占地 / 人数 / 容器格数与内容 / 锚点方块 | `furniture.admin` |
| `/furniture fill <x y z> [material] [数量]` | 往储物家具里塞物品（不开 GUI） | `furniture.admin` |
| `/furniture pack` | 重新下发资源包 | `furniture.admin` |
| `/furniture reload` | 重载配置、重建资源包、重注册配方 | `furniture.admin` |

控制台不受权限限制；`permissions.furniture.admin` 默认给 OP。

## 配置

`plugins/Furniture/config.yml`

- `resource-pack.mode`：`builtin`（插件自己分发）/ `external`（自己托管，填 `public-url`）
- `resource-pack.port`：内置 HTTP 端口，占用时自动顺延
- `resource-pack.public-url`：留空自动用本机局域网 IPv4；正式服填公网 IP / 域名
- `resource-pack.pack-format`：默认 `97.1`
- `place.consume-item` / `place.drop-item`：放置是否消耗、拆除是否掉落
- `interact.sneak-rotate`：潜行右键旋转
- `interact.seat-offset`：坐姿高度微调（格），`/furniture reload` 生效
- `interact.seat-marker`：座位用 marker 隐形盔甲架，坐不上去时改 `false`
- `light.place-block`：开灯是否放真实光源方块（关掉就只是模型自发光，夜里不照亮）

`plugins/Furniture/furniture.yml`

字段：`name / model / material / hitbox-width / hitbox-height / view-range / transform / scale / offset-y / size-width / size-depth / seat / seat-y / seats / light / storage / recipe`
`model` 形如 `furniture:chair`：客户端读 `assets/furniture/items/chair.json`（**重建包时按定义自动生成**）→ 再指向 `assets/furniture/models/chair.json`。

```yaml
  sofa_long:
    size-width: 2      # 占地 2 格
    seat: true
    seat-y: 0.35
    seats: 2           # 两个人能同时坐，沿宽度等分
  cabinet:
    storage: 27        # 27 格储物，右键打开
  ceiling_lamp:
    light: true        # 右键开关灯 + 真实照明方块
```

加新家具的最小步骤：往 `pack/assets/furniture/models/` 放模型 json → `furniture.yml` 加一条 `model: furniture:xxx` → `/furniture reload`。`items/xxx.json` 不用手写。

## 存档

`plugins/Furniture/placed.yml` 每条记录：

`id / world / x / y / z / yaw / lit / display / hitbox / contents`
- `contents` 是新版 Base64 物品字节列表（老存档的 ItemStack / Map 形态会尝试兼容读取）
- 老存档缺 `yaw` / `lit` / `hitbox` 也能读，交互箱和亮度会自动重新绑上

## 批量生成目录

`tools/gen-catalog.ps1` 用「一行一个盒子」描述所有家具（`box` + 贴图键 + 配方 + `sw/sd/seat/seatY/seats/light/storage`），跑一次重新生成模型和整份 `furniture.yml`：

```
pwsh -File tools/gen-catalog.ps1
```

输出：`生成模型 66 个, YAML 条目 76 条`。已有的 10 件（`skipModel=$true`）是手调模型，不会被覆盖。储物格数走 `$storageMap` 白名单（9/18/27/36/54）。

## 模型约定

- 坐标 `0~16` = 1 格，`y=0` 是家具底部，正面朝南（`+z`）；`y` 超过 16 即 2 格高
- 放置锚点取方块中心 `y+0.5`，模型底面贴地；朝向 = 玩家视角 yaw + 180
- 贴图全部复用原版方块贴图，76 件的包只有 56 KB

## 这一版已跑通的（无头服 + 控制台命令）

- 定义 76 / 配方 76 条 / 资源包 153 个条目（76 models + 76 items + mcmeta），58953 字节，重复构建 sha1 一致
- 储物：`fill` 塞 2 件 → `info` 显示 2 件（[1] DIAMOND、[2] STONE）→ 停服重启 → 仍 2 件 → 旋转后仍在 → `remove` 清空并按件掉落
- 槽位保序：存档里 `["", base64]` → 重启后 `info` 报"内容=1 件 / [2] DIAMOND" → `fill` 填到第 1 格 → `[1] STONE + [2] DIAMOND`
- 世界未加载：写入 `world: world_missing` 记录 → 启动 WARN 提示并 park → 后续 `saveAll` 后该记录仍在
- 显示实体丢失：手改 display UUID 为假值 → 启动 WARN"重新生成" → `info` 仍是 `[1] GOLD_INGOT x3`，内容没丢
- 重载：连续 `/furniture reload` 端口一直 8123 不被占用、sha1 不变、`placed.yml` 只加载一次不重复生成实体
- 真实照明：锚点 `AIR` → 开灯 `LIGHT 亮度=15` → 关闭 `AIR`；点亮状态旋转正常；拆灯不留光源方块
- 多人座位：`sofa_long` 解析为 `占地=2x1 人数=2`，旋转 90° 后占地与交互箱同步
- 中文过滤：`list 沙发` 4 件、`list 灯` 8 件、`list 柜` 7 件
- 多格占位互斥：`sofa_long` 占 `(20,5)+(21,5)` 时，`chair` 放 `21,5` 被拒、放 `22,5` 正常
- 无插件异常堆栈（`full.log` / `reload2.log` 仅有服务端自身的 log4j 提示）

## 仍需真人验证（GUI 相关）

- 储物界面开合、`InventoryCloseEvent` 回写、同一容器互斥提示
- 多人同坐的实际落座位置与坐姿高度（`seat-offset` 微调）
- 拆除时正在看的容器会被强制关闭

## 已知限制

- 没有实体碰撞，玩家能穿过家具
- 拆家具没有「只能拆自己放的」限制，只做了权限节点
- 无玩家在线时区块不加载，控制台放的家具要等人靠近才可见
- 真实照明用的是透明 `light` 方块，占锚点格；锚点格不是空气时不会强行替换

## 构建

```
gradle build --offline
```

编译期直接用 `G:/服务端/Purpur-26.3/purpur-api/build/libs/purpur-api-26.3.local-SNAPSHOT.jar` 与服务端 `libraries/` 依赖，离线可编译。

## 下一步

1. 双格床「躺下」姿态、沙发靠背朝向修正
2. 家具碰撞（隐形方块 / barrier 撤销方案）
3. 家具 GUI 图标单独做 2D 模型
4. 资源包与服务器主资源包合并
5. 继续扩量（目标 100+ 件，往 mod 家具包体量靠）
