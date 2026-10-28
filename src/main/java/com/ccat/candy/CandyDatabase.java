package com.ccat.candy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CandyDatabase {

    private static final Path FILE =
            Path.of("data", "candy-gifts.json");

    private static final long COOLDOWN_MS =
            10 * 60 * 1000L;

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private State state = new State();

    public CandyDatabase() throws IOException {
        Files.createDirectories(FILE.getParent());

        if (Files.exists(FILE)) {
            State loaded = GSON.fromJson(
                    Files.readString(FILE), State.class);

            if (loaded != null) {
                state = loaded;
            }
        }

        // Handle missing fields in older JSON files.
        if (state.gifts == null)
            state.gifts = new ArrayList<>();

        if (state.votersByMessage == null)
            state.votersByMessage = new HashMap<>();

        if (state.lastRequestMillis == null)
            state.lastRequestMillis = new HashMap<>();
    }

    private void save() throws IOException {
        Files.writeString(FILE, GSON.toJson(state));
    }

    /**
     * Returns 0 if the request is accepted.
     * Otherwise, returns the remaining cooldown in milliseconds.
     */
    public synchronized long tryStartCooldown(
            String userId) throws IOException {

        long now = System.currentTimeMillis();

        Long lastRequest =
                state.lastRequestMillis.get(userId);

        if (lastRequest != null) {
            long remaining =
                    COOLDOWN_MS - (now - lastRequest);

            if (remaining > 0) {
                return remaining;
            }
        }

        // Roll back if saving fails.
        Long previous = state.lastRequestMillis.put(
                userId, now);

        try {
            save();
        } catch (IOException e) {
            if (previous == null) {
                state.lastRequestMillis.remove(userId);
            } else {
                state.lastRequestMillis.put(userId, previous);
            }
            throw e;
        }

        return 0;
    }

    /**
     * Records a gift only if this giver hasn't already
     * given candy on this particular message.
     */
    public synchronized boolean tryAddGift(
            String giverId,
            String recipientId,
            Candy candy,
            String channelId,
            String messageId) throws IOException {

        Set<String> voters = state.votersByMessage
                .computeIfAbsent(
                        messageId, k -> new HashSet<>());

        if (voters.contains(giverId)) {
            return false;
        }

        Gift gift = new Gift(
                giverId,
                recipientId,
                candy.name(),
                channelId,
                messageId,
                java.time.Instant.now().toString()
        );

        voters.add(giverId);
        state.gifts.add(gift);

        try {
            save();
        } catch (IOException e) {
            voters.remove(giverId);
            state.gifts.remove(gift);

            if (voters.isEmpty()) {
                state.votersByMessage.remove(messageId);
            }

            throw e;
        }

        return true;
    }

    public synchronized List<Map.Entry<String, Long>>
    getSortedLeaderboard() {

        return state.gifts.stream()
                .collect(Collectors.groupingBy(
                        gift -> gift.recipientId,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue()
                                .reversed()
                                .thenComparing(
                                        Map.Entry.comparingByKey())
                )
                .toList();
    }

    private static class State {
        List<Gift> gifts = new ArrayList<>();

        Map<String, Set<String>> votersByMessage =
                new HashMap<>();

        Map<String, Long> lastRequestMillis =
                new HashMap<>();
    }

    private static class Gift {
        String giverId;
        String recipientId;
        String candy;
        String channelId;
        String messageId;
        String timestamp;

        Gift(String giverId, String recipientId,
             String candy, String channelId,
             String messageId, String timestamp) {

            this.giverId = giverId;
            this.recipientId = recipientId;
            this.candy = candy;
            this.channelId = channelId;
            this.messageId = messageId;
            this.timestamp = timestamp;
        }
    }
}
