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
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    /** 世界还没加载时读到的记录，原样保留并回写，避免主世界以外的家具被当成垃圾清掉 */
    private final List<Map<String, Object>> parked = new ArrayList<>();
    /** 灯具按区块索引：区块加载时只查这一格，不再全表遍历 */
    private final Map<String, List<PlacedFurniture>> lightsByChunk = new HashMap<>();
    /** 全部家具的区块索引：区块加载时用来补碰撞实体、纠正实体位置 */
    private final Map<String, List<PlacedFurniture>> byChunk = new HashMap<>();
    /** 单线程写盘：主线程只负责生成快照，IO 不卡 tick */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Furniture-Save");
        thread.setDaemon(true);
        return thread;
    });
    private boolean saveScheduled;
    private volatile boolean writerClosed;
    private volatile boolean shuttingDown;
    private boolean loaded;

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
        return place(block, def, yaw, null);
    }

    /**
     * 在指定方块处放置家具（锚点是家具脚下那格方块的坐标）。
     *
     * @param owner 放置者 UUID；null 表示公共家具（命令 / 控制台放置）
     */
    public PlacedFurniture place(Block block, FurnitureDef def, float yaw, UUID owner) {
        World world = block.getWorld();
        float snapped = snapYaw(yaw);
        if (!canPlace(world, block.getX(), block.getY(), block.getZ(), def, snapped)) {
            return null;
        }
        return spawn(world, block.getX(), block.getY(), block.getZ(), def, snapped, false, owner);
    }

    private PlacedFurniture spawn(World world, int x, int y, int z, FurnitureDef def, float yaw, boolean lit) {
        return spawn(world, x, y, z, def, yaw, lit, null);
    }

    private PlacedFurniture spawn(World world, int x, int y, int z, FurnitureDef def, float yaw, boolean lit, UUID owner) {
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
        record.owner(owner);
        records.add(record);
        byDisplay.put(display.getUniqueId(), record);
        if (hitbox != null) {
            byHitbox.put(hitbox.getUniqueId(), record);
        }
        registerCells(record, def);
        // 挡人用的碰撞实体：Display 没有碰撞，不补这一层玩家会直接穿过去
        spawnCollisions(record, def);
        if (def.light() && lit) {
            syncLightBlock(record);
        }
        saveAll();
        return record;
    }

    /** 交互箱盖住整个占地范围，多格家具也能整块点到 */
    private Interaction spawnHitbox(World world, int x, int y, int z, FurnitureDef def, float yaw) {
        Location hitLoc = hitboxLocation(world, x, y, z, def, yaw);
        Entity spawned = world.spawnEntity(hitLoc, EntityType.INTERACTION);
        if (!(spawned instanceof Interaction interaction)) {
            if (spawned != null) {
                spawned.remove();
            }
            return null;
        }
        shapeHitbox(interaction, def, yaw);
        return interaction;
    }

    /** 交互箱的尺寸 / 响应 / 归属标签；重生成和纠错时都走同一套，避免恢复后尺寸变小点不到 */
    private void shapeHitbox(Interaction interaction, FurnitureDef def, float yaw) {
        interaction.setInteractionWidth(def.hitboxWidth() * def.sizeWidth());
        interaction.setInteractionHeight(def.hitboxHeight());
        interaction.setResponsive(true);
        interaction.setPersistent(true);
        interaction.setRotation(yaw, 0.0F);
        interaction.getPersistentDataContainer().set(plugin.furnitureIdKey(), PersistentDataType.STRING, def.id());
    }

    /** 交互箱的世界坐标（多格家具取占地范围中心） */
    public static Location hitboxLocation(World world, int x, int y, int z, FurnitureDef def, float yaw) {
        double centerX = (def.sizeWidth() - 1) / 2.0D;
        double centerZ = (def.sizeDepth() - 1) / 2.0D;
        double[] offset = worldOffset(yaw, centerX, centerZ);
        return new Location(world, x + 0.5D + offset[0], y, z + 0.5D + offset[1], yaw, 0.0F);
    }

    // ---------------------------------------------------------------- 碰撞实体

    /** 这件家具要不要挡人：全局开关 + 家具自己的 solid + 能坐的默认不挡 */
    public boolean solidEnabled(FurnitureDef def) {
        if (def == null || !def.solid()) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("collision.enabled", true)) {
            return false;
        }
        return !(def.seat() && plugin.getConfig().getBoolean("collision.skip-seatable", true));
    }

    /**
     * 每个占地格塞一只隐形潜影贝。
     * Display / Interaction 都没有碰撞，想让玩家「走不过去」只能靠实体，潜影贝是原版里唯一
     * 1×1×1、无 AI、能被完全隐藏的实心实体。
     */
    private void spawnCollisions(PlacedFurniture record, FurnitureDef def) {
        World world = Bukkit.getWorld(record.worldName());
        if (world == null || !solidEnabled(def)) {
            return;
        }
        removeCollisions(record);
        for (int[] offset : cells(def, record.yaw())) {
            Shulker shulker = spawnCollision(world, record, def, record.x() + offset[0], record.z() + offset[1]);
            if (shulker != null) {
                record.collisionIds().add(shulker.getUniqueId());
            }
        }
    }

    private Shulker spawnCollision(World world, PlacedFurniture record, FurnitureDef def, int cellX, int cellZ) {
        Location at = new Location(world, cellX + 0.5D, record.y(), cellZ + 0.5D);
        try {
            return world.spawn(at, Shulker.class, shulker -> {
                // 不吃不喝不动不看人：只要那个 1×1×1 的碰撞箱
                shulker.setAI(false);
                shulker.setInvisible(true);
                shulker.setSilent(true);
                shulker.setInvulnerable(true);
                shulker.setCollidable(true);
                shulker.setGravity(false);
                shulker.setPersistent(true);
                shulker.setRemoveWhenFarAway(false);
                shulker.setCanPickupItems(false);
                shulker.setLootTable(null);
                shulker.setPeek((byte) 0);
                shulker.setAttachedFace(org.bukkit.block.BlockFace.DOWN);
                shulker.getPersistentDataContainer().set(plugin.collisionKey(),
                        PersistentDataType.STRING, def.id());
                shulker.getPersistentDataContainer().set(plugin.furnitureIdKey(),
                        PersistentDataType.STRING, def.id());
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("生成 " + def.id() + " 的碰撞实体失败: " + ex.getMessage());
            return null;
        }
    }

    /** 拆掉这件家具所有碰撞实体 */
    public void removeCollisions(PlacedFurniture record) {
        if (record.collisionIds().isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(record.worldName());
        if (world != null) {
            for (UUID id : record.collisionIds()) {
                Entity entity = world.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        record.collisionIds().clear();
    }

    /** 补回丢失的碰撞实体（区块重载 / 存档没写 UUID 时） */
    public void ensureCollisions(PlacedFurniture record) {
        FurnitureDef def = plugin.registry().get(record.id());
        World world = Bukkit.getWorld(record.worldName());
        if (def == null || world == null) {
            return;
        }
        if (!solidEnabled(def)) {
            removeCollisions(record);
            return;
        }
        java.util.Set<String> need = new java.util.HashSet<>();
        for (int[] offset : cells(def, record.yaw())) {
            need.add((record.x() + offset[0]) + "," + record.y() + "," + (record.z() + offset[1]));
        }
        // 按「方块坐标」认已有实体，而不是按 UUID：区块刚加载时 world.getEntity(uuid) 可能还查不到，
        // 按 UUID 判断就会重复生成一堆潜影贝。
        Map<String, Shulker> existing = new HashMap<>();
        Location center = new Location(world, record.x() + 0.5D, record.y() + 0.5D, record.z() + 0.5D);
        double radius = Math.max(def.sizeWidth(), def.sizeDepth()) + 2.0D;
        for (Entity entity : world.getNearbyEntities(center, radius, 2.0D, radius)) {
            if (!(entity instanceof Shulker shulker) || !isCollision(shulker)) {
                continue;
            }
            String key = shulker.getLocation().getBlockX() + "," + shulker.getLocation().getBlockY()
                    + "," + shulker.getLocation().getBlockZ();
            if (need.contains(key)) {
                existing.putIfAbsent(key, shulker);
            }
        }
        record.collisionIds().clear();
        for (String key : need) {
            Shulker shulker = existing.get(key);
            if (shulker == null) {
                String[] parts = key.split(",");
                shulker = spawnCollision(world, record, def, Integer.parseInt(parts[0]), Integer.parseInt(parts[2]));
            }
            if (shulker != null) {
                record.collisionIds().add(shulker.getUniqueId());
            }
        }
        for (Shulker shulker : existing.values()) {
            if (!record.collisionIds().contains(shulker.getUniqueId())) {
                shulker.remove();
            }
        }
    }

    /** 是不是本插件生成的挡人实体 */
    public boolean isCollision(Entity entity) {
        return entity instanceof Shulker
                && entity.getPersistentDataContainer().has(plugin.collisionKey(), PersistentDataType.STRING);
    }

    private void registerCells(PlacedFurniture record, FurnitureDef def) {
        for (int[] offset : cells(def, record.yaw())) {
            occupancy.put(new Cell(record.worldName(), record.x() + offset[0], record.y(), record.z() + offset[1]), record);
        }
        indexLight(record, def, true);
        indexChunk(record, def, true);
    }

    private void unregisterCells(PlacedFurniture record, FurnitureDef def) {
        for (int[] offset : cells(def, record.yaw())) {
            occupancy.remove(new Cell(record.worldName(), record.x() + offset[0], record.y(), record.z() + offset[1]), record);
        }
        indexLight(record, def, false);
        indexChunk(record, def, false);
    }

    /** 家具按占用格子的区块建索引，区块加载时只处理这一格的家具 */
    private void indexChunk(PlacedFurniture record, FurnitureDef def, boolean add) {
        if (def == null) {
            return;
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int[] offset : cells(def, record.yaw())) {
            int cx = (record.x() + offset[0]) >> 4;
            int cz = (record.z() + offset[1]) >> 4;
            long packed = ((long) cx << 32) ^ (cz & 0xffffffffL);
            if (!seen.add(packed)) {
                continue;
            }
            String key = chunkKey(record.worldName(), cx, cz);
            List<PlacedFurniture> list = byChunk.get(key);
            if (add) {
                if (list == null) {
                    list = new ArrayList<>(2);
                    byChunk.put(key, list);
                }
                if (!list.contains(record)) {
                    list.add(record);
                }
            } else if (list != null) {
                list.remove(record);
                if (list.isEmpty()) {
                    byChunk.remove(key);
                }
            }
        }
    }

    /** 某个区块里的全部家具（已建索引） */
    public List<PlacedFurniture> chunkRecords(String world, int chunkX, int chunkZ) {
        List<PlacedFurniture> list = byChunk.get(chunkKey(world, chunkX, chunkZ));
        return list == null ? List.of() : list;
    }

    private static String chunkKey(String world, int cx, int cz) {
        return world + "|" + cx + "|" + cz;
    }

    /** 灯具按区块建索引：区块加载时只查这一格，不再遍历全部家具。 */
    private void indexLight(PlacedFurniture record, FurnitureDef def, boolean add) {
        if (def == null || !def.light()) {
            return;
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int[] offset : cells(def, record.yaw())) {
            int cx = (record.x() + offset[0]) >> 4;
            int cz = (record.z() + offset[1]) >> 4;
            long packed = ((long) cx << 32) ^ (cz & 0xffffffffL);
            if (!seen.add(packed)) {
                continue;
            }
            String key = chunkKey(record.worldName(), cx, cz);
            List<PlacedFurniture> list = lightsByChunk.get(key);
            if (add) {
                if (list == null) {
                    list = new ArrayList<>(2);
                    lightsByChunk.put(key, list);
                }
                if (!list.contains(record)) {
                    list.add(record);
                }
            } else if (list != null) {
                list.remove(record);
                if (list.isEmpty()) {
                    lightsByChunk.remove(key);
                }
            }
        }
    }

    /** 某个区块里的灯具（已建索引，O(1)）。 */
    public List<PlacedFurniture> lightsInChunk(String world, int chunkX, int chunkZ) {
        List<PlacedFurniture> list = lightsByChunk.get(chunkKey(world, chunkX, chunkZ));
        return list == null ? List.of() : list;
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
        // 座位是按朝向算的，转之前先把坐着的人请起来
        plugin.ejectSeats(record);
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
                interaction.teleport(hitboxLocation(world, record.x(), record.y(), record.z(), def, newYaw));
            }
        }
        // 占地方格变了，碰撞实体也要跟着挪
        spawnCollisions(record, def);
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
        // 区块没加载时方块读写会拿到错的快照，先顶起来；已在 ChunkLoadEvent 里就不能再强载
        if (!world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            world.getChunkAt(record.x(), record.z());
        }
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
            removeCollisions(record);
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
        // 拆除会立刻掉物品，这里必须马上落盘：万一 1 秒内崩服，记录还在就会刷出一件新家具
        saveImmediate();
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

    /**
     * 合并落盘：1 秒内的多次放置 / 拆除 / 旋转只写一次。
     * 主线程只负责生成快照，真正的 IO 交给 Furniture-Save 线程，避免家具多时卡 tick。
     */
    public void saveAll() {
        if (saveScheduled || writerClosed || shuttingDown) {
            return;
        }
        saveScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveScheduled = false;
            flushAsync();
        }, 10L);
    }

    /** 需要尽快落盘（容器关闭、命令改档）：不排队，立刻生成快照交给写线程。 */
    public void saveImmediate() {
        if (writerClosed || shuttingDown) {
            return;
        }
        flushAsync();
    }

    /** 关服流程开始：之后的零散保存都跳过，最后由 saveBlocking() 统一写一次。 */
    public void beginShutdown() {
        shuttingDown = true;
    }

    private void flushAsync() {
        if (writerClosed) {
            return;
        }
        YamlConfiguration config = snapshot();
        writer.execute(() -> write(config));
    }

    /** 关服路径：同步写完再返回，不依赖写线程有没有排队。 */
    public void saveBlocking() {
        saveScheduled = false;
        YamlConfiguration config = snapshot();
        if (writerClosed) {
            write(config);
            return;
        }
        try {
            // 交给同一个写线程并等它完成，保证前面排队的写入不会反过来把新内容覆盖掉
            writer.submit(() -> write(config)).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("同步保存家具数据失败，改为直接写: " + e.getMessage());
            write(config);
        }
    }

    /** 关掉写线程；调用前应先 saveBlocking()。 */
    public void closeWriter() {
        writerClosed = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("家具存档写线程未在 10 秒内结束，可能有数据没落盘");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 在主线程生成一份完整快照（含世界未加载时保留的原始记录）。 */
    private YamlConfiguration snapshot() {
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
            if (record.owner() != null) {
                entry.put("owner", record.owner().toString());
            }
            if (!record.collisionIds().isEmpty()) {
                List<String> colliders = new ArrayList<>(record.collisionIds().size());
                for (UUID id : record.collisionIds()) {
                    colliders.add(id.toString());
                }
                entry.put("collision", colliders);
            }
            if (!record.contents().isEmpty()) {
                List<String> packed = new ArrayList<>(record.contents().size());
                for (ItemStack stack : record.contents()) {
                    if (stack == null || stack.getType().isAir()) {
                        packed.add("");
                        continue;
                    }
                    try {
                        packed.add(java.util.Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
                    } catch (Exception ignored) {
                        // 个别物品序列化失败就留成空槽，避免整份存档写坏
                        packed.add("");
                    }
                }
                entry.put("contents", packed);
            }
            list.add(entry);
        }
        // 世界尚未加载的记录原样带回，不能丢
        list.addAll(parked);
        config.set("furniture", list);
        return config;
    }

    /** 序列化到临时文件再原子替换；加锁避免主线程（关服）和写线程同时操作同一个临时文件。 */
    private synchronized void write(YamlConfiguration config) {
        File parent = dataFile.getParentFile();
        File tmp = new File(parent == null ? dataFile : parent, "placed.yml.tmp");
        try {
            config.save(tmp);
            try {
                Files.move(tmp.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("保存家具数据失败: " + ex.getMessage());
        }
    }

    public void loadAll() {
        // 重载时先把坐着的人请起来，否则旧记录被替换后座位对不上新记录
        for (PlacedFurniture record : records) {
            plugin.ejectSeats(record);
        }
        records.clear();
        byDisplay.clear();
        byHitbox.clear();
        occupancy.clear();
        lightsByChunk.clear();
        byChunk.clear();
        parked.clear();
        if (!dataFile.exists()) {
            loaded = true;
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<?, ?>> list = config.getMapList("furniture");
        List<Map<String, Object>> retry = new ArrayList<>();
        for (Map<?, ?> raw : list) {
            try {
                if (!restoreOne(raw, true)) {
                    retry.add(copyRaw(raw));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("读取家具记录失败: " + ex.getMessage());
            }
        }
        loaded = true;
        plugin.getLogger().info("已恢复已放置家具 " + records.size() + " 个"
                + (retry.isEmpty() ? "" : "，另有 " + retry.size() + " 件等待实体加载后确认"));
        if (!retry.isEmpty() || !records.isEmpty()) {
            // 显示实体 / 交互箱进世界比记录晚：延后一 tick 再确认一遍。
            // 直接在本 tick 就判定「实体丢失」会重复生成家具，或者留下点不动的空壳。
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Map<String, Object> raw : retry) {
                    try {
                        restoreOne(raw, false);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("二次恢复家具失败: " + ex.getMessage());
                    }
                }
                recoverHitboxes();
            }, 1L);
        }
    }

    private static Map<String, Object> copyRaw(Map<?, ?> raw) {
        Map<String, Object> keep = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            keep.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return keep;
    }

    /**
     * 恢复单条记录。
     *
     * @param firstPass true 表示第一次尝试；显示实体查不到时先不动手，返回 false 交给下一 tick 重试，
     *                  避免实体只是「还没进世界」却被当成丢失而重复生成一件家具。
     * @return false = 需要下一 tick 再试；true = 已处理（恢复、保留或判定丢失重建）
     */
    private boolean restoreOne(Map<?, ?> raw, boolean firstPass) {
        Object idValue = raw.get("id");
        Object worldValue = raw.get("world");
        Object displayValue = raw.get("display");
        if (idValue == null || worldValue == null || displayValue == null) {
            return true;
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
        UUID ownerId = null;
        Object ownerValue = raw.get("owner");
        if (ownerValue != null && !String.valueOf(ownerValue).isBlank()) {
            try {
                ownerId = UUID.fromString(ownerValue.toString());
            } catch (IllegalArgumentException ignored) {
                // 老存档 / 手改坏的 owner 当公共家具处理
            }
        }

        FurnitureDef def = plugin.registry().get(id);
        World world = Bukkit.getWorld(worldName);
        if (def == null) {
            plugin.getLogger().warning("家具 " + id + " 的定义已不存在，跳过 " + worldName + " " + x + "," + y + "," + z);
            return true;
        }
        if (world == null) {
            // 世界没加载（多世界服常见）：保留原始记录，等世界加载后重启恢复
            plugin.getLogger().warning("世界 " + worldName + " 未加载，家具 " + id + " 保留在存档里");
            parked.add(copyRaw(raw));
            return true;
        }
        if (byDisplay.containsKey(displayId)) {
            // 第二次尝试时已经被别的记录接管，避免重复登记
            return true;
        }
        // 先把占地范围内的区块都载入，让持久化实体有机会恢复（多格家具可能跨区块）
        for (int[] offset : cells(def, yaw)) {
            world.getChunkAt((x + offset[0]) >> 4, (z + offset[1]) >> 4);
        }
        Entity display = world.getEntity(displayId);
        if (display == null) {
            if (firstPass) {
                // 可能只是实体还没进世界，下一 tick 再看
                return false;
            }
            plugin.getLogger().warning("家具 " + id + " 的显示实体丢失，重新生成 (" + worldName + " " + x + "," + y + "," + z + ")");
            // 重新生成后把原来的容器内容补回去，别把玩家存的东西弄丢
            PlacedFurniture fresh = spawn(world, x, y, z, def, yaw, lit, ownerId);
            if (fresh != null) {
                readContents(fresh, raw.get("contents"));
                saveAll();
            }
            return true;
        }
        PlacedFurniture record = new PlacedFurniture(id, worldName, x, y, z, yaw, lit, displayId, hitboxId);
        record.owner(ownerId);
        if (hitboxId == null) {
            // 老数据没有 hitbox 字段：就近找一个同 ID 且没被登记的交互箱，
            // 找不到也不要紧，loadAll 结束后的 recoverHitboxes 会重建
            Interaction found = findLooseHitbox(world, x, y, z, id, def, yaw);
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
        readCollisionIds(record, raw.get("collision"));
        // 区块重载 / 世界被编辑过之后，实体可能不在原位，按存档坐标摆回去
        resyncEntity(record, def, display);
        ensureCollisions(record);
        if (def.light() && lit && display instanceof Display d) {
            d.setBrightness(new Display.Brightness(15, 15));
        }
        return true;
    }

    /** 存档读完没有：没读完之前不要清理"孤儿"实体，否则会把还没登记的碰撞体删掉 */
    public boolean isLoaded() {
        return loaded;
    }

    /** 容器内容的读档：新版是 Base64(物品字节)，同时兼容旧的 ItemStack / Map 序列化 */
    private static void readContents(PlacedFurniture record, Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return;
        }
        for (Object element : list) {
            // 空串 = 这个槽位是空的（保留占位，物品不会错位）
            if (element == null || (element instanceof String text && text.isBlank())) {
                record.contents().add(null);
                continue;
            }
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
            } else {
                // 解析失败也占住位置，避免后面的物品整体错位
                record.contents().add(null);
            }
        }
        // 尾部空槽没意义，去掉免得存档越写越长
        while (!record.contents().isEmpty() && record.contents().get(record.contents().size() - 1) == null) {
            record.contents().remove(record.contents().size() - 1);
        }
    }

    /** 在锚点附近找一件还没登记过的、属于同一家具 ID 的交互箱（兼容老存档） */
    private Interaction findLooseHitbox(World world, int x, int y, int z, String id,
                                        FurnitureDef def, float yaw) {
        Location center = new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
        java.util.Set<String> allowed = new java.util.HashSet<>();
        for (int[] offset : cells(def, yaw)) {
            allowed.add((x + offset[0]) + "," + y + "," + (z + offset[1]));
        }
        for (Entity entity : world.getNearbyEntities(center, 4.0D, 2.0D, 4.0D)) {
            if (!(entity instanceof Interaction interaction) || byHitbox.containsKey(entity.getUniqueId())) {
                continue;
            }
            String tagged = interaction.getPersistentDataContainer().get(plugin.furnitureIdKey(), PersistentDataType.STRING);
            if (!id.equals(tagged)) {
                continue;
            }
            Location loc = interaction.getLocation();
            // 只认落在这件家具占地格里的交互箱，否则并排的两件同款家具会互相抢
            if (!allowed.contains(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ())) {
                continue;
            }
            if (claimedByOther(interaction.getUniqueId(), null)) {
                continue;
            }
            return interaction;
        }
        return null;
    }

    /** 这只交互箱是不是已经被别的家具记录认领了 */
    private boolean claimedByOther(UUID uuid, PlacedFurniture self) {
        if (uuid == null) {
            return false;
        }
        PlacedFurniture mapped = byHitbox.get(uuid);
        if (mapped != null && mapped != self) {
            return true;
        }
        for (PlacedFurniture record : records) {
            if (record != self && uuid.equals(record.hitboxId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 交互箱容错。重启、区块重载、别的插件清过实体之后，交互箱可能迟到或彻底丢失：
     * 少了它客户端右键会打空（家具既开不了也拆不掉），所以按三级兜底补齐。
     * 顺序：存档 UUID → 附近同 ID 的未登记交互箱 → 重新生成一只。
     */
    public Interaction ensureHitbox(PlacedFurniture record, FurnitureDef def) {
        World world = Bukkit.getWorld(record.worldName());
        if (world == null || def == null) {
            plugin.getLogger().info("[hitbox] " + record.id() + " @ " + record.x() + "," + record.y() + "," + record.z()
                    + " 跳过（world=" + (world == null ? "未加载" : "已加载") + " def=" + (def == null ? "缺失" : def.id()) + "）");
            return null;
        }
        // 家具区块可能已经卸载，实体就不在世界里：先把区块载进来，否则永远找不到它
        boolean unloaded = false;
        for (int[] offset : cells(def, record.yaw())) {
            if (!world.isChunkLoaded((record.x() + offset[0]) >> 4, (record.z() + offset[1]) >> 4)) {
                world.getChunkAt((record.x() + offset[0]) >> 4, (record.z() + offset[1]) >> 4);
                unloaded = true;
            }
        }
        Interaction hitbox = null;
        if (record.hitboxId() != null) {
            hitbox = loadedHitbox(world, record);
        }
        if (hitbox == null) {
            Interaction loose = findLooseHitbox(world, record.x(), record.y(), record.z(), def.id(), def, record.yaw());
            if (loose != null) {
                hitbox = loose;
                record.hitboxId(loose.getUniqueId());
                plugin.getLogger().info("[hitbox] " + def.id() + " @ " + record.x() + "," + record.y() + "," + record.z()
                        + " 按 UUID 找不到，已接管附近的同类交互箱" + (unloaded ? "（区块是刚载入的）" : ""));
                saveAll();
            }
        }
        if (hitbox == null) {
            hitbox = spawnHitbox(world, record.x(), record.y(), record.z(), def, record.yaw());
            if (hitbox != null) {
                record.hitboxId(hitbox.getUniqueId());
                plugin.getLogger().info("[hitbox] " + def.id() + " @ " + record.x() + "," + record.y() + "," + record.z()
                        + " 交互箱丢失，已重新生成" + (unloaded ? "（区块是刚载入的）" : ""));
                saveAll();
            } else {
                plugin.getLogger().warning("[hitbox] " + def.id() + " @ " + record.x() + "," + record.y() + "," + record.z()
                        + " 交互箱重新生成失败");
            }
        }
        if (hitbox != null) {
            byHitbox.put(hitbox.getUniqueId(), record);
            shapeHitbox(hitbox, def, record.yaw());
            Location target = hitboxLocation(world, record.x(), record.y(), record.z(), def, record.yaw());
            if (!sameSpot(hitbox.getLocation(), target)) {
                hitbox.teleport(target);
            }
        }
        return hitbox;
    }

    /**
     * 按存档 UUID 找交互箱：只在世界已加载的实体里找。
     * 不能直接信 World#getEntity —— 区块卸载过之后它可能还回一个早就不在世界里的残留对象，
     * 那样家具看着"有交互箱"，实际客户端右键全打空。
     */
    private Interaction loadedHitbox(World world, PlacedFurniture record) {
        FurnitureDef def = plugin.registry().get(record.id());
        Location center = new Location(world, record.x() + 0.5D, record.y() + 0.5D, record.z() + 0.5D);
        double reach = def == null ? 3.0D : Math.max(3.0D, def.sizeWidth() + 1.0D);
        for (Entity entity : world.getNearbyEntities(center, reach, 3.0D, reach)) {
            if (entity instanceof Interaction interaction && entity.getUniqueId().equals(record.hitboxId())) {
                return interaction;
            }
        }
        return null;
    }

    /**
     * 反查：拿一只交互箱问它属于哪件家具。
     * 只靠 byHitbox 映射时，恢复顺序稍有偏差（实体比记录晚进世界）就会永久失效，
     * 这里再用 PDC 标签 + 坐标兜底，查到顺手补登记。
     */
    public PlacedFurniture resolveHitbox(Interaction interaction) {
        PlacedFurniture known = byHitbox.get(interaction.getUniqueId());
        if (known != null) {
            return known;
        }
        String tagged = interaction.getPersistentDataContainer().get(plugin.furnitureIdKey(),
                PersistentDataType.STRING);
        Location hitLoc = interaction.getLocation();
        World world = hitLoc.getWorld();
        if (world == null) {
            return null;
        }
        PlacedFurniture byCell = at(world, hitLoc.getBlockX(), hitLoc.getBlockY(), hitLoc.getBlockZ());
        if (byCell != null && (tagged == null || byCell.id().equals(tagged))) {
            adoptHitbox(interaction, byCell);
            return byCell;
        }
        // 多格家具的交互箱在占地中心，锚点格不一定就是它脚下那格，按距离兜底
        PlacedFurniture best = null;
        double bestDistance = 2.5D;
        for (PlacedFurniture record : records) {
            if (!record.worldName().equals(world.getName())) {
                continue;
            }
            if (tagged != null && !record.id().equals(tagged)) {
                continue;
            }
            FurnitureDef def = plugin.registry().get(record.id());
            double distance = Double.MAX_VALUE;
            for (int[] offset : def == null ? List.of(new int[]{0, 0}) : cells(def, record.yaw())) {
                Location center = new Location(world, record.x() + offset[0] + 0.5D,
                        record.y(), record.z() + offset[1] + 0.5D);
                distance = Math.min(distance, center.distance(hitLoc));
            }
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = record;
            }
        }
        if (best != null && !claimedByOther(interaction.getUniqueId(), best)) {
            adoptHitbox(interaction, best);
            return best;
        }
        return best;
    }

    private void adoptHitbox(Interaction interaction, PlacedFurniture record) {
        if (record.hitboxId() == null || record.hitboxId() != interaction.getUniqueId()) {
            if (record.hitboxId() != null) {
                byHitbox.remove(record.hitboxId());
            }
            record.hitboxId(interaction.getUniqueId());
            saveAll();
        }
        byHitbox.put(interaction.getUniqueId(), record);
        FurnitureDef def = plugin.registry().get(record.id());
        if (def != null) {
            shapeHitbox(interaction, def, record.yaw());
        }
    }

    /** 交互箱当前状态，给 /furniture info 排障用 */
    public String hitboxStatus(PlacedFurniture record) {
        if (record.hitboxId() == null) {
            return "记录里没有（下次加载会重建）";
        }
        World world = Bukkit.getWorld(record.worldName());
        if (world == null) {
            return "世界未加载";
        }
        if (!world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return "区块未加载（实体在存档里，玩家靠近就会恢复）";
        }
        Interaction interaction = loadedHitbox(world, record);
        if (interaction == null) {
            return "实体丢失（下次加载会重建）";
        }
        return byHitbox.get(interaction.getUniqueId()) == record ? "在位 / 已登记" : "在位 / 未登记";
    }

    /** 玩家身边（16 格内）的家具统一确认一遍交互箱，进服 / 区块刚加载时用 */
    public void ensureNearby(Player player) {
        String worldName = player.getWorld().getName();
        Location base = player.getLocation();
        for (PlacedFurniture record : records) {
            if (!record.worldName().equals(worldName)) {
                continue;
            }
            Location at = new Location(base.getWorld(), record.x() + 0.5D, record.y() + 0.5D, record.z() + 0.5D);
            if (at.distanceSquared(base) > 256.0D) {
                continue;
            }
            FurnitureDef def = plugin.registry().get(record.id());
            if (def == null) {
                continue;
            }
            ensureHitbox(record, def);
            ensureCollisions(record);
        }
    }

    /** 重启后统一把交互箱补齐（延后一 tick，等实体全部进世界） */
    public void recoverHitboxes() {
        for (PlacedFurniture record : records) {
            FurnitureDef def = plugin.registry().get(record.id());
            if (def == null) {
                continue;
            }
            ensureHitbox(record, def);
        }
    }

    /** 读档时把碰撞实体 UUID 读回来（旧存档没有这一项，后面 ensureCollisions 会补） */
    private static void readCollisionIds(PlacedFurniture record, Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return;
        }
        for (Object element : list) {
            if (element == null) {
                continue;
            }
            try {
                record.collisionIds().add(UUID.fromString(String.valueOf(element)));
            } catch (IllegalArgumentException ignored) {
                // 手改坏的 UUID 直接丢
            }
        }
    }

    /**
     * 把显示实体 / 交互箱摆回存档坐标。
     * 区块重载、WorldEdit 搬过方块、旧版本存档都可能让实体错位，人站上去就会被"穿过"。
     */
    public void resyncEntity(PlacedFurniture record, FurnitureDef def, Entity display) {
        World world = Bukkit.getWorld(record.worldName());
        if (world == null || def == null) {
            return;
        }
        if (display != null) {
            Location target = new Location(world, record.x() + 0.5D, record.y() + 0.5D, record.z() + 0.5D,
                    record.yaw(), 0.0F);
            if (!sameSpot(display.getLocation(), target)) {
                display.teleport(target);
            } else if (Math.abs(normalize(display.getYaw()) - record.yaw()) > 0.5F) {
                display.setRotation(record.yaw(), 0.0F);
            }
        }
        if (record.hitboxId() != null) {
            Entity hitbox = world.getEntity(record.hitboxId());
            if (hitbox instanceof Interaction interaction) {
                Location target = hitboxLocation(world, record.x(), record.y(), record.z(), def, record.yaw());
                if (!sameSpot(interaction.getLocation(), target)) {
                    interaction.teleport(target);
                }
                interaction.setRotation(record.yaw(), 0.0F);
            }
        }
    }

    private static boolean sameSpot(Location a, Location b) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) {
            return false;
        }
        return a.distanceSquared(b) < 0.0025D;
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
