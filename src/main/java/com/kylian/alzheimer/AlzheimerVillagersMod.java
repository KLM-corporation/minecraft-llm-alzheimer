package com.kylian.alzheimer;

import com.kylian.alzheimer.config.ModConfig;
import com.kylian.alzheimer.data.VillagerChatData;
import com.kylian.alzheimer.event.ModEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@Mod(AlzheimerVillagersMod.MOD_ID)
public class AlzheimerVillagersMod {
    public static final String MOD_ID = "alzheimervillagers";

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<VillagerChatData>> VILLAGER_CHAT =
            ATTACHMENT_TYPES.register("villager_chat", () ->
                    AttachmentType.builder(VillagerChatData::new)
                            .serialize(VillagerChatData.CODEC)
                            .build());

    public AlzheimerVillagersMod(IEventBus modEventBus, ModContainer container) {
        // Register config
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);

        // Register attachment types deferred register to mod event bus
        ATTACHMENT_TYPES.register(modEventBus);

        // Register event listener to the main NeoForge event bus
        NeoForge.EVENT_BUS.register(new ModEvents());
    }
}
