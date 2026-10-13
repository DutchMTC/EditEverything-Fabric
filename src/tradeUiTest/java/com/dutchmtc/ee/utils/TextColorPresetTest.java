package com.dutchmtc.ee.utils;

import java.util.ArrayList;
import java.util.List;

/** Pure gradient math and history-format checks; no game or graphics context required. */
public final class TextColorPresetTest {
    private static int checks;
    public static void main(String[] args) {
        var gradient = new TextColorPreset(List.of(stop(0x000000, 0), stop(0xFF0000, .25), stop(0xFFFFFF, 1)));
        equal(gradient.colorAt(0), 0x000000, "first color");
        equal(gradient.colorAt(.125), 0x800000, "first segment interpolation");
        equal(gradient.colorAt(.25), 0xFF0000, "interior color at its exact stop");
        equal(gradient.colorAt(.625), 0xFF8080, "second segment interpolation");
        equal(gradient.colorAt(1), 0xFFFFFF, "last color");
        equal(gradient.colorAt(-10), 0x000000, "before gradient");
        equal(gradient.colorAt(10), 0xFFFFFF, "after gradient");
        equal(TextColorPreset.parse(gradient.encode()), gradient, "all colors and positions persist");
        var held = new TextColorPreset(List.of(stop(0x123456, .3), stop(0xFEDCBA, .7)));
        equal(held.colorAt(.1), 0x123456, "hold first color before moved endpoint");
        equal(held.colorAt(.9), 0xFEDCBA, "hold last color after moved endpoint");
        var reversedInput = new TextColorPreset(List.of(stop(0xFFFFFF, 1), stop(0xFF0000, .25), stop(0, 0)));
        equal(reversedInput, gradient, "stops sort by position");
        var evenly = gradient.evenlySpaced();
        equal(evenly.stops().get(1).position(), .5, "reset spaces intermediate colors evenly");
        equal(evenly.stops().stream().map(TextColorPreset.Stop::color).toList(), gradient.stops().stream().map(TextColorPreset.Stop::color).toList(), "reset keeps colors and order");
        var sharp = new TextColorPreset(List.of(stop(0, 0), stop(0, .5), stop(0xFFFFFF, .5), stop(0xFFFFFF, 1)));
        equal(sharp.colorAt(.499), 0, "coincident stops keep left color before edge");
        equal(sharp.colorAt(.5), 0xFFFFFF, "coincident stops switch cleanly at edge");
        equal(sharp.colorAt(.501), 0xFFFFFF, "coincident stops keep right color after edge");
        equal(TextColorPreset.parse(sharp.encode()), sharp, "hard edges persist");
        equal(TextColorPreset.parse("123456"), TextColorPreset.solid(0x123456), "legacy solid entry");
        equal(TextColorPreset.parse("123456>FEDCBA"), new TextColorPreset(0x123456, 0xFEDCBA), "legacy two-color entry");
        var identical = new TextColorPreset(List.of(stop(0x123456, 0), stop(0x123456, 1)));
        equal(TextColorPreset.parse(identical.encode()), identical, "identical stops still preserve gradient editing state");
        equal(new TextColorPreset(0x123456, 0x123456), TextColorPreset.solid(0x123456), "legacy same-color entry stays solid");
        var fine = new TextColorPreset(List.of(stop(0, 0), stop(0x123456, .000000001), stop(0xFFFFFF, 1)));
        equal(TextColorPreset.parse(fine.encode()), fine, "tiny distances persist without rounding");
        var source = new ArrayList<>(gradient.stops()); var immutable = new TextColorPreset(source); source.clear();
        equal(immutable, gradient, "preset owns an immutable snapshot");
        for (String bad : new String[]{"", "not a color", "000000@NaN>FFFFFF@1", "000000@-1>FFFFFF@1", "000000@0>FFFFFF@2", "000000@0>", "000000@0>FFFFFF", "000000>FFFFFF>FF0000"})
            equal(TextColorPreset.parse(bad), null, "malformed history is ignored: " + bad);
        System.out.println("Gradient regression checks passed: " + checks);
    }
    private static TextColorPreset.Stop stop(int rgb, double position) { return new TextColorPreset.Stop(rgb, position); }
    private static void equal(Object actual, Object expected, String message) {
        if (!java.util.Objects.equals(actual, expected)) throw new AssertionError(message + ": " + actual + " != " + expected);
        checks++;
    }
}
