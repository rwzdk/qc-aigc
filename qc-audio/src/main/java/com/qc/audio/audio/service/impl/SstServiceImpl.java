package com.qc.audio.audio.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qc.audio.audio.service.SstService;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class SstServiceImpl implements SstService {

    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SstServiceImpl(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String sst(String model,
                      String format,
                      Integer sampleRate,
                      String[] languageHints,
                      File audioFile) {
        try {
            Recognition recognizer = new Recognition();
            RecognitionParam.RecognitionParamBuilder<?, ?> builder = RecognitionParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .format(format)
                    .sampleRate(sampleRate);

            if (languageHints != null && languageHints.length > 0) {
                builder.parameter("language_hints", languageHints);
            }

            RecognitionParam param = builder.build();
            String rawResult = recognizer.call(param, audioFile);

            return extractText(rawResult);
        } catch (Exception e) {
            e.printStackTrace();
            return "识别失败: " + e.getMessage();
        }
    }

    private String extractText(String jsonStr) {
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode sentences = root.path("sentences");
            StringBuilder sb = new StringBuilder();
            if (sentences.isArray()) {
                for (JsonNode node : sentences) {
                    sb.append(node.path("text").asText());
                }
            }
            return sb.length() > 0 ? sb.toString() : jsonStr;
        } catch (Exception e) {
            return jsonStr;
        }
    }
}
