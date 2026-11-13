package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/** TargetHud: po najechaniu na gracza pokazuje pasek serc, liczbę serc i jego zbroję. */
public final class TargetHud {
    private TargetHud() {}

    public static final int W = 164;

    private static LivingEntity target, lastDrawn;
    private static long lastSeen;
    private static float appear = 0f, shown = 0f, trail = 0f;
    private static long lastNs = System.nanoTime();

    public static void tick(MinecraftClient mc) {
        if (!HoloConfig.get().targetHud || mc.player == null) { target = null; return; }
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity p && p != mc.player) {
            target = p;
            lastSeen = System.currentTimeMillis();
        }
    }

    /** Po uderzeniu gracza (też gdy już na niego nie patrzymy) HUD zostaje jeszcze chwilę. */
    public static void notifyAttack(LivingEntity e) {
        if (e instanceof PlayerEntity) { target = e; lastSeen = System.currentTimeMillis(); }
    }

    private static LivingEntity current() {
        if (!HoloConfig.get().targetHud || target == null || target.isRemoved()) return null;
        if (System.currentTimeMillis() - lastSeen > 2500) return null;
        return target;
    }

    /** Aktualny cel (do Target ESP) albo null. */
    public static LivingEntity currentTarget() { return current(); }

    public static boolean visible() { return current() != null || appear > 0.02f; }

    public static int[] size() { return new int[]{W, HoloConfig.get().targetArmor ? 54 : 46}; }

    private static String fmt(float v) { return String.format(Locale.ROOT, "%.1f", v).replace('.', ','); }

    private static ItemStack[] armorOf(LivingEntity e) {
        return new ItemStack[]{e.getEquippedStack(EquipmentSlot.HEAD), e.getEquippedStack(EquipmentSlot.CHEST),
                e.getEquippedStack(EquipmentSlot.LEGS), e.getEquippedStack(EquipmentSlot.FEET)};
    }

    /** preview = true w edytorze HUD (atrapa gracza). */
    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;

        long ns = System.nanoTime();
        float dt = Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        LivingEntity t = preview ? null : current();
        float goal = (preview || t != null) ? 1f : 0f;
        appear += (goal - appear) * Math.min(1f, dt * 10f);
        if (preview) appear = 1f;
        if (appear < 0.04f) return;

        float hp, max;
        String name;
        ItemStack[] armor;
        LivingEntity drawn;
        if (t != null) {
            drawn = t;
            hp = t.getHealth();
            max = Math.max(1f, t.getMaxHealth());
            name = t.getName().getString();
            armor = armorOf(t);
        } else if (preview) {
            drawn = mc.player;
            hp = 15f; max = 20f; name = "Gracz";
            armor = new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS), ItemStack.EMPTY};
        } else {
            // cel zniknął w trakcie animacji znikania - rysujemy ostatnie dane
            if (lastDrawn == null) return;
            drawn = lastDrawn;
            hp = Math.max(0f, lastDrawn.getHealth());
            max = Math.max(1f, lastDrawn.getMaxHealth());
            name = lastDrawn.getName().getString();
            armor = armorOf(lastDrawn);
        }
        if (preview || (t != null && t != lastDrawn)) { shown = hp; trail = hp; }
        if (t != null) lastDrawn = t;
        shown += (hp - shown) * Math.min(1f, dt * 9f);
        if (hp >= trail) trail = hp; else trail = Math.max(hp, trail - dt * max * 0.30f);

        float a = appear;
        int H = size()[1];
        y += (int) ((1f - a) * 8);
        int c1 = cfg.tgColor.rgb();
        int c2 = cfg.tgGradient ? cfg.tgColor2.rgb() : c1;

        // karta z gradientową obwódką
        Gfx.rboxH(ctx, x - 1, y - 1, W + 2, H + 2, 9, Gfx.argb(c1, 0.95f * a), Gfx.argb(c2, 0.95f * a));
        Gfx.rrect(ctx, x, y, W, H, 8, Gfx.argb(0x0B0F18, 0.93f * a));
        ctx.fillGradient(x + 3, y + 1, x + W - 3, y + 13, Gfx.argb(c1, 0.16f * a), Gfx.argb(c1, 0f));

        // głowa w ramce
        Gfx.rboxV(ctx, x + 5, y + 5, 36, 36, 4, Gfx.argb(c1, a), Gfx.argb(c2, a));
        if (drawn instanceof AbstractClientPlayerEntity pl) {
            PlayerSkinDrawer.draw(ctx, pl.getSkinTextures(), x + 6, y + 6, 34);
        } else {
            Gfx.rrect(ctx, x + 6, y + 6, 34, 34, 3, Gfx.argb(0x2A3246, a));
        }

        // nick po lewej, liczba serc po prawej (na górze)
        int rx = x + 48, rr = x + W - 8;
        int hearts = 0;
        if (cfg.targetHearts) {
            String val = fmt(shown / 2f);
            Gfx.heart(ctx, rr - 7, y + 8, Gfx.argb(0xFF4D5E, a));
            Gfx.text(ctx, tr, val, rr - 11 - tr.getWidth(val), y + 8, Gfx.argb(0xFFFFFF, a), false);
            hearts = tr.getWidth(val) + 16;
        } else {
            String val = fmt(shown) + " HP";
            Gfx.text(ctx, tr, val, rr - tr.getWidth(val), y + 8, Gfx.argb(0xFFFFFF, a), false);
            hearts = tr.getWidth(val) + 6;
        }
        Gfx.text(ctx, tr, tr.trimToWidth(name, Math.max(20, rr - rx - hearts)), rx, y + 8, Gfx.argb(0xFFFFFF, a), false);

        // pasek życia (z "śladem" ostatnich obrażeń)
        int bx = rx, by = y + 21, bw = rr - rx, bh = 8;
        Gfx.rrect(ctx, bx, by, bw, bh, 4, Gfx.argb(0xFFFFFF, 0.10f * a));
        float ratio = MathHelper.clamp(shown / max, 0f, 1f);
        float tr2 = MathHelper.clamp(trail / max, 0f, 1f);
        int tw = (int) (bw * tr2);
        if (tw > (int) (bw * ratio) + 1) Gfx.rrect(ctx, bx, by, Math.max(bh, tw), bh, 4, Gfx.argb(0xFFFFFF, 0.28f * a));
        int fw = (int) (bw * ratio);
        if (fw > 0) {
            int w = Math.max(bh, fw);
            int ce = Gfx.lerpArgb(Gfx.argb(c1, a), Gfx.argb(c2, a), ratio);
            Gfx.rboxH(ctx, bx, by, w, bh, 4, Gfx.argb(c1, a), ce);
            ctx.fill(bx + 4, by + 1, bx + w - 4, by + 2, Gfx.argb(0xFFFFFF, 0.22f * a));
        }

        // zbroja
        if (cfg.targetArmor) {
            for (int i = 0; i < 4; i++) {
                int ix = rx + i * 20, iy = y + 33;
                if (armor[i].isEmpty()) {
                    ctx.fill(ix, iy, ix + 16, iy + 16, Gfx.argb(0xFFFFFF, 0.05f * a));
                    ctx.drawBorder(ix, iy, 16, 16, Gfx.argb(0xFFFFFF, 0.12f * a));
                } else {
                    ctx.drawItem(armor[i], ix, iy);
                    ctx.drawStackOverlay(tr, armor[i], ix, iy);
                }
            }
        }
    }
}
