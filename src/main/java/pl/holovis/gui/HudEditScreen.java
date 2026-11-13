package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.HoloVisClient;
import pl.holovis.HudRenderer;
import pl.holovis.HudRenderer.Element;
import pl.holovis.SpotifyOverlay;

/**
 * Edytor HUD (klawisz L): przeciągaj elementy, a za rogi (kropki) zmieniaj ich rozmiar.
 * Po najechaniu na Spotify pojawia się też karta z tekstem piosenki - jej rozmiar zmienia prawy dolny róg.
 */
public class HudEditScreen extends Screen {
    private static final int HANDLE = 5;   // promień uchwytu w rogu

    private final Screen parent;
    private Element dragging, resizing;
    private boolean cardResize;
    private int dx, dy;
    private int corner, anchorX, anchorY;

    public HudEditScreen(Screen parent) {
        super(Text.literal("KosaVisuals HUD"));
        this.parent = parent;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        SpotifyOverlay.editing = false;
        SpotifyOverlay.pinned = false;
        HoloConfig.save();
    }

    @Override
    public void close() {
        HoloConfig.save();
        if (client != null) client.setScreen(parent);
    }

    private static boolean near(double mx, double my, int cx, int cy) {
        return Math.abs(mx - cx) <= HANDLE + 1 && Math.abs(my - cy) <= HANDLE + 1;
    }

    /** Współrzędne rogu: 0 = lewy górny, 1 = prawy górny, 2 = lewy dolny, 3 = prawy dolny. */
    private static int cornerX(int[] p, int[] s, int c) { return (c == 1 || c == 3) ? p[0] + s[0] : p[0]; }
    private static int cornerY(int[] p, int[] s, int c) { return (c == 2 || c == 3) ? p[1] + s[1] : p[1]; }

    private void handle(DrawContext c, int x, int y, boolean hot) {
        Gfx.circle(c, x, y, HANDLE + 1, 0xCC000000);
        Gfx.circle(c, x, y, HANDLE, hot ? 0xFFFFFFFF : 0xFFB8C4DC);
        Gfx.circle(c, x, y, HANDLE - 2, hot ? 0xFF4C8DFF : 0xFF6B7488);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        SpotifyOverlay.editing = true;
        c.fill(0, 0, width, height, 0x88000000);
        String hint = "Przeciągnij element   |   kropki w rogach = rozmiar (albo kółko myszy)   |   R - reset   |   L / ESC - wróć";
        c.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, 14, 0xFFE8EEF8, false);

        for (Element e : Element.values()) {
            int[] p = HudRenderer.pixelPos(e, width, height);
            int[] s = HudRenderer.size(e);
            HudRenderer.draw(c, e, p[0], p[1], true);
            boolean on = HudRenderer.enabled(e);
            if (!on) c.fill(p[0], p[1], p[0] + s[0], p[1] + s[1], 0xAA000000);
            boolean hov = mx >= p[0] - HANDLE && mx < p[0] + s[0] + HANDLE && my >= p[1] - HANDLE && my < p[1] + s[1] + HANDLE;
            boolean active = dragging == e || resizing == e;
            int border = active ? 0xFFFFFFFF : (hov ? 0xFFCCCCCC : 0x88FFFFFF);
            c.drawBorder(p[0] - 1, p[1] - 1, s[0] + 2, s[1] + 2, border);
            String lab = e.label + (on ? "" : " (wyłączony)") + "  " + Math.round(HudRenderer.scale(e) * 100) + "%";
            c.drawText(textRenderer, lab, p[0], p[1] - 10, 0xFF9AA4B8, false);
        }

        // karta Spotify (tekst piosenki) - pojawia się po najechaniu na element Spotify
        SpotifyOverlay.render(this, c, mx, my, delta);

        // uchwyty rogów rysujemy na wierzchu
        for (Element e : Element.values()) {
            int[] p = HudRenderer.pixelPos(e, width, height);
            int[] s = HudRenderer.size(e);
            boolean hov = mx >= p[0] - HANDLE * 2 && mx < p[0] + s[0] + HANDLE * 2
                    && my >= p[1] - HANDLE * 2 && my < p[1] + s[1] + HANDLE * 2;
            if (!hov && resizing != e) continue;
            for (int k = 0; k < 4; k++) {
                int hx = cornerX(p, s, k), hy = cornerY(p, s, k);
                handle(c, hx, hy, (resizing == e && corner == k) || near(mx, my, hx, hy));
            }
        }

