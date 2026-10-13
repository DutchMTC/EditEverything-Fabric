package com.dutchmtc.ee.utils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** A solid color or an immutable gradient with individually positioned color stops. */
public record TextColorPreset(List<Stop> stops) {
    public record Stop(int color, double position) {
        public Stop {
            color &= 0xFFFFFF;
            if (!Double.isFinite(position) || position < 0 || position > 1)
                throw new IllegalArgumentException("Color positions must be between 0 and 1.");
            if (position == 0) position = 0;
        }
    }
    public TextColorPreset {
        if (stops == null || stops.isEmpty()) throw new IllegalArgumentException("At least one color is required.");
        stops = stops.stream().sorted(Comparator.comparingDouble(Stop::position)).toList();
    }
    /** Preserve the existing API and old solid/two-color history entries. */
    public TextColorPreset(int start, int end) {
        this((start & 0xFFFFFF) == (end & 0xFFFFFF) ? List.of(new Stop(start, 0))
                : List.of(new Stop(start, 0), new Stop(end, 1)));
    }
    public static TextColorPreset solid(int rgb) { return new TextColorPreset(List.of(new Stop(rgb, 0))); }
    public boolean gradient() { return stops.size() > 1; }
    public int start() { return stops.getFirst().color(); }
    public int end() { return stops.getLast().color(); }
    public int colorAt(double fraction) {
        double t = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
        if (t < stops.getFirst().position()) return start();
        for (int i = 1; i < stops.size(); i++) {
            Stop left = stops.get(i - 1), right = stops.get(i);
            if (t < right.position()) {
                double blend = (t - left.position()) / (right.position() - left.position());
                int rgb = 0;
                for (int shift : new int[]{16, 8, 0}) {
                    int a = left.color() >> shift & 255, b = right.color() >> shift & 255;
                    rgb |= (int) Math.round(a + (b - a) * blend) << shift;
                }
                return rgb;
            }
        }
        // Coincident stops form a sharp transition, with the last color winning at that point.
        return end();
    }
    public TextColorPreset evenlySpaced() {
        if (!gradient()) return solid(start());
        return new TextColorPreset(java.util.stream.IntStream.range(0, stops.size())
                .mapToObj(i -> new Stop(stops.get(i).color(), i / (double) (stops.size() - 1))).toList());
    }
    public String encode() {
        if (!gradient()) return String.format(Locale.ROOT, "%06X", start());
        if (stops.size() == 2 && stops.getFirst().position() == 0 && stops.getLast().position() == 1 && start() != end())
            return String.format(Locale.ROOT, "%06X>%06X", start(), end());
        return stops.stream().map(s -> String.format(Locale.ROOT, "%06X@%s", s.color(), Double.toString(s.position())))
                .collect(Collectors.joining(">"));
    }
    public String label() {
        if (!gradient()) return String.format(Locale.ROOT, "#%06X", start());
        return stops.stream().map(s -> String.format(Locale.ROOT, "#%06X at %.0f%%", s.color(), s.position() * 100))
                .collect(Collectors.joining(" → "));
    }
    public static TextColorPreset parse(String value) {
        if (value == null) return null;
        try {
            if (value.matches("[0-9a-fA-F]{6}(>[0-9a-fA-F]{6})?")) {
                String[] parts = value.split(">");
                int first = Integer.parseInt(parts[0], 16);
                return new TextColorPreset(first, parts.length == 1 ? first : Integer.parseInt(parts[1], 16));
            }
            String[] parts = value.split(">", -1);
            if (parts.length < 2) return null;
            var parsed = new java.util.ArrayList<Stop>();
            for (String part : parts) {
                if (!part.matches("[0-9a-fA-F]{6}@(?:[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)")) return null;
                String[] fields = part.split("@");
                parsed.add(new Stop(Integer.parseInt(fields[0], 16), Double.parseDouble(fields[1])));
            }
            return new TextColorPreset(parsed);
        } catch (IllegalArgumentException e) { return null; }
    }
}
