package fr.pioneers.space.sky.client.mega;

/**
 * 1.0.7 (« ciel grandiose ») : ce que voit l'observateur au moment de l'image : lumiere du Soleil (repere du sol), nuit, brume, temps de
 * l'animation et activite de la megastructure (0 au repos, 1 quand ses ports travaillent). Rempli par le rendu (ou le banc) a chaque
 * image, sans allocation.
 */
public final class MegaView {

    /** Direction du Soleil (repere du sol, unitaire) et sa hauteur (radians). */
    public double lx, ly = 1, lz, sunAlt = Math.PI / 2;
    /** Couleur de la lumiere du Soleil (teinte de l'Essaim comprise) et lumiere ambiante (diffusion du ciel, reflet de l'astre). */
    public double sunR = 1, sunG = 1, sunB = 1, ambient = 0.12;
    /** 0 en plein jour, 1 la nuit (feux et hublots plus visibles). */
    public double night;
    /** Brume de l'atmosphere (0 sans atmosphere) et sa couleur (couleur du ciel). */
    public double haze, hazeR = 0.8, hazeG = 0.6, hazeB = 0.5;
    /** Rayon de l'astre (km) : ombre de l'astre sur les parties hautes (les sommets restent eclaires apres le coucher). */
    public double planetKm = 3390;
    /** Temps de l'animation (ticks, continu) et activite (0..1, lissee). */
    public double ticks, activity;
    /** Soleil visible impose aux faces suivantes (0..1 ; negatif = calcule par la hauteur) : ombre de l'astre par morceau. */
    public double litOverride = -1;
    /** Reglages de la megastructure (T:au_dela/megastructures.py SKY). */
    public double[] params;
    /** Couleurs de la megastructure (0xRRGGBB) : principale et d'accent. */
    public int color = 0xFFFFFF, accent = 0xC06030;

    /**
     * Lumiere du moment : direction du Soleil (repere du sol, normalisee ici), couleur de sa lumiere, atmosphere (brume, lumiere du ciel)
     * et pluie ou tempete (plus de brume). Meme calcul pour le jeu et le banc.
     */
    public void sun(double x, double y, double z, double r, double g, double b, boolean air, double rain) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-9) {
            x = 0;
            y = -1;
            z = 0;
            l = 1;
        }
        lx = x / l;
        ly = y / l;
        lz = z / l;
        sunAlt = Math.asin(Math.max(-1, Math.min(1, ly)));
        night = smooth(0.05, -0.2, sunAlt);
        sunR = r;
        sunG = g;
        sunB = b;
        ambient = air ? 0.08 + 0.27 * (1 - night) : 0.05 + 0.07 * (1 - night);
        haze = air ? Math.min(0.85, 0.32 + 0.5 * rain) : 0;
    }

    /** Hauteur du Soleil vue d'un point a {@code km} au-dessus du sol : le Soleil s'y couche plus tard (depression de l'horizon). */
    public double sunVisibility(double km) {
        double dip = Math.acos(planetKm / (planetKm + Math.max(0, km)));
        double alt = sunAlt + dip;
        return smooth(-0.025, 0.025, alt);
    }

    /**
     * Soleil visible (0..1) en un point vu dans la direction unitaire {@code (dx, dy, dz)} et a {@code km} au-dessus du sol : l'ombre de
     * l'astre balaie les parties qui tournent du cote de la nuit (un anneau en orbite s'eteint par morceaux au crepuscule).
     */
    public double sunAt(double dx, double dy, double dz, double km) {
        double r = planetKm, h = r + Math.max(0, km);
        //distance reelle le long de la visee jusqu'a la hauteur km, puis verticale locale du point
        double s = -r * dy + Math.sqrt(r * r * dy * dy + h * h - r * r);
        double ux = s * dx / h, uy = (r + s * dy) / h, uz = s * dz / h;
        double sinAlt = ux * lx + uy * ly + uz * lz;
        double alt = Math.asin(Math.max(-1, Math.min(1, sinAlt)));
        return smooth(-0.025, 0.025, alt + Math.acos(r / h));
    }

    static double smooth(double a, double b, double v) {
        double x = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return x * x * (3 - 2 * x);
    }
}
