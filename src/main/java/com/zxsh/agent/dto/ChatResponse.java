package com.zxsh.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能客服聊天响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** AI 返回的自然语言回复 */
    private String response;

    /** 当前会话 ID */
    private String sessionId;
}
