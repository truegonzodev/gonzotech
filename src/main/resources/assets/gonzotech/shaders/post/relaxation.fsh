#version 150

uniform sampler2D InSampler;
uniform float EffectStrength;
uniform float ContrastScale;
uniform float WhiteOverlay;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    float strength = clamp(EffectStrength, 0.0, 1.0);

    // Reduce RGB contrast around neutral gray by 23%, then apply a 15% white wash.
    float contrast = mix(1.0, ContrastScale, strength);
    vec3 adjusted = (source.rgb - vec3(0.5)) * contrast + vec3(0.5);
    adjusted = mix(adjusted, vec3(1.0), clamp(WhiteOverlay * strength, 0.0, 1.0));

    fragColor = vec4(clamp(adjusted, 0.0, 1.0), source.a);
}
