package pl.holovis;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Spotify na komputerze, bez logowania.
 * Windows: Media Session (SMTC) przez PowerShell - tytuł, pozycja, długość, pauza, skip, PRZEWIJANIE.
 * Gdy to nie zadziała: tytuł okna + klawisze multimedialne (bez przewijania).
 * macOS / Linux: osascript / playerctl (bez przewijania).
 */
public final class Spotify {
    private Spotify() {}

    public static volatile String track = "", artist = "", title = "";
    public static volatile boolean playing = false, running = false, hasTimeline = false;
    public static volatile long durMs = 0;
    private static volatile long posMs = 0, posStamp = System.nanoTime();

    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static final boolean WIN = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private static final Path BASE = HoloSounds.DIR.getParent();
    private static final Path BRIDGE_PS = BASE.resolve("spotify_bridge.ps1");
    private static final Path MEDIA_PS = BASE.resolve("spotify_media.ps1");
    private static final Path CMD = BASE.resolve("spotify_cmd.txt");

    private static Process bridge, legacyPoller, mediaCtl;
    private static boolean active, hook, legacy;
    private static int gen;

    // ------------------------------------------------------------------
    public static long position() {
        long p = posMs + (playing ? (System.nanoTime() - posStamp) / 1_000_000L : 0);
        long d = durMs;
        return Math.max(0, d > 0 ? Math.min(p, d) : p);
    }

    public static synchronized void sync() {
        if (!hook) {
            hook = true;
            Runtime.getRuntime().addShutdownHook(new Thread(Spotify::shutdown));
        }
        if (HoloConfig.get().spotify) start(); else stop();
    }

    public static void next() { command("next"); }
    public static void previous() { command("prev"); }
    public static void toggle() { command("toggle"); }

    public static void seek(long ms) {
        if (!WIN || legacy || !hasTimeline) return;
        posMs = ms;
        posStamp = System.nanoTime();
        command("seek " + ms);
    }

    // ------------------------------------------------------------------
    private static void start() {
        if (active) return;
        active = true;
        final int my = ++gen;
        Thread t = new Thread(() -> {
            if (WIN) {
                boolean ok = runBridge(my);
                if (!ok && my == gen) { legacy = true; pollLegacy(my); }
            } else {
                pollOther(my);
            }
        }, "holovis-spotify");
        t.setDaemon(true);
        t.start();
    }

    private static void stop() {
        active = false;
        gen++;
        if (bridge != null) { bridge.destroy(); bridge = null; }
        if (legacyPoller != null) { legacyPoller.destroy(); legacyPoller = null; }
        running = false;
        playing = false;
        hasTimeline = false;
        track = "";
    }

    private static void shutdown() {
        try { if (bridge != null) bridge.destroy(); } catch (Exception ignored) {}
        try { if (legacyPoller != null) legacyPoller.destroy(); } catch (Exception ignored) {}
        try { if (mediaCtl != null) mediaCtl.destroy(); } catch (Exception ignored) {}
    }

