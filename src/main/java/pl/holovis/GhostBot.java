package pl.holovis;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * /kosa ghost - gracz "TestBot" istniejący tylko u Ciebie (po stronie klienta). Można go bić:
 * uderzenia nie są wysyłane na serwer, obrażenia i odrzut liczymy lokalnie.
 */
public final class GhostBot {
    private GhostBot() {}

    private static OtherClientPlayerEntity bot;
    private static Vec3d home = Vec3d.ZERO;
    private static SkinTextures skin;
    private static int deadTicks;
    private static boolean dying;
    private static int idCounter;

    public static boolean isBot(Entity e) { return e != null && bot != null && e == bot; }

    public static boolean exists() { return bot != null; }

    /** Skin TestBota (czarny strój, blada twarz) - podstawiany przez SkinMixin. */
    public static SkinTextures skin() {
        if (skin == null)
            skin = new SkinTextures(Identifier.of("holovis", "textures/entity/testbot.png"), null, null, null,
                    SkinTextures.Model.WIDE, true);
        return skin;
    }

    public static void spawn(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) return;
        remove(mc);

        OtherClientPlayerEntity b = new OtherClientPlayerEntity(w, new GameProfile(UUID.randomUUID(), "TestBot"));
        Vec3d look = p.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d pos = p.getPos().add(flat.multiply(3.0));
        float yaw = p.getYaw() + 180f;
        b.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        b.setHeadYaw(yaw);
        b.setBodyYaw(yaw);
        b.setId(2_000_000_000 + (idCounter++ % 1000));
        b.setHealth(20f);
        w.addEntity(b);

        bot = b;
        home = pos;
        deadTicks = 0;
        dying = false;
        p.sendMessage(Text.literal("[KosaVisuals] TestBot pojawił się (widzisz go tylko Ty). Bij go; /kosa ghost remove usuwa."), false);
    }

    public static void remove(MinecraftClient mc) {
        if (bot != null && mc.world != null) {
            try { mc.world.removeEntity(bot.getId(), Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
        }
        bot = null;
        dying = false;
    }

    /** Uderzenie bota (atak jest anulowany przed wysłaniem na serwer). */
    public static void onHit(MinecraftClient mc, ClientPlayerEntity p) {
        if (bot == null || dying) return;
        p.swingHand(Hand.MAIN_HAND);

        HoloConfig cfg = HoloConfig.get();
        if (cfg.hitSound.enabled) HoloSounds.play(cfg.hitSound);
        TargetHudNotify.hit(bot);

        float hp = Math.max(0f, bot.getHealth() - 2f);
        bot.setHealth(hp);
        bot.hurtTime = 10;
        bot.maxHurtTime = 10;

        DamageNumbers.show(bot, 2f);

        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", "entity.player.hurt"));
        if (ev != null) mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1f, 1f));

        if (hp <= 0f) {
            dying = true;
            if (cfg.killSound.enabled) HoloSounds.play(cfg.killSound);
            KillEffects.spawn(bot.getPos(), bot.getYaw(), bot.getSkinTextures().texture());
        }
    }

    public static void tick(MinecraftClient mc) {
        if (bot == null) return;
        if (mc.world == null || bot.getWorld() != mc.world || bot.isRemoved()) { bot = null; return; }

        // bez odrzutu: bot stoi w miejscu (nic go nie przesuwa)
        bot.setVelocity(Vec3d.ZERO);
        if (bot.getPos().squaredDistanceTo(home) > 1e-6) bot.setPosition(home.x, home.y, home.z);

        if (dying) {
            deadTicks++;
            if (deadTicks > 22) remove(mc);
        }
    }

    /** Pomost do TargetHud (żeby GhostBot nie zależał od jego wnętrza). */
    private static final class TargetHudNotify {
        static void hit(net.minecraft.entity.LivingEntity e) { TargetHud.notifyAttack(e); }
    }
}
