package com.github.Recon1991.questeventbridge.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;

public class QebTradeTask extends Task {

    // --- All the strings ---
    private static final String ROOT = "qeb";
    private static final String TOTAL_KEY = "trades_total";
    private static final String BY_PROF_KEY = "trades_by_profession";
    // --- Default ---
    private static final String DEFAULT_PROF = "minecraft:librarian";
    private static final String DEFAULT_ITEM = "minecraft:emerald";
    // --- Input counters ---
    private static final String BY_IN_KEY = "trades_by_input";
    private static final String BY_IN_AMT_KEY = "trades_by_input_amount";
    // --- Input Profession counters ---
    private static final String BY_PROF_IN_KEY = "trades_by_input";
    private static final String BY_PROF_IN_AMT_KEY = "trades_by_input_amount";
    // --- Output counters ---
    private static final String BY_OUT_KEY = "trades_by_output";
    private static final String BY_PROF_OUT_KEY = "trades_by_profession_and_output";
    // --- Output Profession counters ---
    private static final String BY_OUT_AMT_KEY = "trades_by_output_amount";
    private static final String BY_PROF_OUT_AMT_KEY = "trades_by_profession_and_output_amount";

    // Default counter amount
    // (required) is master count for trade count/amount
    private int required = 10;

    // whether to use (required) for task criteria
    private boolean useTotal = false;

    // Dropdown for (Profession) selection + manual dual-mode
    private boolean useDropdown = true;

    // Enable Wanted Item for Task
    private boolean useWantedItem = false;

    // Whether to use input (costA/costB) or output(result)
    private boolean wantedIsInput = false;

    // false = successful trade count (Default)
    // true  = item amount count - sum of items traded (Toggle)
    private boolean useWantedItemCountMode = false;

    // Dropdown for Profession Selector List
    private String professionDropdown = DEFAULT_PROF;

    // Input Field for Manual entry of Profession
    private String professionManual = DEFAULT_PROF;

    // Set wanted item ID
    private String wantedItem = DEFAULT_ITEM;       // resource id string

