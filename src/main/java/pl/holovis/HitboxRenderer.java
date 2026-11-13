package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class HitboxRenderer {
    private HitboxRenderer() {}

    private static int col(int rgb, float a) {
        return (((int) (MathHelper.clamp(a, 0f, 1f) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(WorldRenderContext ctx) {
        HoloConfig cfg = HoloConfig.get();
        if (!cfg.hitboxEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (world == null || ms == null || vcp == null) return;

        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickCounter().getTickDelta(false);
        int rgb = cfg.hitboxColor.rgb();
        long now = Util.getMeasuringTimeMs();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        // 1) zbierz boxy (współrzędne względem kamery - dokładność float)
        List<Box> boxes = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            if (e.isRemoved()) continue;
            if (e == mc.getCameraEntity() && firstPerson) continue;
            switch (cfg.target) {
                case PLAYERS -> { if (!(e instanceof PlayerEntity)) continue; }
                case MOBS -> { if (!(e instanceof LivingEntity) || e instanceof PlayerEntity) continue; }
                default -> {}
            }
            Vec3d lerped = e.getLerpedPos(td);
            if (lerped.squaredDistanceTo(cam) > 96 * 96) continue;
            Box b = e.getBoundingBox().offset(lerped.subtract(e.getPos())).offset(-cam.x, -cam.y, -cam.z);
            boxes.add(b);
        }
        if (boxes.isEmpty()) return;

        MatrixStack.Entry entry = ms.peek();
        Matrix4f mat = entry.getPositionMatrix();
        HoloConfig.HitboxStyle style = cfg.hitboxStyle;

        // 2) wypełnienia (hologram / filled)
        if (style != HoloConfig.HitboxStyle.OUTLINE) {
            VertexConsumer fill = vcp.getBuffer(RenderLayer.getLightning());
            for (int i = 0; i < boxes.size(); i++) {
                Box b = boxes.get(i);
                if (style == HoloConfig.HitboxStyle.FILLED) {
                    quads(mat, fill, b, col(rgb, cfg.fillAlpha));
                } else {
                    float pulse = 0.65f + 0.35f * (float) Math.sin(now / 300.0 + i);
                    quads(mat, fill, b, col(rgb, cfg.fillAlpha * pulse * 0.6f));
                    for (int k = 0; k < 2; k++) {
                        float t = scanT(now, k);
                        double y = b.minY + t * (b.maxY - b.minY);
                        float a = (float) Math.sin(Math.PI * t) * 0.35f;
                        plane(mat, fill, b, y, col(rgb, a));
                    }
                }
            }
            if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLightning());
        }

        // 3) linie
        VertexConsumer lines = vcp.getBuffer(RenderLayer.getLines());
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            switch (style) {
                case OUTLINE -> box(entry, lines, b, col(rgb, 1f));
                case FILLED -> box(entry, lines, b, col(rgb, 0.9f));
                case HOLOGRAM -> {
                    float pulse = 0.6f + 0.4f * (float) Math.sin(now / 250.0 + i * 1.7);
                    box(entry, lines, b, col(rgb, pulse));
                    box(entry, lines, b.expand(0.03), col(rgb, 0.22f)); // "duch" hologramu
                    for (int k = 0; k < 2; k++) {
                        float t = scanT(now, k);
                        double y = b.minY + t * (b.maxY - b.minY);
                        float a = (float) Math.sin(Math.PI * t);
                        ring(entry, lines, b, y, col(rgb, a));
                    }
                }
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
    }

    private static float scanT(long now, int k) {
        return ((now / 1800f) + k * 0.5f) % 1f;
    }

    // ---------- geometria ----------
    private static void line(MatrixStack.Entry e, VertexConsumer vc,
                             double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        dx /= len; dy /= len; dz /= len;
        vc.vertex(e, (float) x1, (float) y1, (float) z1).color(c).normal(e, dx, dy, dz);
        vc.vertex(e, (float) x2, (float) y2, (float) z2).color(c).normal(e, dx, dy, dz);
    }

    private static void box(MatrixStack.Entry e, VertexConsumer vc, Box b, int c) {
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        line(e, vc, x1, y1, z1, x2, y1, z1, c); line(e, vc, x2, y1, z1, x2, y1, z2, c);
        line(e, vc, x2, y1, z2, x1, y1, z2, c); line(e, vc, x1, y1, z2, x1, y1, z1, c);
        line(e, vc, x1, y2, z1, x2, y2, z1, c); line(e, vc, x2, y2, z1, x2, y2, z2, c);
        line(e, vc, x2, y2, z2, x1, y2, z2, c); line(e, vc, x1, y2, z2, x1, y2, z1, c);
        line(e, vc, x1, y1, z1, x1, y2, z1, c); line(e, vc, x2, y1, z1, x2, y2, z1, c);
        line(e, vc, x2, y1, z2, x2, y2, z2, c); line(e, vc, x1, y1, z2, x1, y2, z2, c);
    }

    private static void ring(MatrixStack.Entry e, VertexConsumer vc, Box b, double y, int c) {
        line(e, vc, b.minX, y, b.minZ, b.maxX, y, b.minZ, c);
        line(e, vc, b.maxX, y, b.minZ, b.maxX, y, b.maxZ, c);
        line(e, vc, b.maxX, y, b.maxZ, b.minX, y, b.maxZ, c);
        line(e, vc, b.minX, y, b.maxZ, b.minX, y, b.minZ, c);
    }

    private static void v(Matrix4f m, VertexConsumer vc, double x, double y, double z, int c) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(c);
    }

    private static void plane(Matrix4f m, VertexConsumer vc, Box b, double y, int c) {
        v(m, vc, b.minX, y, b.minZ, c); v(m, vc, b.maxX, y, b.minZ, c);
        v(m, vc, b.maxX, y, b.maxZ, c); v(m, vc, b.minX, y, b.maxZ, c);
    }

    private static void quads(Matrix4f m, VertexConsumer vc, Box b, int c) {
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        // dół / góra
        v(m, vc, x1, y1, z1, c); v(m, vc, x2, y1, z1, c); v(m, vc, x2, y1, z2, c); v(m, vc, x1, y1, z2, c);
        v(m, vc, x1, y2, z1, c); v(m, vc, x2, y2, z1, c); v(m, vc, x2, y2, z2, c); v(m, vc, x1, y2, z2, c);
        // północ / południe
        v(m, vc, x1, y1, z1, c); v(m, vc, x1, y2, z1, c); v(m, vc, x2, y2, z1, c); v(m, vc, x2, y1, z1, c);
        v(m, vc, x1, y1, z2, c); v(m, vc, x1, y2, z2, c); v(m, vc, x2, y2, z2, c); v(m, vc, x2, y1, z2, c);
        // zachód / wschód
        v(m, vc, x1, y1, z1, c); v(m, vc, x1, y2, z1, c); v(m, vc, x1, y2, z2, c); v(m, vc, x1, y1, z2, c);
        v(m, vc, x2, y1, z1, c); v(m, vc, x2, y2, z1, c); v(m, vc, x2, y2, z2, c); v(m, vc, x2, y1, z2, c);
    }
}
