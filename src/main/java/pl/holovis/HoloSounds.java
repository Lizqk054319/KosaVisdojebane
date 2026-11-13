package pl.holovis;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Dźwięki: wbudowane z Minecrafta + własne pliki .wav z config/holovis/sounds. */
public final class HoloSounds {
    private HoloSounds() {}

    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("holovis").resolve("sounds");
    private static final Map<String, String> BUILTIN = new LinkedHashMap<>();
    private static List<String> cache = new ArrayList<>();

    static {
        BUILTIN.put("mc:entity.experience_orb.pickup", "Orb");
        BUILTIN.put("mc:entity.arrow.hit_player", "Ding");
        BUILTIN.put("mc:entity.player.levelup", "Level up");
        BUILTIN.put("mc:entity.player.attack.crit", "Crit");
        BUILTIN.put("mc:block.note_block.pling", "Pling");
        BUILTIN.put("mc:block.note_block.bell", "Dzwonek");
        BUILTIN.put("mc:item.totem.use", "Totem");
        BUILTIN.put("mc:entity.firework_rocket.blast", "Fajerwerk");
        BUILTIN.put("mc:entity.lightning_bolt.thunder", "Grzmot");
        BUILTIN.put("mc:ui.button.click", "Klik");
        BUILTIN.put("mc:entity.cat.ambient@1.55", "Mau (mały kotek)");
        BUILTIN.put("mc:entity.cat.ambient", "Mau (kot)");
        BUILTIN.put("mc:entity.cat.beg_for_food@1.45", "Miau (kotek prosi)");
        BUILTIN.put("mc:entity.cat.purreow", "Mrrau");
    }

    public static void refresh() {
        List<String> list = new ArrayList<>(BUILTIN.keySet());
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> s = Files.list(DIR)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase().endsWith(".wav"))
                        .sorted()
                        .forEach(n -> list.add("file:" + n));
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się odczytać folderu dźwięków: " + e);
        }
        cache = list;
    }

    public static List<String> options() {
        if (cache.isEmpty()) refresh();
        return cache;
    }

    public static String label(String key) {
        if (key.startsWith("file:")) return key.substring(5).replaceAll("(?i)\\.wav$", "") + " (wav)";
        return BUILTIN.getOrDefault(key, key);
    }

    public static void play(HoloConfig.SoundSetting s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (s.sound.startsWith("file:")) {
            playFile(DIR.resolve(s.sound.substring(5)), s.volume * mc.options.getSoundVolume(SoundCategory.MASTER));
        } else if (s.sound.startsWith("mc:")) {
            // "mc:nazwa@1.5" = dźwięk Minecrafta z dodatkowym mnożnikiem wysokości (np. kotek = wyższe Mau)
            String id = s.sound.substring(3);
            float pitch = s.pitch;
            int at = id.indexOf('@');
            if (at > 0) {
                try { pitch *= Float.parseFloat(id.substring(at + 1)); } catch (NumberFormatException ignored) { }
                id = id.substring(0, at);
            }
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
            if (ev != null) mc.getSoundManager().play(PositionedSoundInstance.master(ev, Math.min(2f, pitch), s.volume));
        }
    }

    private static void playFile(Path file, float volume) {
        if (!Files.exists(file)) return;
        Thread t = new Thread(() -> {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
                Clip clip = AudioSystem.getClip();
                clip.open(in);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20.0 * Math.log10(Math.max(volume, 0.0001f)));
                    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
                }
                clip.addLineListener(e -> { if (e.getType() == LineEvent.Type.STOP) clip.close(); });
                clip.start();
            } catch (Exception e) {
                System.err.println("[KosaVisuals] Nie udało się odtworzyć " + file.getFileName() + ": " + e);
            }
        }, "holovis-sound");
        t.setDaemon(true);
        t.start();
    }

    /** Kopiuje plik .wav do folderu dźwięków. Zwraca klucz dźwięku albo null. */
    public static String importFile(Path src) {
        try {
            String name = src.getFileName().toString();
            if (!name.toLowerCase().endsWith(".wav")) return null;
            Files.createDirectories(DIR);
            Files.copy(src, DIR.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return "file:" + name;
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Import dźwięku nie powiódł się: " + e);
            return null;
        }
    }

    public static void openFolder() {
        try {
            Files.createDirectories(DIR);
            String os = System.getProperty("os.name", "").toLowerCase();
            String path = DIR.toAbsolutePath().toString();
            if (os.contains("win")) new ProcessBuilder("explorer.exe", path).start();
            else if (os.contains("mac")) new ProcessBuilder("open", path).start();
            else new ProcessBuilder("xdg-open", path).start();
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się otworzyć folderu: " + e);
        }
    }
}
