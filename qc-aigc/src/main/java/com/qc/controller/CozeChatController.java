package com.qc.controller;

import com.qc.service.CozeChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("ai")
public class CozeChatController {

    private final CozeChatService cozeChatService;

    /**
     * 流式多模态对话接口（后端全自动记忆）
     * 访问地址：http://localhost:8081/ai/stream/generate
     */
    @PostMapping(value = "/stream/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter streamChat(@RequestPart("content") String content,
                                 @RequestPart(value = "userId", required = false) String userId,
                                 @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return cozeChatService.streamChat(content, userId, files);
    }

    /**
     * 非流式多模态对话接口（后端全自动记忆）
     * 访问地址：http://localhost:8081/ai/merged/generate
     */
    @PostMapping(value = "/merged/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String mergedChat(@RequestPart("content") String content,
                             @RequestPart(value = "userId", required = false) String userId,
                             @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return cozeChatService.mergedChat(content, userId, files);
    }
}