package com.alibaba.cloud.ai.example.audio.service.impl;

import com.alibaba.cloud.ai.example.audio.service.SstService;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import java.io.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/*
* 语音转文字
* */
@Service
public class SstServiceImpl implements SstService {

    private final String apiKey;

    public SstServiceImpl(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String sst(String model,
                      String format,
                      Integer sampleRate,
                      String[] languageHints,
                      File audioFile) {
        Recognition recognizer = new Recognition();
        RecognitionParam param;
        if (languageHints != null && languageHints.length > 0) {
            param = RecognitionParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .format(format)
                    .sampleRate(sampleRate)
                    .parameter("language_hints", languageHints)
                    .build();
        }
        else {
            param = RecognitionParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .format(format)
                    .sampleRate(sampleRate)
                    .build();
        }
        return recognizer.call(param, audioFile);
    }
}