    private static synchronized void command(String cmd) {
        if (WIN) {
            if (!legacy) {
                if (!active) start();
                try {
                    Files.createDirectories(BASE);
                    Files.writeString(CMD, cmd);
                } catch (Exception e) {
                    System.err.println("[KosaVisuals] Spotify: nie udało się wysłać polecenia: " + e);
                }
                return;
            }
            mediaKey(cmd);
            return;
        }
        String[] c;
        if (MAC) {
            String a = cmd.equals("next") ? "next track" : cmd.equals("prev") ? "previous track" : "playpause";
            c = new String[]{"osascript", "-e", "tell application \"Spotify\" to " + a};
        } else {
            String a = cmd.equals("next") ? "next" : cmd.equals("prev") ? "previous" : "play-pause";
            c = new String[]{"playerctl", "-p", "spotify", a};
        }
        Thread t = new Thread(() -> run(c), "holovis-spotify-cmd");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------
    //  Windows: Media Session (SMTC)
    // ------------------------------------------------------------------
    private static final String BRIDGE_SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            $cmdFile = $args[0]
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
            $mgr = $null
            try {
              Add-Type -AssemblyName System.Runtime.WindowsRuntime
              $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
              $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
              $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
            } catch { }
            function Await($op, $type) {
              $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
              $t.Wait(-1) | Out-Null
              $t.Result
            }
            try { $mgr = Await ($mgrType::RequestAsync()) $mgrType } catch { $mgr = $null }
            if ($null -eq $mgr) { [Console]::Out.WriteLine('ERR'); [Console]::Out.Flush(); exit 1 }
            [Console]::Out.WriteLine('READY'); [Console]::Out.Flush()
            $sep = [char]9
            function FindSpotify() {
              foreach ($s in $mgr.GetSessions()) { if ($s.SourceAppUserModelId -like '*Spotify*') { return $s } }
              return $null
            }
            function Emit($s) {
              if ($null -eq $s) { [Console]::Out.WriteLine('S' + $sep + '0'); [Console]::Out.Flush(); return }
              $props = Await ($s.TryGetMediaPropertiesAsync()) $propType
              $tl = $s.GetTimelineProperties()
              $st = [int]$s.GetPlaybackInfo().PlaybackStatus
              $pos = [int64]$tl.Position.TotalMilliseconds
              if ($st -eq 4) { $pos = $pos + [int64]([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
              $dur = [int64]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
              if ($pos -lt 0) { $pos = 0 }
              if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
              [Console]::Out.WriteLine('S' + $sep + '1' + $sep + $st + $sep + $pos + $sep + $dur + $sep + $props.Artist + $sep + $props.Title)
              [Console]::Out.Flush()
            }
            function RunCmd($s, $c) {
              if ($c -eq 'next') { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null }
              elseif ($c -eq 'prev') { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }
              elseif ($c -eq 'toggle') { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
              elseif ($c.StartsWith('seek')) { $ms = [int64]($c.Substring(4).Trim()); Await ($s.TryChangePlaybackPositionAsync($ms * 10000)) ([bool]) | Out-Null }
            }
            $n = 0
            while ($true) {
              $sp = FindSpotify
              if (Test-Path $cmdFile) {
                $c = ''
                try { $c = (Get-Content $cmdFile -Raw).Trim() } catch { }
                Remove-Item $cmdFile -Force
                if ($sp -and $c) { RunCmd $sp $c; Start-Sleep -Milliseconds 150; $n = 0 }
              }
              if ($n -le 0) { Emit $sp; $n = 5 } else { $n = $n - 1 }
              Start-Sleep -Milliseconds 200
            }
            """;

    /** @return true, jeśli most SMTC działa i podał choć jeden stan */
    private static boolean runBridge(int my) {
        boolean got = false;
        try {
            Files.createDirectories(BASE);
            Files.writeString(BRIDGE_PS, BRIDGE_SCRIPT);
            Files.deleteIfExists(CMD);
            Process pr = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", BRIDGE_PS.toAbsolutePath().toString(), CMD.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            synchronized (Spotify.class) {
                if (my != gen) { pr.destroy(); return true; }
                bridge = pr;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (my == gen && (line = r.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                    if (line.startsWith("ERR")) { pr.destroy(); return false; }
                    if (line.startsWith("S\t")) { handleBridge(line); got = true; }
                }
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Spotify: błąd mostu SMTC: " + e);
        }
        return got || my != gen;
    }

    private static void handleBridge(String line) {
        String[] f = line.split("\t", 7);
        if (f.length >= 2 && f[1].equals("0")) {
            running = false;
            playing = false;
            hasTimeline = false;
            return;
        }
        if (f.length < 7) return;
        try {
            int st = Integer.parseInt(f[2].trim());
            long pos = Long.parseLong(f[3].trim());
            long dur = Long.parseLong(f[4].trim());
            String ar = f[5].trim(), ti = f[6].trim();
            running = true;
            hasTimeline = dur > 0;
            playing = st == 4;
            durMs = dur;
            posMs = pos;
            posStamp = System.nanoTime();
            artist = ar;
            title = ti;
            track = ar.isEmpty() ? ti : ar + " - " + ti;
            if (!ti.isEmpty()) Lyrics.request(ar, ti, dur);
        } catch (NumberFormatException ignored) {}
    }

    // ------------------------------------------------------------------
    //  Windows: zapasowo - tytuł okna + klawisze multimedialne
    // ------------------------------------------------------------------
    private static final String MEDIA_SCRIPT = """
            Add-Type -TypeDefinition @"
            using System;
            using System.Runtime.InteropServices;
            public class HK { [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra); }
            "@
            while (($l = [Console]::In.ReadLine()) -ne $null) {
              $vk = 0
              if ($l -eq 'next') { $vk = 176 } elseif ($l -eq 'prev') { $vk = 177 } elseif ($l -eq 'toggle') { $vk = 179 }
              if ($vk -ne 0) {
                [HK]::keybd_event([byte]$vk, 0, 1, [UIntPtr]::Zero)
                [HK]::keybd_event([byte]$vk, 0, 3, [UIntPtr]::Zero)
              }
            }
            """;

    private static void mediaKey(String cmd) {
        if (cmd.startsWith("seek")) return;
        try {
            if (mediaCtl == null || !mediaCtl.isAlive()) {
                Files.createDirectories(BASE);
                Files.writeString(MEDIA_PS, MEDIA_SCRIPT);
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", MEDIA_PS.toAbsolutePath().toString()).redirectErrorStream(true).start();
                mediaCtl = p;
                Thread d = new Thread(() -> {
                    try { p.getInputStream().transferTo(OutputStream.nullOutputStream()); } catch (Exception ignored) {}
                }, "holovis-spotify-drain");
                d.setDaemon(true);
                d.start();
            }
            OutputStream os = mediaCtl.getOutputStream();
            os.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Spotify: klawisze multimedialne nie zadziałały: " + e);
        }
    }

    private static void pollLegacy(int my) {
        String script = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false; "
                + "while($true){ "
                + "$p = Get-Process -Name Spotify -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1; "
                + "if($p){ Write-Output ('T|' + $p.MainWindowTitle) } else { Write-Output 'N|' }; "
                + "Start-Sleep -Milliseconds 1000 }";
        try {
            Process pr = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script).redirectErrorStream(true).start();
            synchronized (Spotify.class) {
                if (my != gen) { pr.destroy(); return; }
                legacyPoller = pr;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (my == gen && (line = r.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                    if (line.startsWith("T|")) {
                        String t = line.substring(2).trim();
                        running = true;
                        hasTimeline = false;
                        if (t.matches("(?i)^spotify( (free|premium))?$")) playing = false;
                        else { playing = true; track = t; }
                    } else if (line.startsWith("N|")) {
                        running = false;
                        playing = false;
                        track = "";
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Spotify: błąd odczytu: " + e);
        }
    }

    // ------------------------------------------------------------------
    //  macOS / Linux
    // ------------------------------------------------------------------
    private static void pollOther(int my) {
        String[] cmd = MAC
                ? new String[]{"osascript", "-e",
                "if application \"Spotify\" is running then tell application \"Spotify\" to return (artist of current track) & \" - \" & (name of current track) & \"|\" & (player state as string)"}
                : new String[]{"playerctl", "-p", "spotify", "metadata", "--format", "{{artist}} - {{title}}|{{status}}"};
        while (my == gen) {
            String out = run(cmd);
            int i = out.lastIndexOf('|');
            if (out.isEmpty() || i < 0) {
                running = false;
                playing = false;
            } else {
                running = true;
                hasTimeline = false;
                track = out.substring(0, i).trim();
                playing = out.substring(i + 1).toLowerCase().contains("play");
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    }

    private static String run(String[] cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(3, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return "";
        }
    }
}
