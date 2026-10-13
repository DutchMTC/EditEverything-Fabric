package com.dutchmtc.ee.gui.modifier.mob;

/** Run with gradlew tradeUiTest; no game, graphics context, or test-library dependency is required. */
public final class TradeInputRulesTest {
    private static int checks;

    public static void main(String[] args) {
        for (String key : new String[]{"buy", "buyB", "sell"}) {
            accepts(key, "1"); accepts(key, "127"); accepts(key, " 64 ");
            rejects(key, "0"); rejects(key, "128"); rejects(key, "-1");
            rejects(key, ""); rejects(key, "-"); rejects(key, "1.5");
            rejects(key, "2147483648"); rejects(key, null);
        }
        accepts("maxUses", "1"); accepts("maxUses", "2147483647");
        rejects("maxUses", "0"); rejects("maxUses", "-12");
        for (String key : new String[]{"uses", "xp"}) {
            accepts(key, "0"); accepts(key, "2147483647"); rejects(key, "-1");
        }
        for (String key : new String[]{"demand", "specialPrice"}) {
            accepts(key, "-2147483648"); accepts(key, "2147483647"); accepts(key, "0");
            rejects(key, "2147483648"); rejects(key, "-2147483649"); rejects(key, "1.2");
        }
        accepts("priceMultiplier", "0"); accepts("priceMultiplier", "0.05");
        accepts("priceMultiplier", "1e-3"); accepts("priceMultiplier", " 2.5 ");
        for (String invalid : new String[]{"NaN", "Infinity", "-Infinity", "1e50", "-0.1", "", ".", "abc"})
            rejects("priceMultiplier", invalid);
        // The smallest supported Minecraft GUI still shows the exchange, at least one offer,
        // and the options on the right without overlapping the action buttons.
        for (int[] size : new int[][]{{320, 240}, {426, 240}, {536, 300}, {640, 360}, {854, 480}, {1920, 1080}}) {
            TradeEditorLayout l = TradeEditorLayout.of(size[0], size[1]);
            check(l.left() >= 0 && l.top() >= 0 && l.right() <= size[0] && l.bottom() <= size[1], "Window bounds");
            check(l.tradeWidth() >= 130, "Exchange width at " + size[0]);
            check(l.optionsWidth() >= 136, "Options width");
            check(l.tradeX() + l.tradeWidth() < l.optionsX(), "Options stay to the right");
            check(l.slotsX() - 2 >= l.tradeX(), "First amount stays in exchange");
            check(l.slotsX() + 128 <= l.tradeX() + l.tradeWidth(), "Last amount stays in exchange");
            check(l.visibleRows() >= 1 && l.listTop() + l.visibleRows() * 24 <= l.listBottom(), "Visible offers fit");
            check(l.listBottom() < l.bottom() - 64, "Offers do not overlap actions");
            check(l.slotsY() + 72 < (l.compact() ? l.listTop() : l.bottom() - 64), "Exchange stays visible");
        }
        System.out.println("Trade input regression checks passed: " + checks);
    }

    private static void accepts(String key, String value) {
        checks++;
        if (TradeInputRules.error(key, value) != null)
            throw new AssertionError("Rejected valid " + key + ": " + value);
    }
    private static void rejects(String key, String value) {
        checks++;
        if (TradeInputRules.error(key, value) == null)
            throw new AssertionError("Accepted invalid " + key + ": " + value);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
