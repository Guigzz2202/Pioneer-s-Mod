package fr.pioneers.client.naval;

import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.client.rail.RailMesh;
import fr.pioneers.naval.ShipKind;
import java.util.Objects;

/**
 * Maillages des navires (repere en px : x en travers, z le long du navire, avant vers +z, y = 0 a la ligne de flottaison, origine au
 * milieu).
 * <p>
 * 0.20.13 (refonte, demande de Guillaume : « trop plats et trop larges ») : coques elancees (environ 4 fois plus longues que larges),
 * franc-bord haut ({@link ShipKind#freeboard}), tonture marquee vers l'etrave, etrave inclinee et bulbe (MKIII-IV), voute arriere relevee,
 * carene arrondie (bouchain), gaillard d'avant releve (MKII a MKIV), pavois, bande du tier sous le pont, hublots. Superstructures etagees :
 * MKI vapeur a aubes (rouf a promenade, timonerie, cheminees jumelles), MKII caboteur diesel (chateau arriere, cheminee inclinee, canots),
 * MKIII electrique (chateau vitre en bandeaux, tour des batteries), MKIV nucleaire (grand chateau, dome du reacteur, event, mat radar).
 * Pont selon la ressource (conteneurs, collecteurs, spheres de gaz, batteries, bouteilles d'air, baies de serveurs, salons des passagers).
 * Groupes tournants : roues a aubes (MKI), helices et radars.
 * <p>
 * La coque est faite de troncons fusionnes (une boite par bande tant que la section ne change pas) : peu de faces au milieu du navire.
 */
public final class ShipModels {

    private ShipModels() {
    }

    // ------------------------------------------------------------------ dimensions

    static float hl(int tier) {
        return ShipKind.length(tier) * 8F;
    }

    static float beamHalf(int tier) {
        return ShipKind.beam(tier) * 8F - 0.5F;
    }

    /** Hauteur du pont principal (px au-dessus de l'eau). */
    public static float deck(int tier) {
        return ShipKind.freeboard(tier);
    }

    /** Debut du gaillard d'avant (fraction de la demi-longueur) ; MKI : pas de gaillard. */
    static float forecastleStart(int tier) {
        return tier == 0 ? 2 : 0.8F;
    }

    static float forecastleHeight(int tier) {
        return tier == 0 ? 0 : 3 + tier * 0.5F;
    }

    /** Demi-largeur de la coque a la position z (px) : etrave fine, arriere en tableau (MKI : poupe ronde). */
    public static float halfWidth(int tier, float z) {
        float hl = hl(tier), hw = beamHalf(tier);
        float f = z / hl;
        float bow = tier == 0 ? 0.45F : 0.38F;
        if (f > bow) {
            float t = (1 - f) / (1 - bow);
            return hw * Math.max(0.04F, (float) Math.pow(Math.max(0, t), 0.78));
        }
        float stern = tier == 0 ? -0.75F : -0.82F;
        if (f < stern) {
            float t = (f + 1) / (1 + stern);
            return hw * (tier == 0 ? 0.42F + 0.58F * (float) Math.sqrt(Math.max(0, t)) : 0.74F + 0.26F * (float) Math.sqrt(Math.max(0, t)));
        }
        return hw;
    }

    /** Tonture : le pont se releve vers l'etrave, un peu vers l'arriere. */
    static float sheer(int tier, float z) {
        float f = z / hl(tier);
        if (f > 0.3F) {
            float k = (f - 0.3F) / 0.7F;
            return k * k * (3 + tier * 1.5F);
        }
        if (f < -0.85F) {
            return (-0.85F - f) / 0.15F * 1.2F;
        }
        return 0;
    }

    /** Dessus de la coque (pont, gaillard compris). */
    static float top(int tier, float z) {
        float f = z / hl(tier);
        return deck(tier) + sheer(tier, z) + (f >= forecastleStart(tier) ? forecastleHeight(tier) : 0);
    }

    /** Dessous de la coque : quille, etrave inclinee a l'avant, voute relevee a l'arriere. */
    static float bottom(int tier, float z) {
        float f = z / hl(tier), draft = ShipKind.draft(tier);
        if (f > 0.76F) {
            float k = (f - 0.76F) / 0.24F;
            return -draft + k * (draft + deck(tier) * 0.25F);
        }
        if (f < -0.86F) {
            float k = (-0.86F - f) / 0.14F;
            return -draft + k * draft * 0.65F;
        }
        return -draft;
    }

    static BigMat tierMat(int t) {
        return switch (t) {
            case 1 -> BigMat.R_TIER2;
            case 2 -> BigMat.R_TIER3;
            case 3 -> BigMat.R_TIER4;
            default -> BigMat.R_TIER1;
        };
    }

    static BigMat livery(int t) {
        return switch (t) {
            case 1 -> BigMat.R_LIVERY2;
            case 2 -> BigMat.R_LIVERY3;
            case 3 -> BigMat.R_LIVERY4;
            default -> BigMat.R_LIVERY1;
        };
    }

    static BigMat fam(ShipKind k) {
        return switch (k) {
            case TANKER -> BigMat.R_FAM_FLUID;
            case GAS_CARRIER -> BigMat.R_FAM_GAS;
            case POWER_BARGE -> BigMat.R_FAM_ENERGY;
            case AIR_CARRIER -> BigMat.R_FAM_PRESSURE;
            case DATA_SHIP -> BigMat.R_FAM_DATA;
            default -> BigMat.R_FAM_ITEM;
        };
    }

    /** Zones libres du pont pour la cargaison ({z0, z1} par zone) : entre le gaillard et le chateau ; MKI : de part et d'autre du rouf. */
    static float[][] cargoZones(int tier) {
        float hl = hl(tier);
        return switch (tier) {
            case 0 -> new float[][]{{-hl + 7, -27}, {23, hl - 9}};
            case 1 -> new float[][]{{-hl + 34, hl * 0.8F - 2}};
            case 2 -> new float[][]{{-hl + 42, hl * 0.8F - 2}};
            default -> new float[][]{{-hl + 74, hl * 0.8F - 2}};
        };
    }

