package com.kylian.alzheimer.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfig INSTANCE;

    public final ModConfigSpec.ConfigValue<String> llmUrl;
    public final ModConfigSpec.ConfigValue<String> modelName;
    public final ModConfigSpec.IntValue maxHistorySize;
    public final ModConfigSpec.DoubleValue pricePenaltyFactor;

    static {
        Pair<ModConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ModConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public ModConfig(ModConfigSpec.Builder builder) {
        builder.push("llm");
        llmUrl = builder.comment("The API endpoint for your local/remote LLM (e.g. Ollama or LM Studio chat endpoint).")
                .define("llmUrl", "http://localhost:11434/api/chat");
        modelName = builder.comment("The name of the model to use (e.g. llama3, mistral, gemma).")
                .define("modelName", "llama3");
        builder.pop();

        builder.push("memory");
        maxHistorySize = builder.comment("The maximum number of recent messages kept in memory (sliding window).")
                .defineInRange("maxHistorySize", 8, 2, 50);
        builder.pop();

        builder.push("gameplay");
        pricePenaltyFactor = builder.comment("Price markup coefficient per annoyance point (e.g., price increases by annoyance * pricePenaltyFactor).")
                .defineInRange("pricePenaltyFactor", 0.1, 0.0, 5.0);
        builder.pop();
    }
}
