package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.HoloSounds;
import pl.holovis.PostFx;
import pl.holovis.Profiles;
import pl.holovis.Profiles.Profile;
import pl.holovis.Spotify;

import java.util.List;

/**
 * Okno z profilami (configami): nazwa + Zapisz, zakładki Ostatnie / Najczęściej / Wszystkie,
 * lista z gwiazdką (ulubione), kopiowaniem kodu i usuwaniem, oraz Cofnij / Wklej kod / Domyślne.
 */
public class ConfigScreen extends Screen {
    private static final int W = 316, H = 304, ROWH = 30, VISIBLE = 5;
    private static final int GOOD = 0x5CE08A, BAD = 0xFF6B7A, GOLD = 0xFFC83D;
    private static final String[] TABS = {"Ostatnie", "Najczęściej", "Wszystkie"};
    private static final String[] STAR = {
            "...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", ".#...#."};

    private final long openedAt = Util.getMeasuringTimeMs();
    private float fade = 1f, sc = 1f;
    private int vw, vh;
    private int accent, bg, txt;
    private int tab, scroll;
    private String name = "";
    private boolean focus = true;
    private String status = "";
    private int statusColor = GOOD;

    public ConfigScreen() {
        super(Text.literal("Configi"));
    }

    @Override public boolean shouldPause() { return false; }

    /** Po zamknięciu wracamy do menu - zbudowanego od nowa, bo ustawienia mogły się zmienić. */
    @Override
    public void close() {
        if (client != null) client.setScreen(new HoloScreen());
    }

    @Override
    public void removed() { HoloConfig.save(); }

    @Override
    protected void init() {
        sc = MathHelper.clamp(Math.min((width - 12f) / W, (height - 12f) / H), 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
    }

    // ------------------------------------------------------------------ rysowanie
    private int ox() { return (vw - W) / 2; }
    private int oy() { return (vh - H) / 2; }

    private void t(DrawContext c, String s, int x, int y, int rgb, float a) {
        Gfx.text(c, textRenderer, s, x, y, Gfx.argb(rgb, a * fade), false);
    }

    private void box(DrawContext c, int x, int y, int w, int h, int r, int rgb, float a) {
        Gfx.rrect(c, x, y, w, h, r, Gfx.argb(rgb, a * fade));
    }

    private void star(DrawContext c, int x, int y, boolean on, boolean hov) {
        int col = on ? Gfx.argb(GOLD, fade) : Gfx.argb(0xFFFFFF, (hov ? 0.55f : 0.22f) * fade);
        for (int j = 0; j < STAR.length; j++)
            for (int i = 0; i < STAR[j].length(); i++)
                if (STAR[j].charAt(i) == '#') c.fill(x + i, y + j, x + i + 1, y + j + 1, col);
    }

    private void setStatus(String s, boolean ok) {
        status = s;
        statusColor = ok ? GOOD : BAD;
    }

    private List<Profile> list() { return Profiles.sorted(tab); }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        HoloConfig cfg = HoloConfig.get();
        accent = cfg.guiColor.rgb();
        bg = cfg.guiBg.rgb();
        txt = cfg.textColor.rgb();
        float p = MathHelper.clamp((Util.getMeasuringTimeMs() - openedAt) / 200f, 0f, 1f);
        fade = 1f - (1f - p) * (1f - p);

        PostFx.blurBackground(c, cfg.clientBlur * fade);
        c.fill(0, 0, width, height, ((int) (0x66 * fade)) << 24);

        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);
        int x = ox(), y = oy() + (int) ((1f - fade) * 10);

        // panel
        box(c, x - 1, y - 1, W + 2, H + 2, 13, 0xFFFFFF, 0.10f);
        box(c, x, y, W, H, 12, mix(bg, 0x000000, 0.12f), Math.max(0.9f, cfg.guiBgAlpha));
        c.fillGradient(x + 6, y + 2, x + W - 6, y + 40, Gfx.argb(accent, 0.10f * fade), Gfx.argb(accent, 0f));

