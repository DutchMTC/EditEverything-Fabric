package com.dutchmtc.ee.utils;

import java.util.*;

/** Common effect rows and input drafts for potion contents and entity effect tags. */
public class EffectListData {
    public final List<Entry> effects = new ArrayList<>();

    public static final class Entry {
        public final EffectEditorData effect;
        public boolean fromPotion;
        public String level, seconds;
        private final String initialLevel, initialSeconds;
        public int timedTicks;

        public Entry(EffectEditorData effect, boolean fromPotion) {
            this.effect = effect;
            this.fromPotion = fromPotion;
            level = initialLevel = "" + effect.level;
            seconds =
                    initialSeconds =
                            EffectEditorData.seconds(effect.ticks < 0 ? 1200 : effect.ticks);
            timedTicks = effect.ticks < 0 ? 1200 : effect.ticks;
        }

        public String levelError() {
            try {
                int n = Integer.parseInt(level);
                if (n >= 1 && n <= 256) return null;
            } catch (NumberFormatException ignored) {
            }
            return "Enter a whole level from 1 to 256.";
        }

        public String durationError() {
            if (effect.ticks == -1) return null;
            try {
                double n = Double.parseDouble(seconds);
                if (Double.isFinite(n) && n >= 0 && n <= Integer.MAX_VALUE / 20.0) return null;
            } catch (NumberFormatException ignored) {
            }
            return "Enter seconds from 0 to 107374182.35. Decimals are allowed.";
        }

        public boolean rawChanged() {
            return !level.equals(initialLevel) || !seconds.equals(initialSeconds);
        }

        public String error() {
            if (effect.effect() == null && !effect.modified() && !rawChanged()) return null;
            return levelError() != null
                    ? levelError()
                    : durationError() != null ? durationError() : effect.invalidReason();
        }
    }

    public void materialize() {
        effects.forEach(e -> e.fromPotion = false);
    }

    public void setLevel(Entry entry, String value) {
        entry.level = value;
        if (entry.levelError() == null) {
            int n = Integer.parseInt(value);
            if (entry.effect.level != n && entry.fromPotion) materialize();
            entry.effect.level = n;
        }
    }

    public void setSeconds(Entry entry, String value) {
        entry.seconds = value;
        if (entry.effect.ticks != -1 && entry.durationError() == null) {
            int n = (int) Math.round(Double.parseDouble(value) * 20);
            if (entry.effect.ticks != n && entry.fromPotion) materialize();
            entry.effect.ticks = entry.timedTicks = n;
        }
    }

    public void setInfinite(Entry entry, boolean infinite) {
        if (entry.fromPotion) materialize();
        entry.effect.ticks = infinite ? -1 : entry.timedTicks;
    }

    public void remove(Entry entry) {
        if (entry.fromPotion) materialize();
        effects.remove(entry);
    }

    public String error() {
        return effects.stream().map(Entry::error).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
