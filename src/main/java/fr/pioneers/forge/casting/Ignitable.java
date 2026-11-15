package fr.pioneers.forge.casting;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Tuile qui s'allume a l'allume-feu ({@link FireStarterItem}) : foyer de forge et toute future source de chaleur a allumer. Le feu de camp,
 * les bougies, la fosse a charbon et le feu pose sur un bloc sont reconnus sans cette interface.
 */
public interface Ignitable {

    /**
     * Allume le feu (appele cote serveur au bout du geste de 2 s). Renvoie vrai si quelque chose s'est allume (l'allume-feu s'use alors d'un
     * point), faux sinon (deja allume, pas de combustible... : la tuile peut prevenir le joueur elle-meme).
     */
    boolean ignite(@Nullable Player player);
}
