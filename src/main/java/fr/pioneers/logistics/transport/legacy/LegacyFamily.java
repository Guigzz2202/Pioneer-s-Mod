package fr.pioneers.logistics.transport.legacy;

import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.ChannelKind;
import java.util.Locale;

/**
 * 1.11 (D2c, spec §8.1) : famille d'un ancien cable (1.10 et avant). Chaque ancien reseau d'une famille devient un canal du type
 * correspondant. Pure (sans Minecraft) : utilisee par le banc hors jeu de la migration.
 */
public enum LegacyFamily {
    ITEM(ChannelKind.ITEM),
    FLUID(ChannelKind.FLUID),
    GAS(ChannelKind.GAS),
    HEAT(ChannelKind.HEAT),
    ENERGY(ChannelKind.ENERGY),
    PRESSURE(ChannelKind.PRESSURE),
    /** Fibre optique ({@code <tier>_data_cable}). */
    DATA(ChannelKind.DATA);

    private static final LegacyFamily[] VALUES = values();

    public final ChannelKind kind;

    LegacyFamily(ChannelKind kind) {
        this.kind = kind;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Ancien identifiant d'un cable de tier ({@code basic_item_cable}, {@code elite_data_cable_clear}...). */
    public String cableId(String tier, boolean clear) {
        return tier + "_" + id() + "_cable" + (clear ? "_clear" : "");
    }

    public static LegacyFamily[] all() {
        return VALUES;
    }

    /** Les 4 tiers des anciens cables (prefixes des identifiants) et leur niveau logistique. */
    public static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};

    public static CableLevel levelOfTier(int tier) {
        return switch (tier) {
            case 1 -> CableLevel.ADVANCED;
            case 2 -> CableLevel.ELITE;
            case 3 -> CableLevel.ULTIMATE;
            default -> CableLevel.BASIC;
        };
    }

    /**
     * Les 60 anciens identifiants et le cable logistique qui les remplace (spec §8.1) : chaque ancien cable devient le cable logistique du
     * meme niveau, opaque ou transparent conserve ; goulotte et conduite en bois → bois ; conduite en cuivre → bronze ; conduite
     * refractaire → MKI (garni de briques refractaires). Le cable d'energie MKIV n'est jamais converti en supraconducteur.
     */
    public record Old(String id, LegacyFamily family, CableLevel level, boolean clear) {

        public String newId() {
            //MAJ simplification (03/11/2026) : plus de cable transparent, l'ancien transparent devient l'opaque du meme niveau
            return level.blockId(false);
        }
    }

    public static java.util.List<Old> olds() {
        java.util.List<Old> out = new java.util.ArrayList<>(60);
        for (boolean clear : new boolean[]{false, true}) {
            for (LegacyFamily family : VALUES) {
                for (int t = 0; t < TIERS.length; t++) {
                    out.add(new Old(family.cableId(TIERS[t], clear), family, levelOfTier(t), clear));
                }
            }
        }
        out.add(new Old("primitive_item_cable", ITEM, CableLevel.WOODEN, false));
        out.add(new Old("primitive_fluid_cable", FLUID, CableLevel.WOODEN, false));
        out.add(new Old("primitive_gas_cable", GAS, CableLevel.BRONZE, false));
        out.add(new Old("refractory_fluid_cable", FLUID, CableLevel.BASIC, false));
        return out;
    }
}
