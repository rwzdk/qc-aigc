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

package com.qc.audio.audio.tts;

/**
 * TTSController 用于处理文字转语音(TTS)相关功能。
 * 它提供了一个 RESTful HTTP 接口，接收文本输入并生成对应的 MP3 语音音频文件。
 * 同时，它实现了 ApplicationRunner 接口，用于在应用启动时进行相关资源的初始化。
 */

import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisResponse;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */
/*
* 文字转语音
* */
@RestController
@RequestMapping("/ai/tts") // 定义了该控制器的 HTTP 路径前缀
public class TTSController implements ApplicationRunner {

	// 语音合成模型，用于处理文本到语音的转换
	private final SpeechSynthesisModel speechSynthesisModel;

	// 用于存储生成语音文件的固定目录路径
	private static final String FILE_PATH = "spring-ai-alibaba-audio-example/dashscope-audio/src/main/resources/gen/tts";

		/**
	 * 构造函数，注入语音合成模型。
	 * @param speechSynthesisModel 语音合成模型依赖
	 */
	public TTSController(SpeechSynthesisModel speechSynthesisModel) {
		this.speechSynthesisModel = speechSynthesisModel;
	}

		/**
	 * 文字转语音 (TTS) 接口。
	 * 接收文字转语音的 HTTP 请求，处理后返回生成的 MP3 文件。
	 * 
	 * @param text 需要转换的文字内容
	 * @return 包含 MP3 文件的 ResponseEntity 对象
	 * @throws IOException 当文件写入失败时抛出
	 */
	@GetMapping
	public ResponseEntity<FileSystemResource> tts(@RequestParam String text) throws IOException {
		SpeechSynthesisResponse response = speechSynthesisModel.call(
				new SpeechSynthesisPrompt(text)
		);
		File directory = new File(FILE_PATH);
		if (!directory.exists()) {
			directory.mkdirs();
		}
		String fileName = "tts-" + UUID.randomUUID() + ".mp3";
		Path outputPath = new File(directory, fileName).toPath();
		ByteBuffer byteBuffer = response.getResult().getOutput().getAudio();
		Files.write(outputPath, byteBuffer.array());
		FileSystemResource resource = new FileSystemResource(outputPath);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.contentType(MediaType.parseMediaType("audio/mpeg"))
				.contentLength(resource.contentLength())
				.body(resource);
	}


		/**
	 * 应用启动时初始化生成语音文件存储的目录。
	 * @param args Spring Boot 启动参数
	 */
	@Override
	public void run(ApplicationArguments args) {
		File file = new File(FILE_PATH);
		if (!file.exists()) {
			file.mkdirs();
		}
	}

	//去掉删除生成的mp3代码
	//@PreDestroy
		/**
	 * 删除生成的音频文件。
	 * 
	 * 注意：当前方法被注释，未实际生效。
	 * 若需要启用清理功能，请添加 @PreDestroy 注解。
	 * @throws IOException 如果删除目录时出错
	 */
	public void destroy() throws IOException {
		// 删除默认的示例路径
		String example_file_path = "spring-ai-alibaba-audio-example/dashscope-audio/src/main/resources/gen/tts";
		FileUtils.deleteDirectory(new File(example_file_path));
	}

}
