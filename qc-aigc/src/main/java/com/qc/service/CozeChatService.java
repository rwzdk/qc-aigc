package com.qc.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

public interface CozeChatService {
    SseEmitter streamChat(String content, String userId, List<MultipartFile> files);
    String mergedChat(String content, String userId, List<MultipartFile> files);
}