    /** Navire complet ; {@code fill} 0..1 (cargaison visible), {@code flags} bit 0 : feux allumes. */
    public static RailMesh build(ShipKind kind, int tier, float fill, int flags) {
        RailMesh m = new RailMesh();
        boolean lit = (flags & 1) != 0;
        hull(m, tier);
        switch (tier) {
            case 0 -> paddleSteamer(m, lit);
            case 1 -> diesel(m, lit);
            case 2 -> electric(m, lit);
            default -> nuclear(m, lit);
        }
        float[][] zones = cargoZones(tier);
        int holds = Math.max(1, ShipKind.holds(tier) / zones.length);
        for (int i = 0; i < zones.length; i++) {
            cargo(m, kind, tier, zones[i][0], zones[i][1], fill, holds, i == zones.length - 1);
        }
        hullLogo(m, kind, tier);
        return m;
    }

    // ------------------------------------------------------------------ coque

    private record Section(float hw, float bot, float top) {

        static Section of(int tier, float z) {
            return new Section(q(halfWidth(tier, z)), q(bottom(tier, z)), q(ShipModels.top(tier, z)));
        }

        static float q(float v) {
            return Math.round(v * 4) / 4F;
        }
    }

    static void hull(RailMesh m, int tier) {
        float hl = hl(tier);
        Section cur = null;
        float start = -hl;
        for (float z = -hl; z < hl - 0.01F; z += 1) {
            Section s = Section.of(tier, z + 0.5F);
            if (!Objects.equals(cur, s)) {
                if (cur != null) {
                    section(m, tier, cur, start, z);
                }
                cur = s;
                start = z;
            }
        }
        section(m, tier, cur, start, hl);
        float d = deck(tier), draft = ShipKind.draft(tier);
        //Bulbe d'etrave (MKIII-IV) : fuseau sous l'eau en avant de l'etrave
        if (tier >= 2) {
            float by = -draft * 0.55F, br = draft * 0.32F;
            float b0 = hl * 0.78F, b1 = hl * 0.9F, step = (b1 - b0) / 6;
            for (int i = 0; i < 6; i++) {
                float z0 = b0 + i * step, r = br * (float) Math.sqrt(Math.max(0.12, 1 - Math.pow((i - 1.5) / 4.6, 2)));
                m.cyl('z', 0, by, r, z0, z0 + step, BigMat.R_RED, 10);
            }
        }
        //Ecubiers et ancres sur les joues de l'avant
        float az = hl * 0.86F;
        for (float s : new float[]{-1, 1}) {
            float x = s * (halfWidth(tier, az) + 0.05F), y = top(tier, az) - 3.2F;
            m.box(x - 0.3F, y - 0.6F, az - 0.8F, x + 0.3F, y + 0.6F, az + 0.8F, BigMat.R_DARK);
            m.box(x - 0.25F, y - 3.2F, az - 0.2F, x + 0.25F, y - 0.6F, az + 0.2F, BigMat.R_STEEL);
            m.box(x - 0.25F, y - 3.6F, az - 1.1F, x + 0.25F, y - 3.0F, az + 1.1F, BigMat.R_STEEL);
        }
        //Gouvernail
        float rz = -hl - 0.2F;
        m.box(-0.45F, -draft + 0.6F, rz - 2.6F, 0.45F, bottom(tier, -hl + 1) - 0.2F, rz + 0.4F, BigMat.R_DARK);
        //Bittes d'amarrage, avant et arriere
        for (float z : new float[]{-hl + 5, hl * 0.72F}) {
            for (float s : new float[]{-1, 1}) {
                float x = s * (halfWidth(tier, z) - 2.4F), y = top(tier, z);
                m.cyl('y', x, z - 0.9F, 0.6F, y - 0.1F, y + 1.3F, BigMat.R_DARK, 8);
                m.cyl('y', x, z + 0.9F, 0.6F, y - 0.1F, y + 1.3F, BigMat.R_DARK, 8);
            }
        }
        //Mat de pavillon a la poupe, guindeau sur le gaillard
        m.box(-0.2F, top(tier, -hl + 2), -hl + 1.6F, 0.2F, top(tier, -hl + 2) + 7 + tier, -hl + 2.0F, BigMat.R_STEEL);
        m.box(0.2F, top(tier, -hl + 2) + 4.4F + tier, -hl + 1.7F, 2.8F, top(tier, -hl + 2) + 6.6F + tier, -hl + 1.9F, tierMat(tier));
        if (tier >= 1) {
            float wz = hl * 0.88F, wy = top(tier, wz);
            m.box(-2.4F, wy, wz - 1.2F, 2.4F, wy + 1.4F, wz + 1.2F, BigMat.R_DARK);
            m.cyl('x', wy + 1.2F, wz, 0.9F, -2.8F, 2.8F, BigMat.R_STEEL, 8);
        }
        //Hublots le long du chateau (sous le pont)
        float[] span = castleSpan(tier);
        for (float s : new float[]{-1, 1}) {
            for (float z = span[0] + 2; z < span[1] - 1; z += 3.2F) {
                float x = s * halfWidth(tier, z), y = d * 0.55F;
                m.box(x - 0.06F, y, z, x + 0.06F, y + 0.9F, z + 0.9F, BigMat.R_GLASS_D);
            }
        }
    }

    /** Etendue du chateau (z0, z1) : hublots, et gene la cargaison. */
    static float[] castleSpan(int tier) {
        float hl = hl(tier);
        return switch (tier) {
            case 0 -> new float[]{-24, 20};
            case 1 -> new float[]{-hl + 4, -hl + 30};
            case 2 -> new float[]{-hl + 4, -hl + 38};
            default -> new float[]{-hl + 26, -hl + 70};
        };
    }

