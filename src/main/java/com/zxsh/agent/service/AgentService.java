package com.zxsh.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 智能客服 Agent 接口（由 LangChain4j AiServices 动态代理实现）。
 * <p>
 * 不要手动实现此接口。LangChain4j 在启动时会根据 AgentConfig 中的配置，
 * 自动生成代理实例，负责：发送 prompt → 调用 LLM → 执行 Function Calling → 返回结果。
 * <p>
 * {@link SystemMessage} 定义 Agent 的人设和行为边界，
 * {@link MemoryId} 关联 Redis 中的历史对话，
 * {@link UserMessage} 注入当前用户消息。
 */
@SystemMessage("""
        你是智享生活智能客服助手，类似大众点评智能助手。你可以帮助用户：
        1. 按名称和区域查询店铺信息（地址、均价、评分、营业时间）
        2. 查询指定店铺的优惠券
        3. 查询用户已拥有的优惠券
        4. 预约到店（需提供姓名、电话、预约时间、商家名称）

        要求：友好专业，回答简短明了。店铺信息用编号列出，不要过度冗长。
        """)
public interface AgentService {

    /**
     * 处理用户消息并返回 AI 回复
     *
     * @param sessionId 会话 ID，用于关联 Redis 中的历史对话记忆
     * @param message   用户输入的自然语言
     * @return AI 的自然语言回复
     */
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
