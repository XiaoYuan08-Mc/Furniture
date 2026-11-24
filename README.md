# Furniture

Minecraft 家具插件，支持 Purpur / Paper 26.3 和 Java 25。

家具外观用 ItemDisplay 渲染自定义模型，右键交互靠 Interaction 实体，挡人靠隐形的潜影贝。资源包由插件自己生成并通过内置 HTTP 服务下发，不需要另外装材质包。

内置 76 件家具，都能在工作台合成。

## 安装

1. 把 `Furniture-0.4.3.jar` 放进 `plugins/`，同名的旧 jar 要先删掉。
2. 启动后生成 `plugins/Furniture/config.yml`、`furniture.yml` 和 `pack/`。
3. 默认在 8123 端口托管资源包，被占用会自动往后顺延。正式服用 `resource-pack.public-url` 填公网地址。
4. 玩家进服后自动收到资源包。

## 使用

- 放置：手持家具右键方块，朝向跟着视角走；潜行右键原地转 90 度。
- 拆除：左键，4.5 格内有效，隔着方块打不到。家具物品和容器里的东西会掉出来。
- 座椅：右键坐下，潜行、受击、传送、死亡都会自动起身。
- 储物：右键打开界面，格子位置会原样保留。
- 灯具：右键开关。开灯时会在锚点格放一个 invisible 的 light 方块，真的能照亮。
- 合成：`furniture.yml` 里的 `recipe` 会注册成工作台配方，`/furniture recipes` 可以看图案。

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/furniture list [关键字]` | 列出家具，可按 ID 或中文名过滤 | 所有人 |
| `/furniture recipes` | 查看合成配方 | 所有人 |
| `/furniture nearest` | 查看身边最近一件家具的状态 | 所有人 |
| `/furniture give <ID> [玩家] [数量]` | 拿家具 | `furniture.admin` |
| `/furniture place <ID> [x y z yaw]` | 在准星处或指定坐标放置 | `furniture.admin` |
| `/furniture remove [x y z]` | 拆除并掉落物品 | `furniture.admin` |
| `/furniture rotate [x y z] [角度]` | 旋转，自动对齐到 90 度 | `furniture.admin` |
| `/furniture light [x y z]` | 开关灯 | `furniture.admin` |
| `/furniture info [x y z]` | 查看位置、朝向、占地、容器内容等 | `furniture.admin` |
| `/furniture fill <x y z> [material] [数量]` | 不开界面直接往容器里放东西 | `furniture.admin` |
| `/furniture pack` | 重新下发资源包 | `furniture.admin` |
| `/furniture reload` | 重载配置、重建资源包 | `furniture.admin` |
| `/furniture debug` | 排查交互箱是否丢失 | `furniture.admin` |

别名 `/jiaju`。控制台不受权限限制。

## 配置

`config.yml` 里常用的几项：

- `resource-pack.mode`：`builtin` 由插件分发，`external` 用你自己的地址。
- `resource-pack.port` / `public-url`：内置服务的端口和对外地址。
- `place.consume-item` / `place.drop-item`：放置是否消耗物品、拆除是否掉落。
- `interact.sneak-rotate`：潜行右键旋转。
- `interact.seat-offset`：坐姿整体上下微调，单位是格。
- `collision.enabled`：家具是否挡人。
- `collision.skip-seatable`：能坐的家具不生成挡人碰撞，避免坐不上去。
- `light.place-block`：开灯是否放真实光源方块。

## 自定义家具

`furniture.yml` 里一条定义长这样：

```yaml
  sofa_long:
    size-width: 2
    seat: true
    seat-y: 0.4375
    seats: 2
    seat-points:
      - [9, 7, 10]
      - [23, 7, 10]
  cabinet:
    storage: 27
```

字段：`name / model / material / hitbox-width / hitbox-height / view-range / transform / scale / offset-y / size-width / size-depth / seat / seat-y / seat-points / solid / seats / light / storage / recipe`。

`seat-points` 用模型像素坐标，原点在锚点方块中心，y 从地面算起。

加新家具：把模型 json 放进 `pack/assets/furniture/models/`，在 `furniture.yml` 加一条 `model: furniture:xxx`，然后 `/furniture reload`。`items/xxx.json` 会自动生成。

## 存档

`plugins/Furniture/placed.yml` 保存每件家具的位置、朝向、容器内容和实体 UUID。旧版本的字段缺失时会自动补，不需要手动迁移。

## 已知限制

- 挡人用的是隐形潜影贝，`/kill @e[type=shulker]` 会把它们一起清掉，区块重新加载后会补回来。
- 能坐的家具默认不挡人；想让椅子也挡人，把 `collision.skip-seatable` 改成 false，但起身时可能卡住。
- 没有玩家在附近时区块不加载，控制台放的家具要等人靠近才出现。
- 真实光源占的是锚点格，锚点格不是空气时不会强行替换。

## 构建

```powershell
& 'D:\scoop\apps\gradle\current\bin\gradle.bat' -p 'G:\插件\家具' build --console=plain `
  '-Dorg.gradle.java.installations.paths=D:\mo-jdk\jdk-25.0.5+8'
```

产物在 `build/libs/`。
