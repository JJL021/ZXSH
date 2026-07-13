package com.zxsh.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能客服聊天请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 会话 ID（前端生成 UUID，同一会话复用此 ID 以保留上下文记忆） */
    private String sessionId;

    /** 用户输入的自然语言消息 */
    private String message;
}
