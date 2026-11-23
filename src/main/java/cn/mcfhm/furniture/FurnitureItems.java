package cn.mcfhm.furniture;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 生成 / 识别家具物品 */
public final class FurnitureItems {

    private FurnitureItems() {
    }

    public static ItemStack create(FurniturePlugin plugin, FurnitureDef def) {
        ItemStack stack = new ItemStack(def.material());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(def.name());
        meta.setItemModel(def.model());
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&7家具 &8» &f" + def.id()));
        lore.add(Text.colorize("&8左键拆除 · 潜行右键旋转"));
        if (def.storage() > 0) {
            lore.add(Text.colorize("&8右键打开容器 &7(" + def.storage() + " 格)"));
        }
        if (def.seat()) {
            lore.add(Text.colorize("&8右键坐下 · 潜行起身"));
        }
        if (def.light()) {
            lore.add(Text.colorize("&8右键开关灯"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(plugin.furnitureIdKey(), PersistentDataType.STRING, def.id());
        stack.setItemMeta(meta);
        return stack;
    }

    public static FurnitureDef read(FurniturePlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(plugin.furnitureIdKey(), PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        return plugin.registry().get(id);
    }
}
