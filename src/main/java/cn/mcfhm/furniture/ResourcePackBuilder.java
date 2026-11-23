package cn.mcfhm.furniture;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 把插件内置的 pack/ 资源 + 运行时生成的 pack.mcmeta 打包成一个 zip 资源包。
 * 插件以 jar 运行时从 jar 里读，开发环境（classes 目录）时从文件系统读。
 */
public final class ResourcePackBuilder {

    private static final String PREFIX = "pack/";

    private final FurniturePlugin plugin;

    public ResourcePackBuilder(FurniturePlugin plugin) {
        this.plugin = plugin;
    }

    public PackResult build() throws IOException {
        Map<String, byte[]> assets = collectAssets();
        // 1.21.4+ 的 item_model 组件先解析 items/<id>.json 这个「物品模型定义」，
        // 再由定义指向 models/ 里的模型；缺了定义客户端会直接渲染成紫黑缺失模型。
        for (FurnitureDef def : plugin.registry().all()) {
            String definitionPath = itemDefinitionPath(def);
            if (definitionPath == null) {
                continue;
            }
            assets.put(definitionPath, itemDefinition(def).getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            putEntry(zip, "pack.mcmeta", mcmeta().getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : assets.entrySet()) {
                putEntry(zip, entry.getKey(), entry.getValue());
            }
        }
        byte[] bytes = buffer.toByteArray();
        File output = new File(plugin.getDataFolder(), "pack/FurniturePack.zip");
        Files.createDirectories(output.toPath().getParent());
        Files.write(output.toPath(), bytes);
        byte[] digest = sha1Raw(bytes);
        return new PackResult(output, bytes, digest, hex(digest));
    }

    private String itemDefinitionPath(FurnitureDef def) {
        String namespace = def.model().getNamespace();
        if (!FurniturePlugin.PACK_NAMESPACE.equals(namespace)) {
            plugin.getLogger().warning("家具 " + def.id() + " 的 model 不在 " + FurniturePlugin.PACK_NAMESPACE
                    + " 命名空间下，需要你自己提供 assets/" + namespace + "/items/" + def.model().getKey() + ".json");
            return null;
        }
        return "assets/" + namespace + "/items/" + def.model().getKey() + ".json";
    }

    private String itemDefinition(FurnitureDef def) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + def.model().getNamespace() + ":" + def.model().getKey() + "\"\n  }\n}\n";
    }

    private Map<String, byte[]> collectAssets() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        File self;
        try {
            self = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception ex) {
            self = null;
        }
        if (self != null && self.isFile()) {
            try (ZipFile zip = new ZipFile(self)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(PREFIX)) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        assets.put(entry.getName().substring(PREFIX.length()), in.readAllBytes());
                    }
                }
            }
        } else if (self != null) {
            Path packDir = self.toPath().resolve("pack");
            if (Files.isDirectory(packDir)) {
                try (var walk = Files.walk(packDir)) {
                    for (Path path : (Iterable<Path>) walk::iterator) {
                        if (!Files.isRegularFile(path)) {
                            continue;
                        }
                        String name = packDir.relativize(path).toString().replace('\\', '/');
                        assets.put(name, Files.readAllBytes(path));
                    }
                }
            }
        }
        if (assets.isEmpty()) {
            plugin.getLogger().warning("没有找到任何 pack/ 资源，生成的资源包将只有 pack.mcmeta");
        }
        return assets;
    }

    private String mcmeta() {
        FileConfiguration config = plugin.getConfig();
        int major = config.getInt("resource-pack.pack-format.major", 97);
        int minor = config.getInt("resource-pack.pack-format.minor", 1);
        // max 单独给，默认放到很宽：以前 min == max，客户端版本只要差一点整包就被拒
        int maxMajor = Math.max(major, config.getInt("resource-pack.pack-format.max-major", 999));
        int maxMinor = config.getInt("resource-pack.pack-format.max-minor", 0);
        if (maxMajor == major && maxMinor < minor) {
            maxMinor = minor;
        }
        String description = config.getString("resource-pack.description", "FHM Furniture");
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"" + escape(description) + "\",\n"
                + "    \"min_format\": [" + major + ", " + minor + "],\n"
                + "    \"max_format\": [" + maxMajor + ", " + maxMinor + "]\n"
                + "  }\n"
                + "}\n";
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // 固定时间戳，内容不变时每次重建 sha1 都一样，玩家不会被无意义地重新下载资源包
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] sha1Raw(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            return new byte[20];
        }
    }

    private static String hex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
