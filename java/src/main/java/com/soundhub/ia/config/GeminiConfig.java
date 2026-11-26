package com.soundhub.ia.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Modelo de chat usado pelo modulo de IA.
 *
 * O Gemini expoe um endpoint compativel com a API da OpenAI, entao o mesmo
 * {@link OpenAiChatModel} do LangChain4j serve: so mudam a URL, a chave e o modelo.
 *
 * O bean so existe quando GEMINI_API_KEY esta definida. Sem a chave a aplicacao sobe
 * normalmente (e os testes rodam sem rede); o {@code IaService} responde 503.
 */
@Configuration
@ConditionalOnExpression("!'${ia.gemini.api-key:}'.isBlank()")
public class GeminiConfig {

    @Bean
    public ChatModel chatModel(@Value("${ia.gemini.api-key}") String apiKey,
                               @Value("${ia.gemini.base-url}") String baseUrl,
                               @Value("${ia.gemini.modelo}") String modelo) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelo)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .build();
    }
}
