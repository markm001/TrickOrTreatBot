package com.ccat.listeners;

import com.ccat.candy.Candy;
import com.ccat.candy.SqlCandyDatabase;
import com.ccat.candy.StealResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ccat.candy.StealResult.*;

public class TrickOrTreatListener extends ListenerAdapter {

    private static final List<String> COAL_MESSAGES = List.of(
            "proudly hands <@RECIPIENT> a Danish Butter Cookie jar. After opening it, they discover grandmas sewing kit. 🧵",
            "hands a wrapped piece of candy. Upon closer inspection, it's a rock in a wrapper. 🪨",
            "hands a button to <@RECIPIENT>. It tastes just like a button. 🧵",
            "hands <@RECIPIENT> a piece of candy from last Halloween. It's somehow both stale and suspiciously moist. 🎃",
            "reaches into the bag and pulls out a dusty candy corn from 2004. <@RECIPIENT> watches in sheer horror. 🌽",
            "hands <@RECIPIENT> a chocolate bar that expired five years ago. 🍫",
            "proudly presents <@RECIPIENT> with a single raisin. 🍇",
            "hands <@RECIPIENT> a wrapped candy. It rattles when shaken. Nobody dares investigate. 👻",
            "gives <@RECIPIENT> a suspiciously warm candy. It is best not to ask questions. 🤢",
            "reaches into the candy bag and pulls out... absolutely nothing. The disappointment is immeasurable and their day is ruined. 💀",
            "generously offers <@RECIPIENT> a bag of Marshmallows. Upon closer inspection, they are rock hard. 🥲",
            "gives <@RECIPIENT> a handful of candy wrappers. Who ate all that candy? \uD83E\uDD87"
    );

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


        // ─── STEAL CANDY ─────────────────────────────────────
        MessageChannelUnion channel = event.getChannel();

        if (message.startsWith("!steal ")) {
            if (event.getMessage().getMentions().getUsers().isEmpty()) {
                channel.sendMessage("Usage: `!steal @user`").queue();
                return;
            }

            User giver = event.getAuthor();
            User recipient = event.getMessage().getMentions().getUsers().get(0);

            if (giver.getId().equals(recipient.getId())) {
                channel.sendMessage("🪞 You can't steal candy from yourself!").queue();
                return;
            }

            double roll = Math.random();

            boolean success;
            boolean payPrice;

            if (roll < 0.60) {
                success = true;
                payPrice = true;
            } else if (roll < 0.70) {
                success = true;
                payPrice = false;
            } else if (roll < 0.90) {
                success = false;
                payPrice = true;
            } else {
                success = false;
                payPrice = false;
            }

            try {
                if (!success) {
                    if (payPrice) {
                        StealResult result = database.tryStealCandy(giver.getId(), recipient.getId(), true);

                        if (result == StealResult.INSUFFICIENT_CANDY) {
                            channel.sendMessage(
                                    "🕷️ " + giver.getAsMention()
                                            + " doesn't have enough candy to make a sacrifice!"
                            ).queue();
                            return;
                        }

                        if (result == StealResult.TARGET_HAS_NO_CANDY) {
                            // The target's inventory should not matter for a failed attempt.
                            // This branch is defensive; see the database method note below.
                            channel.sendMessage(
                                    "🕷️ " + recipient.getAsMention()
                                            + " has no candy to steal!"
                            ).queue();
                            return;
                        }

                        channel.sendMessage(
                                "💀 " + giver.getAsMention()
                                        + " failed to steal from " + recipient.getAsMention()
                                        + " and lost three candies!"
                        ).queue();
                    } else {
                        channel.sendMessage(
                                "🍀 " + giver.getAsMention()
                                        + " failed to steal from " + recipient.getAsMention()
                                        + ", but escaped without losing any candy!"
                        ).queue();
                    }

                    return;
                }

                StealResult result =
                        database.tryStealCandy(giver.getId(), recipient.getId(), payPrice);

                switch (result) {
                    case SUCCESS ->
                            channel.sendMessage(
                                    "🕷️ " + giver.getAsMention()
                                            + " successfully stole a candy from "
                                            + recipient.getAsMention()
                                            + "! Three candies were sacrificed for this crime. 🍬"
                            ).queue();

                    case SUCCESS_FREE ->
                            channel.sendMessage(
                                    "🥷 " + giver.getAsMention()
                                            + " successfully stole a candy from "
                                            + recipient.getAsMention()
                                            + " and got away without paying! 🍬"
                            ).queue();

                    case FAILED_PAID ->
                            channel.sendMessage(
                                    "💀 " + giver.getAsMention()
                                            + " failed to steal from " + recipient.getAsMention()
                                            + " and lost three candies!"
                            ).queue();

                    case FAILED_FREE ->
                            channel.sendMessage(
                                    "🍀 " + giver.getAsMention()
                                            + " failed to steal from " + recipient.getAsMention()
                                            + ", but escaped without losing any candy!"
                            ).queue();

                    case INSUFFICIENT_CANDY ->
                            channel.sendMessage(
                                    "🕷️ " + giver.getAsMention()
                                            + " doesn't have enough candy to sacrifice!"
                            ).queue();

                    case TARGET_HAS_NO_CANDY ->
                            channel.sendMessage(
                                    "🕷️ " + recipient.getAsMention()
                                            + " has no candy to steal!"
                            ).queue();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();

                channel.sendMessage(
                        "😔 The bucket seems to be empty. Better luck next time."
                ).queue();
            }

            return;
        }


        // ─── CANDY COUNT ──────────────────────────────────────
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

    private void giveCoal(
            String giverId,
            String recipientId,
            ButtonInteractionEvent event) {

        String message = COAL_MESSAGES.get((int) (Math.random() * COAL_MESSAGES.size()));

        message = message.replace("<@RECIPIENT>", "<@" + recipientId + ">");

        event.reply(
                "\uD83E\uDDDF With a malicious grin <@"
                        + giverId + "> " + message
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