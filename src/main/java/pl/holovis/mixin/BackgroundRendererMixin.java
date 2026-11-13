package pl.holovis.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.SkyHelper;

/** Kolor mgły / horyzontu - dopasowany do własnego nieba. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private static void holovis$fogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
                                         float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (!SkyHelper.active(world)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && (mc.player.isSubmergedInWater() || mc.player.isInLava())) return;
        Vector4f v = cir.getReturnValue();
        int sky = SkyHelper.horizon(world, tickDelta);
        float k = 0.9f;
        float r = ((sky >> 16) & 255) / 255f, g = ((sky >> 8) & 255) / 255f, b = (sky & 255) / 255f;
        cir.setReturnValue(new Vector4f(v.x * (1 - k) + r * k, v.y * (1 - k) + g * k, v.z * (1 - k) + b * k, v.w));
    }
}
