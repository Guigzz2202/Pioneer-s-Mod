package fr.pioneers.space;

import fr.pioneers.space.station.OrbitalStations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Le systeme solaire de la carte de navigation.
 * Trois systemes stellaires (voir {@link Sector}) : le systeme solaire, Alpha du Centaure (T12) et TRAPPIST-1 (T13).
 * Astres accessibles : Terre, Lune (T1) ; Mars, Phobos, Deimos (T2) ; Venus (T3) ; Mercure (T4) ; ceinture (T5) ;
 * lunes de Jupiter (T6), de Saturne (T7), d'Uranus (T8), de Neptune (T9) ; Pluton et Charon (T10).
 * Les etoiles n'ont pas de surface ; les geantes gazeuses non plus, mais on descend dans leurs nuages en fusee tier 11, jusqu'a un aerostat.
 * La carte generale ne montre que les astres principaux ; les satellites apparaissent en cliquant sur leur hote.
 * <p>
 * Distances et periodes "de jeu" (pas a l'echelle) : {@code orbit} en unites de carte, {@code period} en secondes reelles.
 */
public final class SolarSystem {

    /**
     * 1.0.7 (lot 11) : portee d'une fusee de tier 13 qui porte un propulseur a matiere noire ; tier exige par les trois systemes lointains
     * (pulsar, naine blanche, trou noir).
     */
    public static final int DARK_DRIVE_LEVEL = 14;

    private SolarSystem() {
    }

    public enum Body {
        //Astres principaux : orbit = rayon sur la carte generale (unites), size = rayon d'affichage (pixels)
        SUN(null, 0, 0, 1, 9, 0xFFC040, false),
        MERCURY("sun", 1.5F, 10, 0.6F, 2.6F, 0xA8A39A, false),
        VENUS("sun", 2.4F, 17, 2.4F, 4.2F, 0xE8C27A, false),
        EARTH("sun", 3.3F, 26, 0.75F, 4.4F, 0x3E7FD8, false),
        MARS("sun", 4.2F, 38, 4.1F, 3.6F, 0xC4552E, false),
        ASTEROID_BELT("sun", 5.2F, 0, 0, 0, 0x8C8070, false),
        JUPITER("sun", 6.5F, 80, 1.3F, 8, 0xD9A066, false),
        SATURN("sun", 7.8F, 120, 3.3F, 7, 0xE3C98A, true),
        URANUS("sun", 9.0F, 170, 5.0F, 5.2F, 0x9ADBE0, true),
        NEPTUNE("sun", 10.1F, 220, 2.0F, 5.2F, 0x4A6FE3, false),
        PLUTO("sun", 11.1F, 300, 0.4F, 2.4F, 0xC9A27E, false),
        //Satellites, visibles en cliquant sur leur hote : orbit = position (0..1) entre l'hote et le bord de la vue
        MOON("earth", 0.6F, 14, 0.2F, 6, 0xC9C9C9, false),
        PHOBOS("mars", 0.35F, 6, 1.5F, 4, 0x8B7D6B, false),
        DEIMOS("mars", 0.8F, 11, 3.6F, 3.4F, 0x9E9282, false),
        CERES("asteroid_belt", 0.62F, 60, 0.3F, 6.5F, 0x8E8A84, false),
        VESTA("asteroid_belt", 0.72F, 72, 2.2F, 5, 0xA49884, false),
        PALLAS("asteroid_belt", 0.8F, 84, 3.9F, 4.6F, 0x8A8E92, false),
        HYGIEA("asteroid_belt", 0.9F, 96, 5.3F, 4, 0x6F6A66, false),
        IO("jupiter", 0.28F, 6, 0.0F, 5, 0xE6D35A, false),
        EUROPA("jupiter", 0.48F, 9, 1.6F, 4.5F, 0xD8CBB0, false),
        GANYMEDE("jupiter", 0.7F, 14, 3.2F, 6.5F, 0xA59A8C, false),
        CALLISTO("jupiter", 0.92F, 20, 4.7F, 6, 0x6E655C, false),
        ENCELADUS("saturn", 0.4F, 7, 2.5F, 3.8F, 0xF2F6FA, false),
        TITAN("saturn", 0.85F, 16, 0.5F, 7, 0xD9A441, false),
        TRITON("neptune", 0.6F, 12, 1.0F, 5.5F, 0xC9B9A8, false),
        CHARON("pluto", 0.55F, 10, 2.0F, 5, 0xA9A39C, false),
        MIRANDA("uranus", 0.38F, 7, 3.0F, 4.2F, 0xA9A8A6, false),
        TITANIA("uranus", 0.78F, 15, 1.2F, 5.6F, 0xB8AFA6, false),
        //Alpha du Centaure (a 4,4 annees-lumiere) : le couple Rigil Kentaurus / Toliman, et Proxima au loin avec ses planetes
        CENTAURI_A(null, 0, 0, 0, 9, 0xFFE6A8, false),
        CENTAURI_B("centauri_a", 3.4F, 90, 2.1F, 7, 0xFFB25C, false),
        TOLIMAN_B("centauri_a", 1.7F, 30, 0.7F, 4.6F, 0x6FA89A, false),
        PROXIMA("centauri_a", 9.6F, 600, 4.0F, 5, 0xFF5A3C, false),
        PROXIMA_B("proxima", 0.5F, 9, 1.0F, 5.2F, 0x9E6B55, false),
        PROXIMA_D("proxima", 0.28F, 5, 3.3F, 3.6F, 0xC9703E, false),
        //TRAPPIST-1 (a 40 annees-lumiere) : sept planetes serrees autour d'une naine rouge ultra-froide
        TRAPPIST_1(null, 0, 0, 0, 7, 0xFF6A4A, false),
        TRAPPIST_1B("trappist_1", 1.4F, 6, 0.4F, 4.0F, 0xB8553A, false),
        TRAPPIST_1C("trappist_1", 2.1F, 9, 2.6F, 4.0F, 0xA88A74, false),
        TRAPPIST_1D("trappist_1", 2.9F, 13, 4.4F, 3.4F, 0x3A7FBF, false),
        TRAPPIST_1E("trappist_1", 3.8F, 18, 1.5F, 3.8F, 0x4E9A6A, false),
        TRAPPIST_1F("trappist_1", 4.8F, 25, 5.6F, 4.2F, 0xC8DCEB, false),
        TRAPPIST_1G("trappist_1", 5.9F, 33, 3.2F, 4.4F, 0x9FB4C8, false),
        TRAPPIST_1H("trappist_1", 7.1F, 43, 0.9F, 3.2F, 0x8C8FA0, false),
        //1.0.7 (lot 11) : les trois nouveaux systemes, chacun autour d'un astre singulier (T:au_dela/far_systems.py)
        LICH(null, 0, 0, 0, 4, 0xCFE6FF, false),
        DRAUGR("lich", 0.9F, 6, 0.4F, 2.4F, 0x8C90A0, false),
        POLTERGEIST("lich", 1.8F, 14, 2.3F, 4.4F, 0x7A70B0, false),
        PHOBETOR("lich", 2.7F, 22, 4.4F, 4.2F, 0xA8C4DC, false),
        VAN_MAANEN(null, 0, 0, 0, 3.6F, 0xF4F8FF, false),
        VAN_MAANEN_B("van_maanen", 1.1F, 8, 1.2F, 3.6F, 0xD6E4F0, false),
        VAN_MAANEN_C("van_maanen", 2.0F, 15, 3.3F, 2.8F, 0x8E7E70, false),
        VAN_MAANEN_D("van_maanen", 3.2F, 26, 5.0F, 4.0F, 0xBAD6E6, false),
        GAIA_BH1(null, 0, 0, 0, 6, 0x2A1840, false),
        GAIA_BH1_B("gaia_bh1", 1.3F, 10, 0.9F, 3.8F, 0x9A6AD0, false),
        GAIA_BH1_C("gaia_bh1", 2.2F, 17, 2.8F, 3.4F, 0xC0603A, false),
        GAIA_BH1_D("gaia_bh1", 3.4F, 30, 4.7F, 4.2F, 0x4A7AB8, false),
        //1.0.7 : mondes habitables des systemes lointains (relief et biomes de la Terre ; T:au_dela/far_systems.py HABITABLE)
        FYLGJA("lich", 3.6F, 30, 1.1F, 4.6F, 0x5E9A6A, false),
        VAN_MAANEN_E("van_maanen", 4.3F, 36, 2.2F, 4.4F, 0x4E8EC0, false),
        GAIA_BH1_E("gaia_bh1", 4.6F, 44, 0.3F, 4.8F, 0x6AA86A, false);

        /** Tous les astres, dans l'ordre (liste partagee : evite la copie de tableau de {@code values()} dans les boucles frequentes). */
        public static final List<Body> ALL = List.of(values());

        /** Astre d'indice donne (ordinal), ou null hors limites. */
        @Nullable
        public static Body byIndex(int index) {
            return index < 0 || index >= ALL.size() ? null : ALL.get(index);
        }

        private final String parent;
        /** Rayon de l'orbite (unites de carte : autour du Soleil, ou autour de la planete pour une lune). */
        public final float orbit;
        /** Periode de revolution en secondes. */
        public final float period;
        /** Phase initiale (radians). */
        public final float phase;
        /** Taille d'affichage (pixels). */
        public final float size;
        public final int color;
        public final boolean ringed;

        Body(@Nullable String parent, float orbit, float period, float phase, float size, int color, boolean ringed) {
            this.parent = parent;
            this.orbit = orbit;
            this.period = period;
            this.phase = phase;
            this.size = size;
            this.color = color;
            this.ringed = ringed;
        }

        @Nullable
        public Body getParent() {
            return parent == null ? null : Body.valueOf(parent.toUpperCase());
        }

        /** Systeme stellaire de l'astre. */
        public Sector sector() {
            return Sector.of(this);
        }

        /** Etoile (centre d'un systeme ou etoile compagne). */
        public boolean isStar() {
            return this == SUN || this == CENTAURI_A || this == CENTAURI_B || this == PROXIMA || this == TRAPPIST_1 || this == LICH
                   || this == VAN_MAANEN || this == GAIA_BH1;
        }

        /** Etoile centrale de son systeme (racine de la carte). */
        public boolean isRoot() {
            return getParent() == null;
        }

        /** Geante gazeuse : pas de sol, on descend dans ses nuages (fusee tier 11, combinaison atmospherique). */
        public boolean isGasGiant() {
            return this == JUPITER || this == SATURN || this == URANUS || this == NEPTUNE;
        }

        /** Satellite (ou petit corps de la ceinture, ou planete d'une etoile compagne) : visible dans la vue detaillee de son hote. */
        public boolean isMoon() {
            Body p = getParent();
            return p != null && !p.isRoot();
        }

        /** Astre affiche sur la carte generale de son systeme. */
        public boolean isPrimary() {
            Body p = getParent();
            return p == null || p.isRoot();
        }

        public List<Body> getChildren() {
            List<Body> children = new ArrayList<>();
            if (!isRoot()) {
                for (Body body : values()) {
                    if (body.getParent() == this) {
                        children.add(body);
                    }
                }
            }
            return children;
        }

        public boolean hasChildren() {
            return !getChildren().isEmpty();
        }

        /** L'astre principal (sur la carte generale) qui contient cet astre. */
        public Body getPrimary() {
            return isMoon() ? getParent() : this;
        }

        /** Gravite de surface (g terrestres), pour la fiche de la carte. */
        public float surfaceGravity() {
            return switch (this) {
                case SUN -> 28F;
                case MERCURY, MARS -> 0.38F;
                case VENUS -> 0.9F;
                case EARTH -> 1F;
                case MOON -> 0.17F;
                case PHOBOS, DEIMOS -> 0.001F;
                case CERES -> 0.03F;
                case JUPITER -> 2.53F;
                case IO -> 0.18F;
                case EUROPA -> 0.13F;
                case GANYMEDE -> 0.15F;
                case CALLISTO -> 0.13F;
                case SATURN -> 1.07F;
                case TITAN -> 0.14F;
                case ENCELADUS -> 0.01F;
                case URANUS -> 0.89F;
                case NEPTUNE -> 1.14F;
                case TRITON -> 0.08F;
                case PLUTO -> 0.06F;
                case ASTEROID_BELT -> 0;
                case VESTA -> 0.025F;
                case PALLAS -> 0.02F;
                case HYGIEA -> 0.01F;
                case CHARON -> 0.03F;
                case MIRANDA -> 0.008F;
                case TITANIA -> 0.04F;
                case CENTAURI_A -> 30F;
                case CENTAURI_B -> 32F;
                case PROXIMA -> 50F;
                case TRAPPIST_1 -> 60F;
                case TOLIMAN_B -> 1.42F;
                case PROXIMA_B -> 1.1F;
                case PROXIMA_D -> 0.45F;
                case TRAPPIST_1B -> 1.1F;
                case TRAPPIST_1C -> 1.1F;
                case TRAPPIST_1D -> 0.62F;
                case TRAPPIST_1E -> 0.82F;
                case TRAPPIST_1F -> 0.95F;
                case TRAPPIST_1G -> 1.03F;
                case TRAPPIST_1H -> 0.57F;
                case LICH -> 80F;
                case VAN_MAANEN -> 70F;
                case GAIA_BH1 -> 99F;
                case DRAUGR -> 0.32F;
                case POLTERGEIST -> 1.6F;
                case PHOBETOR -> 1.4F;
                case VAN_MAANEN_B -> 1.2F;
                case VAN_MAANEN_C -> 0.7F;
                case VAN_MAANEN_D -> 0.9F;
                case GAIA_BH1_B -> 1.1F;
                case GAIA_BH1_C -> 1.3F;
                case GAIA_BH1_D -> 0.95F;
                case FYLGJA -> 1.0F;
                case VAN_MAANEN_E -> 0.95F;
                case GAIA_BH1_E -> 1.05F;
            };
        }

        /** Categorie affichee : etoile, naine rouge, geante gazeuse, planete, exoplanete, lune, planete naine, asteroide. */
        public String getKindKey() {
            if (isStar()) {
                return switch (this) {
                    case PROXIMA, TRAPPIST_1 -> "solar.pioneers.kind.red_dwarf";
                    case LICH -> "solar.pioneers.kind.neutron_star";
                    case VAN_MAANEN -> "solar.pioneers.kind.white_dwarf";
                    case GAIA_BH1 -> "solar.pioneers.kind.black_hole";
                    default -> "solar.pioneers.kind.star";
                };
            } else if (isGasGiant()) {
                return "solar.pioneers.kind.gas_giant";
            } else if (this == ASTEROID_BELT) {
                return "solar.pioneers.kind.belt";
            } else if (getParent() == ASTEROID_BELT && this != CERES) {
                return "solar.pioneers.kind.asteroid";
            } else if (this == CERES || this == PLUTO) {
                return "solar.pioneers.kind.dwarf";
            } else if (sector() != Sector.SOL) {
                return "solar.pioneers.kind.exoplanet";
            } else if (isMoon()) {
                return "solar.pioneers.kind.moon";
            }
            return "solar.pioneers.kind.planet";
        }

        public String getTranslationKey() {
            return "solar.pioneers." + name().toLowerCase();
        }

        /** Dimension associee (null = pas encore accessible). */
        @Nullable
        public ResourceKey<Level> dimension() {
            return switch (this) {
                case EARTH -> Level.OVERWORLD;
                case MOON -> PiDimensions.MOON;
                case MARS -> PiDimensions.MARS;
                case VENUS -> PiDimensions.VENUS;
                case MERCURY -> PiDimensions.MERCURY;
                case PHOBOS -> PiDimensions.PHOBOS;
                case DEIMOS -> PiDimensions.DEIMOS;
                case CERES -> PiDimensions.CERES;
                case VESTA -> PiDimensions.VESTA;
                case PALLAS -> PiDimensions.PALLAS;
                case HYGIEA -> PiDimensions.HYGIEA;
                case IO -> PiDimensions.IO;
                case EUROPA -> PiDimensions.EUROPA;
                case GANYMEDE -> PiDimensions.GANYMEDE;
                case CALLISTO -> PiDimensions.CALLISTO;
                case ENCELADUS -> PiDimensions.ENCELADUS;
                case TITAN -> PiDimensions.TITAN;
                case MIRANDA -> PiDimensions.MIRANDA;
                case TITANIA -> PiDimensions.TITANIA;
                case TRITON -> PiDimensions.TRITON;
                case PLUTO -> PiDimensions.PLUTO;
                case CHARON -> PiDimensions.CHARON;
                case JUPITER -> PiDimensions.JUPITER;
                case SATURN -> PiDimensions.SATURN;
                case URANUS -> PiDimensions.URANUS;
                case NEPTUNE -> PiDimensions.NEPTUNE;
                case TOLIMAN_B -> PiDimensions.TOLIMAN_B;
                case PROXIMA_B -> PiDimensions.PROXIMA_B;
                case PROXIMA_D -> PiDimensions.PROXIMA_D;
                case TRAPPIST_1B -> PiDimensions.TRAPPIST_1B;
                case TRAPPIST_1D -> PiDimensions.TRAPPIST_1D;
                case TRAPPIST_1E -> PiDimensions.TRAPPIST_1E;
                case TRAPPIST_1F -> PiDimensions.TRAPPIST_1F;
                case DRAUGR -> PiDimensions.DRAUGR;
                case POLTERGEIST -> PiDimensions.POLTERGEIST;
                case PHOBETOR -> PiDimensions.PHOBETOR;
                case VAN_MAANEN_B -> PiDimensions.VAN_MAANEN_B;
                case VAN_MAANEN_C -> PiDimensions.VAN_MAANEN_C;
                case VAN_MAANEN_D -> PiDimensions.VAN_MAANEN_D;
                case GAIA_BH1_B -> PiDimensions.GAIA_BH1_B;
                case GAIA_BH1_C -> PiDimensions.GAIA_BH1_C;
                case GAIA_BH1_D -> PiDimensions.GAIA_BH1_D;
                case FYLGJA -> PiDimensions.FYLGJA;
                case VAN_MAANEN_E -> PiDimensions.VAN_MAANEN_E;
                case GAIA_BH1_E -> PiDimensions.GAIA_BH1_E;
                //Les etoiles n'ont pas de surface ; TRAPPIST-1c, g et h ne s'etudient qu'en orbite
                default -> null;
            };
        }

        /** Tier de fusee necessaire pour atteindre (ou quitter) cet astre. */
        public int requiredTier() {
            return switch (this) {
                case MARS, PHOBOS, DEIMOS -> 2;
                case VENUS -> 3;
                case MERCURY -> 4;
                case ASTEROID_BELT, CERES, VESTA, PALLAS, HYGIEA -> 5;
                case JUPITER, SATURN, URANUS, NEPTUNE -> 11;
                //Stations au plus pres du Soleil : bouclier thermique du tier 11
                case SUN -> 11;
                case IO, EUROPA, GANYMEDE, CALLISTO -> 6;
                case TITAN, ENCELADUS -> 7;
                case MIRANDA, TITANIA -> 8;
                case TRITON -> 9;
                case PLUTO, CHARON -> 10;
                default -> switch (sector()) {
                    case SOL -> 1;
                    case ALPHA_CENTAURI -> 12;
                    case TRAPPIST -> 13;
                    //1.0.7 (lot 11) : propulsion a matiere noire (fusee de tier 13 et son propulseur, J:space/module/RocketModule)
                    case LICH, VAN_MAANEN, GAIA_BH1 -> DARK_DRIVE_LEVEL;
                };
            };
        }

        /**
         * Accessible depuis {@code from} (et, dans la dimension orbitale, depuis la station situee en {@code x}) avec une fusee de ce tier :
         * il faut le tier de l'astre de depart et celui de l'arrivee.
         */
        public boolean isReachableFrom(ResourceKey<Level> from, double x, @Nullable RocketTier tier) {
            return tier != null && isReachableFrom(from, x, tier.level);
        }

        /** 1.0.7 (lot 11) : meme regle pour une portee donnee ({@code RocketEntity.reachLevel} : tier, plus un avec la matiere noire). */
        public boolean isReachableFrom(ResourceKey<Level> from, double x, int reach) {
            ResourceKey<Level> dim = dimension();
            Body origin = origin(from, x);
            return dim != null && dim != from && origin != null && Math.max(requiredTier(), origin.requiredTier()) <= reach;
        }

        public boolean isHere(ResourceKey<Level> from) {
            return dimension() == from;
        }
    }

    /**
     * Les systemes stellaires de la carte galactique. Position sur la carte galactique (annees-lumiere, projetees), etoile centrale et tier
     * de fusee necessaire pour s'y rendre.
     */
    public enum Sector {
        SOL(0, 0, 0xFFC040, 1),
        ALPHA_CENTAURI(-3.1F, 2.9F, 0xFFE6A8, 12),
        TRAPPIST(16, -11, 0xFF6A4A, 13),
        //1.0.7 (lot 11) : pulsar, naine blanche, trou noir ; reveles par l'Observatoire du Halo, atteints a la matiere noire
        LICH(-24, -17, 0xBFDCFF, DARK_DRIVE_LEVEL),
        VAN_MAANEN(7.5F, 8, 0xF2F6FF, DARK_DRIVE_LEVEL),
        GAIA_BH1(26, 18, 0x8A5CFF, DARK_DRIVE_LEVEL);

        public final float galacticX, galacticY;
        public final int color;
        public final int requiredTier;

        Sector(float galacticX, float galacticY, int color, int requiredTier) {
            this.galacticX = galacticX;
            this.galacticY = galacticY;
            this.color = color;
            this.requiredTier = requiredTier;
        }

        public Body root() {
            return switch (this) {
                case SOL -> Body.SUN;
                case ALPHA_CENTAURI -> Body.CENTAURI_A;
                case TRAPPIST -> Body.TRAPPIST_1;
                case LICH -> Body.LICH;
                case VAN_MAANEN -> Body.VAN_MAANEN;
                case GAIA_BH1 -> Body.GAIA_BH1;
            };
        }

        /** Distance au Soleil (annees-lumiere), pour l'affichage. */
        public float lightYears() {
            return switch (this) {
                case SOL -> 0;
                case ALPHA_CENTAURI -> 4.37F;
                case TRAPPIST -> 40.7F;
                case LICH -> 2300F;
                case VAN_MAANEN -> 14.1F;
                case GAIA_BH1 -> 1560F;
            };
        }

        public String getTranslationKey() {
            return "solar.pioneers.sector." + name().toLowerCase();
        }

        /** 1.0.7 (lot 11) : systeme lointain (revele par l'Observatoire du Halo, atteint a la matiere noire). */
        public boolean far() {
            return requiredTier >= DARK_DRIVE_LEVEL;
        }

        public static Sector of(Body body) {
            Body b = body;
            while (b.getParent() != null) {
                b = b.getParent();
            }
            return switch (b) {
                case CENTAURI_A -> ALPHA_CENTAURI;
                case TRAPPIST_1 -> TRAPPIST;
                case LICH -> Sector.LICH;
                case VAN_MAANEN -> Sector.VAN_MAANEN;
                case GAIA_BH1 -> Sector.GAIA_BH1;
                default -> SOL;
            };
        }
    }

    @Nullable
    public static Body fromDimension(ResourceKey<Level> dimension) {
        //Appele tres souvent (gravite de chaque entite, environnement...) : table construite une fois
        return BY_DIMENSION.get(dimension);
    }

    /** Astre de chaque dimension (les astres sans dimension propre, comme les etoiles, n'y figurent pas s'ils n'en ont pas). */
    private static final Map<ResourceKey<Level>, Body> BY_DIMENSION = buildDimensionTable();

    private static Map<ResourceKey<Level>, Body> buildDimensionTable() {
        Map<ResourceKey<Level>, Body> table = new HashMap<>();
        for (Body body : Body.ALL) {
            ResourceKey<Level> dimension = body.dimension();
            if (dimension != null) {
                //Premier astre declare pour une dimension (meme ordre que l'ancienne recherche lineaire)
                table.putIfAbsent(dimension, body);
            }
        }
        return Collections.unmodifiableMap(table);
    }

    /** Astre de depart : celui de la dimension, ou celui que survole la station orbitale ou l'on se trouve. */
    @Nullable
    public static Body origin(ResourceKey<Level> dimension, double x) {
        return dimension == PiDimensions.ORBIT ? OrbitalStations.bodyAt(x) : fromDimension(dimension);
    }

    public static List<Body> reachableFrom(ResourceKey<Level> from, double x, RocketTier tier) {
        return reachableFrom(from, x, tier.level);
    }

    /** 1.0.7 (lot 11) : astres atteignables avec une portee donnee ({@code RocketEntity.reachLevel}). */
    public static List<Body> reachableFrom(ResourceKey<Level> from, double x, int reach) {
        List<Body> list = new ArrayList<>();
        for (Body body : Body.ALL) {
            if (body.isReachableFrom(from, x, reach)) {
                list.add(body);
            }
        }
        return list;
    }
}
