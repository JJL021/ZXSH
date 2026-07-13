package com.zxsh.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * 基于 Redis 的对话记忆存储，实现 LangChain4j 的 ChatMemoryStore 接口。
 * <p>
 * 存储结构：
 * <pre>
 *   Key:   "agent:memory:{sessionId}"
 *   Value: JSON 数组（ChatMessage 列表的 Jackson 序列化结果）
 *   TTL:   30 分钟（可调整 MAX_MEMORY_TTL_MINUTES）
 *   上限:  每次 store 自动修剪到 20 条（可调整 MAX_MESSAGES）
 * </pre>
 */
@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    /* Redis key 前缀 */
    private static final String KEY_PREFIX = "agent:memory:";

    /* 最大保留消息条数（控制 token 消耗） */
    private static final int MAX_MESSAGES = 20;

    /* 对话记忆 TTL（分钟） */
    private static final long MAX_MEMORY_TTL_MINUTES = 30;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Jackson 实例：注册 JavaTimeModule 以支持 LocalDateTime 序列化，
     * 忽略未知属性、不序列化 null 值，避免反序列化报错和膨胀。
     */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(NON_NULL);

    // ---- ChatMemoryStore 接口实现 ----

    //获取本会话的历史记录
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(buildKey(memoryId));
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> jsonList =
                    objectMapper.readValue(json,
                            new TypeReference<List<String>>() {});
            return jsonList.stream()
                    .map(ChatMessageDeserializer::messageFromJson)
                    .toList();
        } catch (JsonProcessingException e) {
            log.error("反序列化 ChatMessage 列表失败，将清除该 key。sessionId={}", memoryId, e);
            deleteMessages(memoryId);
            return Collections.emptyList();
        }
    }

    //每次新增消息后调用已保存新消息+历史消息
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 修剪到最大条数，取最近的消息（保留最新 MAX_MESSAGES 条）
        if (messages.size() > MAX_MESSAGES) {
            messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
        }
        try {
            List<String> jsonList = messages.stream()
                    .map(ChatMessageSerializer::messageToJson)
                    .toList();
            String json = objectMapper.writeValueAsString(jsonList);
            stringRedisTemplate.opsForValue().set(
                    buildKey(memoryId),
                    json,
                    MAX_MEMORY_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.error("序列化 ChatMessage 列表失败。sessionId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(buildKey(memoryId));
    }

    // ---- 内部工具方法 ----

    private String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
