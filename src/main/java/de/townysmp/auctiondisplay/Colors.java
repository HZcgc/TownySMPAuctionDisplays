package de.townysmp.auctiondisplay;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class Colors {
    static final String PINK = hex("FF55FF");
    static final String GREEN = hex("55FF55");
    static final String CYAN = hex("55FFFF");
    static final String YELLOW = hex("FFFF55");
    static final String RED = hex("FF5555");
    static final String GRAY = hex("808080");
    static final String MUTED = hex("AAAAAA");
    static final String WHITE = hex("FFFFFF");
    static final String BOLD = "§l";
    static final String RESET = "§r";
    static final String PREFIX = PINK + BOLD + "TOWNY" + RESET + GREEN + BOLD + "SMP" + RESET + " " + GRAY + "» " + WHITE;

    private Colors() {
    }

    static String hex(String value) {
        StringBuilder result = new StringBuilder("§x");
        for (char character : value.toCharArray()) {
            result.append('§').append(character);
        }
        return result.toString();
    }

    static String money(double amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + format.format(amount);
    }

    static String duration(long milliseconds) {
        if (milliseconds <= 0L) {
            return "Expired";
        }
        long totalMinutes = Math.max(1L, milliseconds / 60_000L);
        long days = totalMinutes / 1_440L;
        long hours = (totalMinutes % 1_440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown Item";
        }
        String result = value.replaceAll("(?i)§x(?:§[0-9a-f]){6}", "")
                .replaceAll("(?i)§[0-9a-fk-or]", "")
                .replaceAll("<[^>]+>", "")
                .trim();
        if (result.length() > 36) {
            return result.substring(0, 33) + "...";
        }
        return result.isEmpty() ? "Unknown Item" : result;
    }

    static String materialName(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
