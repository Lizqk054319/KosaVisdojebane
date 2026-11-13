package pl.holovis;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class Gfx {
    private Gfx() {}

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int lerpArgb(int a, int b, float t) {
        int al = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        return (al << 24) | mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
    }

    /** ARGB z RGB i alfą 0..1. */
    public static int argb(int rgb, float a) {
        return (((int) (Math.max(0f, Math.min(1f, a)) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** Zaokrąglony prostokąt (ARGB). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** Zaokrąglony prostokąt z gradientem poziomym c1 -> c2 (ARGB). */
    public static void rboxH(DrawContext c, int x, int y, int w, int h, int r, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < w; i++) {
            int col = lerpArgb(c1, c2, w <= 1 ? 0f : i / (float) (w - 1));
            int in = 0;
            if (r > 0) {
                int e = i < r ? i : (i >= w - r ? w - 1 - i : -1);
                if (e >= 0) in = inset(r, e);
            }
            c.fill(x + i, y + in, x + i + 1, y + h - in, col);
        }
    }

    /** Zaokrąglony prostokąt z gradientem pionowym c1 -> c2 (ARGB). */
    public static void rboxV(DrawContext c, int x, int y, int w, int h, int r, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < h; j++) {
            int col = lerpArgb(c1, c2, h <= 1 ? 0f : j / (float) (h - 1));
            int in = 0;
            if (r > 0) {
                if (j < r) in = inset(r, j);
                else if (j >= h - r) in = inset(r, h - 1 - j);
            }
            c.fill(x + in, y + j, x + w - in, y + j + 1, col);
        }
    }

    /** Koło o środku (cx, cy) i promieniu r. */
    public static void circle(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy + 0.25)));
            c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /**
     * Minecraft traktuje kolor tekstu z alfą < 4 jako w pełni nieprzezroczysty, przez co napisy
     * "mrugają" na początku i końcu animacji. Takie wywołania pomijamy.
     */
    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) < 6) return;
        c.drawText(tr, s, x, y, argb, shadow);
    }

    public static void text(DrawContext c, TextRenderer tr, Text t, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) < 6) return;
        c.drawText(tr, t, x, y, argb, shadow);
    }

    /** Pikselowe serce 7x6. */
    public static void heart(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y, x + 3, y + 1, color);
        c.fill(x + 4, y, x + 6, y + 1, color);
        c.fill(x, y + 1, x + 7, y + 3, color);
        c.fill(x + 1, y + 3, x + 6, y + 4, color);
        c.fill(x + 2, y + 4, x + 5, y + 5, color);
        c.fill(x + 3, y + 5, x + 4, y + 6, color);
    }

    /** Trójkąt skierowany w prawo, wpisany w prostokąt w x h. */
    public static void triRight(DrawContext c, int x, int y, int w, int h, int color) {
        for (int r = 0; r < h; r++) {
            float d = 1f - Math.abs((r + 0.5f) - h / 2f) / (h / 2f);
            int rw = Math.max(1, Math.round(w * d));
            c.fill(x, y + r, x + rw, y + r + 1, color);
        }
    }

    /** Trójkąt skierowany w lewo. */
    public static void triLeft(DrawContext c, int x, int y, int w, int h, int color) {
        for (int r = 0; r < h; r++) {
            float d = 1f - Math.abs((r + 0.5f) - h / 2f) / (h / 2f);
            int rw = Math.max(1, Math.round(w * d));
            c.fill(x + w - rw, y + r, x + w, y + r + 1, color);
        }
    }

    /** Tekst z gradientem c1 -> c2 (litera po literze). alpha 0..255. */
    public static void gradientText(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                                    int c1, int c2, int alpha, boolean shadow) {
        if (alpha < 6) return;
        int cx = x;
        int n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            ctx.drawText(tr, ch, cx, y, (alpha << 24) | mix(c1, c2, i / (float) n), shadow);
            cx += tr.getWidth(ch);
        }
    }
}
