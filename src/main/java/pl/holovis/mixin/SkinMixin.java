package pl.holovis.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.GhostBot;

/** TestBot dostaje własny skin (czarny strój) zamiast domyślnego Steve'a/Alex. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class SkinMixin {

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void holovis$botSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (GhostBot.isBot((Entity) (Object) this)) cir.setReturnValue(GhostBot.skin());
    }
}
