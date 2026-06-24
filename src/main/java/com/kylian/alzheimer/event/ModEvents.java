package com.kylian.alzheimer.event;

import com.kylian.alzheimer.AlzheimerVillagersMod;
import com.kylian.alzheimer.config.ModConfig;
import com.kylian.alzheimer.data.VillagerChatData;
import com.kylian.alzheimer.llm.LlmClient;
import com.kylian.alzheimer.manager.ChatSessionManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModEvents {
    private int tickCounter = 0;

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }

        // Only handle on server side and for main hand interaction to avoid double triggers
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();

        // Conversation mode is triggered by right-clicking with an empty hand
        if (player.getItemInHand(event.getHand()).isEmpty()) {
            event.setCanceled(true); // Stop the trading GUI from opening

            UUID playerUuid = player.getUUID();
            UUID villagerUuid = villager.getUUID();

            if (ChatSessionManager.isPlayerInSession(playerUuid)) {
                UUID activeVillagerUuid = ChatSessionManager.getTalkingVillager(playerUuid);
                if (activeVillagerUuid.equals(villagerUuid)) {
                    // Clicking the same villager twice ends the conversation
                    ChatSessionManager.endSession(playerUuid);
                    player.sendSystemMessage(Component.literal("§c[System] Conversation with " + villager.getName().getString() + " ended."));
                    player.displayClientMessage(Component.literal("Conversation ended"), true);
                } else {
                    // Clicking another villager switches session to the new one
                    ChatSessionManager.endSession(playerUuid);
                    ChatSessionManager.startSession(playerUuid, villagerUuid);
                    player.sendSystemMessage(Component.literal("§a[System] Talking to " + villager.getName().getString() + ". Type in chat to converse. Type 'exit' to leave."));
                    player.displayClientMessage(Component.literal("Talking to " + villager.getName().getString()), true);
                }
            } else {
                // Open new session
                ChatSessionManager.startSession(playerUuid, villagerUuid);
                player.sendSystemMessage(Component.literal("§a[System] Talking to " + villager.getName().getString() + ". Type in chat to converse. Type 'exit' to leave."));
                player.displayClientMessage(Component.literal("Talking to " + villager.getName().getString()), true);
            }
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID playerUuid = player.getUUID();

        if (ChatSessionManager.isPlayerInSession(playerUuid)) {
            UUID villagerUuid = ChatSessionManager.getTalkingVillager(playerUuid);
            ServerLevel level = player.serverLevel();
            net.minecraft.world.entity.Entity entity = level.getEntity(villagerUuid);

            if (entity instanceof Villager villager && villager.isAlive()) {
                event.setCanceled(true); // Don't broadcast the player's LLM prompts to the whole server

                String rawText = event.getRawText();
                player.sendSystemMessage(Component.literal("§7<You> " + rawText));

                // Handlers for manual exit
                if (rawText.equalsIgnoreCase("exit") || rawText.equalsIgnoreCase("bye") || rawText.equalsIgnoreCase("quitter")) {
                    ChatSessionManager.endSession(playerUuid);
                    player.sendSystemMessage(Component.literal("§c[System] Conversation with " + villager.getName().getString() + " ended."));
                    return;
                }

                // Show processing indicator
                player.displayClientMessage(Component.literal("§e" + villager.getName().getString() + " is thinking..."), true);

                VillagerChatData chatData = villager.getData(AlzheimerVillagersMod.VILLAGER_CHAT);
                String professionName = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath();

                // Build System Prompt
                StringBuilder systemPrompt = new StringBuilder();
                systemPrompt.append("You are a Minecraft villager named ").append(villager.getName().getString())
                        .append(". Your profession is: ").append(professionName)
                        .append(". Speak in a style fitting this profession (e.g. librarians are smart but forgetful, farmers talk about crops, nitwits are goofy). ")
                        .append("Live in a Minecraft environment. Respond to the player's message in their language. ")
                        .append("Keep responses short (1-3 sentences) to fit nicely in chat. ")
                        .append("If the player is rude, hits you, insults you, or makes you angry, append '[ANGRY]' at the end of your message. ")
                        .append("If the player is nice, friendly, or helps you, append '[FRIENDLY]' at the end of your message. ")
                        .append("Otherwise, append '[NEUTRAL]' at the end of your message.");

                // Add trades to prompt
                var offers = villager.getOffers();
                if (!offers.isEmpty()) {
                    systemPrompt.append(" Here are your current trading offers (what you buy and sell):\n");
                    for (MerchantOffer offer : offers) {
                        ItemStack costA = offer.getBaseCostA();
                        ItemStack costB = offer.getCostB();
                        ItemStack result = offer.getResult();
                        
                        systemPrompt.append("- Player gives: ").append(costA.getCount()).append(" ").append(costA.getHoverName().getString());
                        if (costB != null && !costB.isEmpty()) {
                            systemPrompt.append(" + ").append(costB.getCount()).append(" ").append(costB.getHoverName().getString());
                        }
                        systemPrompt.append(" -> You give: ").append(result.getCount()).append(" ").append(result.getHoverName().getString()).append("\n");
                    }
                }

                // Alzheimer prompt injection
                if (chatData.getConfusionTurns() > 0) {
                    systemPrompt.append(" IMPORTANT LORE DIRECTION: You recently suffered a sudden memory lapse (Alzheimer scenario). ")
                            .append("You feel slightly confused or disoriented about what was just said previously, though you still remember who you are. ")
                            .append("Mention your confusion, disorientation, or a slight headache naturally in your response.");
                }

                String url = ModConfig.INSTANCE.llmUrl.get();
                String model = ModConfig.INSTANCE.modelName.get();

                LlmClient.queryLlmAsync(url, model, systemPrompt.toString(), chatData.getHistory(), rawText)
                        .thenAcceptAsync(response -> {
                            // Ensure thread safety by executing inside the server thread
                            player.server.execute(() -> {
                                if (!villager.isAlive()) return;

                                // Decrement confusion turns if active
                                if (chatData.getConfusionTurns() > 0) {
                                    chatData.setConfusionTurns(chatData.getConfusionTurns() - 1);
                                }

                                String processedResponse = response;
                                int annoyance = chatData.getAnnoyance();

                                // Process sentiment tags
                                if (response.contains("[ANGRY]")) {
                                    annoyance += 20;
                                    processedResponse = processedResponse.replace("[ANGRY]", "");
                                } else if (response.contains("[FRIENDLY]")) {
                                    annoyance -= 10;
                                    processedResponse = processedResponse.replace("[FRIENDLY]", "");
                                }
                                processedResponse = processedResponse.replace("[NEUTRAL]", "").trim();

                                chatData.setAnnoyance(annoyance);
                                int finalAnnoyance = chatData.getAnnoyance();

                                // Add turns to history (which triggers sliding window check inside data structure)
                                chatData.addMessage(rawText, processedResponse);

                                // If memory was wiped due to sliding window, let player know
                                if (chatData.hasForgotThisTurn()) {
                                    player.sendSystemMessage(Component.literal("§d[Lore] " + villager.getName().getString() + " looks blankly at you. They seem to have forgotten older parts of your talk..."));
                                }

                                // Output villager response
                                player.sendSystemMessage(Component.literal("§6<" + villager.getName().getString() + "> §f" + processedResponse));
                                player.displayClientMessage(Component.literal(""), true); // Clear action bar

                                // Update prices based on new annoyance
                                updateVillagerTrades(villager, finalAnnoyance);

                                // Save updated attachment back to entity
                                villager.setData(AlzheimerVillagersMod.VILLAGER_CHAT, chatData);
                            });
                        }, player.server);
            } else {
                ChatSessionManager.endSession(playerUuid);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        // Check if attacker is a player
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            VillagerChatData chatData = villager.getData(AlzheimerVillagersMod.VILLAGER_CHAT);
            int newAnnoyance = chatData.getAnnoyance() + 40;
            chatData.setAnnoyance(newAnnoyance);
            int finalAnnoyance = chatData.getAnnoyance();

            // Add hostile context to LLM memory
            chatData.addMessage("System Alert", "Player attacked you!");

            updateVillagerTrades(villager, finalAnnoyance);
            villager.setData(AlzheimerVillagersMod.VILLAGER_CHAT, chatData);

            player.sendSystemMessage(Component.literal("§c<" + villager.getName().getString() + "> Aïe ! Pourquoi m'as-tu frappé ?! (Annoyance: " + finalAnnoyance + "/100)"));
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 20 == 0) { // Check once every second
            List<UUID> sessionsToClose = new ArrayList<>();

            ChatSessionManager.getActiveSessions().forEach((playerUuid, villagerUuid) -> {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerUuid);
                if (player == null) {
                    sessionsToClose.add(playerUuid);
                    return;
                }

                net.minecraft.world.entity.Entity entity = player.serverLevel().getEntity(villagerUuid);
                if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                    sessionsToClose.add(playerUuid);
                    player.sendSystemMessage(Component.literal("§c[System] Conversation partner disappeared."));
                    return;
                }

                // Check distance (8 blocks max)
                if (player.distanceToSqr(villager) > 64.0) { // 8^2 = 64
                    sessionsToClose.add(playerUuid);
                    player.sendSystemMessage(Component.literal("§c[System] You walked too far away from " + villager.getName().getString() + "."));
                }
            });

            for (UUID uuid : sessionsToClose) {
                ChatSessionManager.endSession(uuid);
            }
        }
    }

    private void updateVillagerTrades(Villager villager, int annoyance) {
        double multiplier = ModConfig.INSTANCE.pricePenaltyFactor.get();
        int priceIncrease = (int) (annoyance * multiplier);

        // Adjust prices of all currently unlocked trades
        for (MerchantOffer offer : villager.getOffers()) {
            offer.setSpecialPriceDiff(priceIncrease);
        }
    }
}
