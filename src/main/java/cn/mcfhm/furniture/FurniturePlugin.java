package cn.mcfhm.furniture;

import cn.mcfhm.furniture.command.FurnitureCommand;
import cn.mcfhm.furniture.listen.FurnitureListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.logging.Level;

public final class FurniturePlugin extends JavaPlugin {

    /** 插件自带资源包的命名空间（assets/furniture/...） */
    public static final String PACK_NAMESPACE = "furniture";
    /** 固定 UUID：只为把自己的包和别的插件（比如 FHMTitles）区分开 */
    public static final java.util.UUID PACK_ID =
            java.util.UUID.nameUUIDFromBytes("Furniture:resourcepack".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private final NamespacedKey furnitureIdKey = new NamespacedKey(this, "furniture_id");
    private FurnitureRegistry registry;
    private FurnitureRecipes recipes;
    private FurnitureManager manager;
    private FurnitureStorage storage;
    private PackServer packServer;
    private PackResult pack;
    private int boundPort;
    /** 座位清理钩子，由监听器注册；旋转 / 拆除时让坐着的人起身 */
    private java.util.function.Consumer<PlacedFurniture> seatEjector;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        registry = new FurnitureRegistry(this);
        registry.reload();

        recipes = new FurnitureRecipes(this);
        recipes.sync();

        storage = new FurnitureStorage(this);
        manager = new FurnitureManager(this);
        manager.loadAll();

        PluginCommand command = getCommand("furniture");
        if (command != null) {
            FurnitureCommand handler = new FurnitureCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("plugin.yml 中缺少 furniture 命令，命令功能不可用");
        }

        Bukkit.getPluginManager().registerEvents(new FurnitureListener(this), this);

        if (getConfig().getBoolean("resource-pack.enabled", true)) {
            rebuildPack();
        }

        getLogger().info("家具系统加载完成: 定义 " + registry.ids().size() + " 个, 已放置 " + manager.count() + " 个");
    }

    @Override
    public void onDisable() {
        if (recipes != null) {
            for (NamespacedKey key : new java.util.ArrayList<>(recipes.keys())) {
                Bukkit.removeRecipe(key);
            }
        }
        // 关服时界面还开着的话关闭事件不保证触发，先把内容写回去再存盘
        if (storage != null) {
            // 进入关服流程：中间那些零散保存都跳过，最后统一写一次
            if (manager != null) {
                manager.beginShutdown();
            }
            for (Player online : getServer().getOnlinePlayers()) {
                storage.closeQuietly(online);
                if (storage.isViewing(online)) {
                    online.closeInventory();
                }
            }
        }
        if (manager != null) {
            // 同步写完再关写线程，避免关服时最后一次改动丢掉
            manager.saveBlocking();
            manager.closeWriter();
        }
        if (packServer != null) {
            packServer.stop();
            packServer = null;
        }
    }

    public NamespacedKey furnitureIdKey() {
        return furnitureIdKey;
    }

    public FurnitureRegistry registry() {
        return registry;
    }

    public FurnitureManager manager() {
        return manager;
    }

    public FurnitureRecipes recipes() {
        return recipes;
    }

    public FurnitureStorage storage() {
        return storage;
    }

    public void seatEjector(java.util.function.Consumer<PlacedFurniture> ejector) {
        this.seatEjector = ejector;
    }

    /** 有玩家坐在这件家具上时先让他起身（旋转会改变座位落点） */
    public void ejectSeats(PlacedFurniture record) {
        if (seatEjector != null) {
            seatEjector.accept(record);
        }
    }

    public PackResult pack() {
        return pack;
    }

    public void rebuildPack() {
        if (packServer != null) {
            packServer.stop();
            packServer = null;
        }
        try {
            pack = new ResourcePackBuilder(this).build();
            getLogger().info("资源包已生成: " + pack.file().getAbsolutePath()
                    + " (" + pack.size() + " 字节, sha1=" + pack.sha1Hex() + ")");
            String mode = getConfig().getString("resource-pack.mode", "builtin");
            if (!"external".equalsIgnoreCase(mode)) {
                packServer = new PackServer(this, getConfig().getInt("resource-pack.port", 8123), pack);
                packServer.start();
                boundPort = packServer.port();
            }
            // 重载后重新下发给在线玩家，方便本地反复调试
            for (Player online : getServer().getOnlinePlayers()) {
                sendPack(online);
            }
        } catch (Exception ex) {
            pack = null;
            getLogger().log(Level.SEVERE, "资源包生成 / 分发失败", ex);
        }
    }

    public String packUrl() {
        String mode = getConfig().getString("resource-pack.mode", "builtin");
        String configured = getConfig().getString("resource-pack.public-url", "");
        if ("external".equalsIgnoreCase(mode)) {
            return configured == null ? "" : configured.trim();
        }
        String base = configured == null ? "" : configured.trim();
        if (base.isEmpty()) {
            base = "http://" + localAddress() + ":" + (boundPort > 0 ? boundPort : getConfig().getInt("resource-pack.port", 8123));
        }
        if (base.endsWith(".zip")) {
            return base;
        }
        return base.endsWith("/") ? base + "pack.zip" : base + "/pack.zip";
    }

    private String localAddress() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // 拿不到网卡信息时退回本地回环
        }
        return "127.0.0.1";
    }

    public void sendPack(Player player) {
        if (pack == null) {
            if (getConfig().getBoolean("debug", false)) {
                player.sendMessage(Text.colorize("&c家具资源包尚未生成，请稍后再试"));
            }
            return;
        }
        String url = packUrl();
        if (url.isEmpty()) {
            getLogger().warning("资源包地址为空，无法下发（请检查 resource-pack.public-url）");
            return;
        }
        String prompt = Text.colorize(getConfig().getString("resource-pack.prompt", "请接受资源包"));
        boolean force = getConfig().getBoolean("resource-pack.force", false);
        try {
            // 走 Adventure 的请求，并用 replace(false) 叠加：不会把 FHMTitles 的图片称号包顶掉。
            // 旧 API player.setResourcePack(...) 会替换掉其它插件的包，两个插件同时装就会互相打架。
            net.kyori.adventure.resource.ResourcePackInfo info =
                    net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo()
                            .id(PACK_ID)
                            .uri(java.net.URI.create(url))
                            .hash(pack.sha1Hex())
                            .build();
            net.kyori.adventure.resource.ResourcePackRequest request =
                    net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                            .packs(info)
                            .prompt(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                    .legacySection().deserialize(prompt))
                            .required(force)
                            .replace(false)
                            .build();
            player.sendResourcePacks(request);
        } catch (Throwable t) {
            getLogger().warning("下发资源包失败(" + player.getName() + "): " + t.getMessage());
            return;
        }
        if (getConfig().getBoolean("debug", false)) {
            player.sendMessage(Text.colorize("&7[Furniture] 已下发资源包: &f" + url));
        }
    }
}
