package pl.holovis.mixin;

import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pl.holovis.SkyHelper;

/** Kolor kopuły nieba: SkyRendering.renderSky(float red, float green, float blue). */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {

    @ModifyVariable(method = "renderSky(FFF)V", at = @At("HEAD"), index = 1, argsOnly = true)
    private float holovis$red(float v) { return SkyHelper.channel(0, v); }

    @ModifyVariable(method = "renderSky(FFF)V", at = @At("HEAD"), index = 2, argsOnly = true)
    private float holovis$green(float v) { return SkyHelper.channel(1, v); }

    @ModifyVariable(method = "renderSky(FFF)V", at = @At("HEAD"), index = 3, argsOnly = true)
    private float holovis$blue(float v) { return SkyHelper.channel(2, v); }
}
