package com.gucare.health;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.ResponseFormat;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.ThinkingConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

final class ReportModel {
    private static final String ASSET = "Qwen3-0.6B.litertlm";
    private static final String SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108";
    private static final long REQUIRED_FREE_BYTES = 750_000_000;
    private static final long MIN_RAM_BYTES = 5L * 1024 * 1024 * 1024;

    private final Context context;
    private final File file;
    private final Object conversationLock = new Object();
    private Conversation activeConversation;
    private String failureReason = "";

    String failureReason() { return failureReason; }

    ReportModel(Context context) {
        this.context = context;
        file = new File(context.getNoBackupFilesDir(), "qwen3-e668717.litertlm");
    }

    boolean supported() {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        return Process.is64Bit() && memory.totalMem >= MIN_RAM_BYTES && !memory.lowMemory;
    }

    boolean prepare() {
        if (file.isFile()) return true;
        if (file.getParentFile().getUsableSpace() < REQUIRED_FREE_BYTES) return false;
        File temporary = new File(file.getParentFile(), file.getName() + ".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = context.getAssets().open(ASSET); FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[256 * 1024];
                int read;
                while (!Thread.currentThread().isInterrupted() && (read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                if (Thread.currentThread().isInterrupted()) return false;
                out.getFD().sync();
            }
            byte[] expected = new byte[32];
            for (int i = 0; i < expected.length; i++) {
                expected[i] = (byte) Integer.parseInt(SHA256.substring(i * 2, i * 2 + 2), 16);
            }
            if (!MessageDigest.isEqual(expected, digest.digest())) return false;
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | NoSuchAlgorithmException error) {
            return false;
        } finally {
            try { Files.deleteIfExists(temporary.toPath()); } catch (IOException ignored) {}
        }
    }

    String generate(String prompt, String allowedChoices) {
        if (!supported() || Thread.currentThread().isInterrupted()) {
            failureReason = "Device unavailable or cancelled";
            return null;
        }
        try {
            Engine.Companion.setNativeMinLogSeverity(LogSeverity.INFINITY);
            EngineConfig engineConfig = new EngineConfig(file.getAbsolutePath(), new Backend.CPU(4, null),
                    null, null, 1024, null, ":nocache");
            try (Engine engine = new Engine(engineConfig)) {
                engine.initialize();
                if (Thread.currentThread().isInterrupted()) return null;
                ThinkingConfig thinking = new ThinkingConfig(false);
                ConversationConfig config = new ConversationConfig(null, Collections.emptyList(),
                        Collections.emptyList(), new SamplerConfig(1, 1.0, 0.0, 0), false, null,
                        Collections.emptyMap(), null, false, 4, thinking, true);
                try (Conversation conversation = engine.createConversation(config)) {
                    synchronized (conversationLock) {
                        if (Thread.currentThread().isInterrupted()) return null;
                        activeConversation = conversation;
                    }
                    try {
                        Message response = conversation.sendMessage(prompt,
                                Collections.singletonMap("enable_thinking", false), null, null, null, 4, thinking,
                                ResponseFormat.regex("[" + allowedChoices + "]"));
                        return response.toString();
                    } finally {
                        synchronized (conversationLock) { activeConversation = null; }
                    }
                }
            }
        } catch (RuntimeException | LinkageError | OutOfMemoryError error) {
            // The input contains only controlled item/status codes. Retain diagnostics in RAM for device verification.
            failureReason = error.getClass().getSimpleName() + ": " + error.getMessage();
            return null;
        }
    }

    void cancel() {
        synchronized (conversationLock) {
            if (activeConversation != null) {
                try { activeConversation.cancelProcess(); } catch (RuntimeException ignored) {}
            }
        }
    }
}
