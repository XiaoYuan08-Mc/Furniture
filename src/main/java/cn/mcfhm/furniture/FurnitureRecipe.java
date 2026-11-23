package cn.mcfhm.furniture;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * 一条工作台合成配方。
 *
 * @param furnitureId 产出哪件家具
 * @param shaped      true = 有序（用 shape + key），false = 无序（用 shapeless）
 * @param shape       有序配方的 3 行字符图案
 * @param key         字符 → 材质
 * @param shapeless   无序配方的材料列表
 * @param amount      产出数量
 */
public record FurnitureRecipe(
        String furnitureId,
        boolean shaped,
        List<String> shape,
        Map<Character, Material> key,
        List<Material> shapeless,
        int amount
) {
}