    public QebTradeTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return QebTaskTypes.TRADE;
    }

    @Override
    public long getMaxProgress() {
        return Math.max(1, required);
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 20;
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        long progress = Math.min(getMaxProgress(), readProgressFromPlayer(player));
        teamData.setProgress(this, progress);
    }

    private String effectiveProfession() {
        String p = useDropdown ? professionDropdown : professionManual;
        if (p == null) return DEFAULT_PROF;
        p = p.trim();
        return p.isEmpty() ? DEFAULT_PROF : p;
    }

    private String effectiveWantedItem() {
        if (!useWantedItem) return null;
        String s = wantedItem;
        if (s == null) return DEFAULT_ITEM;
        s = s.trim();
        return s.isEmpty() ? DEFAULT_ITEM : s;
    }

    private long readProgressFromPlayer(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT);

        String wanted = effectiveWantedItem(); // null if useWantedItem=false

        if (wanted == null) {
            if (useTotal) {
                return root.getInt(TOTAL_KEY);
            }
            return root.getCompound(BY_PROF_KEY).getInt(effectiveProfession());
        }

        // set prof to effectiveProfession from Dropdown/Manual
        String prof = effectiveProfession();

        // Total Successful Trade Count
        if (useTotal) {
            if (wantedIsInput) {
                return useWantedItemCountMode
                    ? root.getCompound(BY_IN_AMT_KEY).getInt(wanted)
                    : root.getCompound(BY_IN_KEY).getInt(wanted);
            } else {
                return useWantedItemCountMode
                    ? root.getCompound(BY_OUT_AMT_KEY).getInt(wanted)
                    : root.getCompound(BY_OUT_KEY).getInt(wanted);
            }
        }

        // Total Successful Trade Item + Profession Count
        if (wantedIsInput) {
            CompoundTag bucket = root.getCompound(BY_PROF_IN_KEY).getCompound(prof);
            CompoundTag bucketAmount = root.getCompound(BY_PROF_IN_AMT_KEY).getCompound(prof);

            return useWantedItemCountMode
                ? bucketAmount.getInt(wanted)
                : bucket.getInt(wanted);
        } else {
            CompoundTag bucket = root.getCompound(BY_PROF_OUT_KEY).getCompound(prof);
            CompoundTag bucketAmount = root.getCompound(BY_PROF_OUT_AMT_KEY).getCompound(prof);

            return useWantedItemCountMode
                    ? bucketAmount.getInt(wanted)
                    : bucket.getInt(wanted);
        }
    }

    @Override
    public void writeNetData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeInt(required);
        buffer.writeBoolean(useTotal);
        buffer.writeBoolean(useDropdown);
        buffer.writeUtf(professionDropdown, Short.MAX_VALUE);
        buffer.writeUtf(professionManual, Short.MAX_VALUE);

        buffer.writeBoolean(useWantedItem);
        buffer.writeUtf(wantedItem, Short.MAX_VALUE);

        buffer.writeBoolean(wantedIsInput);
        buffer.writeBoolean(useWantedItemCountMode);
    }

    @Override
    public void readNetData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        required = buffer.readInt();
        useTotal = buffer.readBoolean();
        useDropdown = buffer.readBoolean();
        professionDropdown = buffer.readUtf(Short.MAX_VALUE);
        professionManual = buffer.readUtf(Short.MAX_VALUE);

        useWantedItem = buffer.readBoolean();
        wantedItem = buffer.readUtf(Short.MAX_VALUE);

        wantedIsInput = buffer.readBoolean();
        useWantedItemCountMode = buffer.readBoolean();

        // sanitize
        if (required <= 0) required = 1;
        if (professionDropdown == null || professionDropdown.isBlank()) professionDropdown = DEFAULT_PROF;
        if (professionManual == null || professionManual.isBlank()) professionManual = DEFAULT_PROF;
        if (wantedItem == null || wantedItem.isBlank()) wantedItem = DEFAULT_ITEM;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);

        config.addBool("use_dropdown", useDropdown, v -> useDropdown = v, true)
                .setNameKey("qeb.task.trade.use_dropdown");

        // Build dropdown options from the registry (vanilla + modded)
        var ids = new ArrayList<ResourceLocation>(BuiltInRegistries.VILLAGER_PROFESSION.keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));

        // Default: prefer minecraft:librarian if present; otherwise first in the sorted list; otherwise fallback constant
        String defaultId = DEFAULT_PROF;
        if (!ids.isEmpty()) {
            boolean hasLibrarian = ids.contains(ResourceLocation.fromNamespaceAndPath("minecraft", "librarian"));
            defaultId = hasLibrarian ? DEFAULT_PROF : ids.get(0).toString();
        }

        // Ensure current selection is not blank
        if (professionDropdown == null || professionDropdown.isBlank()) {
            professionDropdown = defaultId;
        }

        NameMap<String> map = NameMap.of(defaultId, ids.stream().map(ResourceLocation::toString).toList())
                .id(s -> s)
                .name(s -> Component.literal(s)) // can prettify later
                .create();

        config.addEnum(
                "profession_dropdown",
                professionDropdown,
                v -> professionDropdown = v,
                map,
                defaultId
        ).setNameKey("qeb.task.trade.profession_dropdown");

        // Profession Manual Field
        config.addString("profession_manual", professionManual, v -> professionManual = v, DEFAULT_PROF)
                .setNameKey("qeb.task.trade.profession_manual");

        // ------------------------------------------------------------
        // --- Master Count for Trade Success or Trade Item Count ---
        // ------------------------------------------------------------
        config.addInt("required", required, v -> required = v, 10, 1, Integer.MAX_VALUE)
                .setNameKey("qeb.task.trade.required");

        // counts all villager trades (ignores profession filter)
        config.addBool("use_total", useTotal, v -> useTotal = v, false)
                .setNameKey("qeb.task.trade.use_total");

        // enables (Wanted Item) for task
        config.addBool("use_wanted_item", useWantedItem, v -> useWantedItem = v, false)
                .setNameKey("qeb.task.trade.use_wanted_item");

        // Wanted Item ID for task
        config.addString("wanted_item", wantedItem, v -> wantedItem = v, DEFAULT_ITEM)
                .setNameKey("qeb.task.trade.wanted_item");

        // If True, use Input Wanted Trade Item, else use Output Wanted Trade Item
        config.addBool("wanted_is_input", wantedIsInput, v -> wantedIsInput = v, false)
                .setNameKey("qeb.task.trade.wanted_is_input");

        // If True, Use Sum of (Wanted Item) for/from (Trade/Item Count)
        config.addBool("use_wanted_item_count", useWantedItemCountMode, v -> useWantedItemCountMode = v, false)
                .setNameKey("qeb.task.trade.use_wanted_item_count");
        }

    @Override
    public void writeData(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        super.writeData(nbt, provider);

        nbt.putInt("required", required);
        nbt.putBoolean("use_total", useTotal);

        nbt.putBoolean("use_dropdown", useDropdown);
        nbt.putString("profession_dropdown", professionDropdown);
        nbt.putString("profession_manual", professionManual);

        nbt.putBoolean("use_wanted_item", useWantedItem);
        nbt.putString("wanted_item", wantedItem);

        nbt.putBoolean("wanted_is_input", wantedIsInput);
        nbt.putBoolean("use_wanted_item_count", useWantedItemCountMode);
    }

    @Override
    public void readData(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        super.readData(nbt, provider);

        required = nbt.getInt("required");
        useTotal = nbt.getBoolean("use_total");

        useDropdown = nbt.contains("use_dropdown") ? nbt.getBoolean("use_dropdown") : true;

        professionDropdown = nbt.contains("profession_dropdown")
                ? nbt.getString("profession_dropdown")
                : DEFAULT_PROF;

        professionManual = nbt.contains("profession_manual")
                ? nbt.getString("profession_manual")
                : DEFAULT_PROF;

        useWantedItem = nbt.contains("use_wanted_item") && nbt.getBoolean("use_wanted_item");
        wantedItem = nbt.contains("wanted_item") ? nbt.getString("wanted_item") : DEFAULT_ITEM;

        wantedIsInput = nbt.contains("wanted_is_input") && nbt.getBoolean("wanted_is_input");
        useWantedItemCountMode = nbt.contains("use_wanted_item_count") && nbt.getBoolean("use_wanted_item_count");

        // sanitize
        if (required <= 0) required = 1;
        if (professionDropdown == null || professionDropdown.isBlank()) professionDropdown = DEFAULT_PROF;
        if (professionManual == null || professionManual.isBlank()) professionManual = DEFAULT_PROF;
        if (wantedItem == null || wantedItem.isBlank()) wantedItem = DEFAULT_ITEM;
        wantedItem = wantedItem.trim();
    }
}
