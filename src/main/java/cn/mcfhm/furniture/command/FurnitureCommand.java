package cn.mcfhm.furniture.command;

import cn.mcfhm.furniture.FurnitureDef;
import cn.mcfhm.furniture.FurnitureItems;
import cn.mcfhm.furniture.FurniturePlugin;
import cn.mcfhm.furniture.FurnitureRecipe;
import cn.mcfhm.furniture.FurnitureStorage;
import cn.mcfhm.furniture.PlacedFurniture;
import cn.mcfhm.furniture.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /furniture give|place|remove|list|pack|reload */
public final class FurnitureCommand implements CommandExecutor, TabCompleter {

    private final FurniturePlugin plugin;

    public FurnitureCommand(FurniturePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = isAdmin(sender);
        switch (sub) {
            case "list" -> list(sender, args, label);
            case "recipes", "recipe" -> recipes(sender);
            case "nearest" -> nearest(sender);
            case "help" -> help(sender, label);
            default -> {
                if (!admin) {
                    sender.sendMessage(Text.colorize("&c你没有权限使用 /" + label + " " + sub));
                    return true;
                }
                switch (sub) {
                    case "give" -> give(sender, args);
                    case "place" -> place(sender, args);
                    case "remove", "break" -> remove(sender, args);
                    case "rotate" -> rotate(sender, args);
                    case "light" -> light(sender, args);
                    case "info" -> info(sender, args);
                    case "fill" -> fill(sender, args);
                    case "pack" -> pack(sender);
                    case "reload" -> reload(sender);
                    default -> help(sender, label);
                }
            }
        }
        return true;
    }

