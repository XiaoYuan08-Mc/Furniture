package cn.mcfhm.furniture;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 读取 furniture.yml 里的家具定义与合成配方 */
public final class FurnitureRegistry {

    private final FurniturePlugin plugin;
    private final Map<String, FurnitureDef> defs = new LinkedHashMap<>();
    private final Map<String, FurnitureRecipe> recipes = new LinkedHashMap<>();

    public FurnitureRegistry(FurniturePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        defs.clear();
        recipes.clear();
        File file = new File(plugin.getDataFolder(), "furniture.yml");
        if (!file.exists()) {
            plugin.saveResource("furniture.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("furniture");
        if (root == null) {
            plugin.getLogger().warning("furniture.yml 缺少 furniture 节点");
            return;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            NamespacedKey model;
            try {
                model = NamespacedKey.fromString(section.getString("model", "furniture:" + id));
            } catch (Exception ex) {
                plugin.getLogger().warning("家具 " + id + " 的 model 无法解析，已跳过");
                continue;
            }
            if (model == null) {
                plugin.getLogger().warning("家具 " + id + " 的 model 无法解析，已跳过");
                continue;
            }
            ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.NONE;
            String transformName = section.getString("transform", "NONE");
            if (transformName != null) {
                try {
                    transform = ItemDisplay.ItemDisplayTransform.valueOf(transformName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("家具 " + id + " 的 transform 无效: " + transformName + "，使用 NONE");
                }
            }
            Material material = Material.matchMaterial(String.valueOf(section.getString("material", "PAPER")).toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) {
                material = Material.PAPER;
            }
            int width = Math.max(1, Math.min(8, section.getInt("size-width", 1)));
            // 座位数不能超过家具宽度，否则几个人会坐到同一格上
            int seats = Math.max(1, Math.min(8, section.getInt("seats", 1)));
            seats = Math.min(seats, Math.max(1, width));
            defs.put(id, new FurnitureDef(
                    id,
                    section.getString("name", id),
                    model,
                    transform,
                    (float) section.getDouble("hitbox-width", 1.0D),
                    (float) section.getDouble("hitbox-height", 1.0D),
                    (float) section.getDouble("view-range", 1.0D),
                    material,
                    (float) section.getDouble("scale", 1.0D),
                    (float) section.getDouble("offset-y", 0.0D),
                    Math.max(1, Math.min(8, section.getInt("size-width", 1))),
                    Math.max(1, Math.min(8, section.getInt("size-depth", 1))),
                    section.getBoolean("seat", false),
                    (float) section.getDouble("seat-y", 0.6D),
                    section.getBoolean("light", false),
                    Math.max(0, Math.min(54, section.getInt("storage", 0))),
                    seats
            ));
            FurnitureRecipe recipe = parseRecipe(id, section.getConfigurationSection("recipe"));
            if (recipe != null) {
                recipes.put(id, recipe);
            }
        }
        plugin.getLogger().info("已加载家具定义 " + defs.size() + " 个, 合成配方 " + recipes.size() + " 条");
        warnIfOutdated(file, defs.size());
    }

    /** 插件内置目录比服务器上的 furniture.yml 多时提醒用户重建，避免升级后只有旧家具 */
    private void warnIfOutdated(File file, int loaded) {
        try (InputStream in = plugin.getResource("furniture.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection bundled = defaults.getConfigurationSection("furniture");
            if (bundled == null) {
                return;
            }
            int expected = bundled.getKeys(false).size();
            if (expected > loaded) {
                plugin.getLogger().warning("当前 furniture.yml 只有 " + loaded + " 件家具，插件内置 " + expected
                        + " 件。删掉 " + file.getAbsolutePath() + " 后重启即可生成完整目录（或手动补上新条目）");
            }
        } catch (Exception ignored) {
            // 读内置默认配置失败不影响正常加载
        }
    }

    /** 解析 recipe 节点，配置有问题就跳过这条配方（家具本体照旧可用） */
    private FurnitureRecipe parseRecipe(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        boolean shaped = section.getBoolean("shaped", true);
        if (shaped) {
            List<String> shape = new ArrayList<>(section.getStringList("shape"));
            if (shape.isEmpty() || shape.size() > 3) {
                plugin.getLogger().warning("家具 " + id + " 的 recipe.shape 必须是 1~3 行，已跳过该配方");
                return null;
            }
            Map<Character, Material> keys = new LinkedHashMap<>();
            ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
            if (ingredients == null) {
                plugin.getLogger().warning("家具 " + id + " 的 recipe.ingredients 缺失，已跳过该配方");
                return null;
            }
            for (String rawKey : ingredients.getKeys(false)) {
                if (rawKey.length() != 1) {
                    plugin.getLogger().warning("家具 " + id + " 的配方字符必须是单个字符: " + rawKey);
                    return null;
                }
                Material material = Material.matchMaterial(String.valueOf(ingredients.getString(rawKey)));
                if (material == null || material.isAir()) {
                    plugin.getLogger().warning("家具 " + id + " 的配方材料无效: " + ingredients.getString(rawKey));
                    return null;
                }
                keys.put(rawKey.charAt(0), material);
            }
            for (String row : shape) {
                if (row.length() > 3) {
                    plugin.getLogger().warning("家具 " + id + " 的 recipe.shape 每行最多 3 个字符: " + row);
                    return null;
                }
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !keys.containsKey(c)) {
                        plugin.getLogger().warning("家具 " + id + " 的配方字符 " + c + " 没有在 ingredients 里定义");
                        return null;
                    }
                }
            }
            return new FurnitureRecipe(id, true, shape, keys, List.of(), amount);
        }
        List<Material> materials = new ArrayList<>();
        for (String raw : section.getStringList("ingredients")) {
            Material material = Material.matchMaterial(raw);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("家具 " + id + " 的无序配方材料无效: " + raw);
                return null;
            }
            materials.add(material);
        }
        if (materials.isEmpty()) {
            plugin.getLogger().warning("家具 " + id + " 的无序配方没有材料，已跳过");
            return null;
        }
        return new FurnitureRecipe(id, false, List.of(), Map.of(), materials, amount);
    }

    public FurnitureDef get(String id) {
        if (id == null) {
            return null;
        }
        return defs.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<FurnitureDef> all() {
        return defs.values();
    }

    public Collection<FurnitureRecipe> recipes() {
        return recipes.values();
    }

    public FurnitureRecipe recipe(String id) {
        return id == null ? null : recipes.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return defs.keySet();
    }
}
