package cn.mcfhm.furniture;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** 内置的极简 HTTP 服务，只负责把 FurniturePack.zip 发给客户端 */
public final class PackServer {

    private final FurniturePlugin plugin;
    private int port;
    private final PackResult pack;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    public PackServer(FurniturePlugin plugin, int port, PackResult pack) {
        this.plugin = plugin;
        this.port = port;
        this.pack = pack;
    }

    /** 端口被占用时顺延最多 10 个，返回实际监听的端口 */
    public int start() throws IOException {
        IOException last = null;
        for (int candidate = port; candidate < port + 10; candidate++) {
            try {
                server = HttpServer.create(new InetSocketAddress(candidate), 0);
                port = candidate;
                last = null;
                break;
            } catch (IOException ex) {
                last = ex;
                plugin.getLogger().warning("资源包端口 " + candidate + " 被占用，尝试下一个");
            }
        }
        if (server == null) {
            throw last == null ? new IOException("没有可用端口") : last;
        }
        server.createContext("/pack.zip", this::handlePack);
        server.createContext("/", this::handleIndex);
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "Furniture-PackServer");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        plugin.getLogger().info("资源包 HTTP 服务已启动，监听 0.0.0.0:" + port + " (/pack.zip)");
        return port;
    }

    public int port() {
        return port;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handlePack(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            boolean head = "HEAD".equalsIgnoreCase(method);
            if (!head && !"GET".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = pack.bytes();
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, head ? -1 : body.length);
            if (!head) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        byte[] body = ("Furniture resource pack\nsha1=" + pack.sha1Hex() + "\nsize=" + pack.size()
                + "\nurl=/pack.zip\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        exchange.close();
    }
}
