package com.github.Recon1991.questeventbridge.command;

import com.github.Recon1991.questeventbridge.BridgeConfig;
import com.github.Recon1991.questeventbridge.QuestEventBridge;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QebCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ROOT = "qeb";
    private static final String TOTAL_KEY = "trades_total";
    private static final String BY_PROF_KEY = "trades_by_profession";
    private static final String BY_OUT_KEY = "trades_by_output";
    private static final String BY_PROF_OUT_KEY = "trades_by_profession_and_output";

    /** Reads permission level from config; falls back to 2 if config isn't available yet. **/
    private static int permLevel() {
        try {
            return BridgeConfig.COMMON.commandPermissionLevel.get();
        } catch (Throwable t) {
            return 2;
        }
    }

    private static List<String> keysSortedByIntDesc(CompoundTag tag) {
        var keys = new ArrayList<>(tag.getAllKeys());
        keys.sort(Comparator.comparingInt(tag::getInt).reversed());
        return keys;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("qeb")
                        .requires(src -> src.hasPermission(permLevel()))
                        .then(tradesBuilder())
                        .then(resetTradesBuilder())
                        .then(setTradesBuilder())
                        .then(setOutputBuilder())
                        .then(setProfOutputBuilder())
        );
    }

    /* ---------------------------- */
    /* Command Builders */
    /* ---------------------------- */

    private static ArgumentBuilder<CommandSourceStack, ?> tradesBuilder() {
        return Commands.literal("trades")
                // /qeb trades  (summary only)
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayerOrException();

                    CompoundTag root = player.getPersistentData().getCompound(ROOT);

                    int total = root.getInt(TOTAL_KEY);
                    CompoundTag byProf = root.getCompound(BY_PROF_KEY);

                    source.sendSuccess(() -> Component.literal("[QEB] trades_total = " + total), false);

                    if (byProf.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("[QEB] trades_by_profession is empty."), false);
                    } else {
                        source.sendSuccess(() -> Component.literal("[QEB] trades_by_profession (top 15):"), false);

                        int shown = 0;
                        for (String key : keysSortedByIntDesc(byProf)) {
                            source.sendSuccess(() -> Component.literal(" - " + key + " = " + byProf.getInt(key)), false);
                            if (++shown >= 15) break;
                        }

                        int remaining = byProf.getAllKeys().size() - shown;
                        if (remaining > 0) {
                            source.sendSuccess(() -> Component.literal(" ... (" + remaining + " more)"), false);
                        }
                    }

                    LOGGER.debug("[{}] /qeb trades used by {}", QuestEventBridge.MOD_ID, player.getGameProfile().getName());
                    return 1;
                })

                // /qeb trades output
                .then(Commands.literal("output")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            CompoundTag root = player.getPersistentData().getCompound(ROOT);
                            CompoundTag byOut = root.getCompound(BY_OUT_KEY);

                            if (byOut.isEmpty()) {
                                source.sendSuccess(() -> Component.literal("[QEB] trades_by_output is empty."), false);
                                return 1;
                            }

                            source.sendSuccess(() -> Component.literal("[QEB] trades_by_output (top 15):"), false);
                            int shown = 0;
                            for (String key : keysSortedByIntDesc(byOut)) {
                                source.sendSuccess(() -> Component.literal(" - " + key + " = " + byOut.getInt(key)), false);
                                if (++shown >= 15) break;
                            }

                            int remaining = byOut.getAllKeys().size() - shown;
                            if (remaining > 0) {
                                source.sendSuccess(() -> Component.literal(" ... (" + remaining + " more)"), false);
                                source.sendSuccess(() -> Component.literal(" Tip: /qeb trades output all"), false);
                            }

                            return 1;
                        })

                        // /qeb trades output all
                        .then(Commands.literal("all")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    CompoundTag root = player.getPersistentData().getCompound(ROOT);
                                    CompoundTag byOut = root.getCompound(BY_OUT_KEY);

                                    if (byOut.isEmpty()) {
                                        source.sendSuccess(() -> Component.literal("[QEB] trades_by_output is empty."), false);
                                        return 1;
                                    }

                                    source.sendSuccess(() -> Component.literal("[QEB] trades_by_output (all):"), false);
                                    for (String key : keysSortedByIntDesc(byOut)) {
                                        source.sendSuccess(() -> Component.literal(" - " + key + " = " + byOut.getInt(key)), false);
                                    }
                                    return 1;
                                })
                        )
                )

                // /qeb trades prof_output
                .then(Commands.literal("prof_output")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            CompoundTag root = player.getPersistentData().getCompound(ROOT);
                            CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);

                            if (byProfOut.isEmpty()) {
                                source.sendSuccess(() -> Component.literal("[QEB] trades_by_profession_and_output is empty."), false);
                                return 1;
                            }

                            source.sendSuccess(() -> Component.literal("[QEB] prof_output summary (distinct outputs per profession):"), false);
                            for (String prof : byProfOut.getAllKeys()) {
                                CompoundTag bucket = byProfOut.getCompound(prof);
                                int distinct = bucket.getAllKeys().size();
                                if (distinct > 0) {
                                    source.sendSuccess(() -> Component.literal(" - " + prof + " -> " + distinct + " outputs"), false);
                                }
                            }

                            source.sendSuccess(() -> Component.literal(" Tip: /qeb trades prof_output <professionId>"), false);
                            return 1;
                        })

                        // /qeb trades prof_output <professionId>
                        .then(Commands.argument("professionId", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    String prof = StringArgumentType.getString(ctx, "professionId").trim();

                                    CompoundTag root = player.getPersistentData().getCompound(ROOT);
                                    CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);
                                    CompoundTag bucket = byProfOut.getCompound(prof);

                                    if (bucket.isEmpty()) {
                                        source.sendSuccess(() -> Component.literal("[QEB] No prof_output entries for: " + prof), false);
                                        return 1;
                                    }

                                    source.sendSuccess(() -> Component.literal("[QEB] prof_output for " + prof + ":"), false);
                                    for (String outId : keysSortedByIntDesc(bucket)) {
                                        source.sendSuccess(() -> Component.literal(" - " + outId + " = " + bucket.getInt(outId)), false);
                                    }
                                    return 1;
                                })
                        )
                )

                // /qeb trades match <itemId>
                .then(Commands.literal("match")
                        .then(Commands.argument("itemId", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    String itemId = StringArgumentType.getString(ctx, "itemId").trim();

                                    CompoundTag root = player.getPersistentData().getCompound(ROOT);
                                    CompoundTag byOut = root.getCompound(BY_OUT_KEY);
                                    CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);

                                    int totalMatches = byOut.getInt(itemId);
                                    source.sendSuccess(() -> Component.literal("[QEB] match " + itemId + " (all professions) = " + totalMatches), false);

                                    if (byProfOut.isEmpty()) {
                                        source.sendSuccess(() -> Component.literal("[QEB] trades_by_profession_and_output is empty."), false);
                                        return 1;
                                    }

                                    // Show professions that have produced this item (top 15 by count)
                                    var profCounts = new ArrayList<String>();
                                    for (String prof : byProfOut.getAllKeys()) {
                                        CompoundTag bucket = byProfOut.getCompound(prof);
                                        int c = bucket.getInt(itemId);
                                        if (c > 0) profCounts.add(prof);
                                    }

                                    if (profCounts.isEmpty()) {
                                        source.sendSuccess(() -> Component.literal("[QEB] No professions have produced " + itemId + " yet."), false);
                                        return 1;
                                    }

                                    profCounts.sort((a, b) -> {
                                        int ca = byProfOut.getCompound(a).getInt(itemId);
                                        int cb = byProfOut.getCompound(b).getInt(itemId);
                                        return Integer.compare(cb, ca);
                                    });

                                    source.sendSuccess(() -> Component.literal("[QEB] professions producing " + itemId + " (top 15):"), false);
                                    int shown = 0;
                                    for (String prof : profCounts) {
                                        int c = byProfOut.getCompound(prof).getInt(itemId);
                                        source.sendSuccess(() -> Component.literal(" - " + prof + " = " + c), false);
                                        if (++shown >= 15) break;
                                    }

                                    int remaining = profCounts.size() - shown;
                                    if (remaining > 0) {
                                        source.sendSuccess(() -> Component.literal(" ... (" + remaining + " more)"), false);
                                    }

                                    return 1;
                                })
                        )
                );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> setOutputBuilder() {
        return Commands.literal("set_output")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            String raw = StringArgumentType.getString(ctx, "args").trim();
                            if (raw.isEmpty()) {
                                source.sendFailure(Component.literal("[QEB] Usage: /qeb set_output <itemId> <value>"));
                                return 0;
                            }

                            String[] parts = raw.split("\\s+");
                            if (parts.length < 2) {
                                source.sendFailure(Component.literal("[QEB] Usage: /qeb set_output <itemId> <value>"));
                                return 0;
                            }

                            String itemId = parts[0].trim();
                            String valueStr = parts[1].trim();

                            int value;
                            try {
                                value = Integer.parseInt(valueStr);
                                if (value < 0) value = 0;
                            } catch (NumberFormatException e) {
                                source.sendFailure(Component.literal("[QEB] Value must be a non-negative integer."));
                                return 0;
                            }

                            int oldValue = setOutputTrade(player, itemId, value);

                            source.sendSuccess(() ->
                                            Component.literal("[QEB] Set output count for " + itemId + ": " + oldValue + " -> " + value),
                                    false
                            );

                            LOGGER.debug("[{}] /qeb set_output {} {} used by {}",
                                    QuestEventBridge.MOD_ID, itemId, value, player.getGameProfile().getName());
                            return 1;
                        })
                )
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("[QEB] Usage: /qeb set_output <itemId> <value>"));
                    return 0;
                });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> setProfOutputBuilder() {
        return Commands.literal("set_prof_output")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            String raw = StringArgumentType.getString(ctx, "args").trim();
                            if (raw.isEmpty()) {
                                source.sendFailure(Component.literal("[QEB] Usage: /qeb set_prof_output <professionId> <itemId> <value>"));
                                return 0;
                            }

                            String[] parts = raw.split("\\s+");
                            if (parts.length < 3) {
                                source.sendFailure(Component.literal("[QEB] Usage: /qeb set_prof_output <professionId> <itemId> <value>"));
                                return 0;
                            }

                            String professionId = parts[0].trim();
                            String itemId = parts[1].trim();
                            String valueStr = parts[2].trim();

                            int value;
                            try {
                                value = Integer.parseInt(valueStr);
                                if (value < 0) value = 0;
                            } catch (NumberFormatException e) {
                                source.sendFailure(Component.literal("[QEB] Value must be a non-negative integer."));
                                return 0;
                            }

                            int oldValue = setProfessionOutputTrade(player, professionId, itemId, value);

                            source.sendSuccess(() ->
                                            Component.literal("[QEB] Set prof_output count for " + professionId + " -> " + itemId + ": " + oldValue + " -> " + value),
                                    false
                            );

                            LOGGER.debug("[{}] /qeb set_prof_output {} {} {} used by {}",
                                    QuestEventBridge.MOD_ID, professionId, itemId, value, player.getGameProfile().getName());
                            return 1;
                        })
                )
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("[QEB] Usage: /qeb set_prof_output <professionId> <itemId> <value>"));
                    return 0;
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
                // /qeb reset_trades <professionId>
                .then(Commands.argument("professionId", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            String id = StringArgumentType.getString(ctx, "professionId").trim();

                            boolean existed = resetProfessionTrade(player, id);

                            if (existed) {
                                source.sendSuccess(() -> Component.literal("[QEB] Reset trades for profession: " + id), false);
                            } else {
                                source.sendSuccess(() -> Component.literal("[QEB] No entry found for profession: " + id), false);
                            }

                            LOGGER.debug("[{}] /qeb reset_trades {} used by {}", QuestEventBridge.MOD_ID, id, player.getGameProfile().getName());
                            return 1;
                        })
                );
    }

    /**
     * /qeb set_trades <professionId> <value>
     * We use greedyString and parse the last token as an int so resource IDs like "minecraft:librarian"
     * always work (and we avoid Brigadier "word" quirks around ':').
     */
    private static ArgumentBuilder<CommandSourceStack, ?> setTradesBuilder() {
        return Commands.literal("set_trades")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            String raw = StringArgumentType.getString(ctx, "args").trim();
                            if (raw.isEmpty()) {
                                source.sendFailure(Component.literal("[QEB] Usage: /qeb set_trades <professionId> <value>"));
                                return 0;
                            }

                            String[] parts = raw.split("\\s+");
                            if (parts.length < 2) {
                                source.sendFailure(Component.literal("[QEB] Usage: /qeb set_trades <professionId> <value>"));
                                return 0;
                            }

                            String professionId = parts[0].trim();
                            String valueStr = parts[1].trim();

                            int value;
                            try {
                                value = Integer.parseInt(valueStr);
                                if (value < 0) value = 0;
                            } catch (NumberFormatException e) {
                                source.sendFailure(Component.literal("[QEB] Value must be a non-negative integer."));
                                return 0;
                            }

                            int oldValue = setProfessionTrade(player, professionId, value);
                            final int finalValue = value;

                            source.sendSuccess(() ->
                                            Component.literal("[QEB] Set trades for " + professionId + ": " + oldValue + " -> " + finalValue),
                                    false
                            );

                            LOGGER.debug("[{}] /qeb set_trades {} {} used by {}",
                                    QuestEventBridge.MOD_ID, professionId, value, player.getGameProfile().getName());
                            return 1;
                        })
                )
                // Friendly usage if args missing
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("[QEB] Usage: /qeb set_trades <professionId> <value>"));
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
        root.put(BY_OUT_KEY, new CompoundTag());
        root.put(BY_PROF_OUT_KEY, new CompoundTag());

        pData.put(ROOT, root);
    }

    /**
     * @return true if the id existed and was removed, false otherwise
     */
    private static boolean resetProfessionTrade(ServerPlayer player, String professionId) {
        CompoundTag pData = player.getPersistentData();

        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        CompoundTag byProf = root.getCompound(BY_PROF_KEY);
        root.put(BY_PROF_KEY, byProf);

        boolean existed = byProf.contains(professionId);
        byProf.remove(professionId);

        root.put(BY_PROF_KEY, byProf);
        pData.put(ROOT, root);

        return existed;
    }

    /**
     * Sets a profession trade count to an exact value.
     * Also adjusts trades_total by the delta so totals stay roughly consistent.
     *
     * @return the previous value for this profession id
     */
    private static int setProfessionTrade(ServerPlayer player, String professionId, int newValue) {
        CompoundTag pData = player.getPersistentData();

        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        int total = root.getInt(TOTAL_KEY);

        CompoundTag byProf = root.getCompound(BY_PROF_KEY);
        root.put(BY_PROF_KEY, byProf);

        int oldValue = byProf.getInt(professionId);

        byProf.putInt(professionId, newValue);

        int delta = newValue - oldValue;
        int newTotal = total + delta;
        if (newTotal < 0) newTotal = 0;
        root.putInt(TOTAL_KEY, newTotal);

        root.put(BY_PROF_KEY, byProf);
        pData.put(ROOT, root);

        return oldValue;
    }

    private static boolean resetOutputTrade(ServerPlayer player, String itemId) {
        CompoundTag pData = player.getPersistentData();
        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        CompoundTag byOut = root.getCompound(BY_OUT_KEY);
        root.put(BY_OUT_KEY, byOut);

        boolean existed = byOut.contains(itemId);
        byOut.remove(itemId);

        root.put(BY_OUT_KEY, byOut);
        pData.put(ROOT, root);
        return existed;
    }

    private static boolean resetProfessionOutputTrade(ServerPlayer player, String professionId, String itemId) {
        CompoundTag pData = player.getPersistentData();
        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);
        root.put(BY_PROF_OUT_KEY, byProfOut);

        CompoundTag bucket = byProfOut.getCompound(professionId);
        byProfOut.put(professionId, bucket);

        boolean existed = bucket.contains(itemId);
        bucket.remove(itemId);

        byProfOut.put(professionId, bucket);
        root.put(BY_PROF_OUT_KEY, byProfOut);
        pData.put(ROOT, root);
        return existed;
    }

    private static int setOutputTrade(ServerPlayer player, String itemId, int newValue) {
        CompoundTag pData = player.getPersistentData();
        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        CompoundTag byOut = root.getCompound(BY_OUT_KEY);
        root.put(BY_OUT_KEY, byOut);

        int oldValue = byOut.getInt(itemId);
        byOut.putInt(itemId, newValue);

        root.put(BY_OUT_KEY, byOut);
        pData.put(ROOT, root);
        return oldValue;
    }

    private static int setProfessionOutputTrade(ServerPlayer player, String professionId, String itemId, int newValue) {
        CompoundTag pData = player.getPersistentData();
        CompoundTag root = pData.getCompound(ROOT);
        pData.put(ROOT, root);

        CompoundTag byProfOut = root.getCompound(BY_PROF_OUT_KEY);
        root.put(BY_PROF_OUT_KEY, byProfOut);

        CompoundTag bucket = byProfOut.getCompound(professionId);
        byProfOut.put(professionId, bucket);

        int oldValue = bucket.getInt(itemId);
        bucket.putInt(itemId, newValue);

        byProfOut.put(professionId, bucket);
        root.put(BY_PROF_OUT_KEY, byProfOut);
        pData.put(ROOT, root);
        return oldValue;
    }
}