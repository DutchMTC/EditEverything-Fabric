package fr.atesab.act.utils;

import java.util.Random;

public final class ColorMath {
    private ColorMath() {
    }

    public static int rgb(int red, int green, int blue) {
        return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    /**
     * @return 24-bit RGB (0xRRGGBB)
     */
    public static int fromHsl(int hue, int saturationPercent, int lightnessPercent) {
        float s = Math.max(0f, Math.min(1f, saturationPercent / 100f));
        float l = Math.max(0f, Math.min(1f, lightnessPercent / 100f));

        float c = (1 - Math.abs(2 * l - 1)) * s;
        float hh = ((hue % 360) + 360) % 360 / 60f;
        float x = c * (1 - Math.abs(hh % 2 - 1));

        float r1, g1, b1;
        int sector = (int) hh;
        switch (sector) {
            case 0 -> {
                r1 = c;
                g1 = x;
                b1 = 0;
            }
            case 1 -> {
                r1 = x;
                g1 = c;
                b1 = 0;
            }
            case 2 -> {
                r1 = 0;
                g1 = c;
                b1 = x;
            }
            case 3 -> {
                r1 = 0;
                g1 = x;
                b1 = c;
            }
            case 4 -> {
                r1 = x;
                g1 = 0;
                b1 = c;
            }
            default -> {
                r1 = c;
                g1 = 0;
                b1 = x;
            }
        }

        float m = l - c / 2;
        int r = Math.round((r1 + m) * 255f);
        int g = Math.round((g1 + m) * 255f);
        int b = Math.round((b1 + m) * 255f);
        return rgb(r, g, b);
    }

    public static int randomRgb(Random random) {
        return random.nextInt(0x1000000);
    }
}

