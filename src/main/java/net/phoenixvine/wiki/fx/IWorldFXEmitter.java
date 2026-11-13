package net.phoenixvine.wiki.fx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

public interface IWorldFXEmitter {

    @OnlyIn(Dist.CLIENT)
    @Nullable
    PhoenixSkyLayer createSkyLayer();

    @OnlyIn(Dist.CLIENT)
    @Nullable
    PhoenixScreenEffect createScreenEffect();

    @OnlyIn(Dist.CLIENT)
    float getEffectRadius();
}
