package fr.pioneers.world.ruins.sentinel;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * 1.9 (MAJ RP, bloc 1) : les cinq roles de Sentinelles (un type d'entite par role, le tier 1..3 est une donnee de l'entite). Taille de la
 * boite en blocs (spec : Gardien 1 x 2,2, Patrouilleur 0,6, Tourelle 1 x 1,4, Reparateur 0,8 x 1,6, Contremaitre 1,6 x 3,2) et tiers
 * ou le role existe. Sans classe Minecraft (banc hors jeu).
 */
public enum SentinelRole {
    GUARDIAN(1.0F, 2.2F, 1, 3),
    PATROLLER(0.6F, 0.6F, 1, 3),
    TURRET(1.0F, 1.4F, 2, 3),
    REPAIRER(0.8F, 1.6F, 2, 3),
    FOREMAN(1.6F, 3.2F, 3, 3);

    public final float width, height;
    public final int minTier, maxTier;

    SentinelRole(float width, float height, int minTier, int maxTier) {
        this.width = width;
        this.height = height;
        this.minTier = minTier;
        this.maxTier = maxTier;
    }

    /** Id court ({@code guardian}...) : fichiers de donnees, textures, maquettes. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Id du type d'entite ({@code sentinel_guardian}...). */
    public String entityId() {
        return "sentinel_" + id();
    }

    /** Tier ramene dans les bornes du role. */
    public int clampTier(int tier) {
        return Math.max(minTier, Math.min(maxTier, tier));
    }

    @Nullable
    public static SentinelRole byId(String id) {
        for (SentinelRole r : values()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }
}
