package net.phoenixvine.wiki.fx;

import net.minecraft.client.renderer.ShaderInstance;

public abstract class PhoenixScreenEffect {

    protected float intensity = 1.0f;

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public void onAdd() {}

    public void onRemove() {}

    public abstract net.minecraft.client.renderer.ShaderInstance getShader();

    public abstract void uploadUniforms(float partialTick);

    public int priority() {
        return 0;
    }
}
