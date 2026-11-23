package cn.mcfhm.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 家具的生成、拆除、旋转与持久化。
 * 占地按 def 的 size-width / size-depth 计算，锚点是「模型局部坐标 (0,0) 那一格」。
 */
public final class FurnitureManager {

    /** 占位格子的键，用一个地图按世界上所有格子上挂的家具 */
    private record Cell(String worldName, int x, int y, int z) {
    }

    private final FurniturePlugin plugin;
    private final File dataFile;
    private final List<PlacedFurniture> records = new ArrayList<>();
    private final Map<UUID, PlacedFurniture> byDisplay = new HashMap<>();
    private final Map<UUID, PlacedFurniture> byHitbox = new HashMap<>();
    private final Map<Cell, PlacedFurniture> occupancy = new HashMap<>();

    public FurnitureManager(FurniturePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "placed.yml");
    }

    public int count() {
        return records.size();
    }

    /** 只读遍历用（亮度补刷等） */
    public List<PlacedFurniture> all() {
        return records;
    }

    /**
     * 计算家具占用的格子（相对锚点的方块偏移）。
     * 模型局部 +x / +z 会跟着 yaw 一起转：yaw=0 时 +x 就是世界 +x，yaw=90 时 +x 指向 +z。
     */
    public static List<int[]> cells(FurnitureDef def, float yaw) {
        int width = def.sizeWidth();
        int depth = def.sizeDepth();
        double radians = Math.toRadians(-yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        List<int[]> result = new ArrayList<>(width * depth);
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int bx = (int) Math.round(dx * cos + dz * sin);
                int bz = (int) Math.round(-dx * sin + dz * cos);
                result.add(new int[]{bx, bz});
            }
        }
        return result;
    }

    /** 把「模型局部格子坐标」换算成世界偏移（连续值，用于碰撞箱中心等） */
    public static double[] worldOffset(float yaw, double localX, double localZ) {
        double radians = Math.toRadians(-yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new double[]{localX * cos + localZ * sin, -localX * sin + localZ * cos};
    }

    /** 检查这批格子是否都能放（可替换方块 + 没被别的家具占着） */
    public boolean canPlace(World world, int x, int y, int z, FurnitureDef def, float yaw) {
        for (int[] offset : cells(def, yaw)) {
            Block block = world.getBlockAt(x + offset[0], y, z + offset[1]);
            if (!block.isReplaceable() || block.isLiquid()) {
                return false;
            }
            if (occupancy.containsKey(new Cell(world.getName(), block.getX(), block.getY(), block.getZ()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在指定方块处放置家具（锚点是家具脚下那格方块的坐标）。
     *
     * @param yaw 朝向（度）
     */
    public PlacedFurniture place(Block block, FurnitureDef def, float yaw) {
        World world = block.getWorld();
        float snapped = snapYaw(yaw);
        if (!canPlace(world, block.getX(), block.getY(), block.getZ(), def, snapped)) {
            return null;
        }
        return spawn(world, block.getX(), block.getY(), block.getZ(), def, snapped, false);
    }

    private PlacedFurniture spawn(World world, int x, int y, int z, FurnitureDef def, float yaw, boolean lit) {
        ItemStack stack = FurnitureItems.create(plugin, def);

        Location displayLoc = new Location(world, x + 0.5D, y + 0.5D, z + 0.5D, yaw, 0.0F);
        Entity spawned = world.spawnEntity(displayLoc, EntityType.ITEM_DISPLAY);
        if (!(spawned instanceof ItemDisplay display)) {
            if (spawned != null) {
                spawned.remove();
            }
            return null;
        }
        display.setItemStack(stack);
        display.setItemDisplayTransform(def.transform());
        display.setBillboard(Display.Billboard.FIXED);
        display.setPersistent(true);
        display.setInterpolationDuration(0);
        display.setInterpolationDelay(0);
        display.setTeleportDuration(0);
        display.setViewRange(def.viewRange());
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setRotation(yaw, 0.0F);
        if (def.scale() != 1.0F || def.offsetY() != 0.0F) {
            display.setTransformation(new Transformation(
                    new Vector3f(0.0F, def.offsetY(), 0.0F),
                    new AxisAngle4f(),
                    new Vector3f(def.scale(), def.scale(), def.scale()),
                    new AxisAngle4f()
            ));
        }
        display.getPersistentDataContainer().set(plugin.furnitureIdKey(), PersistentDataType.STRING, def.id());

        Interaction hitbox = spawnHitbox(world, x, y, z, def, yaw);
        if (def.light() && lit) {
            display.setBrightness(new Display.Brightness(15, 15));
        }

        PlacedFurniture record = new PlacedFurniture(
                def.id(),
                world.getName(),
                x,
                y,
                z,
                yaw,
                lit,
                display.getUniqueId(),
                hitbox == null ? null : hitbox.getUniqueId()
        );
        records.add(record);
        byDisplay.put(display.getUniqueId(), record);
        if (hitbox != null) {
            byHitbox.put(hitbox.getUniqueId(), record);
        }
        registerCells(record, def);
        if (def.light() && lit) {
            syncLightBlock(record);
        }
        saveAll();
        return record;
    }

    /** 交互箱盖住整个占地范围，多格家具也能整块点到 */
    private Interaction spawnHitbox(World world, int x, int y, int z, FurnitureDef def, float yaw) {
        double centerX = (def.sizeWidth() - 1) / 2.0D;
        double centerZ = (def.sizeDepth() - 1) / 2.0D;
        double[] offset = worldOffset(yaw, centerX, centerZ);
        Location hitLoc = new Location(world, x + 0.5D + offset[0], y, z + 0.5D + offset[1], yaw, 0.0F);
        Entity spawned = world.spawnEntity(hitLoc, EntityType.INTERACTION);
        if (!(spawned instanceof Interaction interaction)) {
            if (spawned != null) {
                spawned.remove();
            }
            return null;
        }
        interaction.setInteractionWidth(def.hitboxWidth() * def.sizeWidth());
        interaction.setInteractionHeight(def.hitboxHeight());
        interaction.setResponsive(true);
        interaction.setPersistent(true);
        interaction.setRotation(yaw, 0.0F);
        interaction.getPersistentDataContainer().set(plugin.furnitureIdKey(), PersistentDataType.STRING, def.id());
        return interaction;
    }

    private void registerCells(PlacedFurniture record, FurnitureDef def) {
        for (int[] offset : cells(def, record.yaw())) {
            occupancy.put(new Cell(record.worldName(), record.x() + offset[0], record.y(), record.z() + offset[1]), record);
        }
    }

    private void unregisterCells(PlacedFurniture record, FurnitureDef def) {
        for (int[] offset : cells(def, record.yaw())) {
            occupancy.remove(new Cell(record.worldName(), record.x() + offset[0], record.y(), record.z() + offset[1]), record);
        }
    }

    /** 顺时针旋转 90°，占地格子和实体朝向一起转；新位置被挡就拒绝 */
    public boolean rotate(PlacedFurniture record, float deltaYaw) {
        FurnitureDef def = plugin.registry().get(record.id());
        World world = Bukkit.getWorld(record.worldName());
        if (def == null || world == null) {
            return false;
        }
        float newYaw = normalize(record.yaw() + deltaYaw);
        List<int[]> nextCells = cells(def, newYaw);
        for (int[] offset : nextCells) {
            Block block = world.getBlockAt(record.x() + offset[0], record.y(), record.z() + offset[1]);
            if (!block.isReplaceable() || block.isLiquid()) {
                return false;
            }
            PlacedFurniture other = occupancy.get(new Cell(world.getName(), block.getX(), block.getY(), block.getZ()));
            if (other != null && other != record) {
                return false;
            }
        }

        unregisterCells(record, def);
        record.yaw(newYaw);
        registerCells(record, def);

        Entity display = world.getEntity(record.displayId());
        if (display instanceof Display d) {
            d.setRotation(newYaw, 0.0F);
        }
        if (record.hitboxId() != null) {
            Entity hitbox = world.getEntity(record.hitboxId());
            if (hitbox instanceof Interaction interaction) {
                interaction.setRotation(newYaw, 0.0F);
                double[] offset = worldOffset(newYaw, (def.sizeWidth() - 1) / 2.0D, (def.sizeDepth() - 1) / 2.0D);
                interaction.teleport(new Location(world,
                        record.x() + 0.5D + offset[0], record.y(), record.z() + 0.5D + offset[1], newYaw, 0.0F));
            }
        }
        saveAll();
        return true;
    }

    /**
     * 真实照明：开灯的灯具在锚点格塞一个不可见的 light 方块（亮度 15），关灯或拆除时清掉。
     * 家具本身是 Display 实体不发光，只有这样才能照亮周围的方块和地面。
     */
    public void syncLightBlock(PlacedFurniture record) {
        if (!plugin.getConfig().getBoolean("light.place-block", true)) {
            return;
        }
        FurnitureDef def = plugin.registry().get(record.id());
        World world = Bukkit.getWorld(record.worldName());
        if (def == null || !def.light() || world == null) {
            return;
        }
        // 区块没加载时方块读写会拿到错的快照，先顶起来
        world.getChunkAt(record.x(), record.z());
        Block anchor = world.getBlockAt(record.x(), record.y(), record.z());
        if (record.lit()) {
            if (anchor.getType().isAir()) {
                BlockData data = Bukkit.createBlockData(Material.LIGHT);
                if (data instanceof Levelled levelled) {
                    levelled.setLevel(Math.max(levelled.getMinimumLevel(), 15));
                }
                anchor.setBlockData(data, false);
            }
        } else if (anchor.getType() == Material.LIGHT) {
            anchor.setType(Material.AIR, false);
        }
    }

    /** 灯具开关：切换模型自发光亮度、真实光源方块，并落盘 */
    public boolean toggleLight(PlacedFurniture record) {
        FurnitureDef def = plugin.registry().get(record.id());
        World world = Bukkit.getWorld(record.worldName());
        if (def == null || !def.light() || world == null) {
            return false;
        }
        Entity display = world.getEntity(record.displayId());
        if (display == null) {
            // 实体所在区块可能还没加载（无人在线/刚传送），先把区块顶起来再找一次
            world.getChunkAt(record.x() >> 4, record.z() >> 4);
            display = world.getEntity(record.displayId());
        }
        boolean lit = !record.lit();
        record.lit(lit);
        if (display instanceof Display d) {
            d.setBrightness(lit ? new Display.Brightness(15, 15) : null);
        }
        syncLightBlock(record);
        saveAll();
        return lit;
    }

    /** 拆除家具，drop 为 true 时掉落家具物品；容器内容物一定会掉出来 */
    public boolean remove(PlacedFurniture record, boolean drop) {
        World world = Bukkit.getWorld(record.worldName());
        FurnitureDef def = plugin.registry().get(record.id());
        Bukkit.getPluginManager().callEvent(new FurnitureRemoveEvent(record));
        if (world != null) {
            Entity display = world.getEntity(record.displayId());
            if (display != null) {
                display.remove();
            }
            if (record.hitboxId() != null) {
                Entity hitbox = world.getEntity(record.hitboxId());
                if (hitbox != null) {
                    hitbox.remove();
                }
            }
            if (def != null && def.storage() > 0 && !record.contents().isEmpty()) {
                plugin.storage().dropContents(record);
            }
            if (drop && def != null) {
                Location dropAt = new Location(world, record.x() + 0.5D, record.y() + 0.25D, record.z() + 0.5D);
                world.dropItemNaturally(dropAt, FurnitureItems.create(plugin, def));
            }
        }
        if (def != null) {
            unregisterCells(record, def);
        }
        if (def != null && def.light()) {
            // 拆灯具要把留下的光源方格一起清掉
            record.lit(false);
            syncLightBlock(record);
        }
        records.remove(record);
        byDisplay.remove(record.displayId());
        if (record.hitboxId() != null) {
            byHitbox.remove(record.hitboxId());
        }
        saveAll();
        return true;
    }

    public PlacedFurniture byHitbox(UUID uuid) {
        return uuid == null ? null : byHitbox.get(uuid);
    }

    public PlacedFurniture byDisplay(UUID uuid) {
        return uuid == null ? null : byDisplay.get(uuid);
    }

    /** 按方块坐标找家具（多格家具哪一格都能命中） */
    public PlacedFurniture at(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        return occupancy.get(new Cell(world.getName(), x, y, z));
    }

    public PlacedFurniture nearest(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        PlacedFurniture best = null;
        double bestDistance = radius;
        for (PlacedFurniture record : records) {
            if (!record.worldName().equals(world.getName())) {
                continue;
            }
            FurnitureDef def = plugin.registry().get(record.id());
            List<int[]> occupied = def == null ? List.of(new int[]{0, 0}) : cells(def, record.yaw());
            // 多格家具要按「离任意一格」算距离，否则人在远端那格时命令找不到它
            double distance = Double.MAX_VALUE;
            for (int[] offset : occupied) {
                Location cellCenter = new Location(world,
                        record.x() + offset[0] + 0.5D, record.y() + 0.5D, record.z() + offset[1] + 0.5D);
                distance = Math.min(distance, location.distance(cellCenter));
            }
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = record;
            }
        }
        return best;
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (PlacedFurniture record : records) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", record.id());
            entry.put("world", record.worldName());
            entry.put("x", record.x());
            entry.put("y", record.y());
            entry.put("z", record.z());
            entry.put("yaw", record.yaw());
            entry.put("lit", record.lit());
            entry.put("display", record.displayId().toString());
            if (record.hitboxId() != null) {
                entry.put("hitbox", record.hitboxId().toString());
            }
            if (!record.contents().isEmpty()) {
                List<String> packed = new ArrayList<>(record.contents().size());
                for (ItemStack stack : record.contents()) {
                    try {
                        packed.add(java.util.Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
                    } catch (Exception ignored) {
                        // 个别物品序列化失败就跳过，避免整份存档写坏
                    }
                }
                entry.put("contents", packed);
            }
            list.add(entry);
        }
        config.set("furniture", list);
        try {
            config.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存家具数据失败: " + ex.getMessage());
        }
    }

    public void loadAll() {
        records.clear();
        byDisplay.clear();
        byHitbox.clear();
        occupancy.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<?, ?>> list = config.getMapList("furniture");
        for (Map<?, ?> raw : list) {
            try {
                Object idValue = raw.get("id");
                Object worldValue = raw.get("world");
                Object displayValue = raw.get("display");
                if (idValue == null || worldValue == null || displayValue == null) {
                    continue;
                }
                String id = idValue.toString();
                String worldName = worldValue.toString();
                int x = toInt(raw.get("x"));
                int y = toInt(raw.get("y"));
                int z = toInt(raw.get("z"));
                float yaw = toFloat(raw.get("yaw"), 0.0F);
                boolean lit = raw.get("lit") != null && Boolean.parseBoolean(raw.get("lit").toString());
                UUID displayId = UUID.fromString(displayValue.toString());
                Object hitboxValue = raw.get("hitbox");
                UUID hitboxId = hitboxValue == null ? null : UUID.fromString(hitboxValue.toString());

                FurnitureDef def = plugin.registry().get(id);
                World world = Bukkit.getWorld(worldName);
                if (def == null) {
                    plugin.getLogger().warning("家具 " + id + " 的定义已不存在，跳过 " + worldName + " " + x + "," + y + "," + z);
                    continue;
                }
                if (world == null) {
                    plugin.getLogger().warning("世界 " + worldName + " 未加载，跳过家具 " + id);
                    continue;
                }
                // 先把占地范围内的区块都载入，让持久化实体有机会恢复（多格家具可能跨区块）
                for (int[] offset : cells(def, yaw)) {
                    world.getChunkAt((x + offset[0]) >> 4, (z + offset[1]) >> 4);
                }
                Entity display = world.getEntity(displayId);
                if (display == null) {
                    plugin.getLogger().warning("家具 " + id + " 的显示实体丢失，重新生成 (" + worldName + " " + x + "," + y + "," + z + ")");
                    spawn(world, x, y, z, def, yaw, lit);
                    continue;
                }
                if (hitboxId != null && world.getEntity(hitboxId) == null) {
                    plugin.getLogger().warning("家具 " + id + " 的碰撞箱实体丢失，交互可能失效 (" + worldName + " " + x + "," + y + "," + z + ")");
                }
                PlacedFurniture record = new PlacedFurniture(id, worldName, x, y, z, yaw, lit, displayId, hitboxId);
                if (hitboxId == null || world.getEntity(hitboxId) == null) {
                    // 老数据没有 hitbox 字段，或实体丢失：就近找一个同 ID 且没被登记的交互箱
                    Interaction found = findLooseHitbox(world, x, y, z, id);
                    if (found != null) {
                        hitboxId = found.getUniqueId();
                        record.hitboxId(hitboxId);
                    }
                }
                records.add(record);
                byDisplay.put(displayId, record);
                if (hitboxId != null) {
                    byHitbox.put(hitboxId, record);
                }
                registerCells(record, def);
                readContents(record, raw.get("contents"));
                if (def.light() && lit && display instanceof Display d) {
                    d.setBrightness(new Display.Brightness(15, 15));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("读取家具记录失败: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("已恢复已放置家具 " + records.size() + " 个");
    }

    /** 容器内容的读档：新版是 Base64(物品字节)，同时兼容旧的 ItemStack / Map 序列化 */
    private static void readContents(PlacedFurniture record, Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return;
        }
        for (Object element : list) {
            ItemStack stack = null;
            if (element instanceof ItemStack item) {
                stack = item;
            } else if (element instanceof Map<?, ?> map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    stack = ItemStack.deserialize(typed);
                } catch (Exception ignored) {
                    stack = null;
                }
            } else if (element instanceof String base64) {
                try {
                    stack = ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(base64));
                } catch (Exception ignored) {
                    stack = null;
                }
            }
            if (stack != null && !stack.getType().isAir()) {
                record.contents().add(stack);
            }
        }
    }

    /** 在锚点附近找一件还没登记过的、属于同一家具 ID 的交互箱（兼容老存档） */
    private Interaction findLooseHitbox(World world, int x, int y, int z, String id) {
        Location center = new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
        for (Entity entity : world.getNearbyEntities(center, 3.0D, 3.0D, 3.0D)) {
            if (!(entity instanceof Interaction interaction) || byHitbox.containsKey(entity.getUniqueId())) {
                continue;
            }
            String tagged = interaction.getPersistentDataContainer().get(plugin.furnitureIdKey(), PersistentDataType.STRING);
            if (id.equals(tagged)) {
                return interaction;
            }
        }
        return null;
    }

    private static float normalize(float yaw) {
        float value = yaw % 360.0F;
        if (value < 0) {
            value += 360.0F;
        }
        return value;
    }

    /** 朝向只支持 90° 的整数倍，避免半格朝向导致占地格重叠 */
    public static float snapYaw(float yaw) {
        return normalize(Math.round(normalize(yaw) / 90.0F) * 90.0F);
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static float toFloat(Object value, float fallback) {
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(value));
    }
}
