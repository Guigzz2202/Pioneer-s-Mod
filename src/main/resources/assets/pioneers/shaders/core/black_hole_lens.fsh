#version 150

// Lentille gravitationnelle : on relit l'image de la scene (Sampler0) en deformant les coordonnees
// autour du trou noir, comme la lumiere courbee par sa gravite.

uniform sampler2D Sampler0;      // copie de l'image de la scene
uniform vec2 LensScreenSize;     // taille de l'image en pixels
uniform vec2 CenterScreen;       // centre du trou noir a l'ecran (0..1)
uniform float HorizonRadius;     // rayon de l'ombre (en hauteur d'ecran)
uniform float LensRadius;        // rayon de la zone deformee (en hauteur d'ecran)
uniform float Strength;          // force de la deviation
uniform float Glow;              // intensite de l'anneau de photons
uniform float Time;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / LensScreenSize;
    float aspect = LensScreenSize.x / LensScreenSize.y;
    vec2 d = (uv - CenterScreen) * vec2(aspect, 1.0);
    float r = length(d);
    if (r > LensRadius) {
        discard;
    }
    float rs = HorizonRadius;

    // Horizon des evenements : noir absolu
    if (r < rs) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Deviation de la lumiere, en 1/r. Pres de l'horizon, le rayon echantillonne devient negatif :
    // on voit l'image de l'autre cote du trou noir (anneau d'Einstein).
    float fade = 1.0 - smoothstep(LensRadius * 0.45, LensRadius, r);
    float bend = Strength * rs * rs / r;
    float sampleR = r - bend * fade;
    vec2 dir = d / r;
    vec2 sampleUv = CenterScreen + dir * sampleR / vec2(aspect, 1.0);
    sampleUv = clamp(sampleUv, vec2(0.001), vec2(0.999));
    vec3 col = texture(Sampler0, sampleUv).rgb;

    // Decalage vers le rouge et assombrissement pres de l'horizon
    float dim = smoothstep(rs, rs * 1.9, r);
    col *= mix(0.08, 1.0, dim);

    // Anneau de photons : fin, brillant, qui scintille legerement
    float shimmer = 0.85 + 0.15 * sin(Time * 3.0 + atan(d.y, d.x) * 6.0);
    float ring = exp(-pow((r - rs * 1.07) / (rs * 0.05), 2.0));
    float halo = exp(-pow((r - rs * 1.25) / (rs * 0.25), 2.0)) * 0.25;
    col += vec3(1.0, 0.82, 0.62) * ring * Glow * shimmer + vec3(0.55, 0.25, 0.95) * halo * Glow;

    fragColor = vec4(col, 1.0);
}
