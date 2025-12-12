package com.github.Recon1991.questeventbridge.event;

import com.github.Recon1991.questeventbridge.BridgeConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeTracker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("QuestEventBridge");

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AbstractVillager av = event.getAbstractVillager();
        if (!(av instanceof Villager villager)) return; // ignore wandering traders

        String prefix = BridgeConfig.COMMON.tradeKeyPrefix.get();
        String professionId =
                villager.getVillagerData().getProfession().toString(); // minecraft:librarian

        String key = prefix + professionId;

        var data = player.getPersistentData();
        int newValue = data.getInt(key) + 1;
        data.putInt(key, newValue + 1);

        if (BridgeConfig.COMMON.logEvents.get()) {
            LOGGER.info(
                    "[TradeTracker] {} traded with {} (count={})",
                    player.getGameProfile().getName(),
                    professionId,
                    newValue
            );
        }
    }
}