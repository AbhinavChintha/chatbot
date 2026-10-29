package com.abhinav.agentic_ai_chatbot.agent;

import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import com.abhinav.agentic_ai_chatbot.dto.SupervisorDecision;
import com.abhinav.agentic_ai_chatbot.service.ChatLatencyMetrics;
import com.abhinav.agentic_ai_chatbot.service.SupervisorService;
import com.abhinav.agentic_ai_chatbot.service.GenerationPolicy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Component
public class SupervisorAgent implements DisposableBean {

    private static final Logger log =
            LoggerFactory.getLogger(SupervisorAgent.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Function<ClassificationPrompt, String> classifier;
    private final SupervisorService supervisorService;
    private final ChatLatencyMetrics latencyMetrics;
    private final Duration classificationTimeout;
    private final ThreadPoolExecutor classificationExecutor;

    @Autowired
    public SupervisorAgent(
            ChatClient.Builder chatClientBuilder,
            SupervisorService supervisorService,
            ChatLatencyMetrics latencyMetrics,
            GenerationPolicy generationPolicy,
            @Value("${app.ai.classification-timeout:PT8S}") Duration classificationTimeout) {

        this(llmClassifier(chatClientBuilder.build(), generationPolicy),
                supervisorService, latencyMetrics, classificationTimeout);
    }

    SupervisorAgent(
            Function<ClassificationPrompt, String> classifier,
            SupervisorService supervisorService,
            ChatLatencyMetrics latencyMetrics,
            Duration classificationTimeout) {

        if (classificationTimeout == null
                || classificationTimeout.isNegative()
                || classificationTimeout.isZero()) {
            throw new IllegalArgumentException("Classification timeout must be positive.");
        }
        this.classifier = classifier;
        this.supervisorService = supervisorService;
        this.latencyMetrics = latencyMetrics;
        this.classificationTimeout = classificationTimeout;

        AtomicInteger threadNumber = new AtomicInteger();
        this.classificationExecutor = new ThreadPoolExecutor(
                4, 16, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "classification-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static Function<ClassificationPrompt, String> llmClassifier(
            ChatClient chatClient, GenerationPolicy generationPolicy) {
        return prompt -> chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .options(generationPolicy.classification())
                .call()
                .content();
    }

    /**
     * Routes the message, then executes the selected tools.
     * Trivial messages skip LLM classification; slow or failed
     * classification falls back to keyword routing.
     */
    public ChatResponse process(
            String message,
            String conversationContext) {

        long classificationStart = System.nanoTime();
        SupervisorDecision decision = decide(message, conversationContext);

        log.info(
                "Agent latency | stage=classification | durationMs={}",
                elapsedMs(classificationStart)
        );
        log.info("SUPERVISOR DECISION: {}", decision);

        long executionStart = System.nanoTime();

        try {
            return latencyMetrics.measure(
                    "tool_execution",
                    () -> supervisorService.execute(decision, message)
            );
        } finally {
            log.info(
                    "Agent latency | stage=toolExecutionAndResponse | durationMs={}",
                    elapsedMs(executionStart)
            );
        }
    }

    SupervisorDecision decide(String message, String conversationContext) {
        Optional<SupervisorDecision> employee = EmployeeLookupRouter.route(message, conversationContext);
        if (employee.isPresent()) {
            log.info("Classification skipped | reason=explicitEmployeeLookup");
            return latencyMetrics.measure("database_fast_path", employee::get);
        }
        Optional<SupervisorDecision> quick = QuickIntentRouter.fastPath(message);
        if (quick.isPresent()) {
            log.info("Classification skipped | reason=fastPath");
            latencyMetrics.measure("classification_fast_path", () -> null);
            return quick.get();
        }

        try {
            return latencyMetrics.measure(
                    "classification",
                    () -> classify(message, conversationContext)
            );
        } catch (RuntimeException e) {
            log.warn("Classification fallback | reason={}", e.getMessage());
            return latencyMetrics.measure(
                    "classification_fallback",
                    () -> QuickIntentRouter.fallback(message)
            );
        }
    }

    /**
     * Classifies the user request into DATABASE, RAG,
     * WEB_SEARCH, GENERAL, or MULTI_TOOL using the LLM,
     * bounded by the configured timeout.
     */
    public SupervisorDecision classify(
            String message,
            String conversationContext) {

        ClassificationPrompt prompt =
                ClassificationPrompt.build(message, conversationContext);

        Future<String> call;
        try {
            call = classificationExecutor.submit(() -> classifier.apply(prompt));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new IllegalStateException("Classification capacity exhausted.", e);
        }

        try {
            String response = call.get(
                    classificationTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return parseDecision(response);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new IllegalStateException(
                    "Classification exceeded " + classificationTimeout.toMillis() + " ms.", e);
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Classification interrupted.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Classification call failed: " + e.getCause().getClass().getSimpleName(),
                    e.getCause());
        }
    }

    /**
     * Parses the JSON object returned by the classification model,
     * ignoring markdown fences, reasoning text and unknown properties.
     */
    static SupervisorDecision parseDecision(String response) {
        if (response == null) {
            throw new IllegalStateException("Classification returned no content.");
        }
        String content = response.replaceAll("(?s)<think>.*?</think>", "");
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Classification returned no JSON object.");
        }
        try {
            SupervisorDecision decision = OBJECT_MAPPER.readValue(
                    content.substring(start, end + 1), SupervisorDecision.class);
            if (decision.getIntent() == null || decision.getIntent().isBlank()) {
                throw new IllegalStateException("Classification returned no intent.");
            }
            return decision;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Supervisor Agent response.", e);
        }
    }

    @Override
    public void destroy() {
        classificationExecutor.shutdownNow();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
