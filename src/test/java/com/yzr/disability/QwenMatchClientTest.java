package com.yzr.disability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yzr.disability.config.QwenProperties;
import com.yzr.disability.service.ai.QwenMatchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 模拟HTTP及等待结果，不连接千问、数据库或Redis，不读取环境中的密钥。 */
class QwenMatchClientTest {
    private final QwenProperties properties = new QwenProperties();
    private final HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    private final CompletableFuture<HttpResponse<Void>> pending = mock(CompletableFuture.class);
    private final Map<String, Object> input = Map.of("resume", "private-resume-marker");

    private QwenMatchClient client() {
        properties.setApiKey("test-only-key-marker");
        QwenMatchClient client = new QwenMatchClient(properties, new ObjectMapper());
        ReflectionTestUtils.setField(client, "client", http);
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(pending);
        return client;
    }

    @Test
    void defaultsAndPositiveTimeoutConfigurationAreValidated() {
        assertEquals(5, properties.getConnectTimeoutSeconds());
        assertEquals(45, properties.getRequestTimeoutSeconds());
        properties.setConnectTimeoutSeconds(7);
        var client = new QwenMatchClient(properties, new ObjectMapper());
        var transport = (HttpClient) ReflectionTestUtils.getField(client, "client");
        assertEquals(Duration.ofSeconds(7), transport.connectTimeout().orElseThrow());
        transport.close();
        properties.setRequestTimeoutSeconds(0);
        assertThrows(IllegalArgumentException.class, () -> new QwenMatchClient(properties, new ObjectMapper()));
        properties.setRequestTimeoutSeconds(45); properties.setConnectTimeoutSeconds(-1);
        assertThrows(IllegalArgumentException.class, () -> new QwenMatchClient(properties, new ObjectMapper()));
    }

    @ParameterizedTest
    @ValueSource(ints = {45, 75})
    void requestAndFullBodyWaitShareTheConfiguredDeadlineAndCancelOnTimeout(int seconds) throws Exception {
        properties.setRequestTimeoutSeconds(seconds);
        QwenMatchClient client = client();
        when(pending.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
        var error = assertThrows(QwenMatchClient.UnavailableException.class, () -> client.analyze(input));
        assertTrue(error.getMessage().contains("响应超时"));
        verify(pending).get(seconds * 1000L, TimeUnit.MILLISECONDS);
        verify(pending).cancel(true);
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        assertEquals(Duration.ofSeconds(seconds), request.getValue().timeout().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONNECT_TIMEOUT", "RESPONSE_TIMEOUT", "CALL_FAILED"})
    void logsOnlySafeErrorCategoriesAndDistinguishesTimeouts(String category) throws Exception {
        QwenMatchClient client = client();
        Throwable cause = switch (category) {
            case "CONNECT_TIMEOUT" -> new HttpConnectTimeoutException("private-exception-marker");
            case "RESPONSE_TIMEOUT" -> new HttpTimeoutException("private-exception-marker");
            default -> new IOException("private-exception-marker");
        };
        when(pending.get(anyLong(), any(TimeUnit.class))).thenThrow(new ExecutionException(cause));
        Logger logger = (Logger) LoggerFactory.getLogger(QwenMatchClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); logger.addAppender(appender);
        try {
            var error = assertThrows(QwenMatchClient.UnavailableException.class, () -> client.analyze(input));
            assertTrue(error.getMessage().contains(switch (category) {
                case "CONNECT_TIMEOUT" -> "连接超时";
                case "RESPONSE_TIMEOUT" -> "响应超时";
                default -> "调用失败";
            }));
            assertEquals(1, appender.list.size());
            String message = appender.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("outcome=" + category));
            assertTrue(message.contains("elapsedMs="));
            assertFalse(message.contains("private-")); assertFalse(message.contains("test-only-key-marker"));
            assertNull(appender.list.getFirst().getThrowableProxy());
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rateLimitingKeepsItsOwnFallbackMessage() throws Exception {
        QwenMatchClient client = client();
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(pending.get(anyLong(), any(TimeUnit.class))).thenReturn(response);
        assertTrue(assertThrows(QwenMatchClient.UnavailableException.class, () -> client.analyze(input))
                .getMessage().contains("限流"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validCompleteResponseStillParsesNormally() {
        QwenMatchClient client = client();
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenAnswer(call -> {
                    HttpResponse.BodyHandler<Void> handler = call.getArgument(1);
                    var subscriber = handler.apply(mock(HttpResponse.ResponseInfo.class));
                    subscriber.onSubscribe(mock(Flow.Subscription.class));
                    byte[] body = """
                            {"choices":[{"finish_reason":"stop","message":{"content":"{\\"items\\":[]}"}}]}
                            """.getBytes(StandardCharsets.UTF_8);
                    subscriber.onNext(List.of(ByteBuffer.wrap(body))); subscriber.onComplete();
                    return CompletableFuture.completedFuture(response);
                });
        assertTrue(client.analyze(input).get("items").isArray());
    }
}
