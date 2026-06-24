package com.kylian.smartvillagers.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kylian.smartvillagers.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

public class VillagerChatData {
    private final List<ChatMessage> history;
    private int annoyance;
    private int confusionTurns;
    private transient boolean forgotThisTurn;

    public static final Codec<VillagerChatData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ChatMessage.CODEC.listOf().fieldOf("history").forGetter(VillagerChatData::getHistory),
        Codec.INT.fieldOf("annoyance").forGetter(VillagerChatData::getAnnoyance),
        Codec.INT.fieldOf("confusionTurns").forGetter(VillagerChatData::getConfusionTurns)
    ).apply(instance, VillagerChatData::new));

    public VillagerChatData() {
        this.history = new ArrayList<>();
        this.annoyance = 0;
        this.confusionTurns = 0;
        this.forgotThisTurn = false;
    }

    public VillagerChatData(List<ChatMessage> history, int annoyance, int confusionTurns) {
        this.history = new ArrayList<>(history);
        this.annoyance = annoyance;
        this.confusionTurns = confusionTurns;
        this.forgotThisTurn = false;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public int getAnnoyance() {
        return annoyance;
    }

    public void setAnnoyance(int annoyance) {
        this.annoyance = Math.max(0, Math.min(100, annoyance));
    }

    public int getConfusionTurns() {
        return confusionTurns;
    }

    public void setConfusionTurns(int confusionTurns) {
        this.confusionTurns = Math.max(0, confusionTurns);
    }

    public boolean hasForgotThisTurn() {
        boolean val = forgotThisTurn;
        forgotThisTurn = false; // Reset on read
        return val;
    }

    public void addMessage(String role, String content) {
        this.history.add(new ChatMessage(role, content));
        checkSlidingWindow();
    }

    public void checkSlidingWindow() {
        int limit = ModConfig.INSTANCE.maxHistorySize.get();
        boolean popped = false;
        while (history.size() > limit) {
            if (history.size() >= 2) {
                history.remove(0); // oldest user message
                history.remove(0); // oldest assistant response
                popped = true;
            } else {
                history.remove(0);
                popped = true;
            }
        }
        if (popped) {
            this.confusionTurns = 2; // Annoyance/Lore confusion starts
            this.forgotThisTurn = true;
        }
    }

    public void clearHistory() {
        this.history.clear();
        this.confusionTurns = 0;
        this.forgotThisTurn = false;
    }
}
