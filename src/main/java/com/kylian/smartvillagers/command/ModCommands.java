package com.kylian.smartvillagers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.kylian.smartvillagers.SmartVillagersMod;
import com.kylian.smartvillagers.config.ModConfig;
import com.kylian.smartvillagers.data.VillagerChatData;
import com.kylian.smartvillagers.manager.ChatSessionManager;
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
        var commandNode = Commands.literal("smartvillager")
                .requires(source -> source.hasPermission(2)) // Level 2 is standard for OP/Admin
                .then(Commands.literal("help").executes(ModCommands::showHelp))
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
                );

        var buildNode = dispatcher.register(commandNode);

        // Register alias /sv redirecting to /smartvillager
        dispatcher.register(Commands.literal("sv")
                .requires(source -> source.hasPermission(2))
                .redirect(buildNode)
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§a[Smart Villagers Help] Commandes disponibles (alias /sv) :"), false);
        source.sendSuccess(() -> Component.literal("§e/sv help §7- Affiche ce message d'aide."), false);
        source.sendSuccess(() -> Component.literal("§e/sv config get §7- Affiche les configurations de LLM actuelles."), false);
        source.sendSuccess(() -> Component.literal("§e/sv config set llmUrl <url> §7- Modifie l'adresse de l'API LLM."), false);
        source.sendSuccess(() -> Component.literal("§e/sv config set modelName <model> §7- Modifie le nom du modèle LLM."), false);
        source.sendSuccess(() -> Component.literal("§e/sv config set maxHistorySize <2-50> §7- Définit le nombre de messages mémorisés."), false);
        source.sendSuccess(() -> Component.literal("§e/sv config set pricePenaltyFactor <0.0-5.0> §7- Définit le coefficient de hausse des prix."), false);
        source.sendSuccess(() -> Component.literal("§e/sv villager clear_memory §7- Efface la mémoire et l'agacement du villageois actif."), false);
        source.sendSuccess(() -> Component.literal("§e/sv villager set_annoyance <0-100> §7- Modifie l'agacement du villageois actif et met à jour ses prix."), false);
        return 1;
    }

    private static int getConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§a[Smart Villagers Config]"), false);
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
            VillagerChatData chatData = villager.getData(SmartVillagersMod.VILLAGER_CHAT);
            chatData.clearHistory();
            chatData.setAnnoyance(0);
            updateVillagerTrades(villager, 0); // Reset prices
            villager.setData(SmartVillagersMod.VILLAGER_CHAT, chatData);

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
            VillagerChatData chatData = villager.getData(SmartVillagersMod.VILLAGER_CHAT);
            chatData.setAnnoyance(amount);
            int finalAnnoyance = chatData.getAnnoyance();

            updateVillagerTrades(villager, finalAnnoyance);
            villager.setData(SmartVillagersMod.VILLAGER_CHAT, chatData);

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
