package cn.mcfhm.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 家具容器：打开箱子界面、关闭时把内容写回家具记录、拆除时掉落内容 */
public final class FurnitureStorage {

    private final FurniturePlugin plugin;
    /** 玩家 UUID → 正在看的家具（关闭界面时要落盘） */
    private final Map<UUID, PlacedFurniture> viewing = new HashMap<>();

    public FurnitureStorage(FurniturePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isViewing(Player player) {
        return viewing.containsKey(player.getUniqueId());
    }

    public void open(Player player, PlacedFurniture record, FurnitureDef def) {
        if (def.storage() <= 0) {
            return;
        }
        for (Map.Entry<UUID, PlacedFurniture> entry : viewing.entrySet()) {
            if (entry.getValue() != record || entry.getKey().equals(player.getUniqueId())) {
                continue;
            }
            Player other = Bukkit.getPlayer(entry.getKey());
            player.sendMessage(Text.colorize("&c这件家具正被 &f"
                    + (other == null ? "其他玩家" : other.getName()) + " &c使用"));
            return;
        }
        int size = Math.max(9, Math.min(54, def.storage()));
        if (size % 9 != 0) {
            size = (size / 9 + 1) * 9;
        }
        Inventory inventory = Bukkit.createInventory(null, size, def.name());
        List<ItemStack> contents = record.contents();
        for (int slot = 0; slot < Math.min(size, contents.size()); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack != null && !stack.getType().isAir()) {
                inventory.setItem(slot, stack);
            }
        }
        viewing.put(player.getUniqueId(), record);
        player.openInventory(inventory);
        player.sendMessage(Text.colorize("&7打开 &f" + def.name() + " &7（&f" + size + " &7格）"));
    }

    /** 玩家关掉界面时，把界面内容写回家具记录并保存 */
    public void close(Player player, Inventory inventory) {
        PlacedFurniture record = viewing.remove(player.getUniqueId());
        if (record == null) {
            return;
        }
        capture(record, inventory);
        plugin.manager().saveAll();
    }

    public PlacedFurniture viewingRecord(Player player) {
        return viewing.get(player.getUniqueId());
    }

    private static void capture(PlacedFurniture record, Inventory inventory) {
        List<ItemStack> target = record.contents();
        target.clear();
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                target.add(stack.clone());
            }
        }
    }

    /** 家具被拆 / 玩家退出时兜底，避免内容丢失 */
    public void flush(Player player, Inventory inventory) {
        if (viewing.containsKey(player.getUniqueId())) {
            close(player, inventory);
        }
    }

    /** 玩家直接退出（没有触发关闭界面事件）时兜底 */
    public void closeQuietly(Player player) {
        if (!viewing.containsKey(player.getUniqueId())) {
            return;
        }
        close(player, player.getOpenInventory().getTopInventory());
    }

    /** 把容器里的东西全部掉到地上（拆除时调用） */
    public void dropContents(PlacedFurniture record) {
        if (record.contents().isEmpty()) {
            return;
        }
        Location at = record.location();
        if (at == null) {
            return;
        }
        List<ItemStack> copy = new ArrayList<>(record.contents());
        record.contents().clear();
        for (ItemStack stack : copy) {
            at.getWorld().dropItemNaturally(at, stack);
        }
    }
}