        // nagłówek
        c.getMatrices().push();
        c.getMatrices().translate(x + 18, y + 14, 0f);
        c.getMatrices().scale(1.5f, 1.5f, 1f);
        t(c, "Configi", 0, 0, txt, 1f);
        c.getMatrices().pop();
        if (!status.isEmpty()) t(c, status, x + 18, y + 34, statusColor, 1f);

        int n = Profiles.count();
        String cnt = n + (n == 1 ? " profil" : " profili");
        t(c, cnt, x + W - 56 - textRenderer.getWidth(cnt), y + 19, txt, 0.55f);
        boolean hx = in(mx, my, x + W - 34, y + 12, 22, 22);
        Gfx.circle(c, x + W - 23, y + 23, 10, Gfx.argb(0xFFFFFF, (hx ? 0.20f : 0.09f) * fade));
        t(c, "x", x + W - 23 - textRenderer.getWidth("x") / 2, y + 19, txt, 0.9f);

        // pole nazwy + Zapisz
        int ix = x + 16, iy = y + 54, iw = W - 32 - 76, ih = 24;
        box(c, ix, iy, iw, ih, 8, 0x000000, 0.28f);
        c.drawBorder(ix, iy, iw, ih, Gfx.argb(accent, (focus ? 0.75f : 0.25f) * fade));
        boolean blink = (Util.getMeasuringTimeMs() / 500) % 2 == 0;
        if (name.isEmpty() && !focus) t(c, "Nazwa profilu...", ix + 9, iy + 8, txt, 0.45f);
        else if (name.isEmpty()) {
            t(c, "Nazwa profilu...", ix + 9, iy + 8, txt, 0.45f);
            if (blink) c.fill(ix + 8, iy + 6, ix + 9, iy + 18, Gfx.argb(txt, fade));
        } else {
            t(c, name, ix + 9, iy + 8, txt, 1f);
            if (focus && blink) {
                int cx = ix + 9 + textRenderer.getWidth(name);
                c.fill(cx, iy + 6, cx + 1, iy + 18, Gfx.argb(txt, fade));
            }
        }
        int bx = x + W - 16 - 68;
        boolean hs = in(mx, my, bx, iy, 68, ih);
        box(c, bx, iy, 68, ih, 8, accent, hs ? 0.55f : 0.32f);
        t(c, "Zapisz", bx + (68 - textRenderer.getWidth("Zapisz")) / 2, iy + 8, txt, 1f);

        // zakładki
        int ty = y + 88, tw = (W - 32) / 3;
        box(c, x + 16, ty, tw * 3, 22, 11, 0x000000, 0.25f);
        for (int i = 0; i < 3; i++) {
            int tx = x + 16 + i * tw;
            boolean on = tab == i, hov = in(mx, my, tx, ty, tw, 22);
            if (on) box(c, tx + 2, ty + 2, tw - 4, 18, 9, 0xFFFFFF, 0.14f);
            else if (hov) box(c, tx + 2, ty + 2, tw - 4, 18, 9, 0xFFFFFF, 0.06f);
            t(c, TABS[i], tx + (tw - textRenderer.getWidth(TABS[i])) / 2, ty + 7, txt, on ? 1f : 0.6f);
        }

