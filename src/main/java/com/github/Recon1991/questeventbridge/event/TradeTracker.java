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
    private static final String BY_IN_KEY = "trades_by_input";
    private static final String BY_PROF_IN_KEY = "trades_by_profession_and_input";
    private static final String BY_IN_AMT_KEY = "trades_by_input_amount";
    private static final String BY_PROF_IN_AMT_KEY = "trades_by_profession_and_input_amount";
    private static final String BY_OUT_KEY = "trades_by_output";
    private static final String BY_PROF_OUT_KEY = "trades_by_profession_and_output";
    private static final String BY_OUT_AMT_KEY = "trades_by_output_amount";
    private static final String BY_PROF_OUT_AMT_KEY = "trades_by_profession_and_output_amount";

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AbstractVillager av = event.getAbstractVillager();
        if (!(av instanceof Villager villager)) return; // ignore wandering traders

        MerchantOffer offer = event.getMerchantOffer();
        if (offer == null) return;

        // ---------------------------------------------------
        // --- Capture all the offers (inputs + output) ---
        // ---------------------------------------------------
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB(); // may be empty
        ItemStack result = offer.getResult().copy();

        // --- Profession id ---
        ResourceLocation profRL = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        final String professionId = (profRL != null)
                ? profRL.toString()
                : villager.getVillagerData().getProfession().toString();
        final String profKey = professionId;

        // --- Output id ---
        ResourceLocation outKey = result.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(result.getItem());
        final String outId = (outKey != null) ? outKey.toString() : "";
        final int outCount = result.isEmpty() ? 0 : result.getCount();

        // --- Get QEB root tag (mutable) ---
        final CompoundTag pData = player.getPersistentData();
        final CompoundTag root = pData.getCompound(ROOT);

        // --- Track Inputs (counts + amounts) ---
        trackInput(root, profKey, offer.getCostA());
        trackInput(root, profKey, offer.getCostB());

        // --- Total trades ---
        int newTotal = root.getInt(TOTAL_KEY) + 1;
        root.putInt(TOTAL_KEY, newTotal);

        // --- Trades by profession ---
        CompoundTag byProf = root.getCompound(BY_PROF_KEY);
        byProf.putInt(profKey, byProf.getInt(profKey) + 1);
        root.put(BY_PROF_KEY, byProf);
        int newProfCount = byProf.getInt(profKey);

        // --- Trades by output (success count) ---
        CompoundTag byOut = root.getCompound(BY_OUT_KEY);
        if (!outId.isEmpty()) {
            byOut.putInt(outId, byOut.getInt(outId) + 1);
        }
        root.put(BY_OUT_KEY, byOut);

        // --- Trades by profession + output (success count) ---
        CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);
        CompoundTag profBucket = byProfOut.getCompound(profKey);
        if (!outId.isEmpty()) {
            profBucket.putInt(outId, profBucket.getInt(outId) + 1);
        }
        byProfOut.put(profKey, profBucket);
        root.put(BY_PROF_OUT_KEY, byProfOut);

        // --- Output amount (sum of stack counts) ---
        CompoundTag byOutAmt = root.getCompound(BY_OUT_AMT_KEY);
        if (!outId.isEmpty()) {
            byOutAmt.putInt(outId, byOutAmt.getInt(outId) + outCount);
        }
        root.put(BY_OUT_AMT_KEY, byOutAmt);

        // --- Profession + output amount ---
        CompoundTag byProfOutAmt = root.getCompound(BY_PROF_OUT_AMT_KEY);
        CompoundTag profAmtBucket = byProfOutAmt.getCompound(profKey);
        if (!outId.isEmpty()) {
            profAmtBucket.putInt(outId, profAmtBucket.getInt(outId) + outCount);
        }
        byProfOutAmt.put(profKey, profAmtBucket);
        root.put(BY_PROF_OUT_AMT_KEY, byProfOutAmt);

        // --- Last trade debug info ---
        root.putString("last_trade_output", outId);
        root.putString("last_trade_profession", professionId);

        // --- Write it back ---
        pData.put(ROOT, root);

        // --- Optional logging ---
        if (BridgeConfig.COMMON.logEvents.get()) {
            LOGGER.info("[QEB] Trade tracked: player={}, profession={}, output={}, profCount={}, total={}",
                    player.getGameProfile().getName(),
                    professionId,
                    outId,
                    newProfCount,
                    newTotal
            );

            if (!costA.isEmpty()) {
                LOGGER.info("[QEB]   Input A: {} x{}",
                        BuiltInRegistries.ITEM.getKey(costA.getItem()),
                        costA.getCount()
                );
            }
            if (!costB.isEmpty()) {
                LOGGER.info("[QEB]   Input B: {} x{}",
                        BuiltInRegistries.ITEM.getKey(costB.getItem()),
                        costB.getCount()
                );
            }
        }
    }

    private static void trackInput(CompoundTag root, String profKey, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return;

        ResourceLocation inKey = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (inKey == null) return;

        String inId = inKey.toString();
        int inCount = itemStack.getCount();

        // --- Trades by input (any profession) ---
        CompoundTag byIn = root.getCompound(BY_IN_KEY);
        root.put(BY_IN_KEY, byIn);
        byIn.putInt(inId, byIn.getInt(inId) + 1);

        // --- Input AMOUNT (sum of stack counts) ---
        CompoundTag byInAmt = root.getCompound(BY_IN_AMT_KEY);
        root.put(BY_IN_AMT_KEY, byInAmt);
        byInAmt.putInt(inId, byInAmt.getInt(inId) + inCount);

        // --- Trades by profession + input (trade-success count) ---
        CompoundTag byProfIn = root.getCompound(BY_PROF_IN_KEY);
        root.put(BY_PROF_IN_KEY, byProfIn);

        CompoundTag profInBucket = byProfIn.getCompound(profKey);
        byProfIn.put(profKey, profInBucket);

        profInBucket.putInt(inId, profInBucket.getInt(inId) + 1);

        // --- Trades by profession + input AMOUNT (sum of stack counts) ---
        CompoundTag byProfInAmt = root.getCompound(BY_PROF_IN_AMT_KEY);
        root.put(BY_PROF_IN_AMT_KEY, byProfInAmt);

        CompoundTag profInAmtBucket = byProfInAmt.getCompound(profKey);
        byProfInAmt.put(profKey, profInAmtBucket);

        profInAmtBucket.putInt(inId, profInAmtBucket.getInt(inId) + inCount);
    }
}
