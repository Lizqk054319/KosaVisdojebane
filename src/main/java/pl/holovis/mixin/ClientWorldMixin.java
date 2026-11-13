package pl.holovis.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.SkyHelper;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void holovis$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        ClientWorld w = (ClientWorld) (Object) this;
        if (SkyHelper.active(w)) cir.setReturnValue(0xFF000000 | SkyHelper.color(w, tickDelta));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void holovis$cloudsColor(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        ClientWorld w = (ClientWorld) (Object) this;
        if (SkyHelper.active(w) && HoloConfig.get().skyClouds) {
            int orig = cir.getReturnValue() & 0xFFFFFF;
            cir.setReturnValue(0xFF000000 | Gfx.mix(orig, SkyHelper.horizon(w, tickDelta), 0.6f));
        }
    }
}