        // lista profili
        List<Profile> l = list();
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, l.size() - VISIBLE));
        int ly = y + 120;
        if (l.isEmpty()) {
            String e1 = "Brak profili";
            String e2 = "Wpisz nazwę i kliknij Zapisz";
            t(c, e1, x + (W - textRenderer.getWidth(e1)) / 2, ly + 46, txt, 0.7f);
            t(c, e2, x + (W - textRenderer.getWidth(e2)) / 2, ly + 60, txt, 0.4f);
        }
        for (int r = 0; r < VISIBLE; r++) {
            int idx = scroll + r;
            if (idx >= l.size()) break;
            Profile pr = l.get(idx);
            int ry = ly + r * ROWH;
            int rx = x + 16, rw = W - 32 - (l.size() > VISIBLE ? 8 : 0);
            boolean hov = in(mx, my, rx, ry, rw, ROWH - 4);
            box(c, rx, ry, rw, ROWH - 4, 8, 0xFFFFFF, hov ? 0.10f : 0.05f);
            star(c, rx + 9, ry + 9, pr.fav, in(mx, my, rx + 2, ry + 2, 22, ROWH - 8));
            t(c, textRenderer.trimToWidth(pr.name, rw - 150), rx + 28, ry + 5, txt, 1f);
            String sub = pr.loads + (pr.loads == 1 ? " wczytanie" : " wczytań") + "  ·  "
                    + Profiles.ago(Math.max(pr.lastUsed, pr.created));
            t(c, textRenderer.trimToWidth(sub, rw - 120), rx + 28, ry + 16, txt, 0.45f);
            // przyciski po prawej: Kod, x
            int dx = rx + rw - 22, cxk = dx - 44;
            boolean hc = in(mx, my, cxk, ry + 4, 40, 16), hd = in(mx, my, dx, ry + 4, 18, 16);
            box(c, cxk, ry + 4, 40, 16, 6, accent, hc ? 0.45f : (hov ? 0.22f : 0.10f));
            t(c, "Kod", cxk + (40 - textRenderer.getWidth("Kod")) / 2, ry + 8, txt, hov ? 1f : 0.6f);
            box(c, dx, ry + 4, 18, 16, 6, BAD, hd ? 0.55f : (hov ? 0.20f : 0.08f));
            t(c, "x", dx + (18 - textRenderer.getWidth("x")) / 2, ry + 8, txt, hov ? 1f : 0.6f);
        }
        if (l.size() > VISIBLE) {
            int th = VISIBLE * ROWH - 4, kh = Math.max(14, th * VISIBLE / l.size());
            int kyy = ly + (th - kh) * scroll / Math.max(1, l.size() - VISIBLE);
            c.fill(x + W - 22, ly, x + W - 19, ly + th, Gfx.argb(0xFFFFFF, 0.07f * fade));
            c.fill(x + W - 22, kyy, x + W - 19, kyy + kh, Gfx.argb(accent, 0.8f * fade));
        }

        // stopka
        int fy = y + H - 36;
        c.fill(x + 16, fy - 6, x + W - 16, fy - 5, Gfx.argb(0xFFFFFF, 0.07f * fade));
        String[] fl = {"Cofnij", "Kopiuj kod", "Wklej kod", "Domyślne"};
        int fw = (W - 32 - 3 * 6) / 4;
        for (int i = 0; i < 4; i++) {
            int fx = x + 16 + i * (fw + 6);
            boolean dis = i == 0 && !Profiles.canUndo();
            boolean hov = !dis && in(mx, my, fx, fy, fw, 24);
            box(c, fx, fy, fw, 24, 8, i == 3 ? BAD : accent, dis ? 0.05f : (hov ? 0.40f : 0.18f));
            t(c, fl[i], fx + (fw - textRenderer.getWidth(fl[i])) / 2, fy + 8, txt, dis ? 0.35f : 1f);
        }

        c.getMatrices().pop();
    }

    private static int mix(int a, int b, float t) { return Gfx.mix(a, b, t); }

    // ------------------------------------------------------------------ akcje
    private void doSave() {
        String saved = Profiles.saveCurrent(name);
        if (saved == null) { setStatus("Wpisz nazwę profilu", false); return; }
        setStatus("Zapisano " + saved, true);
        name = "";
    }

    private void doLoad(Profile p) {
        String err = Profiles.load(p);
        if (err != null) { setStatus(err, false); return; }
        afterConfigChange();
        setStatus("Wczytano " + p.name, true);
    }

    private void afterConfigChange() {
        Spotify.sync();
        HoloSounds.refresh();
    }

    private void doFooter(int i) {
        switch (i) {
            case 0 -> {
                String err = Profiles.undo();
                if (err != null) setStatus(err, false);
                else { afterConfigChange(); setStatus("Cofnięto wczytanie", true); }
            }
            case 1 -> {
                String code = HoloConfig.exportCode();
                if (code.isEmpty()) { setStatus("Nie udało się utworzyć kodu", false); return; }
                client.keyboard.setClipboard(code);
                setStatus("Skopiowano kod (" + code.length() + " znaków)", true);
            }
            case 2 -> {
                String err = Profiles.loadCode(client.keyboard.getClipboard());
                if (err != null) { setStatus(err, false); return; }
                afterConfigChange();
                if (!name.trim().isEmpty()) {
                    String saved = Profiles.saveCurrent(name);
                    setStatus("Wczytano i zapisano jako " + saved, true);
                    name = "";
                } else {
                    setStatus("Wczytano config ze schowka", true);
                }
            }
            default -> {
                Profiles.defaults();
                afterConfigChange();
                setStatus("Przywrócono ustawienia domyślne", true);
            }
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / sc, my = rawY / sc;
        if (button != 0) return true;
        int x = ox(), y = oy();

        if (in(mx, my, x + W - 34, y + 12, 22, 22)) { close(); return true; }
        if (!in(mx, my, x, y, W, H)) { close(); return true; }

        int ix = x + 16, iy = y + 54, iw = W - 32 - 76, ih = 24;
        focus = in(mx, my, ix, iy, iw, ih);
        if (focus) return true;
        if (in(mx, my, x + W - 16 - 68, iy, 68, ih)) { doSave(); return true; }

        int ty = y + 88, tw = (W - 32) / 3;
        for (int i = 0; i < 3; i++) {
            if (in(mx, my, x + 16 + i * tw, ty, tw, 22)) { tab = i; scroll = 0; return true; }
        }

        List<Profile> l = list();
        int ly = y + 120;
        for (int r = 0; r < VISIBLE; r++) {
            int idx = scroll + r;
            if (idx >= l.size()) break;
            Profile pr = l.get(idx);
            int ry = ly + r * ROWH;
            int rx = x + 16, rw = W - 32 - (l.size() > VISIBLE ? 8 : 0);
            if (!in(mx, my, rx, ry, rw, ROWH - 4)) continue;
            int dx = rx + rw - 22, cxk = dx - 44;
            if (in(mx, my, dx, ry + 4, 18, 16)) {
                Profiles.delete(pr);
                setStatus("Usunięto " + pr.name, true);
            } else if (in(mx, my, cxk, ry + 4, 40, 16)) {
                client.keyboard.setClipboard(pr.code);
                setStatus("Skopiowano kod: " + pr.name + " (" + pr.code.length() + " znaków)", true);
            } else if (in(mx, my, rx + 2, ry + 2, 22, ROWH - 8)) {
                pr.fav = !pr.fav;
                Profiles.save();
            } else {
                doLoad(pr);
            }
            return true;
        }

        int fy = y + H - 36, fw = (W - 32 - 3 * 6) / 4;
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, x + 16 + i * (fw + 6), fy, fw, 24)) {
                if (i == 0 && !Profiles.canUndo()) return true;
                doFooter(i);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        scroll -= (int) Math.signum(vAmt);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focus && name.length() < 24 && (Character.isLetterOrDigit(chr) || " _-.()".indexOf(chr) >= 0)) {
            name += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (focus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) { name = name.substring(0, name.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { doSave(); return true; }
            if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                StringBuilder sb = new StringBuilder(name);
                for (char ch : client.keyboard.getClipboard().toCharArray()) {
                    if (sb.length() >= 24) break;
                    if (Character.isLetterOrDigit(ch) || " _-.()".indexOf(ch) >= 0) sb.append(ch);
                }
                name = sb.toString();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
