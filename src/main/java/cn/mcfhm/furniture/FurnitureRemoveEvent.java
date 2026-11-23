package cn.mcfhm.furniture;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 家具被拆除时触发（命令拆除 / 左键拆除都会触发），供座位、容器等子系统做清理。
 * 事件只是通知，不能取消。
 */
public final class FurnitureRemoveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlacedFurniture record;

    public FurnitureRemoveEvent(PlacedFurniture record) {
        this.record = record;
    }

    public PlacedFurniture record() {
        return record;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