    /** Un troncon de coque : carene rouge arrondie, liston noir, flanc a la livree, bande du tier, pont, pavois. */
    static void section(RailMesh m, int tier, Section s, float z0, float z1) {
        float hw = s.hw, bot = s.bot, top = s.top;
        if (hw < 0.3F) {
            return;
        }
        BigMat liv = livery(tier);
        //Carene (oeuvres vives) : fond plus etroit, puis bouchain
        if (bot < -1) {
            float k = Math.min(-1, bot + 2);
            m.box(-hw * 0.72F, bot, z0, hw * 0.72F, k, z1, BigMat.R_RED);
            if (k < -1) {
                m.box(-hw * 0.93F, k, z0, hw * 0.93F, -1, z1, BigMat.R_RED);
            }
        }
        //Liston de flottaison
        float a = Math.max(bot, -1);
        if (a < 0.8F) {
            m.box(-hw, a, z0, hw, 0.8F, z1, BigMat.R_BLACK);
        }
        //Flanc, bande du tier, haut de coque
        float b = Math.max(bot, 0.8F);
        float stripe = top - 2.2F;
        if (b < stripe) {
            m.box(-hw, b, z0, hw, stripe, z1, liv);
        }
        m.box(-hw, Math.max(b, stripe), z0, hw, top - 1.4F, z1, tierMat(tier));
        m.box(-hw, Math.max(b, top - 1.4F), z0, hw, top - 0.4F, z1, liv);
        //Pont (bordage de bois en MKI, acier ensuite) entre les pavois
        float inner = Math.max(0.1F, hw - 0.6F);
        m.box(-inner, top - 0.4F, z0, inner, top, z1, tier == 0 ? BigMat.R_PLANKS : BigMat.R_STEEL);
        //Pavois des deux bords, lisse claire
        if (hw > 1.2F) {
            for (float sg : new float[]{-1, 1}) {
                float xa = sg < 0 ? -hw : inner, xb = sg < 0 ? -inner : hw;
                m.box(xa, top - 0.4F, z0, xb, top + 1.3F, z1, liv);
                m.box(xa - 0.05F, top + 1.3F, z0, xb + 0.05F, top + 1.6F, z1, BigMat.R_STEEL_L);
            }
        }
    }

    /** Plaque du logo de la ressource sur les deux joues de l'avant. */
    static void hullLogo(RailMesh m, ShipKind kind, int tier) {
        BigMat fam = kind == ShipKind.FERRY ? BigMat.R_LAMP : fam(kind);
        float hl = hl(tier), s = deck(tier) * 0.42F, z = hl * 0.58F, y = deck(tier) * 0.2F;
        String[] icon = iconOf(kind);
        for (float side : new float[]{-1, 1}) {
            float hw = Math.min(halfWidth(tier, z), halfWidth(tier, z + s));
            float x0 = side > 0 ? hw : -hw - 0.2F;
            m.box(x0, y, z, x0 + 0.2F, y + s, z + s, BigMat.R_DARK);
            icon(m, fam, side > 0 ? hw + 0.15F : -hw - 0.3F, side > 0 ? hw + 0.3F : -hw - 0.15F, y, z, s, icon);
        }
    }

    static final String[] ICON_PERSON = {"..##..", "..##..", ".####.", "#.##.#", "..##..", ".#..#."};

    static String[] iconOf(ShipKind k) {
        return switch (k) {
            case FERRY -> ICON_PERSON;
            case TANKER -> new String[]{"..#...", "..##..", ".####.", "######", "######", ".####."};
            case GAS_CARRIER -> new String[]{"......", "..##..", ".####.", "######", "######", "......"};
            case POWER_BARGE -> new String[]{"...##.", "..##..", ".####.", "..##..", ".##...", ".#...."};
            case AIR_CARRIER -> new String[]{".####.", "#....#", "#..#.#", "#.#..#", "#....#", ".####."};
            case DATA_SHIP -> new String[]{".....#", "...#.#", "...#.#", ".#.#.#", ".#.#.#", "######"};
            default -> new String[]{".####.", "#....#", "######", "#.##.#", "#....#", "######"};
        };
    }

    /** Pictogramme 6 x 6 sur une plaque de {@code size} px, de xa a xb en epaisseur. */
    static void icon(RailMesh m, BigMat mat, float xa, float xb, float y, float z, float size, String[] rows) {
        float p = size / 6F;
        for (int r = 0; r < 6; r++) {
            String row = rows[r];
            for (int c = 0; c < 6; c++) {
                if (row.charAt(c) == '#') {
                    float zz = z + size - (c + 1) * p, yy = y + size - (r + 1) * p;
                    m.box(xa, yy, zz, xb, yy + p, zz + p, mat);
                }
            }
        }
    }

    // ------------------------------------------------------------------ elements de superstructure

    /** Fenetres separees sur les deux flancs d'un rouf. */
    static void windows(RailMesh m, float x0, float x1, float y0, float y1, float z0, float z1, float pitch, boolean lit) {
        BigMat glass = lit ? BigMat.R_LAMP : BigMat.R_GLASS_D;
        for (float z = z0 + 0.8F; z + pitch * 0.6F < z1 - 0.4F; z += pitch) {
            m.box(x0 - 0.06F, y0, z, x0 + 0.06F, y1, z + pitch * 0.6F, glass);
            m.box(x1 - 0.06F, y0, z, x1 + 0.06F, y1, z + pitch * 0.6F, glass);
        }
    }

    /**
     * Rouf (un pont de superstructure) : blanc, liseret du tier autour du toit ; fenetres separees ({@code band} faux) ou bandeau vitre
     * continu tout autour (style moderne).
     */
    static void house(RailMesh m, int tier, float z0, float z1, float y, float h, float w, boolean band, boolean lit) {
        m.box(-w, y, z0, w, y + h, z1, BigMat.R_WHITE);
        trim(m, tier, -w, w, y + h - 0.45F, y + h + 0.15F, z0, z1);
        float g0 = y + h * 0.38F, g1 = y + h * 0.76F;
        BigMat glass = lit ? BigMat.R_LAMP : BigMat.R_GLASS_D;
        if (band) {
            m.box(-w - 0.07F, g0, z0 + 0.6F, w + 0.07F, g1, z1 - 0.6F, glass);
            m.box(-w + 0.6F, g0, z0 - 0.07F, w - 0.6F, g1, z1 + 0.07F, glass);
            //Montants du vitrage
            for (float z = z0 + 3; z < z1 - 1; z += 3.2F) {
                m.box(-w - 0.12F, g0, z, w + 0.12F, g1, z + 0.35F, BigMat.R_WHITE);
            }
        } else {
            windows(m, -w, w, g0, g1, z0, z1, 2.4F, lit);
        }
    }

