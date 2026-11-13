package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Cooldowns: lista przedmiotów z aktywnym cooldownem i czasem, który im został (ender perła, wind charge...). */
public final class Cooldowns {
    private Cooldowns() {}

    public static final int W = 150;

    private static final class Entry {
        final ItemStack stack;
        final long firstMs;
        final float firstP;
        float remaining = -1f;
        Entry(ItemStack s, float p) { stack = s.copyWithCount(1); firstMs = System.currentTimeMillis(); firstP = p; }
    }

    private static final Map<Item, Entry> MAP = new LinkedHashMap<>();

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || !HoloConfig.get().cooldowns) { MAP.clear(); return; }
        var cm = mc.player.getItemCooldownManager();
        PlayerInventory inv = mc.player.getInventory();
        Set<Item> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || seen.contains(s.getItem())) continue;
            float p = cm.getCooldownProgress(s, 0f);
            if (p <= 0.001f) continue;
            seen.add(s.getItem());
            Entry e = MAP.computeIfAbsent(s.getItem(), k -> new Entry(s, p));
            // całkowity czas liczymy z tempa spadku postępu (serwery mają własne czasy cooldownów)
            float dt = (now - e.firstMs) / 1000f;
            float dp = e.firstP - p;
            if (dt > 0.12f && dp > 0.004f) e.remaining = p * dt / dp;
        }
        Iterator<Item> it = MAP.keySet().iterator();
        while (it.hasNext()) if (!seen.contains(it.next())) it.remove();
    }

    public static boolean visible() { return !MAP.isEmpty(); }

    private static int rows() { return Math.max(2, MAP.size()); }

    public static int[] size() { return new int[]{W, 24 + rows() * 20 + 2}; }

    private static String time(float s) {
        if (s < 0f) return "...";
        if (s >= 100f) return Math.round(s) + "s";
        return String.format(Locale.ROOT, "%.1fs", s);
    }

    /** preview = true w edytorze HUD (przykładowe dane). */
    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        HoloConfig cfg = HoloConfig.get();
        int rgb = cfg.cdColor.rgb();

        List<ItemStack> stacks = new ArrayList<>();
        List<String> times = new ArrayList<>();
        if (preview || MAP.isEmpty()) {
            stacks.add(new ItemStack(Items.ENDER_PEARL)); times.add("14.2s");
            stacks.add(new ItemStack(Items.WIND_CHARGE)); times.add("0.5s");
        } else {
            for (Entry e : MAP.values()) { stacks.add(e.stack); times.add(time(e.remaining)); }
        }
        int n = Math.min(stacks.size(), 8);
        int H = 24 + Math.max(n, preview ? 2 : 1) * 20 + 2;
        if (!preview && !MAP.isEmpty()) H = 24 + n * 20 + 2;

        Gfx.rrect(ctx, x - 1, y - 1, W + 2, H + 2, 9, Gfx.argb(rgb, 0.35f));
        Gfx.rrect(ctx, x, y, W, H, 8, 0xEC0C1018);

        // nagłówek: zegar + napis
        Gfx.circle(ctx, x + 14, y + 12, 6, Gfx.argb(rgb, 1f));
        Gfx.circle(ctx, x + 14, y + 12, 4, 0xFF0C1018);
        ctx.fill(x + 14, y + 9, x + 15, y + 13, Gfx.argb(rgb, 1f));
        ctx.fill(x + 14, y + 12, x + 17, y + 13, Gfx.argb(rgb, 1f));
        Gfx.text(ctx, tr, "Cooldowns", x + 26, y + 8, 0xFFE8EEF8, false);

        for (int i = 0; i < n; i++) {
            int ry = y + 24 + i * 20;
            Gfx.rrect(ctx, x + 6, ry, W - 12, 18, 5, 0x30FFFFFF);
            Gfx.rrect(ctx, x + 8, ry + 1, 16, 16, 4, 0x40000000);
            ctx.drawItem(stacks.get(i), x + 8, ry + 1);
            String name = tr.trimToWidth(stacks.get(i).getName().getString(), W - 12 - 64);
            Gfx.text(ctx, tr, name, x + 28, ry + 5, 0xFFE8EEF8, false);
            String t = times.get(i);
            int pw = tr.getWidth(t) + 12;
            Gfx.rrect(ctx, x + W - 8 - pw, ry + 2, pw, 14, 7, Gfx.argb(rgb, 0.85f));
            Gfx.text(ctx, tr, t, x + W - 8 - pw + 6, ry + 5, 0xFFFFFFFF, false);
        }
    }
}
