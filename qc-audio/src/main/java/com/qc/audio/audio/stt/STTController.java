package com.qc.audio.audio.stt;

import com.qc.audio.audio.service.SstService;
import com.qc.audio.audio.service.result.Result;
import lombok.RequiredArgsConstructor;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/ai/stt")
@RequiredArgsConstructor
public class STTController {
	private final SstService sstService;

	@Value("${audio.ffmpeg.path:}")
	private String ffmpegPath;

	@Value("${audio.ffprobe.path:}")
	private String ffprobePath;

	@PostMapping
	public Result<String> stt(@RequestPart("audio") MultipartFile audio,
							  @RequestParam(defaultValue = "paraformer-realtime-v2") String model,
							  @RequestParam(required = false) Integer sampleRate,
							  @RequestParam(defaultValue = "zh,en") String[] languageHints) {
		Path tempDir = null;
		File sourceFile = null;
		File mp3File = null;
		try {
			if (audio == null || audio.isEmpty()) {
				return Result.fail("上传音频不能为空");
			}

			tempDir = Files.createTempDirectory("stt-");
			String originalName = resolveOriginalFilename(audio);
			Path sourcePath = tempDir.resolve(originalName);
			Files.copy(audio.getInputStream(), sourcePath, StandardCopyOption.REPLACE_EXISTING);
			sourceFile = sourcePath.toFile();

			int detectedSampleRate = detectSampleRate(sourceFile, sampleRate);
			File sttInputFile;
			String sttFormat;

			if (isMp3File(audio, sourceFile)) {
				sttInputFile = sourceFile;
				sttFormat = "mp3";
			} else {
				mp3File = convertToMp3(sourceFile, detectedSampleRate);
				sttInputFile = mp3File;
				sttFormat = "mp3";
			}

			String text = sstService.sst(model, sttFormat, detectedSampleRate, languageHints, sttInputFile);

			return Result.ok(text);

		} catch (Exception e) {
			e.printStackTrace();
			return Result.fail("语音识别处理异常: " + e.getMessage());
		} finally {
			deleteQuietly(mp3File);
			deleteQuietly(sourceFile);
			if (tempDir != null) {
				try {
					Files.deleteIfExists(tempDir);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private File convertToMp3(File inputFile, Integer sampleRate) throws IOException {
		File outputFile = new File(inputFile.getParentFile(), "converted-" + System.currentTimeMillis() + ".mp3");
		FFmpeg ffmpeg = createFFmpeg();
		FFmpegBuilder builder = new FFmpegBuilder()
				.setInput(inputFile.getAbsolutePath())
				.overrideOutputFiles(true)
				.addOutput(outputFile.getAbsolutePath())
				.setFormat("mp3")
				.setAudioCodec("libmp3lame")
				.setAudioBitRate(128_000)
				.setAudioSampleRate(sampleRate)
				.done();
		ffmpeg.run(builder);
		return outputFile;
	}

	private int detectSampleRate(File inputFile, Integer requestSampleRate) {
		if (requestSampleRate != null && requestSampleRate > 0) {
			return requestSampleRate;
		}
		try {
			FFprobe ffprobe = createFFprobe();
			FFmpegProbeResult probeResult = ffprobe.probe(inputFile.getAbsolutePath());
			if (probeResult != null && probeResult.streams != null) {
				for (FFmpegStream stream : probeResult.streams) {
					if (stream != null
							&& stream.codec_type == FFmpegStream.CodecType.AUDIO
							&& stream.sample_rate > 0) {
						return stream.sample_rate;
					}
				}
			}
		} catch (Exception ignored) {
		}
		return 16000;
	}

	private String resolveOriginalFilename(MultipartFile audio) {
		String originalName = audio.getOriginalFilename();
		if (originalName != null && !originalName.isBlank()) {
			return originalName;
		}
		String contentType = normalizeContentType(audio.getContentType());
		if (isWebmContentType(contentType)) {
			return "audio.webm";
		}
		if (isMp3ContentType(contentType)) {
			return "audio.mp3";
		}
		return "audio.weba";
	}

	private boolean isMp3File(MultipartFile audio, File sourceFile) {
		String contentType = normalizeContentType(audio.getContentType());
		if (isMp3ContentType(contentType)) {
			return true;
		}
		String name = sourceFile.getName().toLowerCase();
		return name.endsWith(".mp3");
	}

	private boolean isMp3ContentType(String contentType) {
		return contentType != null && (contentType.equals("audio/mpeg")
				|| contentType.equals("audio/mp3")
				|| contentType.equals("audio/x-mpeg"));
	}

	private boolean isWebmContentType(String contentType) {
		return contentType != null && (contentType.equals("audio/webm")
				|| contentType.equals("video/webm")
				|| contentType.equals("audio/weba"));
	}

	private String normalizeContentType(String contentType) {
		if (contentType == null) {
			return null;
		}
		String normalized = contentType.toLowerCase().trim();
		int semicolonIndex = normalized.indexOf(';');
		return semicolonIndex >= 0 ? normalized.substring(0, semicolonIndex).trim() : normalized;
	}

	private FFmpeg createFFmpeg() throws IOException {
		if (ffmpegPath != null && !ffmpegPath.isBlank()) {
			return new FFmpeg(ffmpegPath);
		}
		return new FFmpeg();
	}

	private FFprobe createFFprobe() throws IOException {
		if (ffprobePath != null && !ffprobePath.isBlank()) {
			return new FFprobe(ffprobePath);
		}
		return new FFprobe();
	}

	private void deleteQuietly(File file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException ignored) {
		}
	}
}
