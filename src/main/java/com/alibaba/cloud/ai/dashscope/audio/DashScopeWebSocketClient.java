/*
 * Local override for spring-ai-alibaba DashScopeWebSocketClient with safer error
 * propagation into Reactor Flux sinks. This file mirrors the upstream implementation
 * with minimal changes: onMessage(...) catches now call emittersError(...) so
 * subscribers observe errors instead of silent logs. This is a hotfix placeholder
 * — do not commit upstream changes without review.
 */
package com.alibaba.cloud.ai.dashscope.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.cloud.ai.dashscope.api.ApiUtils;
import com.alibaba.cloud.ai.dashscope.protocol.DashScopeWebSocketClientOptions;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class DashScopeWebSocketClient extends WebSocketListener {

    private final Logger logger = LoggerFactory.getLogger(DashScopeWebSocketClient.class);

    private final DashScopeWebSocketClientOptions options;

    private final AtomicBoolean isOpen;

    private final ObjectMapper objectMapper;

    private WebSocket webSocketClient;

    FluxSink<ByteBuffer> binaryEmitter;

    FluxSink<String> textEmitter;

    private String continueTaskMessage;
    private String continueTaskTemplate;
    private String finishTaskMessage;
    private ByteBuffer binaryData;
    private Flux<ByteBuffer> binaryStream;
    private Flux<String> textStream;
    private volatile boolean taskStarted = false;

    public DashScopeWebSocketClient(DashScopeWebSocketClientOptions options) {
        this.options = options;
        this.isOpen = new AtomicBoolean(false);
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .addModules(JacksonUtils.instantiateAvailableModules())
            .build();
    }

    public Flux<ByteBuffer> command(String runTaskMessage, String continueTaskMessage,
            String finishTaskMessage) {
        this.continueTaskMessage = continueTaskMessage;
        this.finishTaskMessage = finishTaskMessage;
        this.binaryData = null;
        this.binaryStream = null;
        this.textStream = null;
        this.continueTaskTemplate = null;
        this.taskStarted = false;

        return Flux.<ByteBuffer>create(emitter -> {
            this.binaryEmitter = emitter;
            logger.info("CosyVoice single text: Sending run-task message");
            sendText(runTaskMessage);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    public Flux<ByteBuffer> commandStreaming(String runTaskMessage, String continueTaskTemplate,
            String finishTaskMessage, Flux<String> textStream) {
        this.continueTaskMessage = null;
        this.continueTaskTemplate = continueTaskTemplate;
        this.finishTaskMessage = finishTaskMessage;
        this.binaryData = null;
        this.binaryStream = null;
        this.textStream = textStream;
        this.taskStarted = false;

        return Flux.<ByteBuffer>create(emitter -> {
            this.binaryEmitter = emitter;
            logger.info("CosyVoice streaming input: Sending run-task message");
            sendText(runTaskMessage);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    public Flux<ByteBuffer> command(String runTaskMessage) {
        this.continueTaskMessage = null;
        this.finishTaskMessage = null;
        this.binaryData = null;
        this.binaryStream = null;
        this.textStream = null;
        this.continueTaskTemplate = null;
        this.taskStarted = false;

        return Flux.<ByteBuffer>create(emitter -> {
            this.binaryEmitter = emitter;
            logger.info("Sambert: Sending run-task message");
            sendText(runTaskMessage);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    public Flux<String> command(String runTaskMessage, ByteBuffer binaryData, String finishTaskMessage) {
        this.binaryData = binaryData;
        this.binaryStream = null;
        this.finishTaskMessage = finishTaskMessage;
        this.textStream = null;
        this.continueTaskTemplate = null;
        this.taskStarted = false;

        return Flux.<String>create(emitter -> {
            this.textEmitter = emitter;
            logger.info("Binary input: Sending run-task message");
            sendText(runTaskMessage);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    public Flux<String> commandStreaming(String runTaskMessage, String finishTaskMessage,
            Flux<ByteBuffer> binaryStream) {
        this.binaryData = null;
        this.binaryStream = binaryStream;
        this.finishTaskMessage = finishTaskMessage;
        this.textStream = null;
        this.continueTaskTemplate = null;
        this.continueTaskMessage = null;
        this.taskStarted = false;

        return Flux.<String>create(emitter -> {
            this.textEmitter = emitter;
            logger.info("ASR streaming binary: Sending run-task message");
            sendText(runTaskMessage);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    public void sendText(String text) {
        if (!isOpen.get()) {
            establishWebSocketClient();
            try {
                TimeUnit.SECONDS.sleep(Constants.DEFAULT_READY_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for WebSocket connection", e);
            }
        }
        logger.info("send text: {}", text);
        boolean success = webSocketClient.send(text);

        if (!success) {
            logger.error("send text failed");
        }
    }

    public void sendBinary(ByteBuffer binary) {
        if (!isOpen.get()) {
            establishWebSocketClient();
        }

        if (binary == null) {
            logger.error("binary data is null");
            return;
        }

        boolean success = webSocketClient.send(ByteString.of(binary));

        if (!success) {
            logger.error("send binary failed");
        }
    }

    private void establishWebSocketClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.valueOf(Constants.DEFAULT_HTTP_LOGGING_LEVEL));
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(Constants.DEFAULT_MAXIMUM_ASYNC_REQUESTS);
        dispatcher.setMaxRequestsPerHost(Constants.DEFAULT_MAXIMUM_ASYNC_REQUESTS_PER_HOST);

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT)
            .readTimeout(Constants.DEFAULT_READ_TIMEOUT)
            .writeTimeout(Constants.DEFAULT_WRITE_TIMEOUT)
            .addInterceptor(logging)
            .dispatcher(dispatcher)
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .connectionPool(new ConnectionPool(Constants.DEFAULT_CONNECTION_POOL_SIZE,
                    Constants.DEFAULT_CONNECTION_IDLE_TIMEOUT.getSeconds(), TimeUnit.SECONDS));
        OkHttpClient httpClient = clientBuilder.build();

        try {
            this.webSocketClient = httpClient.newWebSocket(buildConnectionRequest(), this);
        }
        catch (Throwable ex) {
            logger.error("create websocket failed: msg={}", ex.getMessage(), ex);
            emittersError("create websocket failed", ex);
        }
    }

    private Request buildConnectionRequest() {
        Builder bd = new Request.Builder();
        bd.headers(Headers.of(ApiUtils.getMapContentHeaders(options.getApiKey(), false,
            options.getWorkSpaceId(), null)));
        return bd.url(options.getUrl()).build();
    }

    private String getRequestBody(Response response) {
        String responseBody = "";
        if (response != null && response.body() != null) {
            try {
                responseBody = response.body().string();
            }
            catch (IOException ex) {
                logger.error("get response body failed: {}", ex.getMessage(), ex);
            }
        }
        return responseBody;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        logger.info("receive ws event onOpen: handle={}, body={}", webSocket, getRequestBody(response));
        isOpen.set(true);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        logger.info("receive ws event onClosed: handle={}, code={}, reason={}", webSocket, code, reason);
        isOpen.set(false);
        emittersComplete("closed");
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        logger.info("receive ws event onClosing: handle={}, code={}, reason={}", webSocket.toString(), code, reason);
        emittersComplete("closing");
        webSocket.close(code, reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        String failureMessage = String.format("msg=%s, cause=%s, body=%s", t == null ? "null" : t.getMessage(),
                t == null ? "null" : t.getCause(), getRequestBody(response));
        logger.error("receive ws event onFailure: handle={}, {}", webSocket, failureMessage, t);
        isOpen.set(false);
        emittersError("failure", t == null ? new Exception(failureMessage) : t);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        logger.debug("receive ws event onMessage(text): handle={}, text={}", webSocket, text);

        try {
            EventMessage message = this.objectMapper.readValue(text, EventMessage.class);
            switch (message.header().event()) {
                case TASK_STARTED:
                    logger.info("task started: text={}", text);
                    this.taskStarted = true;

                    if (!ObjectUtils.isEmpty(this.continueTaskMessage)) {
                        sendText(this.continueTaskMessage);
                        sendText(this.finishTaskMessage);
                    }

                    if (this.textStream != null && this.continueTaskTemplate != null) {
                        subscribeTextStream();
                    }

                    if (!ObjectUtils.isEmpty(this.binaryData)) {
                        sendBinary(this.binaryData);
                        sendText(this.finishTaskMessage);
                    }

                    if (this.binaryStream != null) {
                        subscribeBinaryStream();
                    }
                    break;

                case RESULT_GENERATED:
                    logger.debug("result generated: text={}", text);
                    if (this.textEmitter != null) {
                        try {
                            textEmitter.next(text);
                        } catch (Exception e) {
                            logger.error("textEmitter.next failed", e);
                            emittersError("textEmitter next failed", e);
                        }
                    }
                    break;

                case TASK_FINISHED:
                    logger.info("task finished: text={}", text);
                    emittersComplete("finished");
                    break;

                case TASK_FAILED:
                    String errorCode = message.header().code() != null ? message.header().code() : "UNKNOWN";
                    String errorMessage =
                            message.header().message() != null ? message.header().message() : "No error message provided";
                    String errorDetail = String.format("Task failed with error_code='%s', error_message='%s'", errorCode, errorMessage);
                    logger.error("task failed: text={}, error_code={}, error_message={}", text, errorCode, errorMessage);
                    emittersError("task failed", new Exception(errorDetail));
                    break;
            }
        }
        catch (Exception e) {
            logger.error("parse message failed: text={}, msg={}", text, e.getMessage(), e);
            // propagate error to Flux sinks so subscribers observe the failure
            emittersError("parse message failed", e);
        }
    }

    private void subscribeTextStream() {
        this.textStream
            .doOnNext(textChunk -> {
                String continueMsg = buildContinueTaskMessage(textChunk);
                logger.debug("CosyVoice streaming: sending continue-task for text chunk, length={}", textChunk.length());
                sendText(continueMsg);
            })
            .doOnError(err -> {
                logger.error("CosyVoice streaming: text stream error", err);
                emittersError("text stream error", err);
            })
            .doOnComplete(() -> {
                logger.info("CosyVoice streaming: text stream completed, sending finish-task");
                sendText(this.finishTaskMessage);
            })
            .subscribe();
    }

    private String buildContinueTaskMessage(String textChunk) {
        try {
            var node = this.objectMapper.readTree(this.continueTaskTemplate);
            if (node.has("payload") && node.get("payload").has("input")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("payload").get("input"))
                    .put("text", textChunk);
            }
            return this.objectMapper.writeValueAsString(node);
        }
        catch (Exception e) {
            logger.error("Failed to build continue-task message from template", e);
            throw new RuntimeException("Failed to build continue-task message", e);
        }
    }

    private void subscribeBinaryStream() {
        this.binaryStream
            .filter(chunk -> chunk != null && chunk.hasRemaining())
            .doOnNext(chunk -> {
                logger.debug("ASR streaming: sending binary chunk, size={}", chunk.remaining());
                sendBinary(chunk);
            })
            .doOnError(err -> {
                logger.error("ASR streaming: binary stream error", err);
                emittersError("binary stream error", err);
            })
            .doOnComplete(() -> {
                logger.info("ASR streaming: binary stream completed, sending finish-task");
                sendText(this.finishTaskMessage);
            })
            .subscribe();
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        logger.debug("receive ws event onMessage(bytes): handle={}, size={}", webSocket, bytes.size());
        try {
            ByteBuffer audioData = bytes.asByteBuffer();
            if (this.binaryEmitter != null && !this.binaryEmitter.isCancelled()) {
                this.binaryEmitter.next(audioData);
            } else {
                logger.warn("binaryEmitter is null or cancelled when receiving audio bytes");
            }
        } catch (Exception e) {
            logger.error("error handling incoming binary message", e);
            emittersError("binary message handling error", e);
        }

    }

    private void emittersComplete(String event) {
        if (this.binaryEmitter != null && !this.binaryEmitter.isCancelled()) {
            logger.info("binary emitter handling: complete on {}", event);
            this.binaryEmitter.complete();
        }
        if (this.textEmitter != null && !this.textEmitter.isCancelled()) {
            logger.info("text emitter handling: complete on {}", event);
            this.textEmitter.complete();
            logger.info("done");
        }
    }

    private void emittersError(String event, Throwable t) {
        if (this.binaryEmitter != null && !this.binaryEmitter.isCancelled()) {
            logger.info("binary emitter handling: error on {}", event);
            this.binaryEmitter.error(t);
        }
        if (this.textEmitter != null && !this.textEmitter.isCancelled()) {
            logger.info("text emitter handling: error on {}", event);
            this.textEmitter.error(t);
        }
    }

    public static class Constants {

        private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(120);

        private static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(60);

        private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(300);

        private static final Duration DEFAULT_CONNECTION_IDLE_TIMEOUT = Duration.ofSeconds(300);

        private static final Integer DEFAULT_READY_TIMEOUT = 1;

        private static final Integer DEFAULT_CONNECTION_POOL_SIZE = 32;

        private static final Integer DEFAULT_MAXIMUM_ASYNC_REQUESTS = 32;

        private static final Integer DEFAULT_MAXIMUM_ASYNC_REQUESTS_PER_HOST = 32;

        private static final String DEFAULT_HTTP_LOGGING_LEVEL = "NONE";

    }

    public enum EventType {

        @JsonProperty("task-started")
        TASK_STARTED("task-started"),

        @JsonProperty("result-generated")
        RESULT_GENERATED("result-generated"),

        @JsonProperty("task-finished")
        TASK_FINISHED("task-finished"),

        @JsonProperty("task-failed")
        TASK_FAILED("task-failed"),

        @JsonProperty("run-task")
        RUN_TASK("run-task"),

        @JsonProperty("continue-task")
        CONTINUE_TASK("continue-task"),

        @JsonProperty("finish-task")
        FINISH_TASK("finish-task");

        private final String value;

        private EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
