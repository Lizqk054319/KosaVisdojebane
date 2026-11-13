package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Target ESP: świecący pierścień krążący w górę i w dół wokół celu (albo okrąg pod jego nogami). */
public final class TargetEsp {
    private TargetEsp() {}

    /** Kółko zwrócone do kamery (środek, promień r w blokach). camL = pozycja kamery w układzie rysowania. */
    private static void disc(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, int color,
                             float camX, float camY, float camZ) {
        float dx = camX - x, dy = camY - y, dz = camZ - z;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-4f) return;
        dx /= dl; dy /= dl; dz /= dl;
        // prawo = góra(0,1,0) x kierunek do kamery
        float rx = dz, ry = 0f, rz = -dx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) { rx = 1f; rz = 0f; rl = 1f; }
        rx /= rl; rz /= rl;
        // góra = kierunek do kamery x prawo
        float ux = dy * rz - dz * ry, uy = dz * rx - dx * rz, uz = dx * ry - dy * rx;
        final int N = 14;
        for (int i = 0; i < N; i++) {
            double a1 = i * Math.PI * 2 / N, a2 = (i + 1) * Math.PI * 2 / N;
            float c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
            float c2 = (float) Math.cos(a2) * r, s2 = (float) Math.sin(a2) * r;
            WorldFx.quad2(vc, m, color,
                    x, y, z,
                    x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1,
                    x + rx * c2 + ux * s2, y + ry * c2 + uy * s2, z + rz * c2 + uz * s2,
                    x, y, z);
        }
    }

    /** Dwa duszki krążące wokół całego gracza (góra-dół po spirali), z wyraźną smugą za sobą. */
    private static void orbs(VertexConsumer vc, Matrix4f m, float h, float w, int rgb, long now,
                             float cx, float cy, float cz) {
        final int TR = 30;           // liczba odcinków smugi
        final long STEP = 20L;       // ms między punktami smugi
        float R = w * 0.5f + 0.55f;
        int bright = Gfx.mix(rgb, 0xFFFFFF, 0.35f);
        for (int o = 0; o < 2; o++) {
            float[] px = new float[TR + 1], py = new float[TR + 1], pz = new float[TR + 1];
            for (int k = 0; k <= TR; k++) {
                double t = (now - k * STEP) / 1000.0;
                double ang = t * 3.2 + o * Math.PI;
                double up = 0.5 + 0.5 * Math.sin(t * 1.7 + o * Math.PI);
                px[k] = (float) (Math.cos(ang) * R);
                pz[k] = (float) (Math.sin(ang) * R);
                py[k] = 0.12f + (float) up * Math.max(0.2f, h - 0.15f);
            }
            for (int k = 0; k < TR; k++) {
                float f = 1f - k / (float) TR;
                float hw = 0.010f + 0.052f * f * f;
                WorldFx.ribbon(vc, m, px[k], py[k], pz[k], px[k + 1], py[k + 1], pz[k + 1],
                        hw * 2.4f, WorldFx.argb(rgb, 0.20f * f), cx, cy, cz);
                WorldFx.ribbon(vc, m, px[k], py[k], pz[k], px[k + 1], py[k + 1], pz[k + 1],
                        hw, WorldFx.argb(Gfx.mix(rgb, bright, 1f - f), 0.92f * f), cx, cy, cz);
            }
            // główka duszka: poświata + jądro + jasny środek (kamera w układzie rysowania = -(wektor od kamery))
            float camX = -cx, camY = -cy, camZ = -cz;
            disc(vc, m, px[0], py[0], pz[0], 0.20f, WorldFx.argb(rgb, 0.22f), camX, camY, camZ);
            disc(vc, m, px[0], py[0], pz[0], 0.12f, WorldFx.argb(rgb, 0.75f), camX, camY, camZ);
            disc(vc, m, px[0], py[0], pz[0], 0.065f, WorldFx.argb(0xFFFFFF, 1f), camX, camY, camZ);
        }
    }

    public static void render(WorldRenderContext ctx) {
        HoloConfig cfg = HoloConfig.get();
        if (!cfg.targetEsp) return;
        LivingEntity t = TargetHud.currentTarget();
        if (t == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickCounter().getTickDelta(false);
        Vec3d lp = t.getLerpedPos(td);
        long now = Util.getMeasuringTimeMs();
        int rgb = cfg.espColor.rgb();
        float h = t.getHeight(), w = Math.max(0.5f, t.getWidth());

        ms.push();
        ms.translate((float) (lp.x - cam.x), (float) (lp.y - cam.y), (float) (lp.z - cam.z));
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(WorldFx.flat());

        if (cfg.espStyle == 2) {
            orbs(vc, m, h, w, rgb, now, (float) (lp.x - cam.x), (float) (lp.y - cam.y), (float) (lp.z - cam.z));
        } else if (cfg.espStyle == 0) {
            float r = w * 0.5f + 0.32f;
            double phase = (now % 1700L) / 1700.0;
            for (int k = 7; k >= 0; k--) {
                double p = phase - k * 0.022;
                float y = (float) (0.5 + 0.5 * Math.sin(p * Math.PI * 2)) * (h - 0.1f);
                float a = 1f - k / 8f;
                float th = k == 0 ? 0.11f : 0.07f;
                WorldFx.band(vc, m, r, y, th, WorldFx.argb(k == 0 ? Gfx.mix(rgb, 0xFFFFFF, 0.35f) : rgb, a * (k == 0 ? 0.95f : 0.55f)), 40);
            }
        } else {
            double pulse = 0.5 + 0.5 * Math.sin(now / 380.0);
            float r = (float) (w * 0.5f + 0.42f + 0.10f * pulse);
            WorldFx.flatRing(vc, m, 0f, r - 0.10f, 0.04f, WorldFx.argb(rgb, 0.04f), WorldFx.argb(rgb, 0.22f), 44);
            WorldFx.flatRing(vc, m, r - 0.10f, r, 0.045f, WorldFx.argb(rgb, 0.90f), WorldFx.argb(rgb, 0.90f), 44);
        }
        ms.pop();

        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(WorldFx.flat());
    }
}
