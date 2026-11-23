package cn.mcfhm.furniture;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 把 furniture.yml 里的配方注册成真正的工作台配方 */
public final class FurnitureRecipes {

    private final FurniturePlugin plugin;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public FurnitureRecipes(FurniturePlugin plugin) {
        this.plugin = plugin;
    }

    /** 重建配方（先撤掉旧的，再按当前配置注册） */
    public void sync() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();

        for (FurnitureRecipe spec : plugin.registry().recipes()) {
            FurnitureDef def = plugin.registry().get(spec.furnitureId());
            if (def == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "furniture_" + def.id());
            ItemStack result = FurnitureItems.create(plugin, def);
            result.setAmount(spec.amount());
            Recipe recipe;
            try {
                if (spec.shaped()) {
                    ShapedRecipe shaped = new ShapedRecipe(key, result);
                    shaped.shape(spec.shape().toArray(new String[0]));
                    for (Map.Entry<Character, org.bukkit.Material> entry : spec.key().entrySet()) {
                        shaped.setIngredient(entry.getKey(), entry.getValue());
                    }
                    recipe = shaped;
                } else {
                    ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                    for (org.bukkit.Material material : spec.shapeless()) {
                        shapeless.addIngredient(material);
                    }
                    recipe = shapeless;
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("家具 " + def.id() + " 的配方格式无效: " + ex.getMessage());
                continue;
            }
            try {
                if (Bukkit.addRecipe(recipe)) {
                    registered.add(key);
                } else {
                    plugin.getLogger().warning("家具 " + def.id() + " 的配方注册失败（key 冲突？）");
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("家具 " + def.id() + " 的配方注册异常: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("已注册合成配方 " + registered.size() + " 条");
    }

    /** 让玩家在配方书里直接看到这些家具（没解锁过就补上） */
    public void discover(Player player) {
        for (NamespacedKey key : registered) {
            try {
                if (!player.hasDiscoveredRecipe(key)) {
                    player.discoverRecipe(key);
                }
            } catch (Exception ignored) {
                // 个别版本对自定义 key 不友好，配方本体照样能手动摆
            }
        }
    }

    public List<NamespacedKey> keys() {
        return registered;
    }
}
