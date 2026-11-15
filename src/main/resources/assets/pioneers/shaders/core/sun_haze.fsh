#version 150

// Distorsion de chaleur autour du soleil : l'image de la scene (Sampler0) ondule,
// fortement pres de la surface, puis s'attenue en s'eloignant. Le disque de l'etoile n'est pas deforme.

uniform sampler2D Sampler0;
uniform vec2 LensScreenSize;
uniform vec2 CenterScreen;
uniform float SunRadius;     // rayon de l'etoile (en hauteur d'ecran)
uniform float HazeRadius;    // rayon de la zone qui ondule
uniform float Strength;
uniform float Time;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / LensScreenSize;
    float aspect = LensScreenSize.x / LensScreenSize.y;
    vec2 d = (uv - CenterScreen) * vec2(aspect, 1.0);
    float r = length(d);
    if (r > HazeRadius || r < SunRadius * 0.97) {
        discard;
    }
    float fall = 1.0 - smoothstep(SunRadius, HazeRadius, r);
    fall *= fall;
    // Deux ondulations croisees, qui montent comme de l'air chaud
    vec2 q = d / SunRadius;
    float w1 = sin(q.y * 9.0 - Time * 5.0 + sin(q.x * 3.0 + Time) * 2.0);
    float w2 = cos(q.x * 11.0 + Time * 3.7 + sin(q.y * 4.0 - Time * 1.3) * 2.0);
    vec2 offset = vec2(w1, w2) * SunRadius * 0.035 * Strength * fall;
    vec2 sampleUv = clamp(uv + offset / vec2(aspect, 1.0), vec2(0.001), vec2(0.999));
    vec3 col = texture(Sampler0, sampleUv).rgb;
    // Leger voile chaud
    col += vec3(1.0, 0.55, 0.15) * 0.06 * fall * Strength;
    fragColor = vec4(col, 1.0);
}
