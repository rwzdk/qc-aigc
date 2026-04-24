package com.qc.service.impl;

import com.qc.service.CozeChatService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CozeChatServiceImpl implements CozeChatService {
    // Coze 配置
    @Value("${coze.api.url:https://api.coze.cn/v3/chat}")
    private String COZE_CHAT_API_URL;

    @Value("${coze.api.upload.url:https://api.coze.cn/v1/files/upload}")
    private String COZE_UPLOAD_URL;

    @Value("${coze.auth.token:pat_8QdcoxB7WKaWz7NvET4F1SwAbkPtuvPXhgSKJtV1AMDb48hKGWD0U21gcsYassXy}")
    private String AUTH_TOKEN;

    @Value("${coze.bot.id:7627828198931873801}")
    private String BOT_ID;

    private final OkHttpClient okHttpClient;
    private static final Logger log = LoggerFactory.getLogger(CozeChatServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 会话缓存
    private final Map<String, String> USER_CONVERSATION_CACHE = new ConcurrentHashMap<>();

    public CozeChatServiceImpl(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    // ====================== 会话管理工具方法 ======================

    public String getConversationId(String userId) {
        if (userId == null) {
            return null;
        }
        return USER_CONVERSATION_CACHE.getOrDefault(userId, null);
    }

    public void setConversationId(String userId, String conversationId) {
        if (userId != null && conversationId != null && !conversationId.trim().isEmpty()) {
            USER_CONVERSATION_CACHE.put(userId, conversationId.trim());
            log.info("💾 手动设置会话ID: {} -> {}", userId, conversationId);
        }
    }

    public void clearConversation(String userId) {
        if (userId != null) {
            USER_CONVERSATION_CACHE.remove(userId);
            log.info("🧹 已清除用户 {} 的会话记忆", userId);
        }
    }

    public Map<String, String> getAllConversations() {
        return new HashMap<>(USER_CONVERSATION_CACHE);
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "default_user";
        }
        return userId.trim();
    }

    private String resolveConversationId(String userId) {
        String cached = USER_CONVERSATION_CACHE.get(userId);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        return null;
    }

    // ====================== 工具：从conversation.chat.created事件提取会话ID ======================
    private String extractConversationIdFromEvent(String eventName, String jsonData) {
        if (!"conversation.chat.created".equals(eventName)) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(jsonData);
            if (node.has("conversation_id") && node.get("conversation_id").isTextual()) {
                String convId = node.get("conversation_id").asText().trim();
                if (!convId.isEmpty() && !"null".equals(convId)) {
                    log.info("🎯【会话ID捕获】从 {} 事件获取: {}", eventName, convId);
                    return convId;
                }
            }

            // 兼容其他可能的结构
            if (node.has("id") && node.get("id").isTextual()) {
                String id = node.get("id").asText().trim();
                if (!id.isEmpty()) {
                    log.info("🎯【会话ID捕获-备用】从 {} 事件的id字段获取: {}", eventName, id);
                    return id;
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ 解析conversation.chat.created事件失败: {}", e.getMessage());
        }
        return null;
    }

    // ====================== 工具：查找并保存会话ID ======================
    private String findAndSaveConversationId(String userId, String eventName, String jsonData) {
        if (userId == null || jsonData == null || jsonData.trim().isEmpty()) {
            return null;
        }

        // 【优先级1】从conversation.chat.created事件提取
        if ("conversation.chat.created".equals(eventName)) {
            String convId = extractConversationIdFromEvent(eventName, jsonData);
            if (convId != null) {
                USER_CONVERSATION_CACHE.put(userId, convId);
                log.info("✅【会话ID已保存】用户 {}: {}", userId, convId);
                return convId;
            }
        }

        // 【优先级2】从conversation.chat.completed事件提取
        if ("conversation.chat.completed".equals(eventName) ||
                "conversation.message.completed".equals(eventName)) {
            try {
                JsonNode node = objectMapper.readTree(jsonData);
                if (node.has("conversation_id") && node.get("conversation_id").isTextual()) {
                    String convId = node.get("conversation_id").asText().trim();
                    if (!convId.isEmpty()) {
                        USER_CONVERSATION_CACHE.put(userId, convId);
                        return convId;
                    }
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        // 【优先级3】通用解析（兜底）
        try {
            JsonNode node = objectMapper.readTree(jsonData);
            if (node.has("conversation_id") && node.get("conversation_id").isTextual()) {
                String convId = node.get("conversation_id").asText().trim();
                if (!convId.isEmpty()) {
                    USER_CONVERSATION_CACHE.put(userId, convId);
                    return convId;
                }
            }
        } catch (Exception e) {
            // 不是JSON格式，忽略
        }

        return null;
    }

    // ====================== 工具：提取回答内容 ======================
    private String extractAnswerContent(String jsonData) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            return extractAnswerContent(rootNode);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractAnswerContent(JsonNode node) {
        if (node == null || node.isNull()) return null;

        // 优先适配 Coze 流式返回核心结构
        if (node.has("choices") && node.get("choices").isArray()) {
            JsonNode choices = node.get("choices");
            for (JsonNode choice : choices) {
                JsonNode delta = choice.path("delta");
                if (delta.has("content") && delta.get("content").isTextual()) {
                    String content = delta.get("content").asText().trim();
                    if (!content.isBlank()) return content;
                }
            }
        }

        // 兼容普通 content
        if (node.has("content") && node.get("content").isTextual()) {
            String content = node.get("content").asText().trim();
            if (!content.isBlank()) return content;
        }

        // 递归兜底
        if (node.isObject()) {
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                String content = extractAnswerContent(iterator.next());
                if (content != null && !content.isBlank()) return content;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String content = extractAnswerContent(child);
                if (content != null && !content.isBlank()) return content;
            }
        }
        return null;
    }

    // ====================== 工具：上传文件 ======================
    private String uploadFileToCoze(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;
        log.info("📤 上传文件：{} ({}字节)", file.getOriginalFilename(), file.getSize());

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getOriginalFilename(),
                        RequestBody.create(file.getBytes(), MediaType.parse(file.getContentType())))
                .build();

        Request request = new Request.Builder()
                .url(COZE_UPLOAD_URL)
                .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                .post(requestBody)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = response.body() == null ? "空响应" : response.body().string();
                log.error("❌ 文件上传失败: {}", errorMsg);
                throw new RuntimeException("文件上传失败：" + errorMsg);
            }
            String respBody = response.body().string();
            JsonNode root = objectMapper.readTree(respBody);
            String fileId = root.path("data").path("id").asText();
            log.info("✅ 文件上传成功: {}", fileId);
            return fileId;
        }
    }

    // ====================== 工具：构建多模态内容 ======================
    private String buildMultiModalContent(String content, List<MultipartFile> files) throws IOException {
        List<Map<String, Object>> contentList = new ArrayList<>();

        // 文本
        if (content == null || content.isBlank()) content = "请基于上传的文件内容回答";
        Map<String, Object> textItem = new HashMap<>();
        textItem.put("type", "text");
        textItem.put("text", content);
        contentList.add(textItem);
        log.info("💬 提问文本：{}", content);

        // 文件/图片
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                String fileId = uploadFileToCoze(file);
                if (fileId == null) continue;
                Map<String, Object> fileItem = new HashMap<>();
                String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
                fileItem.put("type", contentType.startsWith("image/") ? "image" : "file");
                fileItem.put("file_id", fileId);
                contentList.add(fileItem);
                log.info("📎 附加文件: {}", fileId);
            }
        }

        return objectMapper.writeValueAsString(contentList);
    }

    // ====================== 工具：构建文本内容 ======================
    private String buildTextContent(String content) {
        if (content == null || content.isBlank()) content = "请基于上传的文件内容回答";
        log.info("💬 提问文本：{}", content);
        return content;
    }

    private boolean sendIfActive(SseEmitter emitter, AtomicBoolean completed, SseEmitter.SseEventBuilder event) {
        if (completed.get()) {
            return false;
        }
        try {
            emitter.send(event);
            return true;
        } catch (IllegalStateException | IOException ex) {
            completed.set(true);
            log.debug("⚠️ SSE已完成或发送失败，停止发送: {}", ex.getMessage());
            return false;
        }
    }

    private void completeIfActive(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (IllegalStateException ex) {
                log.debug("⚠️ SSE已完成，忽略重复完成: {}", ex.getMessage());
            }
        }
    }

    private void completeWithErrorIfActive(SseEmitter emitter, AtomicBoolean completed, Exception ex) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.completeWithError(ex);
            } catch (IllegalStateException err) {
                log.debug("⚠️ SSE已完成，忽略错误完成: {}", err.getMessage());
            }
        }
    }

    // ====================== 流式对话 ======================
    @Override
    public SseEmitter streamChat(String content, String userId, List<MultipartFile> files) {
        SseEmitter emitter = new SseEmitter(300000L);
        AtomicBoolean completed = new AtomicBoolean(false);
        String finalUserId = normalizeUserId(userId);
        log.info("=====================================");
        log.info("🚀【流式对话启动】用户: {}", finalUserId);
        log.info("🔍 当前会话缓存: {}", USER_CONVERSATION_CACHE);
        log.info("🔍 用户 {} 的缓存会话ID: {}", finalUserId, USER_CONVERSATION_CACHE.get(finalUserId));

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("bot_id", BOT_ID);
            requestBody.put("stream", true);
            requestBody.put("user_id", finalUserId);
            requestBody.put("auto_save_history", true);
            requestBody.put("parameters", new HashMap<>());

            String targetConversationId = resolveConversationId(finalUserId);
            if (targetConversationId == null) {
                log.info("🆕【新会话】用户 {} 开始新对话", finalUserId);
            } else {
                log.info("✅【记忆生效】使用会话ID: {}", targetConversationId);
            }

            Map<String, Object> message = new HashMap<>();
            if (files != null && !files.isEmpty()) {
                String multiModalContent = buildMultiModalContent(content, files);
                message.put("content_type", "object_string");
                message.put("role", "user");
                message.put("type", "question");
                message.put("content", multiModalContent);
            } else {
                message.put("content_type", "text");
                message.put("role", "user");
                message.put("type", "question");
                message.put("content", content);
            }
            requestBody.put("additional_messages", List.of(message));

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(COZE_CHAT_API_URL)).newBuilder();
            if (targetConversationId != null) {
                urlBuilder.addQueryParameter("conversation_id", targetConversationId);
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8")))
                    .build();

            Call call = okHttpClient.newCall(request);

            emitter.onTimeout(() -> {
                if (completed.compareAndSet(false, true)) {
                    log.warn("⏱️ SSE超时，结束流式输出");
                }
                call.cancel();
            });
            emitter.onCompletion(() -> {
                completed.set(true);
                call.cancel();
            });
            emitter.onError(ex -> {
                completed.set(true);
                call.cancel();
            });

            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled() || completed.get()) {
                        log.info("✅ SSE已结束，忽略回调失败: {}", e.getMessage());
                        return;
                    }
                    log.error("❌ 流式请求失败", e);
                    sendIfActive(emitter, completed,
                            SseEmitter.event().name("error").data("请求失败：" + e.getMessage()));
                    completeWithErrorIfActive(emitter, completed, e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody body = response.body()) {
                        if (completed.get()) {
                            return;
                        }
                        if (!response.isSuccessful()) {
                            String respText = body == null ? "" : body.string();
                            log.error("❌ Coze接口错误: {}", respText);
                            sendIfActive(emitter, completed,
                                    SseEmitter.event().name("error").data("接口错误：" + respText));
                            completeIfActive(emitter, completed);
                            return;
                        }

                        try (Reader reader = body.charStream();
                             BufferedReader bufferedReader = new BufferedReader(reader)) {
                            String line;
                            StringBuilder dataBuilder = new StringBuilder();
                            String eventName = null;

                            try {
                                while (!completed.get() && (line = bufferedReader.readLine()) != null) {
                                    if (line.startsWith("event:")) {
                                        eventName = line.substring(6).trim();
                                    } else if (line.startsWith("data:")) {
                                        String dataLine = line.substring(5).trim();
                                        if (!dataLine.trim().isEmpty()) {
                                            dataBuilder.append(dataLine);
                                        }
                                    } else if (line.trim().isEmpty()) {
                                        String data = dataBuilder.toString().trim();
                                        if (!data.isEmpty()) {
                                            if ("[DONE]".equals(data)) {
                                                log.info("🏁 收到DONE事件，流式传输完成");
                                                sendIfActive(emitter, completed,
                                                        SseEmitter.event().name("done").data("完成"));
                                                break;
                                            }

                                            String conversationId = findAndSaveConversationId(finalUserId, eventName, data);
                                            if (conversationId != null) {
                                                sendIfActive(emitter, completed,
                                                        SseEmitter.event().name("conversation_id").data(conversationId));
                                            }

                                            if (!"conversation.chat.created".equals(eventName)) {
                                                String answer = extractAnswerContent(data);
                                                if (answer != null && !answer.isBlank()) {
                                                    String eventToUse = eventName != null ? eventName : "answer";
                                                    sendIfActive(emitter, completed,
                                                            SseEmitter.event().name(eventToUse).data(answer));
                                                }
                                            }
                                        }
                                        dataBuilder.setLength(0);
                                        eventName = null;
                                    }
                                }
                            } catch (SocketException | InterruptedIOException e) {
                                // 客户端断开连接 → 正常情况，不打印错误堆栈
                                log.info("✅ 客户端已断开连接，流式输出正常结束");
                            } catch (Exception e) {
                                // 其他真正异常才打印
                                log.error("❌ 读取流异常", e);
                            }
                        }

                    } catch (Exception e) {
                        log.error("❌ 处理SSE流时发生异常", e);
                        sendIfActive(emitter, completed,
                                SseEmitter.event().name("error").data("处理响应失败：" + e.getMessage()));
                        completeWithErrorIfActive(emitter, completed, e);
                    } finally {
                        completeIfActive(emitter, completed);
                        log.info("🏁【流式对话结束】");
                        log.info("=====================================");
                    }
                }
            });

        } catch (Exception e) {
            log.error("❌ 流式对话异常", e);
            sendIfActive(emitter, completed,
                    SseEmitter.event().name("error").data("系统异常：" + e.getMessage()));
            completeWithErrorIfActive(emitter, completed, e);
        }

        return emitter;
    }

    // ====================== 非流式对话 ======================
    @Override
    public String mergedChat(String content, String userId, List<MultipartFile> files) {
        String finalUserId = normalizeUserId(userId);
        log.info("=====================================");
        log.info("🚀【非流式对话启动】用户: {}", finalUserId);
        log.info("🔍 当前会话缓存: {}", USER_CONVERSATION_CACHE);
        log.info("🔍 用户 {} 的缓存会话ID: {}", finalUserId, USER_CONVERSATION_CACHE.get(finalUserId));
        log.info("💬 提问: {}", content);

        int maxRetries = 2;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                return doMergedChat(content, finalUserId, files);
            } catch (Exception e) {
                retryCount++;
                log.warn("⚠️ 第{}次请求失败，准备重试: {}", retryCount, e.getMessage());
                if (retryCount >= maxRetries) {
                    log.error("❌ 所有重试均失败", e);
                    return "请求处理失败，请稍后重试：" + e.getMessage();
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "请求被中断";
                }
            }
        }
        return "请求处理失败";
    }

    // 实际的非流式对话逻辑
    private String doMergedChat(String content, String userId, List<MultipartFile> files) throws IOException {
        StringBuilder fullAnswer = new StringBuilder();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("bot_id", BOT_ID);
        requestBody.put("stream", false);
        requestBody.put("user_id", userId);
        requestBody.put("auto_save_history", true);
        requestBody.put("parameters", new HashMap<>());

        String targetConversationId = resolveConversationId(userId);
        if (targetConversationId == null) {
            log.info("🆕【新会话】用户 {} 开始新对话", userId);
        } else {
            log.info("✅【记忆生效】使用会话ID: {}", targetConversationId);
        }

        Map<String, Object> message = new HashMap<>();
        if (files != null && !files.isEmpty()) {
            String multiModalContent = buildMultiModalContent(content, files);
            message.put("content_type", "object_string");
            message.put("role", "user");
            message.put("type", "question");
            message.put("content", multiModalContent);
        } else {
            message.put("content_type", "text");
            message.put("role", "user");
            message.put("type", "question");
            message.put("content", content);
        }
        requestBody.put("additional_messages", List.of(message));

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(COZE_CHAT_API_URL)).newBuilder();
        if (targetConversationId != null) {
            urlBuilder.addQueryParameter("conversation_id", targetConversationId);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = response.body() == null ? "空响应" : response.body().string();
                log.error("❌ Coze接口错误: {}", errorMsg);
                throw new RuntimeException("接口错误：" + response.code() + " → " + errorMsg);
            }

            if (response.body() == null) {
                throw new RuntimeException("AI返回空响应体");
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);

            // 提取会话ID（无论是否已有会话ID）
            if (rootNode.has("conversation_id")) {
                String returnedConversationId = rootNode.get("conversation_id").asText().trim();
                if (!returnedConversationId.isEmpty()) {
                    USER_CONVERSATION_CACHE.put(userId, returnedConversationId);
                    log.info("✅【非流式】捕获会话ID: {}", returnedConversationId);
                }
            }

            // 提取回答内容
            String answer = extractAnswerContent(rootNode);
            if (answer != null && !answer.isBlank()) {
                fullAnswer.append(answer);
            }

            String finalResult = fullAnswer.toString().trim();
            if (finalResult.isBlank()) {
                log.warn("⚠️ AI未返回有效内容");
                return "AI未返回有效内容，请检查Bot配置或重试";
            }

            log.info("🏁【非流式对话成功】回答长度: {}字符", finalResult.length());
            log.info("=====================================");
            return finalResult;
        }
    }
}
