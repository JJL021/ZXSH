package com.zxsh.agent.config;

import com.zxsh.agent.service.AgentService;
import com.zxsh.agent.tool.ReservationTool;
import com.zxsh.agent.tool.ShopTool;
import com.zxsh.agent.tool.VoucherTool;
import com.zxsh.agent.memory.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 智能客服配置。
 * <p>
 * ChatModel 由 {@code langchain4j-open-ai-spring-boot-starter} 自动配置，
 * 指向阿里云百炼 DashScope 的 OpenAI 兼容端点，无需手动注册。
 * <p>
 * 职责：
 * <ol>
 *   <li>注册 {@link ChatMemoryProvider} — 基于 {@link RedisChatMemoryStore} 提供带窗口的对话记忆</li>
 *   <li>通过 {@link AiServices} 构建 {@link AgentService} 代理实例并注册为 Spring Bean</li>
 * </ol>
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ShopTool shopTool;

    @Resource
    private VoucherTool voucherTool;

    @Resource
    private ReservationTool reservationTool;

    /**
     * 对话记忆提供器 —— 每次会话从 Redis 读取历史消息，限制窗口 20 条。
     * memoryId 即 Controller 传入的 sessionId。
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    /**
     * AgentService 代理 Bean，由 LangChain4j 动态生成实现类。
     * <p>
     * ChatModel 由 starter 自动注入（openAiChatModel），
     * 此处通过 AiServices 将 tools 注册到代理中。
     */
    @Bean
    public AgentService agentService(ChatModel model, ChatMemoryProvider memoryProvider) {
        return AiServices.builder(AgentService.class)
                .chatModel(model)
                .chatMemoryProvider(memoryProvider)
                .tools(shopTool, voucherTool, reservationTool)
                .build();
    }
}
