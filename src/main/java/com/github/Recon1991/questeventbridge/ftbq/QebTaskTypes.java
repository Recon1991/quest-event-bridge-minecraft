package com.github.Recon1991.questeventbridge.ftbq;

import com.github.Recon1991.questeventbridge.QuestEventBridge;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.resources.ResourceLocation;

public interface QebTaskTypes {
    ResourceLocation TRADEICON = ResourceLocation.fromNamespaceAndPath(
            QuestEventBridge.MOD_ID, "textures/item/qeb_trade.png"
    );
    ResourceLocation FILTERICON = ResourceLocation.fromNamespaceAndPath(
            QuestEventBridge.MOD_ID, "textures/item/qeb_filter.png"
    );

    TaskType TRADE = TaskTypes.register(
            ResourceLocation.fromNamespaceAndPath(QuestEventBridge.MOD_ID, "trade_task"),
            QebTradeTask::new,
            () -> Icon.getIcon(TRADEICON)
    );

    static void init() {
        // Intentionally empty, forces classloading/registration.
    }
}