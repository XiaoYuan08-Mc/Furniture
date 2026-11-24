package cn.mcfhm.furniture;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;

import java.io.File;
import java.io.IOException;
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
        YamlConfiguration config;
        try {
            migrateSeatData(file);
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("迁移座位数据失败，按原样读取: " + ex.getMessage());
            config = YamlConfiguration.loadConfiguration(file);
        }
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
            boolean seat = section.getBoolean("seat", false);
            // 没写 solid 时：能坐的默认不挡人（挡了人就没法坐上去），其余默认挡人
            boolean solid = section.contains("solid") ? section.getBoolean("solid") : !seat;
            List<double[]> seatPoints = parseSeatPoints(id, section, seats);
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
                    seats,
                    solid,
                    seatPoints
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

    /**
     * 座位点写在 furniture.yml 的 seat-points 里，单位是模型像素（0~32），
     * x/z 以锚点方块中心为 8、y 以方块底部为 0。
     * 长凳、野餐桌、沙发这种「坐哪一侧、坐哪个垫子」由数据决定，不再靠占地范围硬算。
     */
    private List<double[]> parseSeatPoints(String id, ConfigurationSection section, int seats) {
        List<double[]> points = new ArrayList<>();
        List<?> raw = section.getList("seat-points");
        if (raw == null || raw.isEmpty()) {
            return points;
        }
        for (Object element : raw) {
            if (!(element instanceof List<?> triple) || triple.size() < 3) {
                plugin.getLogger().warning("家具 " + id + " 的 seat-points 每一项都要写成 [x, y, z]，已忽略");
                continue;
            }
            try {
                double px = toNumber(triple.get(0));
                double py = toNumber(triple.get(1));
                double pz = toNumber(triple.get(2));
                points.add(new double[]{(px - 8.0D) / 16.0D, py / 16.0D, (pz - 8.0D) / 16.0D});
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("家具 " + id + " 的 seat-points 有一项不是数字，已忽略");
            }
        }
        if (points.size() > 1 && points.size() != seats) {
            plugin.getLogger().warning("家具 " + id + " 的 seat-points 有 " + points.size()
                    + " 个，但 seats=" + seats + "，多出来的点会按顺序轮着用");
        }
        return points;
    }

    private static double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    /**
     * 老服务器上的 furniture.yml 不会被新 jar 覆盖（saveResource 不覆盖已存在的文件），
     * 所以 0.4.0 的精确座位点得自己补进去：只补缺 seat-points 的条目，用户改过的条目不碰。
     * 同时把地毯的 solid 补成 false，不然升级后地毯会变成一堵墙。
     */
    private void migrateSeatData(File file) throws IOException {
        String raw = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (raw.contains("seat-points")) {
            return;
        }
        String[] source = raw.split("\n", -1);
        List<String> out = new ArrayList<>(source.length + 96);
        List<String> block = new ArrayList<>();
        String currentId = null;
        int seats = 0;
        int solid = 0;
        for (String line : source) {
            java.util.regex.Matcher matcher = BLOCK_ID.matcher(line);
            if (matcher.matches()) {
                if (currentId != null) {
                    if (rewriteBlock(currentId, block)) {
                        seats++;
                    }
                    if ("rug".equals(currentId) && addSolidFalse(block)) {
                        solid++;
                    }
                }
                out.addAll(block);
                block.clear();
                currentId = matcher.group(1);
            }
            block.add(line);
        }
        if (currentId != null) {
            if (rewriteBlock(currentId, block)) {
                seats++;
            }
            if ("rug".equals(currentId) && addSolidFalse(block)) {
                solid++;
            }
        }
        out.addAll(block);
        if (seats == 0 && solid == 0) {
            return;
        }
        java.nio.file.Files.writeString(file.toPath(), String.join("\n", out), StandardCharsets.UTF_8);
        if (seats > 0) {
            plugin.getLogger().info("已给 " + seats + " 件可坐家具补上精确座位点（seat-points）");
        }
        if (solid > 0) {
            plugin.getLogger().info("已给 " + solid + " 件平铺家具补上 solid: false");
        }
    }

    /** 只在块里没有 seat-points 时动它；返回是否改了 */
    private static boolean rewriteBlock(String id, List<String> block) {
        String seat = SEAT_BLOCKS.get(id);
        if (seat == null) {
            return false;
        }
        boolean seatTrue = false;
        boolean hasPoints = false;
        int seatY = -1;
        int seatLine = -1;
        for (int i = 0; i < block.size(); i++) {
            String line = block.get(i);
            if (line.startsWith("    seat-points:")) {
                hasPoints = true;
            } else if (line.startsWith("    seat-y:")) {
                seatY = i;
            } else if (line.startsWith("    seat:") && line.contains("true")) {
                seatTrue = true;
                seatLine = i;
            }
        }
        if (!seatTrue || hasPoints) {
            return false;
        }
        if (seatY >= 0) {
            block.set(seatY, seat);
            return true;
        }
        if (seatLine >= 0) {
            block.add(seatLine + 1, seat);
            return true;
        }
        return false;
    }

    private static boolean addSolidFalse(List<String> block) {
        for (String line : block) {
            if (line.startsWith("    solid:")) {
                return false;
            }
        }
        for (int i = 0; i < block.size(); i++) {
            String line = block.get(i);
            if (line.startsWith("    offset-y:") || line.startsWith("    scale:")) {
                block.add(i + 1, "    solid: false");
                return true;
            }
        }
        return false;
    }

    private static final java.util.regex.Pattern BLOCK_ID =
            java.util.regex.Pattern.compile("^ {2}([a-z_0-9]+):\\s*$");

    /** id → 要写进 furniture.yml 的 seat-y + seat-points（值来自模型里坐垫顶面） */
    private static final Map<String, String> SEAT_BLOCKS = new LinkedHashMap<>();

    static {
        SEAT_BLOCKS.put("chair", "    seat-y: 0.5625\n    seat-points:\n      - [8, 9, 8]");
        SEAT_BLOCKS.put("chair_spruce", "    seat-y: 0.625\n    seat-points:\n      - [8, 10, 8]");
        SEAT_BLOCKS.put("stool", "    seat-y: 0.8125\n    seat-points:\n      - [8, 13, 8]");
        SEAT_BLOCKS.put("bench", "    seat-y: 0.625\n    seat-points:\n      - [8, 10, 8]");
        SEAT_BLOCKS.put("rocking_chair", "    seat-y: 0.625\n    seat-points:\n      - [8, 10, 8]");
        SEAT_BLOCKS.put("armchair", "    seat-y: 0.5\n    seat-points:\n      - [8, 8, 10]");
        SEAT_BLOCKS.put("couch", "    seat-y: 0.25\n    seat-points:\n      - [8, 4, 10]");
        SEAT_BLOCKS.put("couch_blue", "    seat-y: 0.4375\n    seat-points:\n      - [8, 7, 10]");
        SEAT_BLOCKS.put("office_chair", "    seat-y: 0.625\n    seat-points:\n      - [8, 10, 8]");
        SEAT_BLOCKS.put("bar_stool", "    seat-y: 1.6875\n    seat-points:\n      - [8, 27, 8]");
        SEAT_BLOCKS.put("piano_stool", "    seat-y: 0.8125\n    seat-points:\n      - [8, 13, 8]");
        SEAT_BLOCKS.put("sofa_long", "    seat-y: 0.4375\n    seat-points:\n      - [9, 7, 10]\n      - [23, 7, 10]");
        SEAT_BLOCKS.put("bean_bag", "    seat-y: 0.5\n    seat-points:\n      - [8, 8, 9]");
        SEAT_BLOCKS.put("picnic_table", "    seat-y: 0.5\n    seat-points:\n      - [16, 8, 2]\n      - [16, 8, 14]");
        SEAT_BLOCKS.put("bed", "    seat-y: 0.4375\n    seat-points:\n      - [8, 7, 16]");
        SEAT_BLOCKS.put("swing", "    seat-y: 0.75\n    seat-points:\n      - [9, 12, 13]\n      - [23, 12, 13]");
        SEAT_BLOCKS.put("garden_bench", "    seat-y: 0.625\n    seat-points:\n      - [8, 10, 8]");
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
