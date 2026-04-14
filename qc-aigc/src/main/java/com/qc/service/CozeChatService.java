package com.qc.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CozeChatService {
    /**
     * 流式对话，动态传入用户提问内容
     * @param content 前端传入的提问文本
     * @return SseEmitter 流式响应
     */
    SseEmitter streamChat(String content);

    /**
     * 非流式获取完整合并内容
     * @param content 前端传入的提问文本
     * @return 合并后的完整回答
     */
    String mergedChat(String content);
}
