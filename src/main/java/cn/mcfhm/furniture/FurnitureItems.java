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
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(def.name()));
        meta.setItemModel(def.model());
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(legacy("&7家具 &8» &f" + def.id()));
        lore.add(legacy("&8左键拆除 · 潜行右键旋转"));
        if (def.storage() > 0) {
            lore.add(legacy("&8右键打开容器 &7(" + def.storage() + " 格)"));
        }
        if (def.seat()) {
            lore.add(legacy("&8右键坐下 · 潜行起身"));
        }
        if (def.light()) {
            lore.add(legacy("&8右键开关灯"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.furnitureIdKey(), PersistentDataType.STRING, def.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /** & 颜色代码 -> Adventure 组件（ItemMeta 的字符串 lore 已过时） */
    private static net.kyori.adventure.text.Component legacy(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(Text.colorize(text));
    }

    public static FurnitureDef read(FurniturePlugin plugin, ItemStack stack) {        if (stack == null || stack.getType().isAir()) {
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
