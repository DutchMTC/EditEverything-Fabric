package com.dutchmtc.ee.gui.modifier.mob;

/** Numeric rules for the trade editor, independent of the client. */
final class TradeInputRules {
    private TradeInputRules() {}

    static String error(String key, String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Enter a number.";
        String value = raw.trim();
        try {
            if (key.equals("priceMultiplier")) {
                float number = Float.parseFloat(value);
                return Float.isFinite(number) && number >= 0 ? null : "Use a finite number of 0 or more.";
            }
            int number = Integer.parseInt(value);
            return switch (key) {
                case "buy", "buyB", "sell" -> number >= 1 && number <= 127 ? null : "Use 1 to 127.";
                case "maxUses" -> number >= 1 ? null : "Use at least 1.";
                case "uses", "xp" -> number >= 0 ? null : "Use 0 or more.";
                default -> null;
            };
        } catch (NumberFormatException ignored) {
            return key.equals("priceMultiplier") ? "Enter a valid number." : "Enter a whole number within the integer range.";
        }
    }

}
