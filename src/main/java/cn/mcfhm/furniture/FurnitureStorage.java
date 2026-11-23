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
    /** 玩家 UUID → 当初打开的那个界面，避免把别的界面的内容误写进家具 */
    private final Map<UUID, Inventory> viewingInventories = new HashMap<>();

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
        for (int slot = 0; slot < size && slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack != null && !stack.getType().isAir()) {
                inventory.setItem(slot, stack);
            }
        }
        viewing.put(player.getUniqueId(), record);
        viewingInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
        // 被别的插件拦住没打开的话，别留下脏映射挡住其他人
        if (!inventory.equals(player.getOpenInventory().getTopInventory())) {
            viewing.remove(player.getUniqueId());
            viewingInventories.remove(player.getUniqueId());
            player.sendMessage(Text.colorize("&c界面没能打开，请稍后再试"));
            return;
        }
        player.sendMessage(Text.colorize("&7打开 &f" + def.name() + " &7（&f" + size + " &7格）"));
    }

    /** 玩家关掉界面时，把界面内容写回家具记录并保存；只认当初打开的那个界面 */
    public void close(Player player, Inventory inventory) {
        Inventory opened = viewingInventories.get(player.getUniqueId());
        if (opened == null || !opened.equals(inventory)) {
            return;
        }
        PlacedFurniture record = viewing.remove(player.getUniqueId());
        viewingInventories.remove(player.getUniqueId());
        if (record == null) {
            return;
        }
        capture(record, inventory);
        plugin.manager().saveAll();
    }

    public PlacedFurniture viewingRecord(Player player) {
        return viewing.get(player.getUniqueId());
    }

    /**
     * 按槽位把界面内容抄回记录：保留空格占位（null），这样玩家把东西放在第 20 格，
     * 重新打开时还在第 20 格，不会全部挤到前面。
     */
    private static void capture(PlacedFurniture record, Inventory inventory) {
        List<ItemStack> target = record.contents();
        target.clear();
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                target.add(null);
            } else {
                target.add(stack.clone());
            }
        }
        while (!target.isEmpty() && target.get(target.size() - 1) == null) {
            target.remove(target.size() - 1);
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
        Inventory opened = viewingInventories.get(player.getUniqueId());
        if (opened == null) {
            viewing.remove(player.getUniqueId());
            return;
        }
        close(player, opened);
    }

    /** 把容器里的东西全部掉到地上（拆除时调用） */
    public void dropContents(PlacedFurniture record) {
        List<ItemStack> copy = new ArrayList<>(record.contents());
        record.contents().clear();
        Location at = record.location();
        if (at == null) {
            return;
        }
        for (ItemStack stack : copy) {
            if (stack != null && !stack.getType().isAir()) {
                at.getWorld().dropItemNaturally(at, stack);
            }
        }
    }

    /** 容器里真实存在的物品件数（排除空格占位） */
    public static int itemCount(PlacedFurniture record) {
        int count = 0;
        for (ItemStack stack : record.contents()) {
            if (stack != null && !stack.getType().isAir()) {
                count++;
            }
        }
        return count;
    }
}
