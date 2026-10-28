package com.ccat;

import com.ccat.candy.SqlCandyDatabase;
import com.ccat.listeners.TestListener;
import com.ccat.listeners.TrickOrTreatListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    private static final String BOT_TOKEN = System.getenv("DISCORD_TOKEN");

    public static void main(String[] args) {

        if (BOT_TOKEN == null || BOT_TOKEN.isBlank()) {
            throw new IllegalStateException(
                    "Set the DISCORD_TOKEN environment variable."
            );
        }

        SqlCandyDatabase database = null;
        try {
            database = new SqlCandyDatabase();
        } catch (SQLException | IOException e) {
            System.out.print("Failed to initialize SQL Candy Database");
            throw new RuntimeException(e);
        }

        JDA api = JDABuilder.createDefault(
                        BOT_TOKEN,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .addEventListeners(
                        new TrickOrTreatListener(database)
                )
                .build();

        try {
            api.awaitReady();
        } catch (InterruptedException e) {
            System.out.print("Failed to initialize Bot.");
            throw new RuntimeException(e);
        }

        api.addEventListener(new TestListener());

        System.out.print("✅ Initialization finished!");


        // ─── SHUTDOWN HOOK ──────────────────────────────────────
        SqlCandyDatabase finalDatabase = database;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("⚠️ JVM is shutting down. Closing database connection...");
            try {
                finalDatabase.close();
                System.out.println("✅ Database closed successfully.");
            } catch (Exception e) {
                System.err.println("❌ Failed to close database safely during shutdown.");
                e.printStackTrace();
            }
        }));
    }
}