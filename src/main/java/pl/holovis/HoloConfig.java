package pl.holovis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

public class HoloConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("holovis.json");
    private static HoloConfig instance = new HoloConfig();

    public enum HitboxStyle {
        OUTLINE("Kontur"), HOLOGRAM("Hologram"), FILLED("Wypełnienie");
        public final String label;
        HitboxStyle(String l) { label = l; }
        public HitboxStyle next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Target {
        ALL("Wszystkie"), PLAYERS("Gracze"), MOBS("Moby");
        public final String label;
        Target(String l) { label = l; }
        public Target next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static class ColorSetting {
        public float h, s = 1f, b = 1f;
        public boolean rainbow = false;

        public ColorSetting() {}
        public ColorSetting(int rgb) { set(rgb); }

        public void set(int rgb) {
            float[] hsv = toHsv(rgb);
            h = hsv[0]; s = hsv[1]; b = hsv[2];
        }

        /** Aktualny kolor RGB (0xRRGGBB), z uwzględnieniem trybu rainbow. */
        public int rgb() {
            if (!rainbow) return hsv(h, s, b);
            // tęcza: przy bladych kolorach (np. biały napis) wymuszamy nasycenie, żeby było ją widać
            float hue = (Util.getMeasuringTimeMs() % 6000L) / 6000f;
            return hsv(hue, Math.max(s, 0.65f), Math.max(b, 0.8f));
        }
    }

    public static class SoundSetting {
        public boolean enabled = false;
        public String sound = "mc:entity.experience_orb.pickup";
        public float volume = 0.8f;
        public float pitch = 1f;

        public SoundSetting() {}
        public SoundSetting(boolean enabled, String sound) { this.enabled = enabled; this.sound = sound; }
    }

    public boolean hitboxEnabled = true;
    public HitboxStyle hitboxStyle = HitboxStyle.HOLOGRAM;
    public Target target = Target.ALL;
    public float fillAlpha = 0.25f;
    public ColorSetting hitboxColor = new ColorSetting(0x00F0FF);

    public ColorSetting guiColor = new ColorSetting(0x4C8DFF);
    public boolean hologramFx = false;
    public ColorSetting guiColor2 = new ColorSetting(0xA05CFF);
    public boolean guiGradient = false;
    public boolean guiGradientText = false;
    public ColorSetting guiBg = new ColorSetting(0x1A2230);
    public float guiBgAlpha = 0.80f;
    public ColorSetting textColor = new ColorSetting(0xE8EEF8);

    public boolean skyEnabled = false;
    public int skyPreset = 0;   // 0 = własny, reszta: SkyHelper.PRESETS
    public ColorSetting skyColor = new ColorSetting(0x8A2BE2);
    public ColorSetting skyColor2 = new ColorSetting(0xC890FF);   // horyzont
    public boolean skyDaylight = true;
    public boolean skyClouds = true;

    public boolean watermark = true;
    public boolean showFps = true;
    public boolean coords = true;
    public ColorSetting hudColor = new ColorSetting(0x00F0FF);
    public ColorSetting hudColor2 = new ColorSetting(0xB04CFF);
    public boolean hudGradient = false;
    public boolean spotify = false;
    public ColorSetting spColor = new ColorSetting(0x1DB954);
    public ColorSetting spColor2 = new ColorSetting(0x7CFFB0);
    public boolean spGradient = false;

    public boolean cooldowns = true;
    public ColorSetting cdColor = new ColorSetting(0xA45CFF);
    public float cdX = 0.01f, cdY = 0.30f;

    public boolean targetEsp = false;
    public int espStyle = 0;   // 0 pierścień, 1 okrąg pod nogami, 2 duszki
    public ColorSetting espColor = new ColorSetting(0xB04CFF);

    public boolean dmgNumbers = false;
    public float dmgSize = 1.0f;
    public ColorSetting dmgColor = new ColorSetting(0xFF4D5E);

    /** Wersja wyglądu menu: starsze configi dostają nowy, ciemny styl "glass". */
    public int uiVer = 2;

    public boolean targetHud = true, targetHearts = true, targetArmor = true;
    public ColorSetting tgColor = new ColorSetting(0x00F0FF);
    public ColorSetting tgColor2 = new ColorSetting(0xB04CFF);
    public boolean tgGradient = true;
    public float tgX = 0.55f, tgY = 0.55f;

    public boolean killFx = false;
    public int killMode = 0; // 0 duch, 1 piorun, 2 oba
    public float killGhostSecs = 3f;
    public ColorSetting killGhostColor = new ColorSetting(0x9AD8FF);
    public float killBoltHeight = 12f;
    public ColorSetting killBoltColor = new ColorSetting(0xB9A3FF);
    public boolean killMobs = false;

    public float wmX = 0.01f, wmY = 0.02f, spX = 0.01f, spY = 0.09f;
    /** Rozmiary elementów HUD (edytor pod klawiszem L: przeciągnij róg). */
    public float wmScale = 1f, spScale = 1f, spCardScale = 1f, tgScale = 1f, cdScale = 1f;

    // Świat: Motion Blur (0..2 = 0..200%) i Kolorki (korekcja kolorów)
    public boolean motionBlur = false;
    public float motionBlurAmount = 0.8f;
    public boolean colorGrade = false;
    public int cgPreset = 1;
    public float cgSat = 1.35f, cgContrast = 1.08f, cgBright = 0f, cgWarm = 0.05f;

    // Klient: rozmycie gry za menu (0..1 = 0..100)
    public float clientBlur = 0f;

    public SoundSetting hitSound = new SoundSetting(true, "mc:entity.experience_orb.pickup");
    public SoundSetting killSound = new SoundSetting(false, "mc:entity.player.levelup");
    public SoundSetting clickSound = new SoundSetting(false, "mc:block.note_block.pling");

    public static HoloConfig get() { return instance; }

    // =====================================================================
    //  Kody configów
    //  Nowy format "KV1:" zapisuje tylko to, co różni się od ustawień domyślnych
    //  (id pola = 2 bajty, wartość = 1-4 bajty), więc kod jest krótki.
    //  Stary format "HV1:" (gzip + JSON) nadal da się wczytać.
    // =====================================================================

    private enum Kind { BOOL, INT, FLOAT, STR, COLOR, ENUM }

    private static final class Slot {
        final String key;
        final Field owner;   // null = pole bezpośrednio w HoloConfig
        final Field leaf;
        final Kind kind;
        int id;

        Slot(String key, Field owner, Field leaf, Kind kind) {
            this.key = key; this.owner = owner; this.leaf = leaf; this.kind = kind;
        }

        Object target(HoloConfig c) throws IllegalAccessException { return owner == null ? c : owner.get(c); }
        Object get(HoloConfig c) throws IllegalAccessException { return leaf.get(target(c)); }
        void set(HoloConfig c, Object v) throws IllegalAccessException { leaf.set(target(c), v); }
    }

    private static List<Slot> slotList;
    private static Map<Integer, Slot> slotById;

    private static Kind kindOf(Class<?> t) {
        if (t == boolean.class) return Kind.BOOL;
        if (t == int.class) return Kind.INT;
        if (t == float.class) return Kind.FLOAT;
        if (t == String.class) return Kind.STR;
        if (t == ColorSetting.class) return Kind.COLOR;
        if (t.isEnum()) return Kind.ENUM;
        return null;
    }

    private static synchronized void buildSlots() {
        if (slotList != null) return;
        List<Slot> l = new ArrayList<>();
        Field[] fields = HoloConfig.class.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (Field f : fields) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || !Modifier.isPublic(m) || f.getName().equals("uiVer")) continue;
            Kind k = kindOf(f.getType());
            if (k != null) {
                l.add(new Slot(f.getName(), null, f, k));
            } else if (f.getType() == SoundSetting.class) {
                Field[] sub = SoundSetting.class.getDeclaredFields();
                Arrays.sort(sub, Comparator.comparing(Field::getName));
                for (Field sf : sub) {
                    Kind sk = kindOf(sf.getType());
                    if (sk != null && Modifier.isPublic(sf.getModifiers()) && !Modifier.isStatic(sf.getModifiers()))
                        l.add(new Slot(f.getName() + "." + sf.getName(), f, sf, sk));
                }
            }
        }
        l.sort(Comparator.comparing(s -> s.key));
        Map<Integer, Slot> byId = new HashMap<>();
        for (Slot s : l) {
            int h = s.key.hashCode();
            int id = (h ^ (h >>> 16)) & 0xFFFF;
            while (byId.containsKey(id)) id = (id + 1) & 0xFFFF;   // kolizja: deterministycznie następny wolny
            s.id = id;
            byId.put(id, s);
        }
        slotById = byId;
        slotList = l;
    }

    private static boolean sameValue(Slot s, HoloConfig a, HoloConfig b) throws IllegalAccessException {
        Object x = s.get(a), y = s.get(b);
        if (s.kind == Kind.COLOR) {
            ColorSetting c1 = (ColorSetting) x, c2 = (ColorSetting) y;
            return hsv(c1.h, c1.s, c1.b) == hsv(c2.h, c2.s, c2.b) && c1.rainbow == c2.rainbow;
        }
        return x.equals(y);
    }

    private static void writeVarint(DataOutputStream o, int v) throws java.io.IOException {
        while ((v & ~0x7F) != 0) { o.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        o.writeByte(v);
    }

    private static int readVarint(DataInputStream in) throws java.io.IOException {
        int r = 0, shift = 0, b;
        do { b = in.readUnsignedByte(); r |= (b & 0x7F) << shift; shift += 7; } while ((b & 0x80) != 0 && shift < 35);
        return r;
    }

    /** Krótki kod do udostępniania ustawień. Format: KV1:base64url(...). */
    public static String exportCode() {
        try {
            buildSlots();
            HoloConfig def = new HoloConfig();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bos);
            for (Slot s : slotList) {
                if (sameValue(s, instance, def)) continue;
                o.writeShort(s.id);
                Object v = s.get(instance);
                switch (s.kind) {
                    case BOOL -> o.writeByte((Boolean) v ? 1 : 0);
                    case INT -> writeVarint(o, Math.max(0, (Integer) v));
                    case FLOAT -> o.writeFloat((Float) v);
                    case STR -> o.writeUTF((String) v);
                    case COLOR -> {
                        ColorSetting cs = (ColorSetting) v;
                        int rgb = hsv(cs.h, cs.s, cs.b);
                        o.writeByte((rgb >> 16) & 255);
                        o.writeByte((rgb >> 8) & 255);
                        o.writeByte(rgb & 255);
                        o.writeByte(cs.rainbow ? 1 : 0);
                    }
                    case ENUM -> o.writeByte(((Enum<?>) v).ordinal());
                }
            }
            o.flush();
            byte[] raw = bos.toByteArray();

            // krótszy z dwóch wariantów: surowy albo skompresowany (deflate)
            byte[] best = new byte[raw.length + 1];
            best[0] = 0;
            System.arraycopy(raw, 0, best, 1, raw.length);
            Deflater d = new Deflater(9, true);
            d.setInput(raw);
            d.finish();
            byte[] buf = new byte[raw.length + 64];
            int n = d.deflate(buf);
            boolean done = d.finished();
            d.end();
            if (done && n + 1 < best.length) {
                best = new byte[n + 1];
                best[0] = 1;
                System.arraycopy(buf, 0, best, 1, n);
            }
            return "KV1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(best);
        } catch (Exception e) {
            return "";
        }
    }

    private static HoloConfig decodeCompact(String body) throws Exception {
        buildSlots();
        byte[] data = Base64.getUrlDecoder().decode(body.trim());
        if (data.length < 1) throw new IllegalArgumentException("empty");
        byte[] raw;
        if (data[0] == 1) {
            Inflater inf = new Inflater(true);
            inf.setInput(data, 1, data.length - 1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            try {
                while (!inf.finished()) {
                    int n = inf.inflate(buf);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                    out.write(buf, 0, n);
                    if (out.size() > 200_000) throw new IllegalArgumentException("too big");
                }
            } catch (DataFormatException e) {
                throw new IllegalArgumentException("bad data");
            } finally {
                inf.end();
            }
            raw = out.toByteArray();
        } else {
            raw = Arrays.copyOfRange(data, 1, data.length);
        }

        HoloConfig c = new HoloConfig();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        while (in.available() > 0) {
            int id = in.readUnsignedShort();
            Slot s = slotById.get(id);
            if (s == null) throw new IllegalArgumentException("unknown field");
            switch (s.kind) {
                case BOOL -> s.set(c, in.readUnsignedByte() != 0);
                case INT -> s.set(c, readVarint(in));
                case FLOAT -> s.set(c, in.readFloat());
                case STR -> s.set(c, in.readUTF());
                case COLOR -> {
                    int r = in.readUnsignedByte(), g = in.readUnsignedByte(), b = in.readUnsignedByte();
                    boolean rb = in.readUnsignedByte() != 0;
                    ColorSetting cs = new ColorSetting((r << 16) | (g << 8) | b);
                    cs.rainbow = rb;
                    s.set(c, cs);
                }
                case ENUM -> {
                    Object[] consts = s.leaf.getType().getEnumConstants();
                    int ord = in.readUnsignedByte();
                    if (ord < consts.length) s.set(c, consts[ord]);
                }
            }
        }
        return c;
    }

    /** @return null gdy się udało, w przeciwnym razie komunikat błędu */
    public static String importCode(String code) {
        try {
            code = code == null ? "" : code.trim();
            HoloConfig loaded;
            if (code.startsWith("KV1:")) {
                loaded = decodeCompact(code.substring(4));
            } else if (code.startsWith("HV1:")) {      // stary, długi format
                byte[] gz = Base64.getUrlDecoder().decode(code.substring(4).trim());
                String json = new String(new GZIPInputStream(new ByteArrayInputStream(gz)).readAllBytes(), StandardCharsets.UTF_8);
                loaded = GSON.fromJson(json, HoloConfig.class);
            } else {
                return "To nie jest kod KosaVisuals";
            }
            if (loaded == null) return "Pusty kod";
            instance = loaded;
            save();
            return null;
        } catch (Exception e) {
            return "Niepoprawny kod";
        }
    }

    /** Przywraca ustawienia fabryczne. */
    public static void resetDefaults() {
        instance = new HoloConfig();
        save();
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                JsonObject o = JsonParser.parseString(Files.readString(PATH)).getAsJsonObject();
                HoloConfig loaded = GSON.fromJson(o, HoloConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    // starszy wygląd menu -> nowy, ciemny styl "glass"
                    boolean old = !o.has("uiVer") || o.get("uiVer").getAsInt() < 2;
                    if (old) {
                        instance.uiVer = 2;
                        instance.guiColor = new ColorSetting(0x4C8DFF);
                        instance.guiColor2 = new ColorSetting(0xA05CFF);
                        instance.guiGradient = false;
                        instance.guiGradientText = false;
                        instance.guiBg = new ColorSetting(0x1A2230);
                        instance.guiBgAlpha = 0.80f;
                        instance.textColor = new ColorSetting(0xE8EEF8);
                        instance.hologramFx = false;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się wczytać configu: " + e);
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(instance));
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się zapisać configu: " + e);
        }
    }

    // ---- HSV helpers ----
    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6f;
        else if (max == g) h = (2 + (b - r) / d) / 6f;
        else h = (4 + (r - g) / d) / 6f;
        if (h < 0) h += 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
