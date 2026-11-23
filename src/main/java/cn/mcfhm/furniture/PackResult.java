package cn.mcfhm.furniture;

import java.io.File;

/**
 * 一次资源包构建的结果。
 *
 * @param file     落盘的 zip
 * @param bytes    zip 内容（发给客户端时直接从内存吐）
 * @param sha1     SHA-1 原始摘要（20 字节，CraftBukkit 的 setResourcePack 只接受这个长度）
 * @param sha1Hex  十六进制字符串，仅用于日志 / 页面展示
 */
public record PackResult(File file, byte[] bytes, byte[] sha1, String sha1Hex) {

    public int size() {
        return bytes.length;
    }
}
