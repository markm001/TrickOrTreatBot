package com.ccat.listeners;

import com.ccat.candy.Candy;
import com.ccat.candy.SqlCandyDatabase;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TrickOrTreatListener extends ListenerAdapter {

    private final SqlCandyDatabase database;

    public TrickOrTreatListener(SqlCandyDatabase database) {
        this.database = database;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String message = event.getMessage()
                .getContentRaw().trim();

        if (message.equalsIgnoreCase("trick or treat")) {
            String userId = event.getAuthor().getId();

            try {
                long remaining = database.tryStartCooldown(userId);

                if (remaining > 0) {
                    long seconds = (remaining + 999) / 1000;
                    long minutes = seconds / 60;
                    long leftoverSeconds = seconds % 60;

                    event.getMessage().reply(
                            "\uD83E\uDDDB Be patient, you loot-goblin! You've had enough candy for now. "
                                    + "Wait **" + minutes + "m "
                                    + leftoverSeconds
                                    + "s** before trying again."
                    ).queue();

                    return;
                }

                String promptMessage = Math.random() < 0.5
                        ? "Give them candy or suffer the consequences! \uD83D\uDC7B"
                        : "Are you sharing some candy? 🍬";

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🎃 Trick or Treat!")
                        .setDescription(
                                event.getAuthor().getAsMention()
                                        + " is trick-or-treating!\n\n"
                                        + promptMessage)
                        .setColor(new Color(255, 140, 0))
                        .setThumbnail(event.getAuthor().getAvatarUrl())
                        .setFooter("Halloween Candy Exchange");

                event.getChannel()
                        .sendMessageEmbeds(embed.build())
                        .addComponents(
                                ActionRow.of(
                                        Button.success("candy:" + userId,"🍬 Give Candy"
                                        ))
                        )
                        .queue();

            } catch (SQLException e) {
                event.getChannel()
                        .sendMessage("\uD83D\uDD78\uFE0F Looks like the candy jar is empty! Please try again."
                ).queue();

                e.printStackTrace();
            }

            return;
        }

        // ─── LEADERBOARD ──────────────────────────────────────
        if (message.equalsIgnoreCase("!candy leaderboard")) {
            sendLeaderboard(event);
            return;
        }

        // ─── CANDY COUNT ──────────────────────────────────────
        if (message.equalsIgnoreCase("!candy")) {
            event.getChannel().sendMessage(
                    "🍬 Usage: !candy @user"
            ).queue();
            return;
        }

        if (message.toLowerCase().startsWith("!candy ")) {
            var mentionedUsers =
                    event.getMessage().getMentions().getUsers();

            if (mentionedUsers.isEmpty()) {
                event.getChannel().sendMessage(
                        "\uD83D\uDC08\u200D⬛ You have to mention a user! Example: !candy @user"
                ).queue();
                return;
            }

            var target = mentionedUsers.get(0);

            try {
                var inventory =
                        database.getCandyInventory(target.getId());

                if (inventory.isEmpty()) {
                    event.getChannel().sendMessage(
                            "\uD83D\uDE2D " + target.getAsMention()
                                    + " hasn't received any candy yet!"
                    ).queue();
                    return;
                }

                long total = inventory.values().stream()
                        .mapToLong(Long::longValue)
                        .sum();

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🍬 Candy Bag — "
                                + target.getEffectiveName())
                        .setDescription("Total received: "
                                + total + " pieces")
                        .setColor(new Color(255, 140, 0));

                for (var entry : inventory.entrySet()) {
                    Candy candy = Candy.valueOf(entry.getKey());

                    embed.addField(
                            candy.getEmoji() + " "
                                    + candy.getDisplayName(),
                            "" + entry.getValue() + "",
                            true
                    );
                }

                event.getChannel()
                        .sendMessageEmbeds(embed.build())
                        .queue();

            } catch (SQLException e) {
                event.getChannel().sendMessage(
                        "\uD83D\uDE28 You were unable to view the candy inventory. Please try again."
                ).queue();

                e.printStackTrace();
            }
        }
    }

    @Override
    public void onButtonInteraction(
            ButtonInteractionEvent event) {

        String componentId = event.getComponentId();

        if (!componentId.startsWith("candy:")) return;

        String recipientId =
                componentId.substring("candy:".length());

        String giverId = event.getUser().getId();

        if (giverId.equals(recipientId)) {
            event.reply("Hey, no cheating! You can't give candy to yourself! ☠\uFE0F")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            // regular || nothing (coal) || steal || multi
            double roll = Math.random();

            if (roll < 0.80) {
                giveCandy(giverId, recipientId, event);
            } else if (roll < 0.90) {
                giveCoal(giverId, recipientId, event);
            } else {
                takeCandy(giverId, recipientId, event);
            }

        } catch (SQLException e) {
            event.reply(
                    "🎃 Oh no! <@"
                            + recipientId
                            + "> 's bucket is already full. Please try again."
            ).setEphemeral(true).queue();

            e.printStackTrace();
        }
    }

    private void giveCandy(String giverId, String recipientId, ButtonInteractionEvent event) throws SQLException {
        boolean multi = Math.random() < 0.2;

        if (multi) {
            Candy candy = Candy.randomMulti();

            boolean added = database.tryAddTwoGifts(
                    giverId,
                    recipientId,
                    candy,
                    event.getChannel().getId(),
                    event.getMessageId()
            );

            if (!added) {
                event.reply(
                        "🍬 You've already given candy to this trick-or-treater!"
                ).setEphemeral(true).queue();

                return;
            }

            event.reply(
                    "\uD83E\uDDD9 <@"
                            + giverId + "> has been generous and offered <@"
                            + recipientId + "> "
                            + candy.getDisplayMessage()
                            + "! "
                            + candy.getEmoji()
            ).queue();

        } else {
            Candy candy = Candy.random();

            boolean added = database.tryAddGift(
                    giverId,
                    recipientId,
                    candy,
                    event.getChannel().getId(),
                    event.getMessageId()
            );

            if (!added) {
                event.reply(
                        "🍬 You've already given candy to this trick-or-treater!"
                ).setEphemeral(true).queue();

                return;
            }

            event.reply(
                    "🍬 <@"
                            + giverId
                            + "> gives <@"
                            + recipientId
                            + "> "
                            + candy.getDisplayMessage()
                            + "! " + candy.getEmoji()
            ).queue();
        }
    }

    private void takeCandy(String giverId, String recipientId, ButtonInteractionEvent event) throws SQLException {
        boolean removed = database.removeRandomCandy(recipientId);

        if (!removed) {
            event.reply(
                    "🍬 You attempted to steal <@" + recipientId + "> 's candy, but they evaded your vicious grasp."
            ).setEphemeral(true).queue();

            return;
        }

        event.reply(
                "\uD83D\uDD77\uFE0F Oh no! <@"
                        + giverId
                        + "> sneakily reached into <@"
                        + recipientId
                        + "> bucket and stole their candy!"
        ).queue();
    }
    private void giveCoal(String giverId, String recipientId, ButtonInteractionEvent event) {
        event.reply(
                "\uD83E\uDDDF With a malicious grin <@"
                        + giverId + "> hands <@"
                        + recipientId + "> a wrapped piece of candy. Upon closer inspection it's a rock in a wrapper. \uD83E\uDEA8"
        ).queue();

    }

    private void sendLeaderboard(MessageReceivedEvent event) {
        List<Map.Entry<String, Long>> results = null;
        try {
            results = database.getSortedLeaderboard();
        } catch (SQLException e) {
            event.getChannel()
                    .sendMessage("Failed to retrieve leaderboard. Please try again later."
            ).queue();

            e.printStackTrace();

            return;
        }

        if (Objects.requireNonNull(results).isEmpty()) {
            event.getChannel().sendMessage(
                    "🍬 Nobody has received candy yet!"
            ).queue();
            return;
        }

        StringBuilder description = new StringBuilder();
        int limit = Math.min(10, results.size());

        for (int i = 0; i < limit; i++) {
            var entry = results.get(i);

            description.append("**")
                    .append(i + 1)
                    .append(".** <@")
                    .append(entry.getKey())
                    .append("> — ")
                    .append(entry.getValue())
                    .append(" pieces\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏆 Halloween Candy Leaderboard")
                .setDescription(description.toString())
                .setColor(new Color(255, 140, 0));

        event.getChannel()
                .sendMessageEmbeds(embed.build())
                .queue();
    }
}