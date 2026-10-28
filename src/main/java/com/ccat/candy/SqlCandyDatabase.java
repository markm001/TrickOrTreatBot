package com.ccat.candy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class SqlCandyDatabase implements AutoCloseable {

    private static final Path DB_PATH = Path.of("data", "candy.db");

    private static final long COOLDOWN_MS =
            5 * 60 * 1000L;

    private final Connection connection;

    public SqlCandyDatabase() throws IOException, SQLException {
        Files.createDirectories(DB_PATH.getParent());

        connection = DriverManager.getConnection(
                "jdbc:sqlite:" + DB_PATH.toAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 10000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = FULL");

            statement.execute("""
                CREATE TABLE IF NOT EXISTS gifts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    giver_id TEXT NOT NULL,
                    recipient_id TEXT NOT NULL,
                    candy TEXT NOT NULL,
                    channel_id TEXT NOT NULL,
                    message_id TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    UNIQUE(message_id, giver_id)
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS cooldowns (
                    user_id TEXT PRIMARY KEY,
                    last_request_ms INTEGER NOT NULL
                )
                """);
        }
    }

    /**
     * Returns 0 if accepted, otherwise the remaining
     * cooldown in milliseconds.
     */
    public synchronized long tryStartCooldown(
            String userId) throws SQLException {

        long now = System.currentTimeMillis();

        try {
            connection.setAutoCommit(false);

            long lastRequest = -1;

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT last_request_ms FROM cooldowns "
                            + "WHERE user_id = ?")) {
                ps.setString(1, userId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        lastRequest = rs.getLong(1);
                    }
                }
            }

            if (lastRequest >= 0) {
                long remaining =
                        COOLDOWN_MS - (now - lastRequest);

                if (remaining > 0) {
                    connection.rollback();
                    return remaining;
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO cooldowns(user_id, last_request_ms)
                    VALUES (?, ?)
                    ON CONFLICT(user_id)
                    DO UPDATE SET last_request_ms = excluded.last_request_ms
                    """)) {
                ps.setString(1, userId);
                ps.setLong(2, now);
                ps.executeUpdate();
            }

            connection.commit();
            return 0;

        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Records a gift only if this user hasn't already
     * given candy on this message.
     */
    public synchronized boolean tryAddGift(
            String giverId,
            String recipientId,
            Candy candy,
            String channelId,
            String messageId) throws SQLException {

        String sql = """
            INSERT INTO gifts (
                giver_id, recipient_id, candy,
                channel_id, message_id, timestamp
            )
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(message_id, giver_id) DO NOTHING
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, giverId);
            ps.setString(2, recipientId);
            ps.setString(3, candy.name());
            ps.setString(4, channelId);
            ps.setString(5, messageId);
            ps.setString(6, Instant.now().toString());

            return ps.executeUpdate() == 1;
        }
    }

    public synchronized List<Map.Entry<String, Long>>
    getSortedLeaderboard() throws SQLException {

        List<Map.Entry<String, Long>> results =
                new ArrayList<>();

        String sql = """
            SELECT recipient_id, COUNT(*) AS candy_count
            FROM gifts
            GROUP BY recipient_id
            ORDER BY candy_count DESC, recipient_id ASC
            """;

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            while (rs.next()) {
                results.add(new AbstractMap.SimpleImmutableEntry<>(
                        rs.getString("recipient_id"),
                        rs.getLong("candy_count")
                ));
            }
        }

        return results;
    }

    public synchronized Map<String, Long> getCandyInventory(
            String recipientId) throws SQLException {

        Map<String, Long> inventory = new LinkedHashMap<>();

        String sql = """
        SELECT candy, COUNT(*) AS quantity
        FROM gifts
        WHERE recipient_id = ?
        GROUP BY candy
        ORDER BY quantity DESC, candy ASC
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recipientId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    inventory.put(
                            rs.getString("candy"),
                            rs.getLong("quantity")
                    );
                }
            }
        }

        return inventory;
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}
