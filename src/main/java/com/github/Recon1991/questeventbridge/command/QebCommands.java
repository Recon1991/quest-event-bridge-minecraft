package com.github.Recon1991.questeventbridge.command;

import com.github.Recon1991.questeventbridge.BridgeConfig;
import com.github.Recon1991.questeventbridge.QuestEventBridge;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class QebCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ROOT = "qeb";
    private static final String TOTAL_KEY = "trades_total";
    private static final String BY_PROF_KEY = "trades_by_profession";

    /** Reads permission level from config; falls back to 2 if config isn't available yet. */
    private static int permLevel() {
        try {
            return BridgeConfig.COMMON.commandPermissionLevel.get();
        } catch (Throwable t) {
            return 2;
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("qeb")
                        .requires(src -> src.hasPermission(permLevel()))
                        .then(tradesBuilder())
                        .then(resetTradesBuilder())
                        .then(setTradesBuilder())
        );
    }

    /* ---------------------------- */
    /* Command Builders */
    /* ---------------------------- */

    private static ArgumentBuilder<CommandSourceStack, ?> tradesBuilder() {
        return Commands.literal("trades")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayerOrException();

                    CompoundTag pData = player.getPersistentData();
                    CompoundTag root = pData.getCompound(ROOT);

                    int total = root.getInt(TOTAL_KEY);
                    CompoundTag byProf = root.getCompound(BY_PROF_KEY);

                    source.sendSuccess(() -> Component.literal("[QEB] trades_total = " + total), false);

                    if (byProf.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("[QEB] trades_by_profession is empty."), false);
                    } else {
                        source.sendSuccess(() -> Component.literal("[QEB] trades_by_profession:"), false);
                        for (String key : byProf.getAllKeys()) {
                            int count = byProf.getInt(key);
                            source.sendSuccess(() -> Component.literal(" - " + key + " = " + count), false);
                        }
                    }

                    LOGGER.debug("[{}] /qeb trades used by {}", QuestEventBridge.MOD_ID, player.getGameProfile().getName());
                    return 1;
                });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> resetTradesBuilder() {
        return Commands.literal("reset_trades")
                // /qeb reset_trades
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayerOrException();

                    resetAllTrades(player);

                    source.sendSuccess(() -> Component.literal("[QEB] Reset ALL trade counters (total + professions)."), false);
                    LOGGER.debug("[{}] /qeb reset_trades used by {}", QuestEventBridge.MOD_ID, player.getGameProfile().getName());
                    return 1;
                })
                // /qeb reset_trades <professionKey>
                .then(Commands.argument("professionKey", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            String key = StringArgumentType.getString(ctx, "professionKey").trim();

                            boolean existed = resetProfessionTrade(player, key);

                            if (existed) {
                                source.sendSuccess(() -> Component.literal("[QEB] Reset trades for profession key: " + key), false);
                            } else {
                                source.sendSuccess(() -> Component.literal("[QEB] No entry found for profession key: " + key), false);
                            }

                            LOGGER.debug("[{}] /qeb reset_trades {} used by {}", QuestEventBridge.MOD_ID, key, player.getGameProfile().getName());
                            return 1;
                        })
                );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> setTradesBuilder() {
        return Commands.literal("set_trades")
                .then(Commands.argument("professionKey", StringArgumentType.word())
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    String key = StringArgumentType.getString(ctx, "professionKey").trim();
                                    int value = IntegerArgumentType.getInteger(ctx, "value");

                                    int oldValue = setProfessionTrade(player, key, value);

                                    source.sendSuccess(() ->
                                                    Component.literal("[QEB] Set trades for " + key + ": " + oldValue + " -> " + value),
                                            false
                                    );

                                    LOGGER.debug("[{}] /qeb set_trades {} {} used by {}",
                                            QuestEventBridge.MOD_ID, key, value, player.getGameProfile().getName());
                                    return 1;
                                })
                        )
                )
                // Friendly usage if args missing
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("[QEB] Usage: /qeb set_trades <professionKey> <value>"));
                    return 0;
                });
    }

    /* ---------------------------- */
    /* Data Helpers */
    /* ---------------------------- */

    private static void resetAllTrades(ServerPlayer player) {
        CompoundTag pData = player.getPersistentData();

        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        root.putInt(TOTAL_KEY, 0);
        root.put(BY_PROF_KEY, new CompoundTag());

        pData.put(ROOT, root);
    }

    /**
     * @return true if the key existed and was removed, false otherwise
     */
    private static boolean resetProfessionTrade(ServerPlayer player, String professionKey) {
        CompoundTag pData = player.getPersistentData();

        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        CompoundTag byProf = root.getCompound(BY_PROF_KEY);
        root.put(BY_PROF_KEY, byProf);

        boolean existed = byProf.contains(professionKey);
        byProf.remove(professionKey);

        root.put(BY_PROF_KEY, byProf);
        pData.put(ROOT, root);

        return existed;
    }

    /**
     * Sets a profession trade count to an exact value.
     * Also adjusts trades_total by the delta so totals stay roughly consistent.
     *
     * @return the previous value for this profession key
     */
    private static int setProfessionTrade(ServerPlayer player, String professionKey, int newValue) {
        CompoundTag pData = player.getPersistentData();

        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        int total = root.getInt(TOTAL_KEY);

        CompoundTag byProf = root.getCompound(BY_PROF_KEY);
        root.put(BY_PROF_KEY, byProf);

        int oldValue = byProf.getInt(professionKey);

        byProf.putInt(professionKey, newValue);

        int delta = newValue - oldValue;
        int newTotal = total + delta;
        if (newTotal < 0) newTotal = 0;
        root.putInt(TOTAL_KEY, newTotal);

        root.put(BY_PROF_KEY, byProf);
        pData.put(ROOT, root);

        return oldValue;
    }
}
