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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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
    /** 左键射线拆除的节流：玩家 UUID → 上次拆除尝试时间，防止一次点击触发多次 */
    private final Map<UUID, Long> lastSwing = new HashMap<>();

    public FurnitureListener(FurniturePlugin plugin) {
        this.plugin = plugin;
        plugin.seatEjector(this::ejectSeated);
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
        PlacedFurniture placed = plugin.manager().place(target, def, yaw, player.getUniqueId());
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
        PlacedFurniture record = recordFor(event.getEntity());
        if (record == null) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (!canManage(player, record)) {
            player.sendMessage(Text.colorize("&c这不是你的家具，只有放置者或管理员能拆"));
            return;
        }
        breakFurniture(player, record);
    }

    /** 命中的是不是这件家具：交互箱和挡人实体都算 */
    private PlacedFurniture recordFor(Entity entity) {
        if (entity instanceof Interaction hitbox) {
            // 不能只查 byHitbox：重启时实体比记录晚进世界，映射没建起来就会「点了没反应」
            return plugin.manager().resolveHitbox(hitbox);
        }
        if (plugin.manager().isCollision(entity)) {
            Location at = entity.getLocation();
            return plugin.manager().at(entity.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }
        return null;
    }

    /**
     * 左键拆除的备用方式。
     * Interaction 不是 LivingEntity，客户端左键它不会产生 EntityDamageByEntityEvent，
     * 一旦玩家打的是交互箱（而不是挡人的潜影贝），旧逻辑就完全拆不掉。
     * 这里按视角射线找家具，命中就近拆除。
     */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSwing.get(player.getUniqueId());
        if (last != null && now - last < 300L) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eye, direction, 4.5D,
                org.bukkit.FluidCollisionMode.NEVER, true);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : player.getWorld().getNearbyEntities(eye, 5.0D, 5.0D, 5.0D)) {
            if (recordFor(entity) == null) {
                continue;
            }
            RayTraceResult result = entity.getBoundingBox().rayTrace(eye.toVector(), direction, 4.5D);
            if (result == null) {
                continue;
            }
            double distance = result.getHitPosition().distanceSquared(eye.toVector());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        if (nearest == null) {
            return;
        }
        if (blockHit != null && blockHit.getHitPosition().distanceSquared(eye.toVector()) < nearestDistance) {
            return;
        }
        PlacedFurniture record = recordFor(nearest);
        if (record == null) {
            return;
        }
        lastSwing.put(player.getUniqueId(), now);
        if (!canManage(player, record)) {
            player.sendMessage(Text.colorize("&c这不是你的家具，只有放置者或管理员能拆"));
            return;
        }
        breakFurniture(player, record);
    }

    // ---------------------------------------------------------------- 右键：旋转 / 开关灯 / 坐下
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        PlacedFurniture record = recordFor(event.getRightClicked());
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
                if (!canManage(player, record)) {
                    player.sendMessage(Text.colorize("&c这不是你的家具，只有放置者或管理员能旋转"));
                    return;
                }
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
        if (player.getVehicle() != null) {
            player.sendMessage(Text.colorize("&7你已经坐着了，潜行即可起身"));
            return;
        }
        if (def.storage() > 0) {
            if (!canManage(player, record)) {
                player.sendMessage(Text.colorize("&c这不是你的家具，只有放置者或管理员能打开"));
                return;
            }
            plugin.storage().open(player, record, def);
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
        // 数一下这件家具上已经坐了几个人，超员就拒绝（先清掉座位实体已经消失的脏记录）
        for (java.util.Iterator<Map.Entry<UUID, PlacedFurniture>> it = seatRecords.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, PlacedFurniture> entry = it.next();
            org.bukkit.World seatWorld = plugin.getServer().getWorld(entry.getValue().worldName());
            if (seatWorld != null && seatWorld.getEntity(entry.getKey()) == null) {
                it.remove();
            }
        }
        int taken = 0;
        for (PlacedFurniture occupied : seatRecords.values()) {
            if (occupied == record) {
                taken++;
            }
        }
        if (taken >= def.seats()) {
            player.sendMessage(Text.colorize("&c这件家具已经坐满了（&f" + def.seats() + " &c人）"));
            return;
        }
        // 优先用 furniture.yml 里的 seat-points（模型像素坐标），没写就按占地范围均分
        double localX;
        double localZ;
        double seatHeight;
        double[] point = def.seatPoint(taken);
        if (point != null) {
            localX = point[0];
            localZ = point[2];
            seatHeight = point[1];
        } else {
            // 多人座沿家具宽度方向等分落座：单人坐中间，双人分左右，三人三等分
            localX = (def.sizeWidth() - 1) / 2.0D;
            if (def.seats() > 1) {
                localX += ((double) taken / (def.seats() - 1) - 0.5D) * (def.sizeWidth() - 1);
            }
            localZ = (def.sizeDepth() - 1) / 2.0D;
            seatHeight = def.seatY();
        }
        double[] offset = cn.mcfhm.furniture.FurnitureManager.worldOffset(record.yaw(), localX, localZ);
        Location seatAt = new Location(world,
                record.x() + 0.5D + offset[0],
                record.y() + seatHeight + plugin.getConfig().getDouble("interact.seat-offset", 0.0D),
                record.z() + 0.5D + offset[1], record.yaw(), 0.0F);
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
        PlacedFurniture record = seatRecords.remove(seatId);
        Entity seat = player.getWorld().getEntity(seatId);
        if (seat == null && record != null) {
            org.bukkit.World seatWorld = plugin.getServer().getWorld(record.worldName());
            if (seatWorld != null) {
                seat = seatWorld.getEntity(seatId);
            }
        }
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
        Player player = event.getPlayer();
        plugin.storage().closeQuietly(player);
        lastSwing.remove(player.getUniqueId());
        UUID seatId = seats.remove(player.getUniqueId());
        if (seatId != null) {
            seatRecords.remove(seatId);
            Entity seat = player.getWorld().getEntity(seatId);
            if (seat != null) {
                seat.remove();
            }
        }
    }

    // ---------------------------------------------------------------- 容器
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.storage().close(player, event.getInventory());
        }
    }

    /** 家具被拆（命令 / 左键都走这里）：先让人起身，再关掉正打开着的界面 */
    @EventHandler
    public void onFurnitureRemove(cn.mcfhm.furniture.FurnitureRemoveEvent event) {
        ejectSeated(event.record());
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (plugin.storage().viewingRecord(online) == event.record()) {
                online.closeInventory();
            }
        }
    }

    // ---------------------------------------------------------------- 传送 / 死亡时收座位
    @EventHandler
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (event.getTo() == null || !seats.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) > 4.0D) {
            stand(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        stand(event.getEntity());
    }

    /** 区块重新加载后补回灯具亮度（显示实体的亮度不保证跨重载保留） */
    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        // 走区块索引，别再遍历全部家具
        for (PlacedFurniture record : plugin.manager().lightsInChunk(worldName, cx, cz)) {
            FurnitureDef def = plugin.registry().get(record.id());
            if (def == null || !def.light()) {
                continue;
            }
            Entity display = event.getWorld().getEntity(record.displayId());
            if (display instanceof org.bukkit.entity.Display d) {
                d.setBrightness(record.lit() ? new org.bukkit.entity.Display.Brightness(15, 15) : null);
            }
            // 外部改过世界（WorldEdit / 手动放方块）时，把灯具的真实光源方块补回来
            if (record.lit()) {
                plugin.manager().syncLightBlock(record);
            }
        }

        // 实体是在区块之后才进世界的，延后一 tick 再校正位置 / 补碰撞，避免读到半成品
        if (plugin.manager().isLoaded()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                org.bukkit.World world = event.getWorld();
                if (world == null || !world.isChunkLoaded(cx, cz)) {
                    return;
                }
                for (PlacedFurniture record : plugin.manager().chunkRecords(worldName, cx, cz)) {
                    FurnitureDef def = plugin.registry().get(record.id());
                    if (def == null) {
                        continue;
                    }
                    plugin.manager().resyncEntity(record, def, world.getEntity(record.displayId()));
                    // 交互箱可能比记录晚进世界 / 被别的插件清掉，这里补登记或重建，否则家具点不动
                    plugin.manager().ensureHitbox(record, def);
                    plugin.manager().ensureCollisions(record);
                }
                // 孤儿挡人实体：家具已经没了，潜影贝还留着（拆除时那格没加载就会这样）
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!plugin.manager().isCollision(entity)) {
                        continue;
                    }
                    Location at = entity.getLocation();
                    if (plugin.manager().at(world, at.getBlockX(), at.getBlockY(), at.getBlockZ()) == null) {
                        entity.remove();
                    }
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        // 挡人的潜影贝不许被打掉（爆炸、火焰、近战都算）
        if (plugin.manager().isCollision(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && seats.containsKey(player.getUniqueId())) {
            stand(player);
        }
    }

    /** 拆掉的家具如果正有人坐，先把所有坐着的人请起来（多人沙发会同时坐好几个人）。 */
    private void ejectSeated(PlacedFurniture record) {
        java.util.List<UUID> standIds = new java.util.ArrayList<>();
        for (Map.Entry<UUID, PlacedFurniture> entry : seatRecords.entrySet()) {
            if (entry.getValue() == record) {
                standIds.add(entry.getKey());
            }
        }
        for (UUID standId : standIds) {
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
            org.bukkit.World world = plugin.getServer().getWorld(record.worldName());
            Entity seat = world == null ? null : world.getEntity(standId);
            if (seat != null) {
                seat.remove();
            }
        }
    }

    /** 家具归属校验：放置者、管理员、公共家具（owner 为空）放行。 */
    private boolean canManage(Player player, PlacedFurniture record) {
        if (!plugin.getConfig().getBoolean("protect.enabled", true)) {
            return true;
        }
        if (player.hasPermission("furniture.admin") || player.hasPermission("furniture.protect.bypass")) {
            return true;
        }
        UUID owner = record.owner();
        return owner == null || owner.equals(player.getUniqueId());
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
        // 进服后把身边家具的交互箱再确认一遍：重启后区块刚载入时实体状态可能还没稳，
        // 这里补登记 / 纠尺寸，避免"人一到家跟前，家具点不动"
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.manager().ensureNearby(player);
        }, 40L);
    }

    /**
     * 合成结果校正：自己比对 3x3 合成格，命中家具配方就把结果槽换成
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
        // 只认自己的包，别把 FHMTitles 的包状态也当成家具包记日志 / 提示玩家
        if (!FurniturePlugin.PACK_ID.equals(event.getID())) {
            return;
        }
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
