package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.HudRenderer.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Karta w stylu Spotify: po otwarciu czatu i najechaniu na element Spotify pojawia się płynnie okno
 * z tekstem piosenki (przewija się samo), paskiem postępu do przewijania i przyciskami.
 */
public final class SpotifyOverlay {
    private SpotifyOverlay() {}

    private static final int W = 264, H = 200;
    public static final int CARD_W = W, CARD_H = H;
    /** true w edytorze HUD (L): karta jest tylko podglądem, bez klikania. */
    public static boolean editing;
    /** true podczas zmiany rozmiaru karty - karta nie znika, gdy kursor na chwilę ją opuści. */
    public static boolean pinned;
    private static int[] lastRect;
    private static final int GREEN = 0x1DB954, WHITE = 0xFFFFFF, MUTED = 0xA7A7A7, DIM = 0x6A6A6A;

    private static boolean wasDown, dragging;
    private static float dragRatio;
    private static float vis;            // 0..1 animacja pojawiania
    private static float scroll = -1f;   // płynnie animowany indeks linii
    private static String lastTrack = "";
    private static long lastNs = System.nanoTime();

    private static final int LINE_H = 10, BLOCK_GAP = 9, WRAP_W = (W - 36) * 100 / 118;
    private static List<Lyrics.Line> wrapSrc;
    private static int wrapState = -1;
    private static List<List<String>> wrapped = new ArrayList<>();
    private static float[] centres = new float[0];

