package com.github.Recon1991.questeventbridge;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class BridgeConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common {

        public final ModConfigSpec.ConfigValue<String> tradeKeyPrefix;
        public final ModConfigSpec.BooleanValue logEvents;

        Common(ModConfigSpec.Builder builder) {

            builder.comment(
                    "===============================================",
                    "  Quest Event Bridge — Common Configuration",
                    "",
                    "  This file controls how gameplay events are",
                    "  translated into persistent player data for",
                    "  use by FTB Quests and other systems.",
                    "",
                    "==============================================="
            ).push("trade_tracking");

            tradeKeyPrefix = builder
                    .comment(
                            "NBT key prefix used when tracking villager trades.",
                            "",
                            "Format:",
                            "  <prefix><profession_id>",
                            "",
                            "Example:",
                            "  mayview.trade.minecraft:librarian",
                            "",
                            "Changing this will NOT reset existing data automatically.",
                            "If you change it, old values will remain under the old prefix.",
                            "",
                            "Tachikoma wonders...",
                            "Do unused NBT keys dream of electric garbage collection?"
                    )
                    .define("trade_key_prefix", "mayview.trade.");

            builder.pop();

            /* --------------------------------------------- */
            builder.push("debug");

            logEvents = builder
                    .comment(
                            "Enable verbose logging for captured events.",
                            "",
                            "When enabled, the bridge will log each",
                            "captured event and its resolved data.",
                            "",
                            "Warning:",
                            "This can get noisy on active servers.",
                            "",
                            "Tachikoma warning:",
                            "Too much data can be just as blinding as too little."
                    )
                    .define("log_events", false);

            builder.pop();
        }
    }
}
