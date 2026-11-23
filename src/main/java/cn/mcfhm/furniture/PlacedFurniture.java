package cn.mcfhm.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** 一个已放置的家具（锚点是它脚下那格方块的坐标） */
public final class PlacedFurniture {

    private final String id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private float yaw;
    private boolean lit;
    private final UUID displayId;
    private UUID hitboxId;
    /** 放置者 UUID；null = 公共家具（命令放置 / 老存档），谁都能操作 */
    private UUID owner;
    /** 容器类家具的物品内容（非容器家具始终为空） */
    private final java.util.List<org.bukkit.inventory.ItemStack> contents = new java.util.ArrayList<>();

    public PlacedFurniture(String id, String worldName, int x, int y, int z, float yaw, UUID displayId, UUID hitboxId) {
        this(id, worldName, x, y, z, yaw, false, displayId, hitboxId);
    }

    public PlacedFurniture(String id, String worldName, int x, int y, int z, float yaw, boolean lit,
                           UUID displayId, UUID hitboxId) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.lit = lit;
        this.displayId = displayId;
        this.hitboxId = hitboxId;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    void yaw(float value) {
        this.yaw = value;
    }

    public boolean lit() {
        return lit;
    }

    void lit(boolean value) {
        this.lit = value;
    }

    public UUID displayId() {
        return displayId;
    }

    public UUID hitboxId() {
        return hitboxId;
    }

    void hitboxId(UUID value) {
        this.hitboxId = value;
    }

    public UUID owner() {
        return owner;
    }

    void owner(UUID value) {
        this.owner = value;
    }

    /** 可变列表，容器读档时直接往里塞物品 */
    public java.util.List<org.bukkit.inventory.ItemStack> contents() {
        return contents;
    }

    /** 锚点方块中心的世界坐标；世界没加载时返回 null */
    public Location location() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x + 0.5D, y + 0.5D, z + 0.5D, yaw, 0.0F);
    }
}
