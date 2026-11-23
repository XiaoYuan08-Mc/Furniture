package cn.mcfhm.furniture.listen;

import cn.mcfhm.furniture.FurnitureDef;
import cn.mcfhm.furniture.FurnitureItems;
import cn.mcfhm.furniture.FurniturePlugin;
import cn.mcfhm.furniture.FurnitureRecipe;
import cn.mcfhm.furniture.PlacedFurniture;
import cn.mcfhm.furniture.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 放置 / 拆除 / 旋转 / 开关灯 / 坐下，登录时下发资源包 */
public final class FurnitureListener implements Listener {

    private final FurniturePlugin plugin;
    /** 玩家 UUID → 坐用的隐形盔甲架 UUID */
    private final Map<UUID, UUID> seats = new HashMap<>();
    /** 坐用盔甲架 UUID → 它属于哪件家具 */
    private final Map<UUID, PlacedFurniture> seatRecords = new HashMap<>();

    public FurnitureListener(FurniturePlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- 放置
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        FurnitureDef def = FurnitureItems.read(plugin, event.getItem());
        if (def == null) {
            return;
        }
        if (player.isSneaking()) {
            return;
        }
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.isReplaceable() || target.isLiquid()) {
            player.sendMessage(Text.colorize("&c这里放不下家具，换个位置试试"));
            return;
        }
        float yaw = normalize(snapYaw(player.getLocation().getYaw()) + 180.0F);
        if (!plugin.manager().canPlace(target.getWorld(), target.getX(), target.getY(), target.getZ(), def, yaw)) {
            player.sendMessage(Text.colorize("&c这里放不下 &f" + def.name() + "&c：位置被方块挡住或和别的家具重叠"));
            return;
        }
        PlacedFurniture placed = plugin.manager().place(target, def, yaw);
        if (placed == null) {
            player.sendMessage(Text.colorize("&c家具生成失败，请查看控制台日志"));
            return;
        }
        event.setCancelled(true);
        if (plugin.getConfig().getBoolean("place.consume-item", false)) {
            ItemStack hand = event.getItem();
            if (hand != null) {
                int amount = hand.getAmount() - 1;
                if (amount <= 0) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    hand.setAmount(amount);
                }
            }
        }
        player.sendMessage(Text.colorize("&a已放置 &f" + def.name()));
    }

    // ---------------------------------------------------------------- 左键拆除
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction hitbox)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        PlacedFurniture record = plugin.manager().byHitbox(hitbox.getUniqueId());
        if (record == null) {
            return;
        }
        event.setCancelled(true);
        breakFurniture(player, record);
    }

    // ---------------------------------------------------------------- 右键：旋转 / 开关灯 / 坐下
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction hitbox)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        PlacedFurniture record = plugin.manager().byHitbox(hitbox.getUniqueId());
        if (record == null) {
            return;
        }
        FurnitureDef def = plugin.registry().get(record.id());
        if (def == null) {
            return;
        }
        event.setCancelled(true);

        if (player.isSneaking()) {
            if (plugin.getConfig().getBoolean("interact.sneak-rotate", true)) {
                if (plugin.manager().rotate(record, 90.0F)) {
                    player.sendMessage(Text.colorize("&a已旋转 &f" + def.name()));
                    Location soundAt = record.location();
                    player.getWorld().playSound(soundAt == null ? player.getLocation() : soundAt,
                            org.bukkit.Sound.BLOCK_WOOD_PLACE, 0.6F, 1.4F);
                } else {
                    player.sendMessage(Text.colorize("&c转不开，周围有方块挡着"));
                }
            }
            return;
        }

        if (def.seat() && player.getVehicle() == null) {
            sit(player, record, def);
            return;
        }
        if (def.light()) {
            boolean lit = plugin.manager().toggleLight(record);
            player.sendMessage(Text.colorize(lit ? "&a已开灯 &7(" + def.name() + "&7)" : "&7已关灯 &7(" + def.name() + "&7)"));
            Location soundAt = new Location(player.getWorld(), record.x() + 0.5D, record.y() + 0.5D, record.z() + 0.5D);
            player.getWorld().playSound(soundAt, org.bukkit.Sound.BLOCK_LEVER_CLICK, 0.6F, lit ? 1.2F : 0.8F);
        }
    }

    // ---------------------------------------------------------------- 坐下
    private void sit(Player player, PlacedFurniture record, FurnitureDef def) {
        org.bukkit.World world = plugin.getServer().getWorld(record.worldName());
        if (world == null) {
            return;
        }
        Location seatAt = new Location(world,
                record.x() + 0.5D,
                record.y() + def.seatY() + plugin.getConfig().getDouble("interact.seat-offset", 0.0D),
                record.z() + 0.5D, record.yaw(), 0.0F);
        Entity spawned = world.spawnEntity(seatAt, EntityType.ARMOR_STAND);
        if (!(spawned instanceof ArmorStand stand)) {
            if (spawned != null) {
                spawned.remove();
            }
            player.sendMessage(Text.colorize("&c坐不上去（生成座位失败）"));
            return;
        }
        stand.setVisible(false);
        stand.setSmall(true);
        stand.setMarker(plugin.getConfig().getBoolean("interact.seat-marker", true));
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setRotation(record.yaw(), 0.0F);
        stand.setPersistent(false);

        if (!stand.addPassenger(player)) {
            stand.remove();
            player.sendMessage(Text.colorize("&c这件家具暂时坐不了（服务端不接受座位挂载）"));
            return;
        }
        seats.put(player.getUniqueId(), stand.getUniqueId());
        seatRecords.put(stand.getUniqueId(), record);
        player.sendMessage(Text.colorize("&7坐上 &f" + def.name() + " &7— 潜行即可起身"));
    }

    /** 让玩家起身；seat 参数为空时按玩家当前座位找 */
    private void stand(Player player) {
        UUID seatId = seats.remove(player.getUniqueId());
        if (seatId == null) {
            return;
        }
        seatRecords.remove(seatId);
        Entity seat = player.getWorld().getEntity(seatId);
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }
        if (seat != null) {
            seat.remove();
        }
        player.sendMessage(Text.colorize("&7已起身"));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && seats.containsKey(event.getPlayer().getUniqueId())) {
            stand(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID seatId = seats.remove(event.getPlayer().getUniqueId());
        if (seatId != null) {
            seatRecords.remove(seatId);
            Entity seat = event.getPlayer().getWorld().getEntity(seatId);
            if (seat != null) {
                seat.remove();
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && seats.containsKey(player.getUniqueId())) {
            stand(player);
        }
    }

    /** 拆掉的家具如果正有人坐，先让人起身 */
    private void ejectSeated(PlacedFurniture record) {
        for (Map.Entry<UUID, PlacedFurniture> entry : seatRecords.entrySet()) {
            if (entry.getValue() != record) {
                continue;
            }
            UUID standId = entry.getKey();
            seatRecords.remove(standId);
            Player occupant = null;
            for (Map.Entry<UUID, UUID> seat : seats.entrySet()) {
                if (seat.getValue().equals(standId)) {
                    occupant = plugin.getServer().getPlayer(seat.getKey());
                    seats.remove(seat.getKey());
                    break;
                }
            }
            if (occupant != null && occupant.getVehicle() != null) {
                occupant.leaveVehicle();
            }
            Entity seat = plugin.getServer().getWorld(record.worldName()) == null ? null
                    : plugin.getServer().getWorld(record.worldName()).getEntity(standId);
            if (seat != null) {
                seat.remove();
            }
            return;
        }
    }

    private void breakFurniture(Player player, PlacedFurniture record) {
        ejectSeated(record);
        boolean drop = plugin.getConfig().getBoolean("place.drop-item", true);
        plugin.manager().remove(record, drop);
        String name = record.id();
        FurnitureDef def = plugin.registry().get(record.id());
        if (def != null) {
            name = def.name();
        }
        player.sendMessage(Text.colorize("&e已拆除 &f" + name));
        Location soundAt = new Location(player.getWorld(), record.x() + 0.5D, record.y() + 0.5D, record.z() + 0.5D);
        player.getWorld().playSound(soundAt, org.bukkit.Sound.BLOCK_WOOD_BREAK, 0.8F, 1.0F);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.sendPack(player);
                plugin.recipes().discover(player);
            }
        }, 20L);
    }

    /**
     * 合成结果兜底：自己比对 3x3 合成格，命中家具配方就把结果槽换成
     * 带 PDC + item_model 的家具物品，不依赖服务端对配方产物的序列化行为。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inventory)) {
            return;
        }
        ItemStack[] matrix = inventory.getMatrix();
        for (FurnitureRecipe spec : plugin.registry().recipes()) {
            FurnitureDef def = plugin.registry().get(spec.furnitureId());
            if (def == null || !matches(spec, matrix)) {
                continue;
            }
            ItemStack result = FurnitureItems.create(plugin, def);
            result.setAmount(spec.amount());
            inventory.setResult(result);
            return;
        }
    }

    private static boolean matches(FurnitureRecipe spec, ItemStack[] matrix) {
        if (spec.shaped()) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    char expected = ' ';
                    if (row < spec.shape().size()) {
                        String line = spec.shape().get(row);
                        if (col < line.length()) {
                            expected = line.charAt(col);
                        }
                    }
                    ItemStack actual = matrix[row * 3 + col];
                    if (expected == ' ') {
                        if (actual != null && !actual.getType().isAir()) {
                            return false;
                        }
                    } else if (actual == null || actual.getType() != spec.key().get(expected)) {
                        return false;
                    }
                }
            }
            return true;
        }
        java.util.List<org.bukkit.Material> need = new java.util.ArrayList<>(spec.shapeless());
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!need.remove(stack.getType())) {
                return false;
            }
        }
        return need.isEmpty();
    }

    /** 把资源包下发结果打到控制台，客户端加载失败时能立刻看出来 */
    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> plugin.getLogger().info(
                    "资源包已加载: " + player.getName());
            case DECLINED -> {
                plugin.getLogger().warning("玩家 " + player.getName() + " 拒绝了资源包，家具会显示成紫黑方块");
                player.sendMessage(Text.colorize("&c你拒绝了资源包，家具模型无法显示。&7可用 &f/furniture pack &7重新获取"));
            }
            case FAILED_DOWNLOAD -> {
                plugin.getLogger().warning("玩家 " + player.getName() + " 资源包下载失败，检查 public-url / 8123 端口是否可从客户端访问");
                player.sendMessage(Text.colorize("&c资源包下载失败，家具模型无法显示"));
            }
            default -> {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("资源包状态 " + player.getName() + ": " + event.getStatus());
                }
            }
        }
    }

    private static float snapYaw(float yaw) {
        float normalized = normalize(yaw);
        return Math.round(normalized / 90.0F) * 90.0F;
    }

    private static float normalize(float yaw) {
        float result = yaw % 360.0F;
        if (result < 0) {
            result += 360.0F;
        }
        return result;
    }
}
