package com.codepocalypse.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Pattern;

/**
 * File-backed chat memory — one JSON file per memory id under
 * {@code ~/.jclaw/memory/<id>.json}. Picked up automatically by
 * Quarkus LangChain4j because it's the sole {@link ChatMemoryStore} bean.
 */
@ApplicationScoped
public class FileChatMemoryStore implements ChatMemoryStore {

    private static final Logger LOG = Logger.getLogger(FileChatMemoryStore.class);
    private static final Pattern SAFE_ID = Pattern.compile("[^a-zA-Z0-9._-]");

    private Path root;

    @PostConstruct
    void init() {
        this.root = Path.of(System.getProperty("user.home"), ".jclaw", "memory");
        try {
            Files.createDirectories(root);
            LOG.infof("JClaw memory store initialized at %s", root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create memory directory " + root, e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Path file = fileFor(memoryId);
        if (!Files.exists(file)) {
            LOG.debugf("No prior memory for id=%s", memoryId);
            return List.of();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(json);
            LOG.infof("Loaded %d messages for id=%s", messages.size(), memoryId);
            return messages;
        } catch (IOException e) {
            LOG.warnf(e, "Failed to read memory file %s — starting fresh", file);
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Path file = fileFor(memoryId);
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            Files.writeString(
                    file,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            LOG.debugf("Persisted %d messages for id=%s", messages.size(), memoryId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory file " + file, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Path file = fileFor(memoryId);
        try {
            Files.deleteIfExists(file);
            LOG.infof("Deleted memory for id=%s", memoryId);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to delete memory file %s", file);
        }
    }

    private Path fileFor(Object memoryId) {
        String safe = SAFE_ID.matcher(String.valueOf(memoryId)).replaceAll("_");
        return root.resolve(safe + ".json");
    }
}
