package pl.holovis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Teksty piosenek z lrclib.net (darmowe, bez klucza). Zsynchronizowane (LRC) albo zwykły tekst. */
public final class Lyrics {
    private Lyrics() {}

    public record Line(long t, String text) {}

    /** 0 brak, 1 ładowanie, 2 zsynchronizowany, 3 zwykły tekst, 4 nie znaleziono */
    public static volatile int state = 0;
    public static volatile List<Line> lines = List.of();

    private static String key = "";
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)\\]");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static void request(String artist, String title, long durMs) {
        String k = artist + "\t" + title;
        synchronized (Lyrics.class) {
            if (k.equals(key)) return;
            key = k;
        }
        state = 1;
        lines = List.of();
        Thread t = new Thread(() -> load(k, artist, title, durMs), "holovis-lyrics");
        t.setDaemon(true);
        t.start();
    }

    public static int indexAt(long posMs) {
        List<Line> l = lines;
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).t() <= posMs + 250) idx = i; else break;
        }
        return idx;
    }

    private static void load(String k, String artist, String title, long durMs) {
        try {
            String url = "https://lrclib.net/api/get?artist_name=" + enc(artist) + "&track_name=" + enc(title)
                    + (durMs > 0 ? "&duration=" + Math.round(durMs / 1000.0) : "");
            String body = get(url);
            JsonObject o = null;
            if (body != null) o = JsonParser.parseString(body).getAsJsonObject();
            if (o == null) {
                String sb = get("https://lrclib.net/api/search?q=" + enc(artist + " " + title));
                if (sb != null) {
                    JsonArray a = JsonParser.parseString(sb).getAsJsonArray();
                    for (JsonElement e : a) {
                        JsonObject c = e.getAsJsonObject();
                        if (has(c, "syncedLyrics")) { o = c; break; }
                        if (o == null && has(c, "plainLyrics")) o = c;
                    }
                }
            }
            synchronized (Lyrics.class) { if (!k.equals(key)) return; }
            if (o != null && has(o, "syncedLyrics")) {
                List<Line> parsed = parseLrc(o.get("syncedLyrics").getAsString());
                if (!parsed.isEmpty()) { lines = parsed; state = 2; return; }
            }
            if (o != null && has(o, "plainLyrics")) {
                List<Line> plain = new ArrayList<>();
                for (String s : o.get("plainLyrics").getAsString().split("\\R")) plain.add(new Line(-1, s.trim()));
                lines = plain;
                state = 3;
                return;
            }
            state = 4;
        } catch (Exception e) {
            synchronized (Lyrics.class) { if (k.equals(key)) state = 4; }
        }
    }

    private static boolean has(JsonObject o, String f) {
        JsonElement e = o.get(f);
        return e != null && !e.isJsonNull() && !e.getAsString().isBlank();
    }

    private static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                double sec = Double.parseDouble(m.group(2).replace(':', '.'));
                times.add((long) (Long.parseLong(m.group(1)) * 60000L + sec * 1000));
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::t));
        return out;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "KosaVisuals/1.1 (Minecraft Fabric mod)")
                    .GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
