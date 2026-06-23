package com.kylian.alzheimer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kylian.alzheimer.data.ChatMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LlmClient {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static CompletableFuture<String> queryLlmAsync(
            String urlString,
            String modelName,
            String systemPrompt,
            List<ChatMessage> history,
            String userPrompt
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("model", modelName);
                json.addProperty("stream", false);

                JsonArray messagesArray = new JsonArray();

                // 1. Dynamic System prompt
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messagesArray.add(sysMsg);

                // 2. Chat history
                for (ChatMessage msg : history) {
                    JsonObject m = new JsonObject();
                    m.addProperty("role", msg.role());
                    m.addProperty("content", msg.content());
                    messagesArray.add(m);
                }

                // 3. Current user prompt
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userPrompt);
                messagesArray.add(userMsg);

                json.add("messages", messagesArray);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .timeout(Duration.ofSeconds(20))
                        .build();

                return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .thenApply(LlmClient::parseLlmResponse)
                        .exceptionally(ex -> "..." + " (Err: Connection failed. Is Ollama/LM Studio running?)")
                        .get();
            } catch (Exception e) {
                return "My head hurts... I couldn't think straight. (Err: " + e.getMessage() + ")";
            }
        });
    }

    private static String parseLlmResponse(String responseBody) {
        try {
            JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

            // Ollama /api/chat response parser
            if (responseJson.has("message")) {
                JsonObject messageObj = responseJson.getAsJsonObject("message");
                if (messageObj.has("content")) {
                    return messageObj.get("content").getAsString();
                }
            }

            // LM Studio / OpenAI /v1/chat/completions parser
            if (responseJson.has("choices")) {
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (!choices.isEmpty()) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("message")) {
                        JsonObject messageObj = choice.getAsJsonObject("message");
                        if (messageObj.has("content")) {
                            return messageObj.get("content").getAsString();
                        }
                    }
                }
            }

            return "I feel dizzy. (Err: Unexpected response format)";
        } catch (Exception e) {
            return "Huh? What was that? (Err: JSON parsing error)";
        }
    }
}
