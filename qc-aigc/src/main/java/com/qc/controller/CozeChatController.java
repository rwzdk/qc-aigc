package com.qc.controller;

import com.qc.service.CozeChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("ai")
public class CozeChatController {

    // 注入Service
    private final CozeChatService cozeChatService;

    /**
     * 前端调用接口：流式对话,用于生成教案
     * 访问地址：http://localhost:8081/ai/stream?content=提问内容
     * @param content 前端传入的提问文本
     * @return 流式响应
     */
    @GetMapping("/stream/generate")
    public SseEmitter streamChat(@RequestParam String content) {
        return cozeChatService.streamChat(content);
    }

    /**
     * 获取完整合并内容（非流式），便于测试查看完整结果
     * 访问地址：http://localhost:8081/ai/merged/generate?content=提问内容
     * @param content 前端传入的提问文本
     * @return 合并后的完整回答
     */
    @GetMapping("/merged/generate")
    public String mergedChat(@RequestParam String content) {
        return cozeChatService.mergedChat(content);
    }
}
