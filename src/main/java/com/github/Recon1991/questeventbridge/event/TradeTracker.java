package com.github.Recon1991.questeventbridge.event;

import com.github.Recon1991.questeventbridge.BridgeConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("QuestEventBridge");

    private static final String ROOT = "qeb";
    private static final String TOTAL_KEY = "trades_total";
    private static final String BY_PROF_KEY = "trades_by_profession";
    private static final String BY_OUT_KEY = "trades_by_output";
    private static final String BY_PROF_OUT_KEY = "trades_by_profession_and_output";

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AbstractVillager av = event.getAbstractVillager();
        if (!(av instanceof Villager villager)) return; // ignore wandering traders

        // Registry key for "minecraft:librarian" (or "mayview:librarian")
        ResourceLocation rl = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        final String professionId = (rl != null)
                ? rl.toString()
                : villager.getVillagerData().getProfession().toString();

        final String profKey = professionId; // prefix-free, namespaced

        // --- offer output capture ---
        MerchantOffer offer = event.getMerchantOffer();
        if (offer == null) return;
        ItemStack result = offer.getResult().copy();

        ResourceLocation outKey = BuiltInRegistries.ITEM.getKey(result.getItem());
        String outId = (outKey != null) ? outKey.toString() : "";

        final CompoundTag pData = player.getPersistentData();

        // Root compound
        final CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        // Total trades
        final int newTotal = root.getInt(TOTAL_KEY) + 1;
        root.putInt(TOTAL_KEY, newTotal);

        // Trades by profession
        final CompoundTag byProf = root.getCompound(BY_PROF_KEY);
        root.put(BY_PROF_KEY, byProf);

        final int newProfCount = byProf.getInt(profKey) + 1;
        byProf.putInt(profKey, newProfCount);

        // --- NEW: Trades by output (any profession) ---
        final CompoundTag byOut = root.getCompound(BY_OUT_KEY);
        root.put(BY_OUT_KEY, byOut);

        if (!outId.isEmpty()) {
            byOut.putInt(outId, byOut.getInt(outId) + 1);
        }

        // --- NEW: Trades by profession + output ---
        final CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);
        root.put(BY_PROF_OUT_KEY, byProfOut);

        final CompoundTag profBucket = byProfOut.getCompound(profKey);
        byProfOut.put(profKey, profBucket);

        if (!outId.isEmpty()) {
            profBucket.putInt(outId, profBucket.getInt(outId) + 1);
        }


        root.putString("last_trade_output", outId);
        root.putString("last_trade_profession", professionId); // optional but nice

        // Write back
        pData.put(ROOT, root);

        // Optional logging
        LOGGER.info("[QEB] Trade tracked: player={}, profession={}, output={}, profCount={}, total={}",
                player.getGameProfile().getName(),
                professionId,
                outId,
                newProfCount,
                newTotal
        );
    }
}