    private static boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player player) || player.hasPermission("furniture.admin");
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Text.colorize("&8&m----&r &f家具系统 &8&m----"));
        sender.sendMessage(Text.colorize("&f/" + label + " list [关键字] &7查看所有家具（可按 ID 过滤）"));
        sender.sendMessage(Text.colorize("&f/" + label + " recipes &7查看工作台合成配方"));
        sender.sendMessage(Text.colorize("&f/" + label + " give <ID> [玩家] [数量] &7拿家具（默认给自己 1 个）"));
        sender.sendMessage(Text.colorize("&f/" + label + " place <ID> &7在准星处直接放置"));
        sender.sendMessage(Text.colorize("&f/" + label + " place <ID> <x> <y> <z> [yaw] &7指定坐标放置（控制台可用）"));
        sender.sendMessage(Text.colorize("&f/" + label + " remove &7拆除附近最近的一件"));
        sender.sendMessage(Text.colorize("&f/" + label + " remove <x> <y> <z> &7拆除指定坐标的家具"));
        sender.sendMessage(Text.colorize("&f/" + label + " rotate [x y z] [角度] &7原地旋转 90°"));
        sender.sendMessage(Text.colorize("&f/" + label + " light [x y z] &7开关灯具"));
        sender.sendMessage(Text.colorize("&f/" + label + " nearest &7看身边最近的家具状态"));
        sender.sendMessage(Text.colorize("&f/" + label + " info [x y z] &7看某件家具的完整状态（含容器内容）"));
        sender.sendMessage(Text.colorize("&f/" + label + " fill <x> <y> <z> [material] [数量] &7直接往储物家具里塞物品"));
        sender.sendMessage(Text.colorize("&f/" + label + " pack &7重新下发资源包"));
        sender.sendMessage(Text.colorize("&f/" + label + " reload &7重载配置并重建资源包"));
        sender.sendMessage(Text.colorize("&7手持家具右键方块放置；左键拆除；潜行右键旋转；右键椅子坐下 / 柜子开箱 / 灯具开关"));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.colorize("&c用法: /furniture give <ID> [玩家] [数量]"));
            return;
        }
        FurnitureDef def = plugin.registry().get(args[1]);
        if (def == null) {
            sender.sendMessage(Text.colorize("&c没有这个家具: " + args[1] + "，用 /furniture list 查看"));
            return;
        }
        Player target;
        int count = 1;
        if (args.length >= 3) {
            Player named = Bukkit.getPlayerExact(args[2]);
            if (named != null) {
                target = named;
                if (args.length >= 4) {
                    count = parseCount(args[3], sender);
                }
            } else if (sender instanceof Player self) {
                target = self;
                count = parseCount(args[2], sender);
            } else {
                sender.sendMessage(Text.colorize("&c玩家不在线: " + args[2]));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Text.colorize("&c控制台执行需要指定玩家: /furniture give " + def.id() + " <玩家> [数量]"));
            return;
        }
        int left = count;
        while (left > 0) {
            int amount = Math.min(left, def.material().getMaxStackSize());
            ItemStack item = FurnitureItems.create(plugin, def);
            item.setAmount(amount);
            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            } else {
                target.getInventory().addItem(item);
            }
            left -= amount;
        }
        sender.sendMessage(Text.colorize("&a已给予 &f" + target.getName() + " &f" + def.name() + " &ax" + count));
    }

    private static int parseCount(String raw, CommandSender sender) {
        try {
            return Math.max(1, Math.min(256, Integer.parseInt(raw)));
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.colorize("&e数量不是数字，按 1 处理: " + raw));
            return 1;
        }
    }

    private void place(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.colorize("&c用法: /furniture place <ID> [x y z yaw]"));
            return;
        }
        FurnitureDef def = plugin.registry().get(args[1]);
        if (def == null) {
            sender.sendMessage(Text.colorize("&c没有这个家具: " + args[1]));
            return;
        }
        if (args.length >= 5) {
            placeAt(sender, def, args);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.colorize("&c控制台执行请带坐标: /furniture place " + def.id() + " <x> <y> <z> [yaw]"));
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            Location front = player.getLocation().add(player.getLocation().getDirection().multiply(2.0D));
            target = front.getBlock();
        }
        if (!target.isReplaceable() || target.isLiquid()) {
            Block above = target.getRelative(BlockFace.UP);
            if (above.isReplaceable() && !above.isLiquid()) {
                target = above;
            } else {
                player.sendMessage(Text.colorize("&c准星位置放不下家具"));
                return;
            }
        }
        float yaw = normalize(Math.round(player.getLocation().getYaw() / 90.0F) * 90.0F + 180.0F);
        PlacedFurniture placed = plugin.manager().place(target, def, yaw);
        if (placed == null) {
            player.sendMessage(Text.colorize("&c放置失败，请查看控制台日志"));
            return;
        }
        player.sendMessage(Text.colorize("&a已放置 &f" + def.name() + " &7(" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")"));
    }

    private void placeAt(CommandSender sender, FurnitureDef def, String[] args) {
        int x;
        int y;
        int z;
        float yaw = 0.0F;
        try {
            x = Integer.parseInt(args[2]);
            y = Integer.parseInt(args[3]);
            z = Integer.parseInt(args[4]);
            if (args.length >= 6) {
                yaw = Float.parseFloat(args[5]);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.colorize("&c坐标或朝向格式不对: /furniture place <ID> <x> <y> <z> [yaw]"));
            return;
        }
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        PlacedFurniture placed = plugin.manager().place(world.getBlockAt(x, y, z), def, normalize(yaw));
        if (placed == null) {
            sender.sendMessage(Text.colorize("&c放置失败，请查看控制台日志"));
            return;
        }
        sender.sendMessage(Text.colorize("&a已放置 &f" + def.name() + " &7(" + x + ", " + y + ", " + z + " yaw=" + normalize(yaw) + ")"));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length >= 4) {
            int x;
            int y;
            int z;
            try {
                x = Integer.parseInt(args[1]);
                y = Integer.parseInt(args[2]);
                z = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Text.colorize("&c坐标格式不对: /furniture remove <x> <y> <z>"));
                return;
            }
            World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
            PlacedFurniture record = plugin.manager().nearest(new Location(world, x + 0.5D, y + 0.5D, z + 0.5D), 0.6D);
            if (record == null) {
                sender.sendMessage(Text.colorize("&c那里没有家具"));
                return;
            }
            plugin.manager().remove(record, plugin.getConfig().getBoolean("place.drop-item", true));
            sender.sendMessage(Text.colorize("&e已拆除 &f" + record.id() + " &7(" + record.x() + ", " + record.y() + ", " + record.z() + ")"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.colorize("&c控制台执行请带坐标: /furniture remove <x> <y> <z>"));
            return;
        }
        double radius = plugin.getConfig().getDouble("place.remove-radius", 5.0D);
        PlacedFurniture record = plugin.manager().nearest(player.getLocation(), radius);
        if (record == null) {
            player.sendMessage(Text.colorize("&c附近 " + (int) radius + " 格内没有家具"));
            return;
        }
        boolean drop = plugin.getConfig().getBoolean("place.drop-item", true);
        plugin.manager().remove(record, drop);
        FurnitureDef def = plugin.registry().get(record.id());
        player.sendMessage(Text.colorize("&e已拆除 &f" + (def == null ? record.id() : def.name())));
    }

    private void nearest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.colorize("&c这个子命令需要玩家执行（或改用坐标形式）"));
            return;
        }
        Location at = player.getLocation();
        PlacedFurniture record = plugin.manager().nearest(at, 8.0D);
        if (record == null) {
            sender.sendMessage(Text.colorize("&c附近 8 格内没有家具"));
            return;
        }
        FurnitureDef def = plugin.registry().get(record.id());
        sender.sendMessage(Text.colorize("&7最近家具: &f" + record.id()
                + " &7(" + record.worldName() + " " + record.x() + " " + record.y() + " " + record.z()
                + " yaw=" + record.yaw() + " lit=" + record.lit() + ")"
                + (def == null ? "" : " &8占地 " + def.sizeWidth() + "x" + def.sizeDepth())));
    }

    /** 测试用：按坐标旋转 / 开关灯，控制台也能跑 */
    private void rotate(CommandSender sender, String[] args) {
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        Location at;
        float delta = 90.0F;
        try {
            if (args.length >= 4) {
                at = new Location(world, Integer.parseInt(args[1]) + 0.5D, Integer.parseInt(args[2]) + 0.5D, Integer.parseInt(args[3]) + 0.5D);
                if (args.length >= 5) {
                    delta = Float.parseFloat(args[4]);
                }
            } else if (sender instanceof Player player) {
                at = player.getLocation();
                if (args.length >= 2) {
                    delta = Float.parseFloat(args[1]);
                }
            } else {
                sender.sendMessage(Text.colorize("&c用法: /furniture rotate <x> <y> <z> [角度]"));
                return;
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.colorize("&c坐标或角度格式不对"));
            return;
        }
        PlacedFurniture record = plugin.manager().nearest(at, 1.2D);
        if (record == null) {
            sender.sendMessage(Text.colorize("&c那个位置附近没有家具"));
            return;
        }
        if (!plugin.manager().rotate(record, delta)) {
            sender.sendMessage(Text.colorize("&c旋转失败（周围有方块挡着）"));
            return;
        }
        sender.sendMessage(Text.colorize("&a已旋转 &f" + record.id() + " &7→ yaw=" + record.yaw()));
    }

    private void light(CommandSender sender, String[] args) {
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        Location at;
        try {
            if (args.length >= 4) {
                at = new Location(world, Integer.parseInt(args[1]) + 0.5D, Integer.parseInt(args[2]) + 0.5D, Integer.parseInt(args[3]) + 0.5D);
            } else if (sender instanceof Player player) {
                at = player.getLocation();
            } else {
                sender.sendMessage(Text.colorize("&c用法: /furniture light <x> <y> <z>"));
                return;
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.colorize("&c坐标格式不对"));
            return;
        }
        PlacedFurniture record = plugin.manager().nearest(at, 1.2D);
        if (record == null) {
            sender.sendMessage(Text.colorize("&c那个位置附近没有家具"));
            return;
        }
        FurnitureDef def = plugin.registry().get(record.id());
        if (def == null || !def.light()) {
            sender.sendMessage(Text.colorize("&e" + record.id() + " 不是灯具（furniture.yml 里加 light: true 才能开关）"));
            return;
        }
        boolean lit = plugin.manager().toggleLight(record);
        sender.sendMessage(Text.colorize("&a已" + (lit ? "开灯" : "关灯") + " &f" + def.name()));
    }

    /** 座位点转成一句话，方便验收时对一遍 /furniture info 的输出 */
    private static String seatPointsText(FurnitureDef def) {
        if (def.seatPoints() == null || def.seatPoints().isEmpty()) {
            return "无（按占地均分, seat-y=" + def.seatY() + "）";
        }
        StringBuilder text = new StringBuilder();
        for (double[] point : def.seatPoints()) {
            if (text.length() > 0) {
                text.append(" / ");
            }
            text.append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", point[0], point[1], point[2]));
        }
        return text.toString();
    }

    /** 查看某件家具的完整状态（含容器内容），调试与验收用 */
    private void info(CommandSender sender, String[] args) {
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        Location at;
        try {
            if (args.length >= 4) {
                at = new Location(world, Integer.parseInt(args[1]) + 0.5D, Integer.parseInt(args[2]) + 0.5D, Integer.parseInt(args[3]) + 0.5D);
            } else if (sender instanceof Player player) {
                at = player.getLocation();
            } else {
                sender.sendMessage(Text.colorize("&c用法: /furniture info <x> <y> <z>"));
                return;
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.colorize("&c坐标格式不对"));
            return;
        }
        PlacedFurniture record = plugin.manager().nearest(at, 1.6D);
        if (record == null) {
            sender.sendMessage(Text.colorize("&c那里没有家具"));
            return;
        }
        FurnitureDef def = plugin.registry().get(record.id());
        sender.sendMessage(Text.colorize("&8&m----&r &f" + (def == null ? record.id() : def.name()) + " &8&m----"));
        sender.sendMessage(Text.colorize("&7id=&f" + record.id() + " &7位置=&f" + record.worldName() + " "
                + record.x() + " " + record.y() + " " + record.z()));
        sender.sendMessage(Text.colorize("&7yaw=&f" + record.yaw() + " &7lit=&f" + record.lit()
                + " &7display=&f" + record.displayId()));
        if (def != null) {
            sender.sendMessage(Text.colorize("&7占地=&f" + def.sizeWidth() + "x" + def.sizeDepth()
                    + " &7坐=&f" + def.seat() + " &7人数=&f" + def.seats()
                    + " &7灯=&f" + def.light() + " &7容器=&f" + def.storage()));
            if (def.seat()) {
                sender.sendMessage(Text.colorize("&7挡人=&f" + plugin.manager().solidEnabled(def)
                        + " &7座位点=&f" + seatPointsText(def)
                        + " &7碰撞实体=&f" + record.collisionIds().size()));
            } else {
                sender.sendMessage(Text.colorize("&7挡人=&f" + plugin.manager().solidEnabled(def)
                        + " &7碰撞实体=&f" + record.collisionIds().size()));
            }
        }
        sender.sendMessage(Text.colorize("&7容器内容=&f" + FurnitureStorage.itemCount(record) + " &7件"));
        World recordWorld = Bukkit.getWorld(record.worldName());
        if (recordWorld != null) {
            Block anchor = recordWorld.getBlockAt(record.x(), record.y(), record.z());
            String level = "";
            if (anchor.getBlockData() instanceof org.bukkit.block.data.Levelled levelled) {
                level = " &7亮度=&f" + levelled.getLevel();
            }
            sender.sendMessage(Text.colorize("&7锚点方块=&f" + anchor.getType().name() + level));
        }
        for (int i = 0; i < record.contents().size() && i < 10; i++) {
            ItemStack stack = record.contents().get(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            sender.sendMessage(Text.colorize("&8- &7[" + (i + 1) + "] &f" + stack.getType().name() + " &7x" + stack.getAmount()));
        }
    }

    /** 往储物家具里塞物品，避免为了验收专门开 GUI */
    private void fill(CommandSender sender, String[] args) {
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        if (args.length < 4) {
            sender.sendMessage(Text.colorize("&c用法: /furniture fill <x> <y> <z> [material] [数量]"));
            return;
        }
        Location at;
        try {
            at = new Location(world, Integer.parseInt(args[1]) + 0.5D,
                    Integer.parseInt(args[2]) + 0.5D, Integer.parseInt(args[3]) + 0.5D);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.colorize("&c坐标格式不对"));
            return;
        }
        PlacedFurniture record = plugin.manager().nearest(at, 1.6D);
        if (record == null) {
            sender.sendMessage(Text.colorize("&c那里没有家具"));
            return;
        }
        FurnitureDef def = plugin.registry().get(record.id());
        if (def == null || def.storage() <= 0) {
            sender.sendMessage(Text.colorize("&c这件家具不是储物容器"));
            return;
        }
        Material material = Material.STONE;
        if (args.length >= 5) {
            material = Material.matchMaterial(args[4].toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) {
                sender.sendMessage(Text.colorize("&c不认识的物品: " + args[4]));
                return;
            }
        }
        int amount = 1;
        if (args.length >= 6) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[5])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Text.colorize("&c数量格式不对"));
                return;
            }
        }
        int size = def.storage();
        int used = FurnitureStorage.itemCount(record);
        if (used >= size && record.contents().size() >= size) {
            sender.sendMessage(Text.colorize("&c容器已满（" + size + " 格）"));
            return;
        }
        // 找第一个空槽，保持槽位不挤到前面
        int slot = -1;
        for (int i = 0; i < record.contents().size(); i++) {
            ItemStack existing = record.contents().get(i);
            if (existing == null || existing.getType().isAir()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            if (record.contents().size() >= size) {
                sender.sendMessage(Text.colorize("&c容器已满（" + size + " 格）"));
                return;
            }
            record.contents().add(new ItemStack(material, amount));
            slot = record.contents().size() - 1;
        } else {
            record.contents().set(slot, new ItemStack(material, amount));
        }
        plugin.manager().saveAll();
        sender.sendMessage(Text.colorize("&a已放入 &f" + material.name() + " x" + amount
                + " &7→ " + def.name() + " 第 &f" + (slot + 1) + " &7格，当前 &f"
                + FurnitureStorage.itemCount(record) + "&7/" + size + " 格"));
    }

    private void list(CommandSender sender, String[] args, String label) {
        if (plugin.registry().ids().isEmpty()) {
            sender.sendMessage(Text.colorize("&c还没有任何家具定义"));
            return;
        }
        String filter = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        sender.sendMessage(Text.colorize("&8&m----&r &f家具列表 &8&m----"));
        StringBuilder line = new StringBuilder();
        int inLine = 0;
        int shown = 0;
        for (FurnitureDef def : plugin.registry().all()) {
            if (filter != null && !def.id().toLowerCase(Locale.ROOT).contains(filter)
                    && !def.name().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            if (inLine == 0) {
                line.append("&7- ");
            }
            line.append("&f").append(def.id()).append("&8(").append(def.name()).append("&8) ");
            inLine++;
            shown++;
            if (inLine >= 3) {
                sender.sendMessage(Text.colorize(line.toString()));
                line.setLength(0);
                inLine = 0;
            }
        }
        if (inLine > 0) {
            sender.sendMessage(Text.colorize(line.toString()));
        }
        sender.sendMessage(Text.colorize("&7共 &f" + shown + " &7件，已放置 &f"
                + plugin.manager().count() + " &7个；详细用法 &f/" + label + " help"));
    }

    private void recipes(CommandSender sender) {
        if (plugin.registry().recipes().isEmpty()) {
            sender.sendMessage(Text.colorize("&c还没有任何合成配方（在 furniture.yml 里给家具加 recipe 节点）"));
            return;
        }
        sender.sendMessage(Text.colorize("&8&m----&r &f合成配方 &8&m----"));
        for (FurnitureRecipe recipe : plugin.registry().recipes()) {
            FurnitureDef def = plugin.registry().get(recipe.furnitureId());
            String name = def == null ? recipe.furnitureId() : def.name();
            sender.sendMessage(Text.colorize("&f" + name + " &8(&7" + recipe.furnitureId() + "&8) &7×" + recipe.amount()));
            if (recipe.shaped()) {
                for (String row : recipe.shape()) {
                    StringBuilder line = new StringBuilder("  &8[&r");
                    for (char c : row.toCharArray()) {
                        line.append(c == ' ' ? "&8· " : "&e" + c + " ");
                    }
                    line.append("&8]");
                    sender.sendMessage(Text.colorize(line.toString()));
                }
                StringBuilder legend = new StringBuilder("  &7");
                for (var entry : recipe.key().entrySet()) {
                    legend.append(entry.getKey()).append(" = ")
                            .append(entry.getValue().name().toLowerCase(Locale.ROOT)).append("  ");
                }
                sender.sendMessage(Text.colorize(legend.toString()));
            } else {
                StringBuilder legend = new StringBuilder("  &7无序: ");
                for (var material : recipe.shapeless()) {
                    legend.append(material.name().toLowerCase(Locale.ROOT)).append("  ");
                }
                sender.sendMessage(Text.colorize(legend.toString()));
            }
        }
    }

    private void pack(CommandSender sender) {        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.colorize("&7资源包地址: &f" + plugin.packUrl()));
            return;
        }
        plugin.sendPack(player);
        player.sendMessage(Text.colorize("&a已重新请求下发资源包"));
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.registry().reload();
        plugin.recipes().sync();
        // 重新读一遍 placed.yml：手工改过存档也能直接生效，不会重复生成实体
        plugin.manager().loadAll();
        plugin.rebuildPack();
        sender.sendMessage(Text.colorize("&a已重载: 家具 " + plugin.registry().ids().size()
                + " 个, 配方 " + plugin.recipes().keys().size()
                + " 条, 已放置 " + plugin.manager().count()
                + " 个, 资源包地址 " + plugin.packUrl()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("give", "place", "remove", "rotate", "light", "info", "fill", "nearest", "list", "recipes", "pack", "reload")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("place"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String id : plugin.registry().ids()) {
                if (id.startsWith(prefix)) {
                    result.add(id);
                }
            }
            return result;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(player.getName());
                }
            }
            return result;
        }
        return result;
    }

    private static float normalize(float yaw) {
        float value = yaw % 360.0F;
        if (value < 0) {
            value += 360.0F;
        }
        return value;
    }
}

