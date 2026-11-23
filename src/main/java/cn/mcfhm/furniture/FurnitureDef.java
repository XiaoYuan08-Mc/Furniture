package cn.mcfhm.furniture;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;

/**
 * 一个家具定义。
 *
 * @param id           家具 ID（furniture.yml 的键）
 * @param rawName      带 & 颜色代码的显示名
 * @param model        资源包中的 item model（命名空间:路径）
 * @param transform    ItemDisplay 的显示变换上下文，NONE 表示按方块比例渲染
 * @param hitboxWidth  单格交互碰撞箱宽度
 * @param hitboxHeight 交互碰撞箱高度
 * @param viewRange    显示实体可见范围倍率
 * @param material     家具物品的底子材质
 * @param scale        模型缩放（1.0 为原尺寸）
 * @param offsetY      模型垂直偏移（格，正数向上）
 * @param sizeWidth    占地宽度（格，模型局部 +x 方向）
 * @param sizeDepth    占地深度（格，模型局部 +z 方向）
 * @param seat         能否坐人
 * @param seatY        坐姿高度（相对锚点方块底部，格）
 * @param light        是否可开关灯（右键切换模型亮度）
 * @param storage      容器槽位数量（0 = 不能储物；9/18/27/36/54）
 * @param seats        同时能坐几个人（1 = 单人，沙发这类可以 2~3）
 * @param solid        是否生成碰撞实体（挡人不让穿过）
 * @param seatPoints   精确座位点（本地坐标，单位格；相对锚点方块中心），空则按占地范围均分
 */
public record FurnitureDef(
        String id,
        String rawName,
        NamespacedKey model,
        ItemDisplay.ItemDisplayTransform transform,
        float hitboxWidth,
        float hitboxHeight,
        float viewRange,
        Material material,
        float scale,
        float offsetY,
        int sizeWidth,
        int sizeDepth,
        boolean seat,
        float seatY,
        boolean light,
        int storage,
        int seats,
        boolean solid,
        java.util.List<double[]> seatPoints
) {

    public String name() {
        return Text.colorize(rawName);
    }

    /** 第 index 个人的座位点（本地格坐标 x,y,z），没有自定义点返回 null */
    public double[] seatPoint(int index) {
        if (seatPoints == null || seatPoints.isEmpty()) {
            return null;
        }
        return seatPoints.get(Math.floorMod(index, seatPoints.size()));
    }
}
