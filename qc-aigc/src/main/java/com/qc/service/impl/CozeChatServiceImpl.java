package com.qc.service.impl;

import com.qc.service.CozeChatService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CozeChatServiceImpl implements CozeChatService {
    // Coze 配置（建议放到 application.yml）
    @Value("${coze.api.url:https://api.coze.cn/v3/chat}")
    private String COZE_API_URL;

    @Value("${coze.auth.token:pat_8QdcoxB7WKaWz7NvET4F1SwAbkPtuvPXhgSKJtV1AMDb48hKGWD0U21gcsYassXy}")
    private String AUTH_TOKEN;

    @Value("${coze.bot.id:7627828198931873801}")
    private String BOT_ID;

    private final OkHttpClient okHttpClient;
    private static final Logger log = LoggerFactory.getLogger(CozeChatServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CozeChatServiceImpl(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * 从 Coze 返回的 data 字符串中解析并提取 content 字段的文本内容（支持多种碎片情况），
     * 返回合并后的纯文本（用空格分隔片段）。
     */
    private String extractContentFromJson(String data) {
        if (data == null || data.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        // 有时 data 中会包含多行 JSON 或多个 JSON 串联，用换行拆分
        String[] parts = data.split("\\r?\\n");
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        for (String part : parts) {
            if (part == null) continue;
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                JsonNode contentNode = node.path("content");
                if (!contentNode.isMissingNode()) {
                    String text = contentNode.asText();
                    if (text != null && !text.isEmpty()) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(text);
                    }
                    continue;
                }
            } catch (Exception ignore) {
                // 可能不是完整 JSON，使用正则提取 content
            }

            Matcher m = p.matcher(trimmed);
            while (m.find()) {
                String group = m.group(1);
                if (group == null || group.isEmpty()) continue;
                String unescaped = group;
                try {
                    // 通过 ObjectMapper 解析 JSON 字符串以还原转义字符
                    String wrapper = '"' + group.replace("\"", "\\\"") + '"';
                    unescaped = objectMapper.readValue(wrapper, String.class);
                } catch (Exception ex) {
                    // fallback: 使用原始捕获
                    unescaped = group.replace("\\\"", "\"");
                }
                if (!unescaped.isEmpty()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(unescaped);
                }
            }
        }
        return sb.toString();
    }

    private String extractAnswerDelta(String data) {
        return extractTextByCandidateKeys(data, List.of("content"));
    }

    private String extractThinkingDelta(String data) {
        return extractTextByCandidateKeys(data, List.of("reasoning_content", "thinking_content"));
    }

    private String extractTextByCandidateKeys(String data, List<String> keys) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String[] parts = data.split("\\r?\\n");
        StringBuilder merged = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String text = extractTextFromNode(part.trim(), keys);
            if (text != null && !text.isBlank()) {
                merged.append(text);
            }
        }
        if (merged.length() > 0) {
            return merged.toString();
        }
        return null;
    }

    private String extractTextFromNode(String json, List<String> keys) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> hits = new ArrayList<>();
            collectCandidateText(root, keys, hits);
            if (!hits.isEmpty()) {
                StringBuilder result = new StringBuilder();
                for (String hit : hits) {
                    if (hit != null && !hit.isBlank()) {
                        result.append(hit);
                    }
                }
                if (result.length() > 0) {
                    return result.toString();
                }
            }
        } catch (Exception ignore) {
            // fallback below
        }

        if (keys.contains("content")) {
            return extractContentFromJson(json);
        }
        return null;
    }

    private void collectCandidateText(JsonNode node, List<String> keys, List<String> hits) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            for (String key : keys) {
                JsonNode value = node.get(key);
                if (value != null && value.isTextual()) {
                    String text = value.asText();
                    if (text != null && !text.isBlank()) {
                        hits.add(text);
                    }
                }
            }
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                collectCandidateText(iterator.next(), keys, hits);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectCandidateText(child, keys, hits);
            }
        }
    }

    /**
     * 流式调用 Coze 接口，动态传入用户提问内容
     */
    @Override
    public SseEmitter streamChat(String content) {
        // 创建 SSE 发射器
        SseEmitter emitter = new SseEmitter(120000L);

        // 动态拼接请求体，content 为前端传入参数
        String requestJson = """
                {
                  "bot_id": "%s",
                  "stream": true,
                  "additional_messages": [
                    {
                      "content_type": "text",
                      "role": "user",
                      "type": "question",
                      "content": "%s"
                    }
                  ],
                  "parameters": {},
                  "user_id": "123"
                }
                """.formatted(BOT_ID, content.replace("\"", "\\\"")); // 转义双引号，避免JSON报错

        // 构建请求
        Request request = new Request.Builder()
                .url(COZE_API_URL)
                .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                .addHeader("Content-Type", "application/json")
                // 请求流式返回时，最好也声明 Accept: text/event-stream
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8")))
                .build();

        // 异步执行请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
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
                        log.error("Coze API returned error. code={}, body={}", response.code(), respText);
                        emitter.send(SseEmitter.event().data("接口错误，状态码：" + response.code() + ", body:" + respText).name("error"));
                        emitter.complete();
                        return;
                    }

                    if (body == null) {
                        emitter.send(SseEmitter.event().data("空响应体").name("error"));
                        emitter.complete();
                        return;
                    }

                    try (Reader reader = body.charStream(); BufferedReader bufferedReader = new BufferedReader(reader)) {
                        String line;
                        StringBuilder dataBuilder = new StringBuilder();
                        StringBuilder mergedAnswer = new StringBuilder();
                        StringBuilder mergedThinking = new StringBuilder();
                        String eventName = null;

                        while ((line = bufferedReader.readLine()) != null) {
                            // 记录原始行，便于排查（日志量可能很大，DEBUG 级别）
                            log.debug("Coze RAW: [{}]", line);

                            // 标准 SSE 行以 "data:" 或 "event:" 开头，空行表示一个事件结束
                            if (line.startsWith("data:")) {
                                dataBuilder.append(line.substring(5).trim());
                                dataBuilder.append('\n');
                            } else if (line.startsWith("event:")) {
                                eventName = line.substring(6).trim();
                            } else if (line.trim().isEmpty()) {
                                // 事件结束，发送拼接后的 data 内容
                                String data = dataBuilder.toString().trim();
                                if (!data.isEmpty()) {
                                    if ("[DONE]".equals(data)) {
                                        String answer = mergedAnswer.toString().trim();
                                        String thinking = mergedThinking.toString().trim();
                                        if (!thinking.isEmpty()) {
                                            emitter.send(SseEmitter.event().name("thinking_merged").data(thinking));
                                        }
                                        if (!answer.isEmpty()) {
                                            emitter.send(SseEmitter.event().name("answer_merged").data(answer));
                                            emitter.send(SseEmitter.event().name("merged").data(answer));
                                        }
                                        emitter.send(SseEmitter.event().name("done").data("流式返回完成"));
                                        break;
                                    } else {
                                        try {
                                            String thinkingDelta = extractThinkingDelta(data);
                                            if (thinkingDelta != null && !thinkingDelta.isBlank()) {
                                                mergedThinking.append(thinkingDelta);
                                                emitter.send(SseEmitter.event().name("thinking").data(thinkingDelta));
                                            }

                                            String answerDelta = extractAnswerDelta(data);
                                            if (answerDelta != null && !answerDelta.isBlank()) {
                                                mergedAnswer.append(answerDelta);
                                                emitter.send(SseEmitter.event().name("answer").data(answerDelta));
                                                emitter.send(SseEmitter.event().name("delta").data(answerDelta));
                                            }
                                        } catch (Exception ex) {
                                            log.debug("解析 data JSON 时忽略错误，原始 data={}", data, ex);
                                        }

                                        try {
                                            SseEmitter.SseEventBuilder ev = SseEmitter.event().name("raw").data(data);
                                            if (eventName != null) ev.name(eventName);
                                            emitter.send(ev);
                                        } catch (IOException ioe) {
                                            log.warn("向前端发送 SSE 失败", ioe);
                                            break;
                                        }
                                    }
                                }
                                dataBuilder.setLength(0);
                                eventName = null;
                            } else {
                                // 非标准 SSE 也可能是直接的 JSON chunk，尝试直接发送
                                String trimmed = line.trim();
                                if (!trimmed.isEmpty()) {
                                    try {
                                        emitter.send(SseEmitter.event().name("raw").data(trimmed));
                                    } catch (IOException ioe) {
                                        log.warn("向前端发送非标准行失败", ioe);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    emitter.completeWithError(e);
                } finally {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    /**
     * 非流式获取完整合并内容，便于测试
     */
    @Override
    public String mergedChat(String content) {
        String requestJson = """
                {
                  "bot_id": "%s",
                  "stream": true,
                  "additional_messages": [
                    {
                      "content_type": "text",
                      "role": "user",
                      "type": "question",
                      "content": "%s"
                    }
                  ],
                  "parameters": {},
                  "user_id": "123"
                }
                """.formatted(BOT_ID, content.replace("\"", "\\\""));

        Request request = new Request.Builder()
                .url(COZE_API_URL)
                .addHeader("Authorization", "Bearer " + AUTH_TOKEN)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respText = response.body() == null ? "" : response.body().string();
                log.error("Coze API returned error. code={}, body={}", response.code(), respText);
                return "接口错误，状态码：" + response.code() + ", body:" + respText;
            }

            ResponseBody body = response.body();
            if (body == null) {
                return "空响应体";
            }

            StringBuilder mergedContent = new StringBuilder();
            try (Reader reader = body.charStream(); BufferedReader bufferedReader = new BufferedReader(reader)) {
                String line;
                StringBuilder dataBuilder = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    log.debug("Coze RAW: [{}]", line);

                    if (line.startsWith("data:")) {
                        dataBuilder.append(line.substring(5).trim());
                        dataBuilder.append('\n');
                    } else if (line.trim().isEmpty()) {
                        String data = dataBuilder.toString().trim();
                        if (!data.isEmpty()) {
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            try {
                                String extracted = extractContentFromJson(data);
                                if (extracted != null && !extracted.isEmpty()) {
                                    mergedContent.append(extracted);
                                }
                            } catch (Exception ex) {
                                log.debug("解析 data JSON 时忽略错误，原始 data={}", data, ex);
                            }
                        }
                        dataBuilder.setLength(0);
                    }
                }
            }
            return mergedContent.toString().trim();
        } catch (Exception e) {
            log.error("Coze mergedChat 调用失败", e);
            return "请求失败：" + e.getMessage();
        }
    }
}
