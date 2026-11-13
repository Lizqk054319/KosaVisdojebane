package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Damage Numbers: nad trafionym graczem pojawia się liczba zadanych obrażeń i unosi się w górę. */
public final class DamageNumbers {
    private DamageNumbers() {}

    private static final class Pending {
        final LivingEntity e; final float hp; int age;
        Pending(LivingEntity e, float hp) { this.e = e; this.hp = hp; }
    }

    private record Num(Vec3d pos, float dmg, long start) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Num> NUMS = new ArrayList<>();
    private static final Random RND = new Random();

    /** Wołane po uderzeniu prawdziwego celu; obrażenia liczymy z różnicy zdrowia w kolejnych tickach. */
    public static void onAttack(LivingEntity e) {
        if (!HoloConfig.get().dmgNumbers) return;
        PENDING.removeIf(p -> p.e == e);
        PENDING.add(new Pending(e, e.getHealth()));
    }

    /** Natychmiastowa liczba (np. dla TestBota). */
    public static void show(LivingEntity e, float dmg) {
        if (!HoloConfig.get().dmgNumbers || dmg <= 0f) return;
        spawn(e, dmg);
    }

    private static void spawn(LivingEntity e, float dmg) {
        Vec3d p = e.getPos().add((RND.nextDouble() - 0.5) * 0.6, e.getHeight() + 0.25, (RND.nextDouble() - 0.5) * 0.6);
        NUMS.add(new Num(p, dmg, Util.getMeasuringTimeMs()));
        if (NUMS.size() > 40) NUMS.remove(0);
    }

    public static void tick() {
        if (PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            p.age++;
            float hp = p.e.getHealth();
            if (hp < p.hp - 0.05f) { spawn(p.e, p.hp - hp); it.remove(); }
            else if (p.age > 8 || p.e.isRemoved()) it.remove();
        }
    }

    private static String fmt(float v) {
        float r = Math.round(v * 10f) / 10f;
        return Math.abs(r - Math.round(r)) < 0.05f ? String.valueOf(Math.round(r)) : String.format(Locale.ROOT, "%.1f", r);
    }

    public static void render(WorldRenderContext ctx) {
        if (NUMS.isEmpty()) return;
        HoloConfig cfg = HoloConfig.get();
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        long now = Util.getMeasuringTimeMs();

        // osie kamery (prawo / góra / przód) - tekst zawsze zwrócony do gracza
        double yaw = Math.toRadians(camera.getYaw()), pitch = Math.toRadians(camera.getPitch());
        float fx = (float) (-Math.sin(yaw) * Math.cos(pitch)), fy = (float) -Math.sin(pitch), fz = (float) (Math.cos(yaw) * Math.cos(pitch));
        float rx = (float) -Math.cos(yaw), rz = (float) -Math.sin(yaw);
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        int rgb = cfg.dmgColor.rgb();
        Iterator<Num> it = NUMS.iterator();
        while (it.hasNext()) {
            Num n = it.next();
            float age = (now - n.start()) / 1100f;
            if (age >= 1f) { it.remove(); continue; }
            float pop = 1f + 0.5f * Math.max(0f, 1f - age / 0.15f);
            float s = 0.025f * cfg.dmgSize * pop;
            int alpha = (int) (255 * (age < 0.6f ? 1f : 1f - (age - 0.6f) / 0.4f));
            if (alpha < 12) continue;
            double px = n.pos().x - cam.x, py = n.pos().y - cam.y + age * 0.9, pz = n.pos().z - cam.z;

            Matrix4f mat = new Matrix4f(ms.peek().getPositionMatrix()).translate((float) px, (float) py, (float) pz);
            mat.mul(new Matrix4f(rx * s, 0f, rz * s, 0f,
                    -ux * s, -uy * s, -uz * s, 0f,
                    fx * s, fy * s, fz * s, 0f,
                    0f, 0f, 0f, 1f));
            String txt = fmt(n.dmg());
            tr.draw(txt, -tr.getWidth(txt) / 2f, 0f, (alpha << 24) | rgb, true, mat, vcp,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw();
    }
}
