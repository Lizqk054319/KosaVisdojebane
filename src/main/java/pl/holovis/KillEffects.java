package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Kill Effect.
 * Duch: półprzezroczysty model gracza ze skina unosi się w górę (kolor = wybrany kolor ducha).
 * Piorun: uderza piorun w kolorze pioruna, a zabity gracz zostaje wystrzelony w górę.
 */
public final class KillEffects {
    private KillEffects() {}

    private record Fx(Vec3d pos, float yaw, long start, int kind, float secs, int color, float height, long seed,
                      Identifier skin) {}

    private static final List<Fx> LIST = new ArrayList<>();

    /** Wywołanie po zabiciu (sprawdza, czy efekt jest włączony). */
    public static void spawn(Vec3d pos, float yaw, Identifier skin) {
        if (!HoloConfig.get().killFx) return;
        add(pos, yaw, skin);
    }

    private static void add(Vec3d pos, float yaw, Identifier skin) {
        HoloConfig c = HoloConfig.get();
        Identifier sk = skin == null ? WorldFx.STEVE : skin;
        long now = Util.getMeasuringTimeMs();
        // tryb: 0 = tylko duch, 1 = tylko piorun, 2 = oba
        if (c.killMode == 0 || c.killMode == 2)
            LIST.add(new Fx(pos, yaw, now, 0, Math.max(0.5f, c.killGhostSecs), c.killGhostColor.rgb(), 0f, now, sk));
        if (c.killMode == 1 || c.killMode == 2) {
            LIST.add(new Fx(pos, yaw, now, 1, 2.0f, c.killBoltColor.rgb(), Math.max(1f, c.killBoltHeight), now, sk));
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", "entity.lightning_bolt.thunder"));
            if (ev != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ev, 0.8f, 1f));
        }
    }

    /** Przycisk "Test" w menu: efekt 3 bloki przed graczem, nawet gdy moduł jest wyłączony. */
    public static void spawnTest() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        add(mc.player.getPos().add(flat.multiply(3.0)), mc.player.getYaw() + 180f, mc.player.getSkinTextures().texture());
    }

    // ------------------------------------------------------------------
    private static int argb(int rgb, float a) { return WorldFx.argb(rgb, a); }

    public static void render(WorldRenderContext ctx) {
        if (LIST.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        long now = Util.getMeasuringTimeMs();

        Set<Identifier> used = new HashSet<>();
        Iterator<Fx> it = LIST.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            float t = (now - f.start()) / (f.secs() * 1000f);
            if (t >= 1f) { it.remove(); continue; }
            double rx = f.pos().x - cam.x, ry = f.pos().y - cam.y, rz = f.pos().z - cam.z;
            if (f.kind() == 0) ghost(ms, vcp, used, f, t, rx, ry, rz);
            else bolt(ms, vcp, used, f, t, rx, ry, rz);
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) {
            for (Identifier id : used) imm.draw(WorldFx.skin(id));
            imm.draw(WorldFx.flat());
        }
    }

    /** Model gracza ze skina: skin (lekko wybarwiony) + półprzezroczysta poświata w kolorze efektu. */
    private static void playerModel(MatrixStack ms, VertexConsumerProvider vcp, Set<Identifier> used, Identifier skin,
                                    int color, float skinAlpha, float skinTint, float auraAlpha) {
        Matrix4f m = ms.peek().getPositionMatrix();
        Vector3f camL = WorldFx.cameraLocal(m);
        if (skinAlpha > 0.01f) {
            used.add(skin);
            VertexConsumer sv = vcp.getBuffer(WorldFx.skin(skin));
            WorldFx.player(sv, m, camL, argb(Gfx.mix(0xFFFFFF, color, skinTint), skinAlpha), 0f, true);
        }
        if (auraAlpha > 0.01f) {
            VertexConsumer fv = vcp.getBuffer(WorldFx.flat());
            WorldFx.player(fv, m, camL, argb(color, auraAlpha), 1.1f, false);
        }
    }

    private static void ghost(MatrixStack ms, VertexConsumerProvider vcp, Set<Identifier> used, Fx f, float t,
                              double rx, double ry, double rz) {
        float rise = 3.0f + f.secs() * 0.9f;
        for (int k = 0; k < 3; k++) {
            float tk = t - k * 0.04f;
            if (tk <= 0f) continue;
            float e = 1f - (1f - tk) * (1f - tk) * (1f - tk);
            float fadeIn = Math.min(1f, tk / 0.10f);
            float fadeOut = tk < 0.60f ? 1f : Math.max(0f, 1f - (tk - 0.60f) / 0.40f);
            float k0 = k == 0 ? 1f : (k == 1 ? 0.35f : 0.18f);
            float a = fadeIn * fadeOut * k0;
            float sway = (float) Math.sin(tk * 5.0) * 0.07f;
            ms.push();
            ms.translate((float) rx + sway, (float) ry + e * rise, (float) rz);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.yaw() + tk * 40f));
            ms.scale(WorldFx.PX, WorldFx.PX, WorldFx.PX);
            playerModel(ms, vcp, used, f.skin(), f.color(), a * 0.75f, 0.45f, a * 0.40f);
            ms.pop();
        }
    }

    private static void bolt(MatrixStack ms, VertexConsumerProvider vcp, Set<Identifier> used, Fx f, float t,
                             double rx, double ry, double rz) {
        float el = t * f.secs();

        // sam piorun: błysk, migotanie, wygasanie (ok. 1 s)
        if (el < 1.0f) {
            Random fl = new Random(f.seed() + (long) (el * 40));
            float ba;
            if (el < 0.15f) ba = 1f;
            else if (el < 0.65f) ba = 0.7f + 0.3f * fl.nextFloat();
            else ba = (1f - (el - 0.65f) / 0.35f) * (0.75f + 0.25f * fl.nextFloat());
            ba = MathHelper.clamp(ba, 0f, 1f);

            ms.push();
            ms.translate((float) rx, (float) ry, (float) rz);
            Matrix4f m = ms.peek().getPositionMatrix();
            VertexConsumer vc = vcp.getBuffer(WorldFx.flat());
            float cx = (float) rx, cy = (float) ry, cz = (float) rz;

            Random r = new Random(f.seed() * 31 + 7);
            int n = 18;
            float topY = Math.max(40f, f.height() + 28f);
            float[] xs = new float[n + 1], ys = new float[n + 1], zs = new float[n + 1];
            float ox = 0, oz = 0;
            ys[0] = topY;
            for (int i = 1; i <= n; i++) {
                float k = 1f - i / (float) n;
                ox += (r.nextFloat() - 0.5f) * 3.0f;
                oz += (r.nextFloat() - 0.5f) * 3.0f;
                xs[i] = i == n ? 0 : ox * k;
                zs[i] = i == n ? 0 : oz * k;
                ys[i] = topY * (1f - i / (float) n);
            }
            for (int i = 1; i <= n; i++)
                boltSeg(vc, m, xs[i - 1], ys[i - 1], zs[i - 1], xs[i], ys[i], zs[i], 1f, f.color(), ba, cx, cy, cz);

            for (int bi = 0; bi < 4; bi++) {
                int s0 = 3 + r.nextInt(n - 6);
                float bx = xs[s0], by = ys[s0], bz = zs[s0];
                float dx = (r.nextFloat() - 0.5f) * 3.2f, dz = (r.nextFloat() - 0.5f) * 3.2f;
                for (int k = 0; k < 5; k++) {
                    float nx = bx + dx + (r.nextFloat() - 0.5f) * 1.2f;
                    float nz = bz + dz + (r.nextFloat() - 0.5f) * 1.2f;
                    float ny = by - 2.4f - r.nextFloat() * 1.2f;
                    if (ny < 0f) break;
                    boltSeg(vc, m, bx, by, bz, nx, ny, nz, 0.5f, f.color(), ba * (1f - k * 0.15f), cx, cy, cz);
                    bx = nx; by = ny; bz = nz;
                }
            }

            // pierścień uderzenia na ziemi
            float rr = 0.6f + el * 4.5f;
            float ra = Math.max(0f, 1f - el / 0.8f);
            if (ra > 0.01f)
                WorldFx.flatRing(vc, m, Math.max(0f, rr - 0.45f), rr + 0.1f, 0.05f, argb(f.color(), ra * 0.15f), argb(f.color(), ra * 0.75f), 28);
            ms.pop();
        }

        // zabity gracz wystrzelony w górę (jego skin, bez ducha)
        float lt = MathHelper.clamp((el - 0.12f) / 1.55f, 0f, 1f);
        if (lt > 0f && lt < 1f) {
            float ease = 1f - (1f - lt) * (1f - lt);
            float a = (lt < 0.08f ? lt / 0.08f : 1f) * (lt > 0.72f ? 1f - (lt - 0.72f) / 0.28f : 1f);
            float flash = Math.max(0f, 1f - lt * 4f);
            ms.push();
            ms.translate((float) rx, (float) ry + f.height() * ease, (float) rz);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.yaw() + lt * 160f));
            ms.translate(0f, 0.9f, 0f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lt * 300f));
            ms.translate(0f, -0.9f, 0f);
            ms.scale(WorldFx.PX, WorldFx.PX, WorldFx.PX);
            playerModel(ms, vcp, used, f.skin(), f.color(), a, 0.25f + 0.55f * flash, a * flash * 0.5f);
            ms.pop();
        }
    }

    /** Segment pioruna: szeroka poświata w kolorze + jaśniejszy środek + biały rdzeń. */
    private static void boltSeg(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                                float scale, int rgb, float ba, float cx, float cy, float cz) {
        int mid = Gfx.mix(rgb, 0xFFFFFF, 0.5f);
        WorldFx.ribbon(vc, m, x1, y1, z1, x2, y2, z2, 0.60f * scale, argb(rgb, ba * 0.28f), cx, cy, cz);
        WorldFx.ribbon(vc, m, x1, y1, z1, x2, y2, z2, 0.30f * scale, argb(rgb, ba * 0.60f), cx, cy, cz);
        WorldFx.ribbon(vc, m, x1, y1, z1, x2, y2, z2, 0.15f * scale, argb(mid, ba * 0.92f), cx, cy, cz);
        WorldFx.ribbon(vc, m, x1, y1, z1, x2, y2, z2, 0.07f * scale, argb(0xFFFFFF, ba), cx, cy, cz);
    }
}
