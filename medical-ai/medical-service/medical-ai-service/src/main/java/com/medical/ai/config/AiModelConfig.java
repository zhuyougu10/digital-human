package com.medical.ai.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiModelConfig {

    @Bean
    @Primary
    public OpenAiChatModel deepSeekChatModel(
            @Value("${ai.deepseek.api-key}") String apiKey,
            @Value("${ai.deepseek.base-url}") String baseUrl,
            @Value("${ai.deepseek.model}") String model) {
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.7)
                .withMaxTokens(2048)
                .build();
        return new OpenAiChatModel(api, options);
    }

    @Bean("qwenChatModel")
    public OpenAiChatModel qwenChatModel(
            @Value("${ai.qwen.api-key}") String apiKey,
            @Value("${ai.qwen.base-url}") String baseUrl,
            @Value("${ai.qwen.model}") String model) {
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.7)
                .withMaxTokens(2048)
                .build();
        return new OpenAiChatModel(api, options);
    }
}
