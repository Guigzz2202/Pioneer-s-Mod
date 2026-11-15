#version 150

// Le quad est deja en coordonnees ecran (NDC) : pas de matrices.
in vec3 Position;

void main() {
    gl_Position = vec4(Position, 1.0);
}
