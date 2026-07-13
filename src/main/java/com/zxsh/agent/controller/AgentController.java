package com.zxsh.agent.controller;

import com.zxsh.agent.dto.ChatRequest;
import com.zxsh.agent.dto.ChatResponse;
import com.zxsh.agent.service.AgentService;
import com.zxsh.dto.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 智能客服聊天接口。
 * <p>
 * 前端使用方式：
 * <ol>
 *   <li>首次进入客服页面时，前端生成一个 UUID 作为 sessionId</li>
 *   <li>后续每轮对话都带上同一个 sessionId，后端通过 Redis 维护上下文记忆</li>
 *   <li>若 sessionId 为空，后端自动生成</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentService agentService;

    /**
     * 发送消息给 AI 客服
     *
     * @param request {@code sessionId}（可选，首次为空则自动生成）和 {@code message}
     * @return AI 回复 + 当前 sessionId
     */
    @PostMapping("/chat")
    public Result chat(@RequestBody ChatRequest request) {
        // 首次请求时前端传空 sessionId，后端生成并返回
        String sessionId = (request.getSessionId() == null || request.getSessionId().isEmpty())
                ? UUID.randomUUID().toString()
                : request.getSessionId();

        log.info("AgentController: sessionId={}, message={}", sessionId, request.getMessage());

        String response = agentService.chat(sessionId, request.getMessage());

        log.info("AgentController: sessionId={}, response预览={}", sessionId,
                response.length() > 200 ? response.substring(0, 200) + "..." : response);

        return Result.ok(new ChatResponse(response, sessionId));
    }
}