    /** Timonerie : vitrage panoramique sur l'avant et les cotes, ailerons de passerelle jusqu'aux bords. */
    static void wheelhouse(RailMesh m, int tier, float z0, float z1, float y, float h, float w, float wings, boolean lit) {
        m.box(-w, y, z0, w, y + h, z1, BigMat.R_WHITE);
        BigMat glass = lit ? BigMat.R_LAMP : BigMat.R_GLASS_D;
        float g0 = y + h * 0.42F, g1 = y + h * 0.86F;
        m.box(-w + 0.5F, g0, z1 - 0.07F, w - 0.5F, g1, z1 + 0.07F, glass);
        m.box(-w - 0.07F, g0, z1 - 4, w + 0.07F, g1, z1 - 0.5F, glass);
        //Toit debordant
        m.box(-w - 0.5F, y + h, z0 - 0.4F, w + 0.5F, y + h + 0.6F, z1 + 0.8F, BigMat.R_WHITE);
        trim(m, tier, -w - 0.5F, w + 0.5F, y + h + 0.2F, y + h + 0.75F, z0 - 0.4F, z1 + 0.8F);
        //Pare-soleil sombre au-dessus des vitres
        m.box(-w + 0.3F, g1, z1 + 0.07F, w - 0.3F, g1 + 0.5F, z1 + 0.9F, BigMat.R_DARK);
        if (wings > w) {
            m.box(-wings, y + h * 0.42F - 0.5F, z1 - 3.2F, wings, y + h * 0.42F, z1 - 0.2F, BigMat.R_WHITE);
            for (float s : new float[]{-1, 1}) {
                m.box(s * wings - 0.3F, y + h * 0.42F, z1 - 3.2F, s * wings + 0.3F, y + h * 0.42F + 1.4F, z1 - 0.2F, BigMat.R_WHITE);
            }
            sideLights(m, wings - 0.9F, y + h * 0.42F + 1.4F, z1 - 2.4F, lit);
        } else {
            sideLights(m, w - 0.6F, y + h + 0.75F, z1 - 1.6F, lit);
        }
    }

    /** Cheminee inclinee vers l'arriere : manteau noir, bande du tier, chapeau sombre. */
    static void funnel(RailMesh m, int tier, float x, float z, float base, float h, float r, float rake) {
        m.rotate('x', x, base, z, -rake);
        m.cyl('y', x, z, r, base, base + h, BigMat.R_BLACK, 12);
        m.cyl('y', x, z, r + 0.15F, base + h * 0.52F, base + h * 0.7F, tierMat(tier), 12);
        m.cyl('y', x, z, r + 0.35F, base + h - 0.7F, base + h, BigMat.R_DARK, 12);
        m.cyl('y', x, z, r * 0.55F, base + h, base + h + 0.4F, BigMat.R_DARK, 8);
        m.pop();
    }

    /** Mat : fut, barre de flèche, feu de tete de mat. */
    static void mast(RailMesh m, float z, float base, float h, boolean lit) {
        m.cyl('y', 0, z, 0.4F, base, base + h, BigMat.R_STEEL_L, 6);
        m.box(-2.6F, base + h * 0.72F, z - 0.2F, 2.6F, base + h * 0.72F + 0.4F, z + 0.2F, BigMat.R_STEEL);
        m.box(-0.5F, base + h, z - 0.5F, 0.5F, base + h + 0.9F, z + 0.5F, lit ? BigMat.R_LAMP : BigMat.R_LAMP_OFF);
    }

    /** Radar tournant (pendant la marche) au sommet d'un mat. */
    static void radar(RailMesh m, float z, float base, float h, float span, boolean lit) {
        m.cyl('y', 0, z, 0.45F, base, base + h, BigMat.R_STEEL_L, 6);
        m.box(-1.6F, base + h * 0.6F, z - 0.2F, 1.6F, base + h * 0.6F + 0.4F, z + 0.2F, BigMat.R_STEEL);
        m.box(-0.5F, base + h + 1.6F, z - 0.5F, 0.5F, base + h + 2.4F, z + 0.5F, lit ? BigMat.R_LAMP : BigMat.R_LAMP_OFF);
        m.spin('y', 0, base + h, z, 6F);
        m.box(-span, base + h + 0.3F, z - 0.35F, span, base + h + 1.1F, z + 0.35F, BigMat.R_WHITE);
        m.box(-0.4F, base + h, z - 0.4F, 0.4F, base + h + 0.3F, z + 0.4F, BigMat.R_DARK);
        m.fixed();
    }

    /** Canot de sauvetage orange sous ses bossoirs. */
    static void lifeboat(RailMesh m, float x, float y, float z, float len) {
        float hl = len / 2;
        int orange = 0xFFE0661E;
        m.tinted(x - 1.0F, y + 0.4F, z - hl + 0.7F, x + 1.0F, y + 1.5F, z + hl - 0.7F, BigMat.R_WHITE, orange);
        m.tinted(x - 0.7F, y + 0.3F, z - hl, x + 0.7F, y + 1.5F, z + hl, BigMat.R_WHITE, orange);
        m.box(x - 0.8F, y + 1.5F, z - hl + 1.0F, x + 0.8F, y + 2.3F, z + hl - 1.0F, BigMat.R_WHITE);
        for (float dz : new float[]{-hl + 1.2F, hl - 1.2F}) {
            m.box(x - 0.2F, y, z + dz - 0.2F, x + 0.2F, y + 3.0F, z + dz + 0.2F, BigMat.R_STEEL);
            m.box(x - 0.2F - Math.signum(x) * 0.8F, y + 2.6F, z + dz - 0.2F, x + 0.2F, y + 3.0F, z + dz + 0.2F, BigMat.R_STEEL);
        }
    }

