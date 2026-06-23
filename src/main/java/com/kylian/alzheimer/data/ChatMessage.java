package com.kylian.alzheimer.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ChatMessage(String role, String content) {
    public static final Codec<ChatMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("role").forGetter(ChatMessage::role),
        Codec.STRING.fieldOf("content").forGetter(ChatMessage::content)
    ).apply(instance, ChatMessage::new));
}
