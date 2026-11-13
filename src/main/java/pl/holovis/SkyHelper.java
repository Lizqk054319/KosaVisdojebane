package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class SkyHelper {
    private SkyHelper() {}

    /** Gotowe niebo: nazwa, kolor zenitu (góra), kolor horyzontu (dół, mgła). Indeks 0 = własne kolory. */
    public static final String[] PRESET_NAMES = {
            "Własne", "Sakura", "Zachód słońca", "Neon", "Galaxy", "Fiolet", "Lodowe", "Toxic", "Krwawe", "Mięta", "Złota godzina"};
    private static final int[][] PRESET_COLORS = {
            {0, 0},
            {0xEE7FB0, 0xFFD6E6},   // Sakura
            {0x5B3A8C, 0xFF8A4C},   // Zachód słońca
            {0x2A0A5E, 0xFF2BD6},   // Neon
            {0x0B0630, 0x6A35D0},   // Galaxy
            {0x8A2BE2, 0xC890FF},   // Fiolet
            {0x4A90E2, 0xDDF4FF},   // Lodowe
            {0x0F5A2A, 0xA8FF3C},   // Toxic
            {0x4A0606, 0xFF3B2B},   // Krwawe
            {0x1FA89A, 0xC9FFEF},   // Mięta
            {0xE8803A, 0xFFE9A8}};  // Złota godzina

    public static void applyPreset(int idx) {
        if (idx <= 0 || idx >= PRESET_COLORS.length) return;
        HoloConfig c = HoloConfig.get();
        c.skyPreset = idx;
        c.skyColor.set(PRESET_COLORS[idx][0]);
        c.skyColor.rainbow = false;
        c.skyColor2.set(PRESET_COLORS[idx][1]);
        c.skyColor2.rainbow = false;
        c.skyEnabled = true;
    }

    /** Custom Sky działa tylko w Overworld i tylko gdy jest włączony. */
    public static boolean active(World w) {
        return HoloConfig.get().skyEnabled && w != null && w.getRegistryKey() == World.OVERWORLD;
    }

    private static int dim(World w, float tickDelta, int rgb) {
        if (!HoloConfig.get().skyDaylight) return rgb;
        float f = MathHelper.clamp(MathHelper.cos(w.getSkyAngle(tickDelta) * ((float) Math.PI * 2f)) * 2f + 0.5f, 0f, 1f);
        float k = 0.12f + 0.88f * f;
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** Kolor nieba (zenit, 0xRRGGBB), opcjonalnie przyciemniany nocą. */
    public static int color(World w, float tickDelta) {
        return dim(w, tickDelta, HoloConfig.get().skyColor.rgb());
    }

    /** Kolor horyzontu / mgły. */
    public static int horizon(World w, float tickDelta) {
        return dim(w, tickDelta, HoloConfig.get().skyColor2.rgb());
    }

    /** Kanał 0=R, 1=G, 2=B jako float 0..1 albo wartość oryginalna, gdy sky wyłączone. */
    public static float channel(int idx, float orig) {
        World w = MinecraftClient.getInstance().world;
        if (!active(w)) return orig;
        int rgb = color(w, 1f);
        return ((rgb >> (16 - 8 * idx)) & 255) / 255f;
    }
}