        int[] cr = SpotifyOverlay.cardRect();
        if (cr != null) {
            c.drawBorder(cr[0] - 1, cr[1] - 1, cr[2] + 2, cr[3] + 2, cardResize ? 0xFFFFFFFF : 0x88FFFFFF);
            String lab = "Karta tekstu  " + Math.round(HoloConfig.get().spCardScale * 100) + "%  (prawy dolny róg)";
            c.drawText(textRenderer, lab, cr[0], cr[1] - 10, 0xFF9AA4B8, false);
            handle(c, cr[0] + cr[2], cr[1] + cr[3], cardResize || near(mx, my, cr[0] + cr[2], cr[1] + cr[3]));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        // 1) uchwyt karty Spotify
        int[] cr = SpotifyOverlay.cardRect();
        if (cr != null && near(mx, my, cr[0] + cr[2], cr[1] + cr[3])) {
            cardResize = true;
            SpotifyOverlay.pinned = true;
            return true;
        }

        Element[] all = Element.values();
        // 2) uchwyty rogów elementów
        for (int i = all.length - 1; i >= 0; i--) {
            Element e = all[i];
            int[] p = HudRenderer.pixelPos(e, width, height);
            int[] s = HudRenderer.size(e);
            for (int k = 0; k < 4; k++) {
                if (!near(mx, my, cornerX(p, s, k), cornerY(p, s, k))) continue;
                resizing = e;
                corner = k;
                anchorX = cornerX(p, s, 3 - k);   // przeciwległy róg stoi w miejscu
                anchorY = cornerY(p, s, 3 - k);
                return true;
            }
        }
        // 3) przeciąganie całego elementu
        for (int i = all.length - 1; i >= 0; i--) {
            Element e = all[i];
            int[] p = HudRenderer.pixelPos(e, width, height);
            int[] s = HudRenderer.size(e);
            if (mx >= p[0] && mx < p[0] + s[0] && my >= p[1] && my < p[1] + s[1]) {
                dragging = e;
                dx = (int) mx - p[0];
                dy = (int) my - p[1];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (cardResize) {
            int[] cr = SpotifyOverlay.cardRect();
            if (cr != null) {
                float sc = Math.max((float) (mx - cr[0]) / SpotifyOverlay.CARD_W, (float) (my - cr[1]) / SpotifyOverlay.CARD_H);
                HoloConfig.get().spCardScale = MathHelper.clamp(sc, 0.5f, 1.6f);
            }
            return true;
        }
        if (resizing != null) {
            int[] b = HudRenderer.baseSize(resizing);
            boolean right = corner == 1 || corner == 3, bottom = corner == 2 || corner == 3;
            float wantW = Math.abs((float) mx - anchorX), wantH = Math.abs((float) my - anchorY);
            HudRenderer.setScale(resizing, Math.max(wantW / b[0], wantH / b[1]));
            int[] ns = HudRenderer.size(resizing);
            int nx = right ? anchorX : anchorX - ns[0];
            int ny = bottom ? anchorY : anchorY - ns[1];
            nx = MathHelper.clamp(nx, 0, Math.max(0, width - ns[0]));
            ny = MathHelper.clamp(ny, 0, Math.max(0, height - ns[1]));
            HudRenderer.setPos(resizing, nx / (float) width, ny / (float) height);
            return true;
        }
        if (dragging == null) return false;
        int[] s = HudRenderer.size(dragging);
        int x = MathHelper.clamp((int) mx - dx, 0, Math.max(0, width - s[0]));
        int y = MathHelper.clamp((int) my - dy, 0, Math.max(0, height - s[1]));
        HudRenderer.setPos(dragging, x / (float) width, y / (float) height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null || resizing != null || cardResize) HoloConfig.save();
        dragging = null;
        resizing = null;
        cardResize = false;
        SpotifyOverlay.pinned = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        // kółko myszy nad elementem = szybka zmiana rozmiaru
        int[] cr = SpotifyOverlay.cardRect();
        if (cr != null && mx >= cr[0] && mx < cr[0] + cr[2] && my >= cr[1] && my < cr[1] + cr[3]) {
            HoloConfig cfg = HoloConfig.get();
            cfg.spCardScale = MathHelper.clamp(cfg.spCardScale + (float) vAmt * 0.05f, 0.5f, 1.6f);
            return true;
        }
        Element[] all = Element.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Element e = all[i];
            int[] p = HudRenderer.pixelPos(e, width, height);
            int[] s = HudRenderer.size(e);
            if (mx >= p[0] && mx < p[0] + s[0] && my >= p[1] && my < p[1] + s[1]) {
                HudRenderer.setScale(e, HudRenderer.scale(e) + (float) vAmt * 0.05f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_L || HoloVisClient.OPEN_MENU.matchesKey(key, scan)) { close(); return true; }
        if (key == GLFW.GLFW_KEY_R) { HudRenderer.resetPositions(); HoloConfig.save(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
