/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.audio.stt;

import com.alibaba.cloud.ai.example.audio.service.SstService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * @author qc
 * 语音转文字接口
 */

@RestController
@RequestMapping("/ai/stt")
@RequiredArgsConstructor
public class STTController {
   private final SstService sstService;
	@PostMapping
	public String stt(@RequestPart("audio") MultipartFile audio,
					  @RequestParam(defaultValue = "paraformer-realtime-v2") String model,
					  @RequestParam(defaultValue = "mp3") String format,
					  @RequestParam(defaultValue = "48000") Integer sampleRate,
					  @RequestParam(defaultValue = "zh,en") String[] languageHints) throws IOException {
		Path tempDir = Files.createTempDirectory("stt-");
		Path tempFile = tempDir.resolve(audio.getOriginalFilename());
		Files.copy(audio.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
		File audioFile = tempFile.toFile();
		return sstService.sst(model, format, sampleRate, languageHints, audioFile);
	}
}
