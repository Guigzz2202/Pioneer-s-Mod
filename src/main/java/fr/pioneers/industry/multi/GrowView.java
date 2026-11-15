package fr.pioneers.industry.multi;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 26, Guillaume : « l'animation de la ferme hydroponique doit representer les crops qui sont plantes et qui poussent. Pareil
 * pour les arbres ») : ce que le client doit savoir pour dessiner les VRAIES plantes de la recette en cours (ferme hydroponique, plantation
 * forestiere) : l'id de la recette du lot ({@link MultiProcess#batch}) et sa progression. Envoye avec l'etat client du controleur
 * ({@code ControllerTile.writeClient}, cle {@code grow}) au depart et a la fin d'un lot, puis toutes les 10 s pendant le lot (le client
 * extrapole entre deux envois : la croissance avance pendant que le controleur est en marche). Aucun paquet reseau nouveau.
 * Rendu : {@code client/industry/big/GrowRender}.
 */
public final class GrowView {

    /** Multiblocs dont les plantes de la recette sont dessinees. */
    public static final Set<String> MACHINES = Set.of("hydroponic_farm", "tree_plantation");
    /** Renvoi periodique pendant un lot (ticks). */
    static final int RESYNC = 200;

    private GrowView() {
    }

    /** Etat recu par le client (dans le {@link MultiProcess} du controleur cote client). */
    public static final class State {

        @Nullable
        ResourceLocation recipe;
        float progress;
        int duration = 1;
        long at = Long.MIN_VALUE;

        /** Recette du lot en cours (null : rien ne pousse). */
        @Nullable
        public ResourceLocation recipe() {
            return recipe;
        }

        /**
         * Progression 0..1 a l'instant {@code now} (temps de jeu + fraction) : celle recue, avancee du temps ecoule depuis l'envoi si le
         * controleur est en marche ; bornee a 1.
         */
        public float progress(double now, boolean active) {
            if (recipe == null) {
                return 0;
            }
            double p = progress + (active && at != Long.MIN_VALUE ? Math.max(0, now - at) / Math.max(1, duration) : 0);
            return (float) Math.max(0, Math.min(1, p));
        }
    }

    /** Le multibloc dessine-t-il ses plantes ? */
    public static boolean grows(ControllerTile c) {
        return MACHINES.contains(c.machine.key);
    }

    /** Etat client du controleur (null : machine sans plantes). */
    @Nullable
    public static State of(ControllerTile c) {
        return grows(c) ? c.process.grow : null;
    }

    /** Serveur : etat ecrit avec l'etat client du controleur. */
    static void write(ControllerTile c, MultiProcess p, CompoundTag tag) {
        if (!grows(c)) {
            return;
        }
        CompoundTag g = new CompoundTag();
        if (p.batch != null) {
            g.putString("r", p.batch.getId().toString());
            g.putFloat("p", (float) Math.min(1, p.progress / Math.max(1, p.duration)));
            g.putInt("d", Math.max(1, p.duration));
        }
        tag.put("grow", g);
    }

    /** Client : etat recu ({@code now} = temps de jeu a la reception). */
    static void read(MultiProcess p, CompoundTag tag, long now) {
        if (!tag.contains("grow")) {
            return;
        }
        CompoundTag g = tag.getCompound("grow");
        State s = p.grow;
        s.recipe = g.contains("r") ? ResourceLocation.tryParse(g.getString("r")) : null;
        s.progress = g.getFloat("p");
        s.duration = Math.max(1, g.getInt("d"));
        s.at = now;
    }
}
