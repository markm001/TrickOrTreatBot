package com.ccat.candy;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum Candy {
    GUMMY_WORMS("some Gummy Worms","Gummy Worms", "🪱"),
    CHOCOLATE_BAR("a Chocolate Bar","Chocolate Bar", "🍫"),
    LOLLIPOP("a Lollipop", "Lollipop","🍭"),
    CANDY_CORN("a very generous handful of Candy Corn", "Candy Corn", "🌽"),
    JELLY_BEANS("some Jelly Beans","Jelly Beans", "🍬"),
    CARAMEL_APPLE("a Caramel Apple", "Caramel Apple","🍎"),
    GUMMY_BEAR("a single Gummy Bear", "Gummy Bears","🐻"),
    SOUR_CANDY("some Sour Candy","Sour Candy", "🍋"),
    CANDY_APPLE("a Candy Apple", "Candy Apple","🍏");

    private final String displayMessage;
    private final String displayName;
    private final String emoji;

    Candy(String displayMessage, String displayName, String emoji) {
        this.displayMessage = displayMessage;
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String getDisplayMessage() {
        return displayMessage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public static Candy random() {
        Candy[] candies = values();
        return candies[ThreadLocalRandom.current()
                .nextInt(candies.length)];
    }

    public static Candy randomMulti() {
        List<Candy> applicableCandies = List.of(GUMMY_WORMS, CANDY_CORN, JELLY_BEANS, SOUR_CANDY);

        return applicableCandies.get(ThreadLocalRandom.current()
                .nextInt(applicableCandies.size()));
    }
}