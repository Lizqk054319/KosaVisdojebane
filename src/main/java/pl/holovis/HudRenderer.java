package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** Elementy HUD: Watermark (nazwa, FPS, koordynaty), Spotify i TargetHUD. Pozycje (ułamki ekranu) są w configu. */
public final class HudRenderer {
    private HudRenderer() {}

    public enum Element {
        WATERMARK("Watermark"), SPOTIFY("Spotify"), TARGET("TargetHUD"), COOLDOWNS("Cooldowns");
        public final String label;
        Element(String l) { label = l; }
    }

    public static boolean enabled(Element e) {
        HoloConfig c = HoloConfig.get();
        return switch (e) {
            case WATERMARK -> c.watermark;
            case SPOTIFY -> c.spotify;
            case TARGET -> c.targetHud;
            case COOLDOWNS -> c.cooldowns;
        };
    }

    public static float[] pos(Element e) {
        HoloConfig c = HoloConfig.get();
        return switch (e) {
            case WATERMARK -> new float[]{c.wmX, c.wmY};
            case SPOTIFY -> new float[]{c.spX, c.spY};
            case TARGET -> new float[]{c.tgX, c.tgY};
            case COOLDOWNS -> new float[]{c.cdX, c.cdY};
        };
    }

    public static void setPos(Element e, float x, float y) {
        HoloConfig c = HoloConfig.get();
        switch (e) {
            case WATERMARK -> { c.wmX = x; c.wmY = y; }
            case SPOTIFY -> { c.spX = x; c.spY = y; }
            case TARGET -> { c.tgX = x; c.tgY = y; }
            case COOLDOWNS -> { c.cdX = x; c.cdY = y; }
        }
    }

    public static void resetPositions() {
        HoloConfig c = HoloConfig.get();
        c.wmX = 0.01f; c.wmY = 0.02f;
        c.spX = 0.01f; c.spY = 0.09f;
        c.tgX = 0.55f; c.tgY = 0.55f;
        c.cdX = 0.01f; c.cdY = 0.30f;
        c.wmScale = c.spScale = c.spCardScale = c.tgScale = c.cdScale = 1f;
    }

    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 3f;

    public static float scale(Element e) {
        HoloConfig c = HoloConfig.get();
        float s = switch (e) {
            case WATERMARK -> c.wmScale;
            case SPOTIFY -> c.spScale;
            case TARGET -> c.tgScale;
            case COOLDOWNS -> c.cdScale;
        };
        return MathHelper.clamp(s, MIN_SCALE, MAX_SCALE);
    }

    public static void setScale(Element e, float s) {
        HoloConfig c = HoloConfig.get();
        s = MathHelper.clamp(s, MIN_SCALE, MAX_SCALE);
        switch (e) {
            case WATERMARK -> c.wmScale = s;
            case SPOTIFY -> c.spScale = s;
            case TARGET -> c.tgScale = s;
            case COOLDOWNS -> c.cdScale = s;
        }
    }

    private static final int GAP = 7;

    /** Segment tekstu: kolorowy (akcent) albo zwykły. */
    private record Seg(String text, boolean accent) {}

    private static List<Seg> parts(Element e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        List<Seg> l = new ArrayList<>();
        if (e == Element.WATERMARK) {
            l.add(new Seg("Kosa", true));
            if (cfg.showFps) {
                l.add(new Seg("|", false));
                l.add(new Seg(mc.getCurrentFps() + " FPS", false));
            }
            if (cfg.coords) {
                l.add(new Seg("|", false));
                l.add(new Seg("XYZ", true));
                l.add(new Seg(mc.player == null ? "0 0 0"
                        : mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ(), false));
            }
        } else {
            String t;
            if (!Spotify.running) t = "nie wykryto";
            else if (Spotify.track.isEmpty()) t = "pauza";
            else t = Spotify.playing ? Spotify.track : Spotify.track + " (pauza)";
            l.add(new Seg("Spotify", true));
            l.add(new Seg(mc.textRenderer.trimToWidth(t, 200), false));
        }
        return l;
    }

    private static int totalWidth(TextRenderer tr, List<Seg> p) {
        int w = 8;
        for (int i = 0; i < p.size(); i++) w += tr.getWidth(p.get(i).text()) + (i > 0 ? GAP : 0);
        return w;
    }

    /** Rozmiar elementu w skali 1x. */
    public static int[] baseSize(Element e) {
        if (e == Element.TARGET) return TargetHud.size();
        if (e == Element.COOLDOWNS) return Cooldowns.size();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        return new int[]{totalWidth(tr, parts(e)), 14};
    }

    /** Rozmiar elementu na ekranie (z uwzględnieniem skali). */
    public static int[] size(Element e) {
        int[] b = baseSize(e);
        float s = scale(e);
        return new int[]{Math.max(1, Math.round(b[0] * s)), Math.max(1, Math.round(b[1] * s))};
    }

    public static int[] pixelPos(Element e, int sw, int sh) {
        int[] s = size(e);
        float[] f = pos(e);
        return new int[]{
                MathHelper.clamp((int) (f[0] * sw), 0, Math.max(0, sw - s[0])),
                MathHelper.clamp((int) (f[1] * sh), 0, Math.max(0, sh - s[1]))};
    }

    /** preview = true w edytorze HUD. Rysuje w skali ustawionej dla elementu (x, y = lewy górny róg na ekranie). */
    public static void draw(DrawContext ctx, Element e, int x, int y, boolean preview) {
        float s = scale(e);
        if (Math.abs(s - 1f) < 0.005f) { drawBase(ctx, e, x, y, preview); return; }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0f);
        ctx.getMatrices().scale(s, s, 1f);
        drawBase(ctx, e, 0, 0, preview);
        ctx.getMatrices().pop();
    }

    private static void drawBase(DrawContext ctx, Element e, int x, int y, boolean preview) {
        if (e == Element.TARGET) { TargetHud.draw(ctx, x, y, preview); return; }
        if (e == Element.COOLDOWNS) { Cooldowns.draw(ctx, x, y, preview); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;
        int c1, c2;
        if (e == Element.SPOTIFY) {
            c1 = cfg.spColor.rgb();
            c2 = cfg.spGradient ? cfg.spColor2.rgb() : c1;
        } else {
            c1 = cfg.hudColor.rgb();
            c2 = cfg.hudGradient ? cfg.hudColor2.rgb() : c1;
        }

        List<Seg> p = parts(e);
        int w = totalWidth(tr, p);
        ctx.fill(x, y, x + w, y + 14, 0x99000000);
        ctx.fillGradient(x, y, x + 2, y + 14, 0xFF000000 | c1, 0xFF000000 | c2);
        int cx = x + 5;
        for (int i = 0; i < p.size(); i++) {
            Seg s = p.get(i);
            if (i > 0) cx += GAP;
            if (s.accent()) {
                Gfx.gradientText(ctx, tr, s.text(), cx, y + 3, c1, c2, 255, false);
            } else {
                int col = s.text().equals("|") ? 0xFF6B7488 : 0xFFE8EEF8;
                ctx.drawText(tr, s.text(), cx, y + 3, col, false);
            }
            cx += tr.getWidth(s.text());
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (Element e : Element.values()) {
            if (!enabled(e)) continue;
            if (e == Element.TARGET && !TargetHud.visible()) continue;
            if (e == Element.COOLDOWNS && !Cooldowns.visible()) continue;
            int[] p = pixelPos(e, sw, sh);
            draw(ctx, e, p[0], p[1], false);
        }
    }
}
