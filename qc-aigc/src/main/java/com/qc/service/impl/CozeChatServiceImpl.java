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
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    public CozeChatServiceImpl() {
        // 【核心修复1】配置超稳定的 OkHttpClient
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)      // 连接超时1分钟
                .readTimeout(300, TimeUnit.SECONDS)        // 【关键】读超时5分钟，彻底解决AI生成超时
                .writeTimeout(60, TimeUnit.SECONDS)         // 写入超时1分钟
                .callTimeout(300, TimeUnit.SECONDS)         // 整个调用超时5分钟
                .retryOnConnectionFailure(true)              // 连接失败自动重试
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES)) // 连接池保活
                .protocols(Arrays.asList(Protocol.HTTP_1_1)) // 【核心修复2】强制只用HTTP/1.1，彻底解决HTTP/2流重置
                .build();
    }

    // ====================== 工具：查找并保存会话ID ======================
    private String findAndSaveConversationId(String userId, String jsonData) {
        try {
            JsonNode node = objectMapper.readTree(jsonData);
            return findAndSaveConversationId(userId, node);
        } catch (Exception e) {
            return null;
        }
    }

    private String findAndSaveConversationId(String userId, JsonNode node) {
        if (node == null || node.isNull()) return null;

        if (node.has("conversation_id") && node.get("conversation_id").isTextual()) {
            String convId = node.get("conversation_id").asText().trim();
            if (!convId.isBlank()) {
                USER_CONVERSATION_CACHE.put(userId, convId);
                log.info("✅【会话ID捕获】用户 {}: {}", userId, convId);
                return convId;
            }
        }

        if (node.isObject()) {
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                String convId = findAndSaveConversationId(userId, iterator.next());
                if (convId != null) return convId;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String convId = findAndSaveConversationId(userId, child);
                if (convId != null) return convId;
            }
        }
        return null;
    }

    // ====================== 工具：提取回答内容（适配Coze流式返回） ======================
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

    // ====================== 流式对话（前端SSE，保持不变） ======================
    @Override
    public SseEmitter streamChat(String content, String userId, List<MultipartFile> files) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
        String finalUserId = (userId == null || userId.isBlank()) ? "default_user" : userId;
        log.info("=====================================");
        log.info("🚀【流式对话启动】用户: {}", finalUserId);

        try {
            String multiModalContent = buildMultiModalContent(content, files);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("bot_id", BOT_ID);
            requestBody.put("stream", true);
            requestBody.put("user_id", finalUserId);
            requestBody.put("parameters", new HashMap<>());

            String cachedConvId = USER_CONVERSATION_CACHE.get(finalUserId);
            if (cachedConvId != null && !cachedConvId.isBlank()) {
                requestBody.put("conversation_id", cachedConvId);
                log.info("✅【记忆生效】会话ID: {}", cachedConvId);
            }

            Map<String, Object> message = new HashMap<>();
            message.put("content_type", "object_string");
            message.put("role", "user");
            message.put("type", "question");
            message.put("content", multiModalContent);
            requestBody.put("additional_messages", List.of(message));

            String requestJson = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(COZE_CHAT_API_URL)
                    .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8")))
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("❌ 流式请求失败", e);
                    try {
                        emitter.send("请求失败：" + e.getMessage());
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful()) {
                            String respText = body == null ? "" : body.string();
                            log.error("❌ Coze接口错误: {}", respText);
                            emitter.send(SseEmitter.event().data("接口错误：" + respText).name("error"));
                            emitter.complete();
                            return;
                        }

                        try (Reader reader = body.charStream();
                             BufferedReader bufferedReader = new BufferedReader(reader)) {
                            String line;
                            StringBuilder dataBuilder = new StringBuilder();
                            String eventName = null;

                            while ((line = bufferedReader.readLine()) != null) {
                                if (line.startsWith("event:")) {
                                    eventName = line.substring(6).trim();
                                } else if (line.startsWith("data:")) {
                                    dataBuilder.append(line.substring(5).trim());
                                    dataBuilder.append('\n');
                                } else if (line.trim().isEmpty()) {
                                    String data = dataBuilder.toString().trim();
                                    if (!data.isEmpty()) {
                                        if ("[DONE]".equals(data)) {
                                            emitter.send(SseEmitter.event().name("done").data("完成"));
                                            break;
                                        }

                                        findAndSaveConversationId(finalUserId, data);
                                        String answer = extractAnswerContent(data);
                                        if (answer != null && !answer.isBlank()) {
                                            emitter.send(SseEmitter.event().name(eventName == null ? "answer" : eventName).data(answer));
                                        }
                                    }
                                    dataBuilder.setLength(0);
                                    eventName = null;
                                }
                            }
                        }

                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    } finally {
                        emitter.complete();
                        log.info("🏁【流式对话结束】");
                        log.info("=====================================");
                    }
                }
            });

        } catch (Exception e) {
            log.error("❌ 流式对话异常", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // ====================== 非流式对话（终极稳定版） ======================
    @Override
    public String mergedChat(String content, String userId, List<MultipartFile> files) {
        String finalUserId = (userId == null || userId.isBlank()) ? "default_user" : userId;
        log.info("=====================================");
        log.info("🚀【非流式对话启动】用户: {}", finalUserId);
        log.info("💬 提问: {}", content);

        // 【核心修复3】添加重试机制，失败自动重试1次
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
                    Thread.sleep(2000); // 重试前等待2秒
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
        String multiModalContent = buildMultiModalContent(content, files);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("bot_id", BOT_ID);
        requestBody.put("stream", true);
        requestBody.put("user_id", userId);
        requestBody.put("parameters", new HashMap<>());

        String cachedConvId = USER_CONVERSATION_CACHE.get(userId);
        if (cachedConvId != null && !cachedConvId.isBlank()) {
            requestBody.put("conversation_id", cachedConvId);
            log.info("✅【记忆生效】会话ID: {}", cachedConvId);
        }

        Map<String, Object> message = new HashMap<>();
        message.put("content_type", "object_string");
        message.put("role", "user");
        message.put("type", "question");
        message.put("content", multiModalContent);
        requestBody.put("additional_messages", List.of(message));

        String requestJson = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(COZE_CHAT_API_URL)
                .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .addHeader("Connection", "keep-alive") // 保活
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

            try (Reader reader = response.body().charStream();
                 BufferedReader bufferedReader = new BufferedReader(reader)) {

                String line;
                StringBuilder dataBuilder = new StringBuilder();

                while ((line = bufferedReader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        dataBuilder.append(line.substring(5).trim());
                    } else if (line.trim().isEmpty()) {
                        String data = dataBuilder.toString().trim();
                        dataBuilder.setLength(0);

                        if ("[DONE]".equals(data)) {
                            log.info("✅ 收到[DONE]，流读取完成");
                            break;
                        }

                        if (data.isBlank()) continue;

                        findAndSaveConversationId(userId, data);
                        String answerFragment = extractAnswerContent(data);
                        if (answerFragment != null && !answerFragment.isBlank()) {
                            fullAnswer.append(answerFragment);
                        }
                    }
                }
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