    private static List<String> wrapLine(TextRenderer tr, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) { out.add(""); return out; }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && tr.getWidth(test) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
            while (tr.getWidth(cur.toString()) > maxW && cur.length() > 1) {   // bardzo długie "słowo"
                String head = tr.trimToWidth(cur.toString(), maxW);
                if (head.isEmpty()) break;
                out.add(head);
                cur = new StringBuilder(cur.substring(head.length()));
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static void ensureWrapped(TextRenderer tr, List<Lyrics.Line> lines, int st) {
        if (lines == wrapSrc && st == wrapState) return;
        wrapSrc = lines;
        wrapState = st;
        wrapped = new ArrayList<>();
        centres = new float[lines.size()];
        float y = 0f;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).text();
            if (t.isEmpty() && st == 2) t = "♪";
            List<String> bl = wrapLine(tr, t, WRAP_W);
            wrapped.add(bl);
            float h = bl.size() * LINE_H;
            centres[i] = y + h / 2f;
            y += h + BLOCK_GAP;
        }
    }

    /** Aktualny prostokąt karty na ekranie {x, y, szerokość, wysokość} albo null, gdy karta jest ukryta. */
    public static int[] cardRect() { return vis > 0.3f ? lastRect : null; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static void render(Screen screen, DrawContext c, int mx, int my, float delta) {
        HoloConfig cfg = HoloConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        long ns = System.nanoTime();
        float dt = Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;
        if (!cfg.spotify) { wasDown = down; dragging = false; vis = 0f; lastRect = null; return; }

        float cs = MathHelper.clamp(cfg.spCardScale, 0.5f, 1.6f);
        int cw = Math.round(W * cs), chh = Math.round(H * cs);   // rozmiar karty na ekranie
        int sw = screen.width, sh = screen.height;
        int[] ep = HudRenderer.pixelPos(Element.SPOTIFY, sw, sh);
        int[] es = HudRenderer.size(Element.SPOTIFY);
        int px = MathHelper.clamp(ep[0], 6, Math.max(6, sw - cw - 6));
        int py = ep[1] + es[1] + 4;
        if (py + chh > sh - 6) py = Math.max(6, ep[1] - chh - 4);

        boolean over = in(mx, my, ep[0], ep[1], es[0], es[1]) || in(mx, my, px - 4, py - 4, cw + 8, chh + 8);
        float goal = (over || dragging || pinned) ? 1f : 0f;
        vis += (goal - vis) * Math.min(1f, dt * 14f);
        if (Math.abs(goal - vis) < 0.01f) vis = goal;
        if (vis < 0.02f) { wasDown = down; lastRect = null; return; }
        boolean live = !editing && vis > 0.6f && (over || dragging);

        float a = vis;
        py += (int) ((1f - a) * 8);
        lastRect = new int[]{px, py, cw, chh};
        TextRenderer tr = mc.textRenderer;

        // dalej rysujemy w układzie karty (skala cs, lewy górny róg = 0,0); mysz przeliczamy tak samo
        int screenX = px, screenY = py;
        mx = (int) ((mx - screenX) / cs);
        my = (int) ((my - screenY) / cs);
        px = 0;
        py = 0;
        c.getMatrices().push();
        c.getMatrices().translate(screenX, screenY, 0f);
        c.getMatrices().scale(cs, cs, 1f);

        // --- karta ---
        Gfx.rrect(c, px - 1, py - 1, W + 2, H + 2, 11, Gfx.argb(0x2C2C2C, a));
        Gfx.rrect(c, px, py, W, H, 10, Gfx.argb(0x121212, 0.97f * a));
        // delikatny poblask na górze
        for (int i = 0; i < 34; i++)
            c.fill(px + 5, py + 2 + i, px + W - 5, py + 3 + i, Gfx.argb(GREEN, 0.10f * a * (1f - i / 34f)));

        // --- tekst piosenki ---
        int ly1 = py + 46, ly2 = py + H - 66;
        int lh = 17, mid = (ly1 + ly2) / 2;
        String key = Spotify.artist + "|" + Spotify.title;
        if (!key.equals(lastTrack)) { lastTrack = key; scroll = -1f; }
        List<Lyrics.Line> lines = Lyrics.lines;
        int st = Lyrics.state;

        if (!Spotify.running) {
            center(c, tr, "Brak połączenia ze Spotify", px, mid - 4, Gfx.argb(DIM, a));
        } else if (st == 1) {
            center(c, tr, "Wczytywanie tekstu...", px, mid - 4, Gfx.argb(DIM, a));
        } else if (st == 4 || st == 0 || lines.isEmpty()) {
            center(c, tr, "Brak tekstu dla tego utworu", px, mid - 4, Gfx.argb(DIM, a));
        } else {
            float target;
            if (st == 2) {
                int cur = Lyrics.indexAt(Spotify.position());
                target = Math.max(0, cur);
            } else {
                float ratio = Spotify.durMs > 0 ? MathHelper.clamp(Spotify.position() / (float) Spotify.durMs, 0f, 1f) : 0f;
                target = ratio * Math.max(0, lines.size() - 1);
            }
            if (scroll < 0f) scroll = target;
            scroll += (target - scroll) * Math.min(1f, dt * 7f);

            // długie linie zawijamy (zamiast ucinać), więc tekst nigdy nie "znika" przy podświetleniu
            ensureWrapped(tr, lines, st);
            int n = wrapped.size();
            int i0 = MathHelper.clamp((int) Math.floor(scroll), 0, n - 1);
            int i1 = Math.min(n - 1, i0 + 1);
            float frac = MathHelper.clamp(scroll - i0, 0f, 1f);
            float centre = centres[i0] + (centres[i1] - centres[i0]) * frac;

            int curIdx = st == 2 ? Lyrics.indexAt(Spotify.position()) : -1;
            int first = Math.max(0, i0 - 8), last = Math.min(n - 1, i0 + 9);
            for (int i = first; i <= last; i++) {
                List<String> bl = wrapped.get(i);
                if (bl.isEmpty()) continue;
                float d = i - scroll;
                float w = MathHelper.clamp(1f - Math.abs(d), 0f, 1f);
                float sc = 1f + 0.18f * w;
                float dist = Math.abs(centres[i] - centre);
                float fade = 1f - MathHelper.clamp((dist - 22f) / 34f, 0f, 1f);
                float la = (0.40f + 0.60f * w) * fade * a;
                if (st == 3) la = (0.58f + 0.42f * w) * fade * a;
                int col = Gfx.mix(0x7A7A7A, WHITE, st == 2 && i == curIdx ? Math.max(w, 0.85f) : w);
                float bh = bl.size() * LINE_H;
                float yc = mid + (centres[i] - centre);
                if (yc + bh * sc / 2f < ly1 - 14 || yc - bh * sc / 2f > ly2 + 14) continue;   // poza polem tekstu
                c.getMatrices().push();
                c.getMatrices().translate(px + 16, yc, 0f);
                c.getMatrices().scale(sc, sc, 1f);
                for (int k = 0; k < bl.size(); k++)
                    Gfx.text(c, tr, bl.get(k), 0, (int) (-bh / 2f + k * LINE_H), Gfx.argb(col, la), false);
                c.getMatrices().pop();
            }
        }

        // maska zamiast scissor: zakrywa tekst wychodzący poza pole z tekstem (działa przy każdej skali)
        int mask = Gfx.argb(0x121212, a);
        c.fill(px + 4, py + 30, px + W - 4, ly1, mask);
        c.fill(px + 4, ly2, px + W - 4, py + H - 12, mask);

        // --- nagłówek: logo, tytuł, wykonawca ---
        Gfx.circle(c, px + 22, py + 22, 10, Gfx.argb(GREEN, a));
        for (int i = 0; i < 3; i++) {
            int bw = 12 - i * 2, by = py + 17 + i * 4;
            c.fill(px + 22 - bw / 2, by, px + 22 + bw / 2, by + 2, Gfx.argb(0x121212, a));
        }
        String title = Spotify.title.isEmpty() ? (Spotify.running ? "Spotify" : "Nie wykryto Spotify") : Spotify.title;
        Gfx.text(c, tr, tr.trimToWidth(title, W - 62), px + 40, py + 14, Gfx.argb(WHITE, a), false);
        String artist = Spotify.running ? Spotify.artist : "Uruchom Spotify na komputerze";
        Gfx.text(c, tr, tr.trimToWidth(artist, W - 62), px + 40, py + 26, Gfx.argb(MUTED, a), false);


        // przyciemnienie na krawędziach tekstu (płynne wejście/wyjście linii)
        c.fillGradient(px + 4, ly1, px + W - 4, ly1 + 12, Gfx.argb(0x121212, 0.95f * a), Gfx.argb(0x121212, 0f));
        c.fillGradient(px + 4, ly2 - 12, px + W - 4, ly2, Gfx.argb(0x121212, 0f), Gfx.argb(0x121212, 0.95f * a));

        // --- pasek postępu ---
        int bx = px + 16, bw = W - 32, by = py + H - 56;
        boolean canSeek = Spotify.running && Spotify.hasTimeline && Spotify.durMs > 0;
        boolean barHover = canSeek && (in(mx, my, bx - 4, by - 6, bw + 8, 16) || dragging);
        if (canSeek) {
            float ratio = dragging ? dragRatio : MathHelper.clamp(Spotify.position() / (float) Spotify.durMs, 0f, 1f);
            Gfx.rrect(c, bx, by, bw, 4, 2, Gfx.argb(0x4D4D4D, a));
            int fw = (int) (bw * ratio);
            if (fw > 0) Gfx.rrect(c, bx, by, Math.max(4, fw), 4, 2, Gfx.argb(barHover ? GREEN : WHITE, a));
            if (barHover) Gfx.circle(c, bx + fw, by + 2, 5, Gfx.argb(WHITE, a));
            long shown = dragging ? (long) (dragRatio * Spotify.durMs) : Spotify.position();
            Gfx.text(c, tr, time(shown), bx, by + 9, Gfx.argb(MUTED, a), false);
            String end = time(Spotify.durMs);
            Gfx.text(c, tr, end, bx + bw - tr.getWidth(end), by + 9, Gfx.argb(MUTED, a), false);

            if (live && down && !wasDown && in(mx, my, bx - 4, by - 6, bw + 8, 16)) dragging = true;
            if (dragging) {
                dragRatio = MathHelper.clamp((mx - bx) / (float) bw, 0f, 1f);
                if (!down) {
                    Spotify.seek((long) (dragRatio * Spotify.durMs));
                    dragging = false;
                }
            }
        } else {
            dragging = false;
            Gfx.rrect(c, bx, by, bw, 4, 2, Gfx.argb(0x4D4D4D, 0.5f * a));
            Gfx.text(c, tr, "Przewijanie: Windows 10/11", bx, by + 9, Gfx.argb(DIM, a), false);
        }

        // --- przyciski ---
        int cy = py + H - 24, cx = px + W / 2;
        boolean hPrev = in(mx, my, cx - 52, cy - 12, 24, 24);
        boolean hPlay = in(mx, my, cx - 13, cy - 13, 26, 26);
        boolean hNext = in(mx, my, cx + 28, cy - 12, 24, 24);

        int icPrev = Gfx.argb(hPrev ? WHITE : MUTED, a), icNext = Gfx.argb(hNext ? WHITE : MUTED, a);
        // poprzedni
        c.fill(cx - 46, cy - 6, cx - 44, cy + 6, icPrev);
        Gfx.triLeft(c, cx - 43, cy - 6, 9, 12, icPrev);
        // następny
        c.fill(cx + 44, cy - 6, cx + 46, cy + 6, icNext);
        Gfx.triRight(c, cx + 34, cy - 6, 9, 12, icNext);
        // graj / pauza
        int r = hPlay ? 13 : 12;
        Gfx.circle(c, cx, cy, r, Gfx.argb(WHITE, a));
        int dark = Gfx.argb(0x121212, a);
        if (Spotify.playing) {
            c.fill(cx - 4, cy - 5, cx - 1, cy + 5, dark);
            c.fill(cx + 1, cy - 5, cx + 4, cy + 5, dark);
        } else {
            Gfx.triRight(c, cx - 3, cy - 6, 10, 12, dark);
        }

        if (live && down && !wasDown) {
            if (hPrev) Spotify.previous();
            else if (hPlay) Spotify.toggle();
            else if (hNext) Spotify.next();
        }
        wasDown = down;
        c.getMatrices().pop();
    }

    private static void center(DrawContext c, TextRenderer tr, String s, int px, int y, int argb) {
        Gfx.text(c, tr, s, px + (W - tr.getWidth(s)) / 2, y, argb, false);
    }
}
