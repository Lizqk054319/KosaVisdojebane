package pl.holovis;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Wspólne narzędzia do rysowania efektów w świecie: jednolite półprzezroczyste kształty
 * (zwykłe mieszanie alfa - kolor zostaje taki, jaki wybrano, nie "wypala się" do bieli)
 * oraz model gracza zbudowany ze skina.
 */
public final class WorldFx {
    private WorldFx() {}

    public static final Identifier WHITE = Identifier.of("holovis", "textures/misc/white.png");
    public static final Identifier STEVE = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    private static final int LIGHT = 15728880;   // pełne światło
    private static final int OVERLAY = 655360;   // brak czerwonego mignięcia
    /** Jeden piksel skina w blokach (gracz ma 32 px = 1,8 bloku). */
    public static final float PX = 1.8f / 32f;

    // Warstwa "beacon beam" (przezroczysta): bez oświetlenia i bez zapisu głębi, więc kolory zostają
    // takie, jak ustawiono, a półprzezroczyste elementy się nie zasłaniają (nic nie migocze).
    public static RenderLayer flat() { return RenderLayer.getBeaconBeam(WHITE, true); }

    public static RenderLayer skin(Identifier tex) { return RenderLayer.getBeaconBeam(tex, true); }

    public static int argb(int rgb, float a) {
        return (((int) (Math.max(0f, Math.min(1f, a)) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int shade(int argb, float k) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 255) * k), g = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb, float u, float v) {
        vc.vertex(m, x, y, z).color(argb).texture(u, v).overlay(OVERLAY).light(LIGHT).normal(0f, 1f, 0f);
    }

    /** Jednolity czworokąt (widoczny z obu stron). */
    public static void quad2(VertexConsumer vc, Matrix4f m, int c,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vtx(vc, m, x1, y1, z1, c, 0.5f, 0.5f); vtx(vc, m, x2, y2, z2, c, 0.5f, 0.5f);
        vtx(vc, m, x3, y3, z3, c, 0.5f, 0.5f); vtx(vc, m, x4, y4, z4, c, 0.5f, 0.5f);
        vtx(vc, m, x4, y4, z4, c, 0.5f, 0.5f); vtx(vc, m, x3, y3, z3, c, 0.5f, 0.5f);
        vtx(vc, m, x2, y2, z2, c, 0.5f, 0.5f); vtx(vc, m, x1, y1, z1, c, 0.5f, 0.5f);
    }

    /**
     * Płaski pasek między dwoma punktami, zwrócony szeroką stroną do kamery.
     * (cx,cy,cz) = pozycja kamery względem układu, w którym podano punkty, ze znakiem minus
     * (czyli wektor "od kamery do początku układu").
     */
    public static void ribbon(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                              float hw, int c, float cx, float cy, float cz) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float mx = (x1 + x2) * 0.5f + cx, my = (y1 + y2) * 0.5f + cy, mz = (z1 + z2) * 0.5f + cz;
        float px = dy * mz - dz * my, py = dz * mx - dx * mz, pz = dx * my - dy * mx;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1e-5f) { px = 1f; py = 0f; pz = 0f; len = 1f; }
        px = px / len * hw; py = py / len * hw; pz = pz / len * hw;
        quad2(vc, m, c, x1 + px, y1 + py, z1 + pz, x1 - px, y1 - py, z1 - pz,
                x2 - px, y2 - py, z2 - pz, x2 + px, y2 + py, z2 + pz);
    }

    /** Pionowy pierścień-obręcz (walec bez dna) o promieniu r, wysokości h, na wysokości y. */
    public static void band(VertexConsumer vc, Matrix4f m, float r, float y, float h, int c, int segs) {
        for (int i = 0; i < segs; i++) {
            double a1 = i * Math.PI * 2 / segs, a2 = (i + 1) * Math.PI * 2 / segs;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float x2 = (float) Math.cos(a2) * r, z2 = (float) Math.sin(a2) * r;
            quad2(vc, m, c, x1, y, z1, x2, y, z2, x2, y + h, z2, x1, y + h, z1);
        }
    }

    /** Płaski pierścień (płaszczyzna pozioma) od promienia r0 do r1. */
    public static void flatRing(VertexConsumer vc, Matrix4f m, float r0, float r1, float y, int cInner, int cOuter, int segs) {
        for (int i = 0; i < segs; i++) {
            double a1 = i * Math.PI * 2 / segs, a2 = (i + 1) * Math.PI * 2 / segs;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            vtx(vc, m, c1 * r0, y, s1 * r0, cInner, 0.5f, 0.5f); vtx(vc, m, c1 * r1, y, s1 * r1, cOuter, 0.5f, 0.5f);
            vtx(vc, m, c2 * r1, y, s2 * r1, cOuter, 0.5f, 0.5f); vtx(vc, m, c2 * r0, y, s2 * r0, cInner, 0.5f, 0.5f);
            vtx(vc, m, c2 * r0, y, s2 * r0, cInner, 0.5f, 0.5f); vtx(vc, m, c2 * r1, y, s2 * r1, cOuter, 0.5f, 0.5f);
            vtx(vc, m, c1 * r1, y, s1 * r1, cOuter, 0.5f, 0.5f); vtx(vc, m, c1 * r0, y, s1 * r0, cInner, 0.5f, 0.5f);
        }
    }

    // ------------------------------------------------------------------
    //  Model gracza ze skina (jednostki: piksele skina, stopy w y=0, twarz w stronę +Z)
    // ------------------------------------------------------------------

    /** Pozycja kamery w układzie modelu (do pomijania tylnych ścianek - dzięki temu nic nie "miga"). */
    public static Vector3f cameraLocal(Matrix4f model) {
        return new Matrix4f(model).invert().transformPosition(new Vector3f());
    }

    /**
     * @param tint     kolor mnożony ze skinem (ARGB; alfa = przezroczystość)
     * @param inflate  powiększenie modelu w pikselach (0 = normalny)
     * @param outer    czy rysować też zewnętrzną warstwę skina (włosy/kurtka/spodnie)
     */
    public static void player(VertexConsumer vc, Matrix4f m, Vector3f cam, int tint, float inflate, boolean outer) {
        float g = inflate;
        box(vc, m, cam, -4 - g, 0 - g, -2 - g, 0 + g, 12 + g, 2 + g, 4, 12, 4, 0, 16, tint);      // prawa noga
        box(vc, m, cam, 0 - g, 0 - g, -2 - g, 4 + g, 12 + g, 2 + g, 4, 12, 4, 16, 48, tint);      // lewa noga
        box(vc, m, cam, -8 - g, 12 - g, -2 - g, -4 + g, 24 + g, 2 + g, 4, 12, 4, 40, 16, tint);   // prawa ręka
        box(vc, m, cam, 4 - g, 12 - g, -2 - g, 8 + g, 24 + g, 2 + g, 4, 12, 4, 32, 48, tint);     // lewa ręka
        box(vc, m, cam, -4 - g, 12 - g, -2 - g, 4 + g, 24 + g, 2 + g, 8, 12, 4, 16, 16, tint);    // tułów
        box(vc, m, cam, -4 - g, 24 - g, -4 - g, 4 + g, 32 + g, 4 + g, 8, 8, 8, 0, 0, tint);       // głowa
        if (outer) {
            float o = g + 0.35f;
            box(vc, m, cam, -4 - o, 0 - o, -2 - o, 0 + o, 12 + o, 2 + o, 4, 12, 4, 0, 32, tint);
            box(vc, m, cam, 0 - o, 0 - o, -2 - o, 4 + o, 12 + o, 2 + o, 4, 12, 4, 0, 48, tint);
            box(vc, m, cam, -8 - o, 12 - o, -2 - o, -4 + o, 24 + o, 2 + o, 4, 12, 4, 40, 32, tint);
            box(vc, m, cam, 4 - o, 12 - o, -2 - o, 8 + o, 24 + o, 2 + o, 4, 12, 4, 48, 48, tint);
            box(vc, m, cam, -4 - o, 12 - o, -2 - o, 4 + o, 24 + o, 2 + o, 8, 12, 4, 16, 32, tint);
            box(vc, m, cam, -4 - o - 0.15f, 24 - o, -4 - o - 0.15f, 4 + o + 0.15f, 32 + o + 0.15f, 4 + o + 0.15f, 8, 8, 8, 32, 0, tint);
        }
    }

    private static void face(VertexConsumer vc, Matrix4f m, int c,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        vtx(vc, m, x1, y1, z1, c, u1, v1); vtx(vc, m, x2, y2, z2, c, u2, v2);
        vtx(vc, m, x3, y3, z3, c, u3, v3); vtx(vc, m, x4, y4, z4, c, u4, v4);
    }

    /** Prostopadłościan z teksturą skina (układ UV jak w grze). Rysuje tylko ścianki zwrócone do kamery. */
    private static void box(VertexConsumer vc, Matrix4f m, Vector3f cam,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            int w, int h, int d, int u0, int v0, int tint) {
        final float T = 64f;
        float uf0 = (u0 + d) / T, uf1 = (u0 + d + w) / T;
        float vf0 = (v0 + d) / T, vf1 = (v0 + d + h) / T;
        float vt0 = v0 / T, vt1 = (v0 + d) / T;

        if (cam.z > z1) {   // przód (+Z)
            int c = shade(tint, 0.95f);
            face(vc, m, c, x0, y0, z1, uf0, vf1, x1, y0, z1, uf1, vf1, x1, y1, z1, uf1, vf0, x0, y1, z1, uf0, vf0);
        }
        if (cam.z < z0) {   // tył (-Z)
            int c = shade(tint, 0.85f);
            float ub0 = (u0 + 2 * d + w) / T, ub1 = (u0 + 2 * d + 2 * w) / T;
            face(vc, m, c, x1, y0, z0, ub0, vf1, x0, y0, z0, ub1, vf1, x0, y1, z0, ub1, vf0, x1, y1, z0, ub0, vf0);
        }
        if (cam.x < x0) {   // prawa strona postaci (-X)
            int c = shade(tint, 0.8f);
            float ur0 = u0 / T, ur1 = (u0 + d) / T;
            face(vc, m, c, x0, y0, z0, ur0, vf1, x0, y0, z1, ur1, vf1, x0, y1, z1, ur1, vf0, x0, y1, z0, ur0, vf0);
        }
        if (cam.x > x1) {   // lewa strona postaci (+X)
            int c = shade(tint, 0.8f);
            float ul0 = (u0 + d + w) / T, ul1 = (u0 + 2 * d + w) / T;
            face(vc, m, c, x1, y0, z1, ul0, vf1, x1, y0, z0, ul1, vf1, x1, y1, z0, ul1, vf0, x1, y1, z1, ul0, vf0);
        }
        if (cam.y > y1) {   // góra
            int c = shade(tint, 1.0f);
            face(vc, m, c, x0, y1, z1, uf0, vt1, x1, y1, z1, uf1, vt1, x1, y1, z0, uf1, vt0, x0, y1, z0, uf0, vt0);
        }
        if (cam.y < y0) {   // dół
            int c = shade(tint, 0.6f);
            float ud0 = (u0 + d + w) / T, ud1 = (u0 + d + 2 * w) / T;
            face(vc, m, c, x0, y0, z0, ud0, vt1, x1, y0, z0, ud1, vt1, x1, y0, z1, ud1, vt0, x0, y0, z1, ud0, vt0);
        }
    }
}
