package pl.holovis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Zapisane profile (configi) z nazwami: config/holovis/profiles.json.
 * Każdy profil to krótki kod KV1 + licznik wczytań i czas ostatniego użycia.
 */
public final class Profiles {
    private Profiles() {}

    public static final class Profile {
        public String name = "";
        public String code = "";
        public int loads;
        public long created, lastUsed;
        public boolean fav;
    }

    private static final class Store { List<Profile> profiles = new ArrayList<>(); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("holovis").resolve("profiles.json");
    private static Store store;
    /** Ustawienia sprzed ostatniego wczytania profilu - do przycisku "Cofnij". */
    private static String undoCode;

    private static Store store() {
        if (store != null) return store;
        store = new Store();
        try {
            if (Files.exists(PATH)) {
                Store s = GSON.fromJson(Files.readString(PATH), Store.class);
                if (s != null && s.profiles != null) store = s;
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się wczytać profili: " + e);
        }
        return store;
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(store()));
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się zapisać profili: " + e);
        }
    }

    public static List<Profile> all() { return store().profiles; }

    public static int count() { return store().profiles.size(); }

    public static Profile find(String name) {
        for (Profile p : store().profiles) if (p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    /** Sortowanie do zakładek: 0 = ostatnie, 1 = najczęściej używane, 2 = wszystkie (A-Z). Ulubione zawsze na górze. */
    public static List<Profile> sorted(int tab) {
        List<Profile> l = new ArrayList<>(store().profiles);
        Comparator<Profile> c = switch (tab) {
            case 0 -> Comparator.comparingLong((Profile p) -> Math.max(p.lastUsed, p.created)).reversed();
            case 1 -> Comparator.comparingInt((Profile p) -> p.loads).reversed()
                    .thenComparing(p -> p.name.toLowerCase(Locale.ROOT));
            default -> Comparator.comparing((Profile p) -> p.name.toLowerCase(Locale.ROOT));
        };
        l.sort(Comparator.comparing((Profile p) -> !p.fav).thenComparing(c));
        return l;
    }

    /** Zapisuje aktualne ustawienia pod nazwą (nadpisuje profil o tej samej nazwie). @return komunikat albo null przy błędzie */
    public static String saveCurrent(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return null;
        if (name.length() > 24) name = name.substring(0, 24);
        String code = HoloConfig.exportCode();
        if (code.isEmpty()) return null;
        Profile p = find(name);
        if (p == null) {
            p = new Profile();
            p.name = name;
            p.created = System.currentTimeMillis();
            store().profiles.add(p);
        }
        p.code = code;
        save();
        return name;
    }

    /** Wczytuje profil. @return null gdy OK, w przeciwnym razie komunikat błędu */
    public static String load(Profile p) {
        String before = HoloConfig.exportCode();
        String err = HoloConfig.importCode(p.code);
        if (err != null) return err;
        undoCode = before;
        p.loads++;
        p.lastUsed = System.currentTimeMillis();
        save();
        return null;
    }

    /** Wczytuje kod wklejony ręcznie. @return null gdy OK */
    public static String loadCode(String code) {
        String before = HoloConfig.exportCode();
        String err = HoloConfig.importCode(code);
        if (err != null) return err;
        undoCode = before;
        return null;
    }

    public static void defaults() {
        undoCode = HoloConfig.exportCode();
        HoloConfig.resetDefaults();
    }

    public static boolean canUndo() { return undoCode != null; }

    public static String undo() {
        if (undoCode == null) return "Nie ma czego cofać";
        String code = undoCode;
        String err = HoloConfig.importCode(code);
        if (err == null) undoCode = null;
        return err;
    }

    public static void delete(Profile p) {
        store().profiles.remove(p);
        save();
    }

    public static String ago(long ms) {
        long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (s < 45) return "przed chwilą";
        if (s < 3600) return (s / 60 + 1) + " min temu";
        if (s < 86400) return (s / 3600) + " h temu";
        return (s / 86400) + " d temu";
    }
}
