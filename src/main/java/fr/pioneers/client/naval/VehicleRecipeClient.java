package fr.pioneers.client.naval;

import fr.pioneers.Pioneers;
import fr.pioneers.core.vanilla.VanillaRecipeFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 32, retour 47 : bateaux seulement au chantier naval) : a la reception des recettes, le CLIENT retire celles qui donnent un
 * vehicule hors des ateliers ({@link VanillaRecipeFilter#filterVehicles}) AVANT JEI (priorite la plus haute) : JEI et le livre de recettes ne
 * montrent plus de bateau a la grille, meme si le serveur (ou un paquet de donnees) en garde une. Le serveur, lui, les retire au demarrage
 * et a chaque /reload. 1.0.5-beta (lot 36, retour 61) : les bateaux en bois de l'etabli du CHARPENTIER restent (liste blanche :
 * chantier naval + charpentier, {@link VanillaRecipeFilter#carpenterBoat}). Nom court unique (classes d'abonnes : CLAUDE.md, piege de
 * l'EventBus).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class VehicleRecipeClient {

    private VehicleRecipeClient() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRecipes(RecipesUpdatedEvent event) {
        var connection = Minecraft.getInstance().getConnection();
        RegistryAccess access = connection != null ? connection.registryAccess() : RegistryAccess.EMPTY;
        int removed = VanillaRecipeFilter.filterVehicles(event.getRecipeManager(), access);
        if (removed > 0) {
            Pioneers.LOGGER.info("Recettes de vehicules hors des ateliers retirees cote client : {}", removed);
        }
    }
}
