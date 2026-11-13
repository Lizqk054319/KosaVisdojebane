package pl.holovis;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pl.holovis.gui.ConfigScreen;
import pl.holovis.gui.HoloScreen;

import java.util.Locale;

public class HoloVisClient implements ClientModInitializer {
    public static KeyBinding OPEN_MENU, SP_NEXT, SP_PREV, SP_TOGGLE;

    private static LivingEntity lastTarget;
    private static int lastTargetTicks;
    private static Vec3d lastPos = Vec3d.ZERO;
    private static float lastYaw;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code, "key.categories.holovis"));
    }

    /** Click Sound: kliknięcie przycisku w dowolnym menu (np. ESC -> Options). */
    private static boolean onScreenClick(Screen screen, double mx, double my, int button) {
        HoloConfig cfg = HoloConfig.get();
        if (cfg.clickSound.enabled && button == 0) {
            for (Element el : screen.children()) {
                if (el instanceof ClickableWidget w && w.visible && w.active && w.isMouseOver(mx, my)) {
                    HoloSounds.play(cfg.clickSound);
                    break;
                }
            }
        }
        return true;
    }

    private static void onKill(LivingEntity e) {
        HoloConfig cfg = HoloConfig.get();
        if (cfg.killSound.enabled) HoloSounds.play(cfg.killSound);
        if (e instanceof PlayerEntity) {
            Identifier skin = e instanceof AbstractClientPlayerEntity pl ? pl.getSkinTextures().texture() : null;
            KillEffects.spawn(lastPos, lastYaw, skin);
        }
    }

    // ---- komendy ----
    private static final String[] KEY_NAMES = {"Insert", "RightShift", "RightControl", "LeftAlt", "Home", "End", "Delete",
            "PageUp", "PageDown", "F6", "F7", "F8", "K", "G", "Mouse4", "Mouse5"};

    private static final SuggestionProvider<FabricClientCommandSource> KEY_SUGGESTIONS = (ctx, b) -> {
        String rem = b.getRemainingLowerCase();
        for (String k : KEY_NAMES) if (k.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(k);
        return b.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> PROFILE_SUGGESTIONS = (ctx, b) -> {
        String rem = b.getRemainingLowerCase();
        for (Profiles.Profile p : Profiles.all()) if (p.name.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(p.name);
        return b.buildFuture();
    };

    private static int say(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Text.literal("[KosaVisuals] " + msg));
        return 1;
    }

    private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = ctx.getSource().getClient();
        mc.send(() -> mc.setScreen(new HoloScreen()));
        return 1;
    }

    /** /kosa config load <kod albo nazwa profilu> */
    private static int configLoad(CommandContext<FabricClientCommandSource> ctx, String arg) {
        String a = arg.trim();
        String err;
        String what;
        if (a.startsWith("KV1:") || a.startsWith("HV1:")) {
            err = Profiles.loadCode(a);
            what = "config z kodu";
        } else {
            Profiles.Profile p = Profiles.find(a);
            if (p == null) return say(ctx, "Nie ma profilu o nazwie \"" + a + "\" (lista: /kosa config list)");
            err = Profiles.load(p);
            what = "profil " + p.name;
        }
        if (err != null) return say(ctx, err);
        Spotify.sync();
        HoloSounds.refresh();
        return say(ctx, "Wczytano " + what);
    }

    private static InputUtil.Key parseKey(String raw) {
        String k = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '.').replace('_', '.').replace('-', '.');
        switch (k) {
            case "rshift", "rightshift" -> k = "right.shift";
            case "lshift", "leftshift", "shift" -> k = "left.shift";
            case "rctrl", "rightctrl", "rightcontrol" -> k = "right.control";
            case "lctrl", "leftctrl", "leftcontrol", "ctrl", "control" -> k = "left.control";
            case "ralt", "rightalt" -> k = "right.alt";
            case "lalt", "leftalt", "alt" -> k = "left.alt";
            case "esc" -> k = "escape";
            case "del" -> k = "delete";
            case "ins" -> k = "insert";
            case "pgup", "pageup" -> k = "page.up";
            case "pgdn", "pagedown" -> k = "page.down";
            case "caps", "capslock" -> k = "caps.lock";
            case "tilde", "backtick", "grave" -> k = "grave.accent";
            default -> { }
        }
        try {
            if (k.startsWith("mouse")) return InputUtil.fromTranslationKey("key.mouse." + (Integer.parseInt(k.substring(5)) - 1));
            return InputUtil.fromTranslationKey(k.startsWith("key.") ? k : "key.keyboard." + k);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** /kosa clickgui <klawisz>: ustawia klawisz otwierający menu. */
    private static int bindKey(CommandContext<FabricClientCommandSource> ctx, String name) {
        InputUtil.Key key = parseKey(name);
        if (key == null) return say(ctx, "Nieznany klawisz: " + name + " (np. Insert, RightShift, F6, K, Home)");
        MinecraftClient mc = ctx.getSource().getClient();
        OPEN_MENU.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        mc.options.write();
        return say(ctx, "Menu otworzysz teraz klawiszem: " + key.getLocalizedText().getString());
    }

    @Override
    public void onInitializeClient() {
        HoloConfig.load();
        HoloSounds.refresh();
        Spotify.sync();

        OPEN_MENU = key("key.holovis.open", GLFW.GLFW_KEY_RIGHT_SHIFT);
        SP_NEXT = key("key.holovis.spotify_next", GLFW.GLFW_KEY_RIGHT_BRACKET);
        SP_PREV = key("key.holovis.spotify_prev", GLFW.GLFW_KEY_LEFT_BRACKET);
        SP_TOGGLE = key("key.holovis.spotify_toggle", GLFW.GLFW_KEY_BACKSLASH);

        // /kosa (albo /holovis): menu, toggle, clickgui <klawisz>, config [load|save|copy|list], ghost, killtest
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String name : new String[]{"kosa", "holovis"}) {
                dispatcher.register(ClientCommandManager.literal(name)
                        .executes(ctx -> openGui(ctx))
                        .then(ClientCommandManager.literal("toggle").executes(ctx -> openGui(ctx)))
                        .then(ClientCommandManager.literal("clickgui")
                                .executes(ctx -> say(ctx, "Klawisz menu: " + OPEN_MENU.getBoundKeyLocalizedText().getString()
                                        + "  |  zmiana: /kosa clickgui <klawisz>, np. /kosa clickgui Insert"))
                                .then(ClientCommandManager.argument("klawisz", StringArgumentType.word())
                                        .suggests(KEY_SUGGESTIONS)
                                        .executes(ctx -> bindKey(ctx, StringArgumentType.getString(ctx, "klawisz")))))
                        .then(ClientCommandManager.literal("config")
                                .executes(ctx -> {
                                    MinecraftClient mc = ctx.getSource().getClient();
                                    mc.send(() -> mc.setScreen(new ConfigScreen()));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("load")
                                        .then(ClientCommandManager.argument("kod", StringArgumentType.greedyString())
                                                .suggests(PROFILE_SUGGESTIONS)
                                                .executes(ctx -> configLoad(ctx, StringArgumentType.getString(ctx, "kod")))))
                                .then(ClientCommandManager.literal("save")
                                        .then(ClientCommandManager.argument("nazwa", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String saved = Profiles.saveCurrent(StringArgumentType.getString(ctx, "nazwa"));
                                                    return say(ctx, saved == null ? "Podaj nazwę profilu" : "Zapisano profil: " + saved);
                                                })))
                                .then(ClientCommandManager.literal("copy")
                                        .executes(ctx -> {
                                            String code = HoloConfig.exportCode();
                                            ctx.getSource().getClient().keyboard.setClipboard(code);
                                            return say(ctx, "Skopiowano kod configu (" + code.length() + " znaków)");
                                        }))
                                .then(ClientCommandManager.literal("list")
                                        .executes(ctx -> {
                                            if (Profiles.count() == 0) return say(ctx, "Brak zapisanych profili");
                                            StringBuilder sb = new StringBuilder("Profile: ");
                                            for (Profiles.Profile p : Profiles.sorted(2)) sb.append(p.name).append(", ");
                                            return say(ctx, sb.substring(0, sb.length() - 2));
                                        })))
                        .then(ClientCommandManager.literal("ghost")
                                .executes(ctx -> { GhostBot.spawn(ctx.getSource().getClient()); return 1; })
                                .then(ClientCommandManager.literal("remove")
                                        .executes(ctx -> { GhostBot.remove(ctx.getSource().getClient()); return 1; })))
                        .then(ClientCommandManager.literal("killtest")
                                .executes(ctx -> { KillEffects.spawnTest(); return 1; })));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_MENU.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new HoloScreen());
            }
            while (SP_NEXT.wasPressed()) Spotify.next();
            while (SP_PREV.wasPressed()) Spotify.previous();
            while (SP_TOGGLE.wasPressed()) Spotify.toggle();

            TargetHud.tick(mc);
            GhostBot.tick(mc);
            Cooldowns.tick(mc);
            DamageNumbers.tick();

            if (lastTarget != null) {
                lastTargetTicks++;
                if (lastTarget.isAlive()) {
                    lastPos = lastTarget.getPos();
                    lastYaw = lastTarget.getYaw();
                    if (lastTargetTicks > 100) lastTarget = null;
                } else {
                    onKill(lastTarget);
                    lastTarget = null;
                }
            }
        });

        // Uderzenie prawdziwego moba / gracza
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof LivingEntity living && !GhostBot.isBot(entity)) {
                HoloConfig cfg = HoloConfig.get();
                if (cfg.hitSound.enabled) HoloSounds.play(cfg.hitSound);
                TargetHud.notifyAttack(living);
                DamageNumbers.onAttack(living);
                lastTarget = living;
                lastTargetTicks = 0;
                lastPos = living.getPos();
                lastYaw = living.getYaw();
            }
            return ActionResult.PASS;
        });

        // Atak na TestBota (anulowany, żeby nic nie poszło na serwer) + Click Sound przy machnięciu w powietrze
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.currentScreen != null || clickCount <= 0) return false;
            HitResult hr = client.crosshairTarget;
            if (hr instanceof EntityHitResult ehr && GhostBot.isBot(ehr.getEntity())) {
                GhostBot.onHit(client, player);
                return true;
            }
            HoloConfig cfg = HoloConfig.get();
            if (cfg.clickSound.enabled && (hr == null || hr.getType() == HitResult.Type.MISS)) {
                HoloSounds.play(cfg.clickSound);
            }
            return false;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenMouseEvents.allowMouseClick(screen).register(HoloVisClient::onScreenClick);
            if (screen instanceof ChatScreen) ScreenEvents.afterRender(screen).register(SpotifyOverlay::render);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(KillEffects::render);
        WorldRenderEvents.AFTER_ENTITIES.register(TargetEsp::render);
        WorldRenderEvents.AFTER_ENTITIES.register(DamageNumbers::render);
        WorldRenderEvents.END.register(PostFx::onWorldEnd);
        HudRenderCallback.EVENT.register(HudRenderer::render);
    }
}
