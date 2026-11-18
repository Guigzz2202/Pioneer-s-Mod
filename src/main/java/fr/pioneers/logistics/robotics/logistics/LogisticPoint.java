package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Point logistique du reseau robotique : une balise posee sur un bloc, ou un coffre logistique.
 */
public interface LogisticPoint {

    @Nullable
    Level getPointLevel();

    /** Position du point lui-meme (balise ou coffre) : appartenance au reseau, carte. */
    BlockPos getPointPos();

    PointConfig getConfig();

    /** Applique de nouveaux reglages (cote serveur ; synchronise). */
    void setConfig(PointConfig config);

    /** Bloc servi, face d'acces et endroit ou se tient l'unite. */
    Endpoint endpoint();

    /** Acces pour une ressource donnee : la face reglee pour elle dans les ressources du point, sinon la face d'acces. */
    default Endpoint endpointFor(@Nullable LogisticResource res) {
        Endpoint endpoint = endpoint();
        if (res != null) {
            for (PointConfig.Entry entry : getConfig().entries) {
                if (entry.side() != null && entry.res().covers(res)) {
                    return endpoint.withSide(entry.side());
                }
            }
        }
        return endpoint;
    }

    boolean isPointRemoved();

    /** Nom affiche (nom donne par le joueur, sinon le bloc servi). */
    Component displayName();

    ItemStack displayIcon();
}