    /** Feux de cote : babord (+x) rouge, tribord (-x) vert. */
    static void sideLights(RailMesh m, float x, float y, float z, boolean lit) {
        m.box(x, y, z, x + 0.7F, y + 0.7F, z + 1.0F, lit ? BigMat.R_LAMP_R : BigMat.R_LAMP_OFF);
        m.box(-x - 0.7F, y, z, -x, y + 0.7F, z + 1.0F, lit ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
    }

    /** Liseret du tier autour d'un toit (quatre bandes, le toit reste blanc). */
    static void trim(RailMesh m, int tier, float x0, float x1, float y0, float y1, float z0, float z1) {
        BigMat t = tierMat(tier);
        m.box(x0 - 0.1F, y0, z0 - 0.1F, x0 + 0.5F, y1, z1 + 0.1F, t);
        m.box(x1 - 0.5F, y0, z0 - 0.1F, x1 + 0.1F, y1, z1 + 0.1F, t);
        m.box(x0 + 0.5F, y0, z0 - 0.1F, x1 - 0.5F, y1, z0 + 0.5F, t);
        m.box(x0 + 0.5F, y0, z1 - 0.5F, x1 - 0.5F, y1, z1 + 0.1F, t);
    }

    static void propeller(RailMesh m, float x, float z, float y, float r) {
        m.spin('z', x, y, z, 18F);
        m.cyl('z', x, y, 0.7F, z - 0.7F, z + 0.7F, BigMat.R_BRASS, 8);
        for (int i = 0; i < 4; i++) {
            m.rotate('z', x, y, z, 45 + 90 * i);
            m.box(x - 0.45F, y, z - 0.25F, x + 0.45F, y + r, z + 0.25F, BigMat.R_BRASS);
            m.pop();
        }
        m.fixed();
        m.cyl('z', x, y, 0.35F, z, z + 4, BigMat.R_STEEL, 6);
    }

    /** Garde-corps (lisse et chandeliers) le long d'un pont de superstructure. */
    static void railing(RailMesh m, float x, float y, float z0, float z1) {
        m.box(x - 0.1F, y + 1.1F, z0, x + 0.1F, y + 1.3F, z1, BigMat.R_STEEL_L);
        for (float z = z0; z <= z1; z += 2.5F) {
            m.box(x - 0.1F, y, z - 0.1F, x + 0.1F, y + 1.1F, z + 0.1F, BigMat.R_STEEL_L);
        }
    }

    // ------------------------------------------------------------------ superstructures par propulsion

    /** MKI : vapeur a aubes, rouf a promenade, timonerie sur le pont superieur, deux cheminees jumelles, roues sous leurs tambours. */
    static void paddleSteamer(RailMesh m, boolean lit) {
        int t = 0;
        float d = deck(t), hl = hl(t), hw = beamHalf(t);
        float z0 = -24, z1 = 20;
        //Pont principal : rouf ; pont promenade debordant (bois) ; rouf superieur ; timonerie a l'avant
        house(m, t, z0, z1, d, 6, hw - 2.2F, false, lit);
        m.box(-hw + 0.4F, d + 6, z0 - 2, hw - 0.4F, d + 6.6F, z1 + 2, BigMat.R_PLANKS);
        for (float s : new float[]{-1, 1}) {
            railing(m, s * (hw - 0.6F), d + 6.6F, z0 - 1.6F, z1 + 1.6F);
            for (float z = z0; z <= z1; z += 8.8F) {
                m.box(s * (hw - 0.9F) - 0.25F, d, z - 0.25F, s * (hw - 0.9F) + 0.25F, d + 6, z + 0.25F, BigMat.R_WHITE);
            }
        }
        house(m, t, -16, 8, d + 6.6F, 5, hw - 5, false, lit);
        wheelhouse(m, t, 8, 15, d + 6.6F, 6, 4.4F, 0, lit);
        //Cheminees jumelles cote a cote, hautes et fines
        for (float x : new float[]{-4, 4}) {
            funnel(m, t, x, -5, d + 11.6F, 21, 1.6F, 4);
        }
        mast(m, hl - 6, top(t, hl - 6), 18, lit);
        //Roues a aubes de part et d'autre, sous des tambours
        float wz = -8, wy = 3.0F, r = 8.6F;
        for (float s : new float[]{-1, 1}) {
            float xa = s > 0 ? hw + 0.2F : -hw - 3.6F, xb = xa + 3.4F;
            m.spin('x', 0, wy, wz, 9F);
            for (int i = 0; i < 8; i++) {
                m.rotate('x', 0, wy, wz, 22.5F * i);
                m.box(xa + 0.2F, wy - r, wz - 0.25F, xb - 0.2F, wy + r, wz + 0.25F, i % 2 == 0 ? BigMat.R_PLANKS : BigMat.R_OAK);
                m.pop();
            }
            m.cyl('x', wy, wz, 1.2F, xa, xb, BigMat.R_DARK, 10);
            m.cyl('x', wy, wz, r - 0.4F, xa - 0.1F, xa + 0.3F, BigMat.R_STEEL, 16, false, 0.4F);
            m.cyl('x', wy, wz, r - 0.4F, xb - 0.3F, xb + 0.1F, BigMat.R_STEEL, 16, false, 0.4F);
            m.fixed();
            //Tambour : demi-cylindre au-dessus de l'eau, eventail decoratif sur la face exterieure
            float rr = r + 0.9F;
            for (int i = 0; i < 8; i++) {
                float a0 = (float) Math.toRadians(180 * i / 8.0), a1 = (float) Math.toRadians(180 * (i + 1) / 8.0);
                float za = wz + rr * (float) Math.cos(a0), zb = wz + rr * (float) Math.cos(a1);
                float ya = wy + rr * (float) Math.sin(a0), yb = wy + rr * (float) Math.sin(a1);
                m.box(xa - 0.3F, Math.max(1, Math.min(ya, yb) - 0.6F), Math.min(za, zb), xb + 0.3F, Math.max(ya, yb), Math.max(za, zb),
                      i == 3 || i == 4 ? tierMat(t) : BigMat.R_WHITE);
            }
            float face = s > 0 ? xb + 0.3F : xa - 0.3F;
            for (int i = 1; i < 8; i += 2) {
                double a = Math.toRadians(180 * i / 8.0);
                m.rodYZ(face - 0.1F, face + 0.1F, wy + 1.2F, wz, wy + (float) Math.sin(a) * (rr - 1.2F), wz + (float) Math.cos(a) * (rr - 1.2F),
                      0.5F, tierMat(t));
            }
        }
    }

    /** MKII : caboteur diesel, chateau arriere sur trois ponts, cheminee inclinee, canots, mat sur le gaillard. */
    static void diesel(RailMesh m, boolean lit) {
        int t = 1;
        float d = deck(t), hl = hl(t), hw = beamHalf(t);
        float z0 = -hl + 4, z1 = -hl + 30;
        house(m, t, z0, z1, d, 6, hw - 1.4F, false, lit);
        house(m, t, z0 + 2, z1 - 2, d + 6, 5.5F, hw - 4, false, lit);
        wheelhouse(m, t, z1 - 12, z1 - 3, d + 11.5F, 6, hw - 7, hw - 0.6F, lit);
        funnel(m, t, 0, z0 + 7, d + 11.5F, 13, 2.6F, 8);
        radar(m, z1 - 7, d + 18.1F, 5, 2.6F, lit);
        for (float s : new float[]{-1, 1}) {
            lifeboat(m, s * (hw - 2.4F), d + 6, z0 + 10, 8);
            railing(m, s * (hw - 1.6F), d, z1, z1 + 1);
        }
        mast(m, hl * 0.86F, top(t, hl * 0.86F), 15, lit);
        propeller(m, 0, -hl - 0.8F, -ShipKind.draft(t) + 3.6F, 3.0F);
    }

    /** MKIII : navire electrique, chateau vitre en bandeaux, tour des batteries lumineuse, radar, deux helices. */
    static void electric(RailMesh m, boolean lit) {
        int t = 2;
        float d = deck(t), hl = hl(t), hw = beamHalf(t);
        float z0 = -hl + 4, z1 = -hl + 38;
        house(m, t, z0, z1, d, 6, hw - 1.4F, true, lit);
        house(m, t, z0 + 2, z1 - 1, d + 6, 5.5F, hw - 3, true, lit);
        house(m, t, z0 + 6, z1 - 3, d + 11.5F, 5.5F, hw - 5, true, lit);
        wheelhouse(m, t, z1 - 13, z1 - 2, d + 17, 6, hw - 6, hw - 0.6F, lit);
        //Tour des batteries : fentes lumineuses cyan, ventilations
        float bz0 = z0 + 7, bz1 = z0 + 15;
        m.box(-4.5F, d + 17, bz0, 4.5F, d + 27, bz1, BigMat.R_DARK);
        for (float y = d + 18; y < d + 26; y += 1.6F) {
            m.box(-4.56F, y, bz0 + 1, 4.56F, y + 0.7F, bz1 - 1, BigMat.R_GLOW_CYAN);
        }
        m.box(-5, d + 27, bz0 - 0.5F, 5, d + 27.6F, bz1 + 0.5F, tierMat(t));
        radar(m, z1 - 8, d + 23.6F, 6, 3.2F, lit);
        for (float s : new float[]{-1, 1}) {
            lifeboat(m, s * (hw - 1.9F), d + 6, z0 + 22, 8);
        }
        mast(m, hl * 0.87F, top(t, hl * 0.87F), 15, lit);
        for (float x : new float[]{-6, 6}) {
            propeller(m, x, -hl - 0.6F, -ShipKind.draft(t) + 3.8F, 3.0F);
        }
    }

    /** MKIV : navire nucleaire, dome du reacteur a l'arriere, grand chateau sur cinq ponts, event du reacteur, mat radar. */
    static void nuclear(RailMesh m, boolean lit) {
        int t = 3;
        float d = deck(t), hl = hl(t), hw = beamHalf(t);
        //Dome du reacteur sur le pont arriere, anneau lumineux bleu, prises d'air
        float rz = -hl + 13;
        m.cyl('y', 0, rz, 10.4F, d, d + 1.4F, BigMat.R_GLOW_BLUE, 20);
        for (int i = 0; i < 7; i++) {
            float r = 10F * (float) Math.cos(Math.toRadians(i * 13));
            m.cyl('y', 0, rz, r, d + 1.4F + i * 1.5F, d + 2.9F + i * 1.5F, BigMat.R_LEAD, 20);
        }
        m.cyl('y', 0, rz, 1.6F, d + 11.9F, d + 13.4F, BigMat.R_GLOW_BLUE, 10);
        for (float s : new float[]{-1, 1}) {
            m.box(s * 12.5F - 1.5F, d, rz - 3, s * 12.5F + 1.5F, d + 4, rz + 3, BigMat.R_DARK);
            m.box(s * 12.5F - 1.6F, d + 1, rz - 2.4F, s * 12.5F + 1.6F, d + 3.4F, rz + 2.4F, BigMat.R_GRILL);
        }
        //Chateau
        float z0 = -hl + 26, z1 = -hl + 70;
        house(m, t, z0, z1, d, 6.5F, hw - 1.6F, true, lit);
        house(m, t, z0 + 2, z1 - 2, d + 6.5F, 6, hw - 3.2F, true, lit);
        house(m, t, z0 + 4, z1 - 4, d + 12.5F, 6, hw - 5.4F, true, lit);
        house(m, t, z0 + 8, z1 - 6, d + 18.5F, 6, hw - 8, true, lit);
        wheelhouse(m, t, z1 - 16, z1 - 4, d + 24.5F, 6.5F, hw - 9, hw - 0.6F, lit);
        //Event du reacteur (sans fumee) : fut ovale gris, bande du tier, grille
        float vz = z0 + 12;
        for (float s : new float[]{-1, 1}) {
            m.cyl('y', s * 2.2F, vz, 3.2F, d + 24.5F, d + 36, BigMat.R_STEEL_L, 14);
        }
        m.box(-2.2F, d + 24.5F, vz - 3.2F, 2.2F, d + 36, vz + 3.2F, BigMat.R_STEEL_L);
        m.box(-5.6F, d + 30, vz - 3.4F, 5.6F, d + 32, vz + 3.4F, tierMat(t));
        m.box(-4.6F, d + 36, vz - 2.4F, 4.6F, d + 36.4F, vz + 2.4F, BigMat.R_GRILL);
        radar(m, z1 - 9, d + 31.6F, 8, 4.2F, lit);
        for (float s : new float[]{-1, 1}) {
            lifeboat(m, s * (hw - 2.0F), d + 6.5F, z0 + 12, 9);
            lifeboat(m, s * (hw - 2.0F), d + 6.5F, z0 + 26, 9);
        }
        mast(m, hl * 0.88F, top(t, hl * 0.88F), 17, lit);
        for (float x : new float[]{-8, 8}) {
            propeller(m, x, -hl - 0.6F, -ShipKind.draft(t) + 4.4F, 3.8F);
        }
    }

    // ------------------------------------------------------------------ cargaison

    /** Une zone de cargaison (z0, z1) avec {@code holds} cales ; {@code main} : la zone qui porte les pieces uniques (grue, antenne). */
    static void cargo(RailMesh m, ShipKind kind, int tier, float z0, float z1, float fill, int holds, boolean main) {
        float d = deck(tier), len = z1 - z0;
        float hw = halfWidth(tier, (z0 + z1) / 2) - 2.2F;
        switch (kind) {
            case CARGO -> {
                float pitch = len / holds;
                int[] colors = {0xFFB4513A, 0xFF3A6EA8, 0xFF3F8C4E, 0xFFC89A30, 0xFF7A4E9A, 0xFF8A8F97};
                for (int i = 0; i < holds; i++) {
                    float a = z0 + i * pitch + 0.8F, b = a + pitch - 1.6F;
                    float w = Math.min(hw, Math.min(halfWidth(tier, a), halfWidth(tier, b)) - 2.2F);
                    //Hiloire et panneau de cale
                    m.box(-w, d, a, w, d + 1.6F, b, BigMat.R_DARK);
                    m.box(-w + 0.4F, d + 1.6F, a + 0.4F, w - 0.4F, d + 1.9F, b - 0.4F, BigMat.R_STEEL_L);
                    trim(m, tier, -w + 0.4F, w - 0.4F, d + 1.6F, d + 1.95F, a + 0.4F, b - 0.4F);
                    //Conteneurs empiles selon le remplissage
                    int stacks = Math.round(fill * (2 + tier));
                    float cw = 2.6F, cl = Math.min(b - a - 1, 12);
                    int cols = Math.max(1, (int) ((2 * w - 1) / (cw + 0.3F)));
                    float x0 = -(cols * (cw + 0.3F) - 0.3F) / 2;
                    for (int s = 0; s < stacks; s++) {
                        for (int c = 0; c < cols; c++) {
                            float x = x0 + c * (cw + 0.3F);
                            int col = colors[(i * 5 + c * 3 + s * 7) % colors.length];
                            float zc = (a + b) / 2 - cl / 2;
                            m.tinted(x, d + 1.9F + s * 2.6F, zc, x + cw, d + 1.9F + (s + 1) * 2.6F - 0.1F, zc + cl, BigMat.R_WHITE, col);
                        }
                    }
                }
                if (main && tier >= 1) {
                    crane(m, tier, z0 - 1.5F, d);
                }
            }
            case TANKER -> {
                //Pont plat, rampe de tuyaux au milieu, collecteur au centre, coupoles de cales, passerelle sur pilotis
                m.box(-1.6F, d, z0, 1.6F, d + 1.2F, z1, BigMat.R_DARK);
                m.cyl('z', -0.8F, d + 1.9F, 0.55F, z0, z1, fam(kind), 8);
                m.cyl('z', 0.8F, d + 1.9F, 0.55F, z0, z1, BigMat.R_STEEL, 8);
                float mid = (z0 + z1) / 2;
                for (float s : new float[]{-1, 1}) {
                    m.cyl('x', d + 1.9F, mid, 0.5F, 0, s * (hw - 0.5F), fam(kind), 8);
                    m.box(s * (hw - 0.6F) - 0.6F, d, mid - 0.8F, s * (hw - 0.6F) + 0.6F, d + 2.8F, mid + 0.8F, BigMat.R_RED);
                }
                if (main) {
                    m.box(-3, d, mid - 2.5F, 3, d + 3.2F, mid + 2.5F, BigMat.R_STEEL_L);
                    m.box(-3.1F, d + 2.6F, mid - 2.6F, 3.1F, d + 3.3F, mid + 2.6F, tierMat(tier));
                }
                for (int i = 0; i < holds; i++) {
                    float z = z0 + (i + 0.5F) * len / holds;
                    for (float s : new float[]{-1, 1}) {
                        m.cyl('y', s * hw * 0.55F, z, 1.4F, d, d + 1.1F, BigMat.R_STEEL_L, 10);
                    }
                }
                for (float z = z0; z < z1; z += 6) {
                    m.box(-0.2F, d + 1.2F, z, 0.2F, d + 3.6F, z + 0.4F, BigMat.R_STEEL);
                }
                m.box(-1.0F, d + 3.6F, z0, 1.0F, d + 3.9F, z1, BigMat.R_GRILL);
            }
            case GAS_CARRIER -> {
                //Spheres de type Moss : centre au niveau du pont, bas pose dans la coque (jamais sous la quille), jupe a la couleur du gaz,
                //rampe de tuyaux sur leur sommet (pas sur les zones courtes du MKI)
                float rMax = Math.min(hw + 1, d + ShipKind.draft(tier) - 1.5F);
                float[] f = fit(tier, z0, z1, rMax - 1.2F);
                z0 = f[0];
                z1 = f[1];
                len = z1 - z0;
                int spheres = Math.max(1, Math.round(len / (2 * rMax + 2)));
                float pitch = len / spheres, r = Math.min(pitch / 2 - 1, rMax);
                for (int i = 0; i < spheres; i++) {
                    float z = z0 + (i + 0.5F) * pitch;
                    sphere(m, 0, d, z, r, BigMat.R_WHITE, fam(kind));
                    m.cyl('y', 0, z, r * 0.96F, d, d + 1.4F, fam(kind), 18);
                    m.cyl('y', 0, z, 0.9F, d + r - 0.2F, d + r + 1.2F, BigMat.R_STEEL, 8);
                }
                if (spheres > 1) {
                    float first = z0 + pitch / 2, last = z1 - pitch / 2, topY = d + r + 0.9F;
                    m.cyl('z', 0, topY, 0.4F, first, last, BigMat.R_STEEL, 6);
                }
            }
            case POWER_BARGE -> {
                float pitch = len / holds;
                for (int i = 0; i < holds; i++) {
                    float a = z0 + i * pitch + 0.6F, b = a + pitch - 1.2F;
                    float w = Math.min(hw, Math.min(halfWidth(tier, a), halfWidth(tier, b)) - 2.2F);
                    m.box(-w, d, a, w, d + 4.6F, b, BigMat.R_DARK);
                    int lit = Math.round(fill * 5);
                    for (int k = 0; k < 5; k++) {
                        m.box(-w - 0.05F, d + 0.6F + k * 0.75F, a + 1, w + 0.05F, d + 1.0F + k * 0.75F, b - 1, k < lit ? fam(kind) : BigMat.R_LAMP_OFF);
                    }
                    trim(m, tier, -w, w, d + 4.4F, d + 4.8F, a, b);
                    m.box(-1.2F, d + 4.8F, a + 1.5F, 1.2F, d + 5.6F, b - 1.5F, BigMat.R_COPPER);
                }
            }
            case AIR_CARRIER -> {
                int rows = tier >= 2 ? 3 : 2;
                float r = Math.min(6F, (2 * hw - 1) / (rows * 2.1F));
                float[] f = fit(tier, z0, z1, hw * 0.9F);
                z0 = f[0];
                z1 = f[1];
                for (int k = 0; k < rows; k++) {
                    float x = rows == 1 ? 0 : -hw + r + 0.4F + k * ((2 * hw - 2 * r - 0.8F) / (rows - 1));
                    m.cyl('z', x, d + r + 0.3F, r, z0 + 1, z1 - 1, BigMat.R_STEEL_L, 12);
                    m.cyl('z', x, d + r + 0.3F, r * 0.55F, z0 + 0.2F, z1 - 0.2F, BigMat.R_STEEL, 10);
                    for (float z = z0 + 3; z < z1 - 2; z += 6) {
                        m.cyl('z', x, d + r + 0.3F, r + 0.15F, z, z + 0.7F, fam(kind), 12);
                    }
                    m.box(x - 0.4F, d, z0 + 2, x + 0.4F, d + 0.5F, z1 - 2, BigMat.R_DARK);
                }
                if (main) {
                    m.cyl('y', 0, z0 + 2, 0.8F, d, d + 2 * r + 2, BigMat.R_STEEL, 8);
                    m.box(-1.2F, d + 2 * r + 2, z0 + 0.8F, 1.2F, d + 2 * r + 2.6F, z0 + 3.2F, fam(kind));
                }
            }
            case DATA_SHIP -> {
                float zEnd = main ? z1 - 5 : z1;
                float pitch = (zEnd - z0) / holds;
                for (int i = 0; i < holds; i++) {
                    float a = z0 + i * pitch + 0.8F, b = a + pitch - 1.6F;
                    float w = Math.min(hw, Math.min(halfWidth(tier, a), halfWidth(tier, b)) - 2.2F);
                    m.box(-w, d, a, w, d + 5.0F, b, BigMat.R_DARK);
                    int lit = Math.max(1, Math.round(fill * 4));
                    for (float z = a + 0.8F; z < b - 0.6F; z += 1.4F) {
                        for (int k = 0; k < 4; k++) {
                            BigMat mat = k < lit ? fam(kind) : BigMat.R_LAMP_OFF;
                            m.box(-w - 0.05F, d + 0.8F + k * 1.0F, z, -w + 0.05F, d + 1.3F + k * 1.0F, z + 0.7F, mat);
                            m.box(w - 0.05F, d + 0.8F + k * 1.0F, z, w + 0.05F, d + 1.3F + k * 1.0F, z + 0.7F, mat);
                        }
                    }
                    trim(m, tier, -w, w, d + 4.8F, d + 5.2F, a, b);
                }
                if (main) {
                    //Antenne parabolique, devant les baies
                    float z = z1 - 2;
                    m.cyl('y', 0, z, 0.5F, d, d + 8, BigMat.R_STEEL, 8);
                    m.rotate('x', 0, d + 9, z, -35);
                    m.cyl('y', 0, z, 3.6F, d + 8.6F, d + 9.2F, BigMat.R_WHITE, 14);
                    m.cyl('y', 0, z, 0.4F, d + 9.2F, d + 11.4F, fam(kind), 6);
                    m.pop();
                }
            }
            default -> {
                //Ferry : salons vitres sur deux ou trois ponts, pont superieur a garde-corps, canots
                int levels = tier >= 2 ? 3 : 2;
                float w = hw - 1.0F;
                float[] f = fit(tier, z0, z1, w);
                z0 = f[0];
                z1 = f[1];
                for (int lv = 0; lv < levels; lv++) {
                    float y = d + lv * 5.2F, ww = w - lv * 1.6F, a = z0 + lv * 2.5F, b = z1 - lv * 4;
                    if (b - a < 6) {
                        break;
                    }
                    m.box(-ww, y, a, ww, y + 5, b, BigMat.R_WHITE);
                    m.box(-ww - 0.1F, y + 1.6F, a + 0.6F, ww + 0.1F, y + 3.6F, b - 0.6F, fill > 0 ? BigMat.R_LAMP : BigMat.R_GLASS_D);
                    m.box(-ww + 0.6F, y + 1.6F, b - 0.1F, ww - 0.6F, y + 3.6F, b + 0.1F, fill > 0 ? BigMat.R_LAMP : BigMat.R_GLASS_D);
                    for (float z = a + 3; z < b - 1; z += 3) {
                        m.box(-ww - 0.15F, y + 1.6F, z, ww + 0.15F, y + 3.6F, z + 0.35F, BigMat.R_WHITE);
                    }
                    trim(m, tier, -ww, ww, y + 4.6F, y + 5.2F, a, b);
                    if (lv == levels - 1) {
                        for (float s : new float[]{-1, 1}) {
                            railing(m, s * (ww - 0.3F), y + 5.2F, a + 0.5F, b - 0.5F);
                        }
                    }
                }
                for (float s : new float[]{-1, 1}) {
                    for (float z = z0 + 4; z < z1 - 8; z += 12) {
                        lifeboat(m, s * (w + 1.0F), d + 5.2F, z + 3.5F, 7);
                    }
                }
            }
        }
    }

    /** Partie de [z0, z1] ou la coque laisse au moins {@code w} px de demi-largeur utile (cargaison droite : cylindres, salons). */
    static float[] fit(int tier, float z0, float z1, float w) {
        float a = z0, b = z1;
        while (a < b && halfWidth(tier, a) - 2.2F < w) {
            a += 0.5F;
        }
        while (b > a && halfWidth(tier, b) - 2.2F < w) {
            b -= 0.5F;
        }
        return new float[]{a, b};
    }

    static void crane(RailMesh m, int tier, float z, float d) {
        m.cyl('y', 0, z, 1.4F, d, d + 9, BigMat.R_STRIPE, 10);
        m.box(-1.8F, d + 9, z - 1.8F, 1.8F, d + 11.4F, z + 1.8F, BigMat.R_DARK);
        m.box(-1.2F, d + 9.6F, z + 1.8F, 1.2F, d + 11F, z + 2.1F, BigMat.R_GLASS_D);
        m.rotate('x', 0, d + 10, z, -26);
        m.box(-0.6F, d + 9.6F, z, 0.6F, d + 10.6F, z + 20 + tier * 4, BigMat.R_STRIPE);
        m.pop();
    }

    /** Sphere de cuve (methanier) : tranches cylindriques, ceinture a la couleur de la ressource. */
    static void sphere(RailMesh m, float x, float y, float z, float r, BigMat mat, BigMat band) {
        int n = 9;
        for (int i = 0; i < n; i++) {
            float a = (float) (-Math.PI / 2 + Math.PI * (i + 0.5) / n);
            float rr = r * (float) Math.cos(a), y0 = y + r * (float) Math.sin(-Math.PI / 2 + Math.PI * i / n);
            float y1 = y + r * (float) Math.sin(-Math.PI / 2 + Math.PI * (i + 1) / n);
            m.cyl('y', x, z, rr, y0, y1, i == n / 2 + 1 ? band : mat, 18);
        }
    }
}
