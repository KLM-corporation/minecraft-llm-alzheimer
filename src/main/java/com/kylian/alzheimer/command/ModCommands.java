package com.kylian.alzheimer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.kylian.alzheimer.AlzheimerVillagersMod;
import com.kylian.alzheimer.config.ModConfig;
import com.kylian.alzheimer.data.VillagerChatData;
import com.kylian.alzheimer.manager.ChatSessionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.UUID;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("alzheimer")
                .requires(source -> source.hasPermission(2)) // Level 2 is standard for OP/Admin
                .then(Commands.literal("config")
                        .then(Commands.literal("get").executes(ModCommands::getConfig))
                        .then(Commands.literal("set")
                                .then(Commands.literal("llmUrl")
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(context -> setConfigString(context, "llmUrl"))))
                                .then(Commands.literal("modelName")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> setConfigString(context, "modelName"))))
                                .then(Commands.literal("maxHistorySize")
                                        .then(Commands.argument("size", IntegerArgumentType.integer(2, 50))
                                                .executes(ModCommands::setMaxHistorySize)))
                                .then(Commands.literal("pricePenaltyFactor")
                                        .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.0, 5.0))
                                                .executes(ModCommands::setPricePenaltyFactor)))
                        )
                )
                .then(Commands.literal("villager")
                        .then(Commands.literal("clear_memory").executes(ModCommands::clearVillagerMemory))
                        .then(Commands.literal("set_annoyance")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                        .executes(ModCommands::setVillagerAnnoyance)))
                )
        );
    }

    private static int getConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§a[Alzheimer Config]"), false);
        source.sendSuccess(() -> Component.literal("§eLLM URL: §f" + ModConfig.INSTANCE.llmUrl.get()), false);
        source.sendSuccess(() -> Component.literal("§eModel: §f" + ModConfig.INSTANCE.modelName.get()), false);
        source.sendSuccess(() -> Component.literal("§eMax History: §f" + ModConfig.INSTANCE.maxHistorySize.get()), false);
        source.sendSuccess(() -> Component.literal("§ePrice Penalty: §f" + ModConfig.INSTANCE.pricePenaltyFactor.get()), false);
        return 1;
    }

    private static int setConfigString(CommandContext<CommandSourceStack> context, String configKey) {
        CommandSourceStack source = context.getSource();
        if (configKey.equals("llmUrl")) {
            String val = StringArgumentType.getString(context, "url");
            ModConfig.INSTANCE.llmUrl.set(val);
            source.sendSuccess(() -> Component.literal("§aLLM URL set to: " + val), true);
        } else if (configKey.equals("modelName")) {
            String val = StringArgumentType.getString(context, "name");
            ModConfig.INSTANCE.modelName.set(val);
            source.sendSuccess(() -> Component.literal("§aModel Name set to: " + val), true);
        }
        return 1;
    }

    private static int setMaxHistorySize(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        ModConfig.INSTANCE.maxHistorySize.set(size);
        context.getSource().sendSuccess(() -> Component.literal("§aMax History Size set to: " + size), true);
        return 1;
    }

    private static int setPricePenaltyFactor(CommandContext<CommandSourceStack> context) {
        double factor = DoubleArgumentType.getDouble(context, "factor");
        ModConfig.INSTANCE.pricePenaltyFactor.set(factor);
        context.getSource().sendSuccess(() -> Component.literal("§aPrice Penalty Factor set to: " + factor), true);
        return 1;
    }

    private static int clearVillagerMemory(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        Villager villager = getActiveVillager(player);
        if (villager != null) {
            VillagerChatData chatData = villager.getData(AlzheimerVillagersMod.VILLAGER_CHAT);
            chatData.clearHistory();
            chatData.setAnnoyance(0);
            updateVillagerTrades(villager, 0); // Reset prices
            villager.setData(AlzheimerVillagersMod.VILLAGER_CHAT, chatData);

            player.sendSystemMessage(Component.literal("§a[System] " + villager.getName().getString() + "'s memory and annoyance have been fully cleared."));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("§c[Error] You must be in an active conversation with a villager (right-click them) to use this command."));
            return 0;
        }
    }

    private static int setVillagerAnnoyance(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        int amount = IntegerArgumentType.getInteger(context, "amount");
        Villager villager = getActiveVillager(player);

        if (villager != null) {
            VillagerChatData chatData = villager.getData(AlzheimerVillagersMod.VILLAGER_CHAT);
            chatData.setAnnoyance(amount);
            int finalAnnoyance = chatData.getAnnoyance();

            updateVillagerTrades(villager, finalAnnoyance);
            villager.setData(AlzheimerVillagersMod.VILLAGER_CHAT, chatData);

            player.sendSystemMessage(Component.literal("§a[System] " + villager.getName().getString() + "'s annoyance set to " + finalAnnoyance + ". Prices updated."));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("§c[Error] You must be in an active conversation with a villager (right-click them) to use this command."));
            return 0;
        }
    }

    private static Villager getActiveVillager(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        if (ChatSessionManager.isPlayerInSession(playerUuid)) {
            UUID villagerUuid = ChatSessionManager.getTalkingVillager(playerUuid);
            Entity entity = player.serverLevel().getEntity(villagerUuid);
            if (entity instanceof Villager) {
                return (Villager) entity;
            }
        }
        return null;
    }

    private static void updateVillagerTrades(Villager villager, int annoyance) {
        double multiplier = ModConfig.INSTANCE.pricePenaltyFactor.get();
        int priceIncrease = (int) (annoyance * multiplier);

        for (MerchantOffer offer : villager.getOffers()) {
            offer.setSpecialPriceDiff(priceIncrease);
        }
    }
}
