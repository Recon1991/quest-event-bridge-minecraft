package com.github.Recon1991.questeventbridge.ftbq;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QebTaskData {
    private QebTaskData() {}

    /**
     * Returns profession IDs like "minecraft:librarian".
     * Client-side only (intended to be called from Task#fillConfigGroup).
     */
    public static List<String> professionIdsClient() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return List.of("minecraft:librarian");
        }

        RegistryAccess access = mc.level.registryAccess();
        Registry<VillagerProfession> reg = access.registryOrThrow(Registries.VILLAGER_PROFESSION);

        List<String> out = new ArrayList<>();
        for (ResourceLocation id : reg.keySet()) {
            out.add(id.toString());
        }

        out.sort(Comparator.naturalOrder());

        out.add("questeventbridge:_dummy");

        return out;
    }
}
