package fr.pioneers.core.machine;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.network.PacketTileAction;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc du socle dote de l'interface standard ({@link MachineMenu} + {@code GuiMachine}) : ses cases, ses valeurs affichees (synchronisees
 * en entiers longs), sa configuration des faces, et ses boutons.
 * <p>
 * Actions communes (bouton de l'interface) : {@code 0..5} reglage suivant de la face relative, {@code 8..13} reglage precedent, {@link #EJECT}
 * ejection automatique ; les actions propres a la machine commencent a {@link #CUSTOM}.
 */
public interface MachineGui extends PacketTileAction.Handler {

    /**
     * API Machine (lot 8c) : LE menu de l'interface standard, construit ici et nulle part ailleurs (MachineTile et les bases adaptees : buffers,
     * radiateur, compresseur, tiles du bronze, adaptateur cinetique) ; une machine ne fabrique jamais son propre menu.
     */
    default net.minecraft.world.inventory.AbstractContainerMenu standardMenu(int id, net.minecraft.world.entity.player.Inventory inventory) {
        return new MachineMenu(id, inventory, (net.minecraft.world.level.block.entity.BlockEntity) this);
    }

    int EJECT = 6, BACK = 8, CUSTOM = 100;

    /** Cases de l'interface (positions comprises). */
    List<ItemSlot> guiSlots();

    /** Hauteur de la zone propre a la machine (au-dessus de l'inventaire du joueur). */
    int guiHeight();

    /** Largeur du panneau (176 par defaut ; les tables larges en ont 236 : inventaire du joueur centre). */
    default int guiWidth() {
        return 176;
    }

    /** Tier affiche par l'interface (couleur des cerclages : basic, advanced, elite, ultimate). */
    default String guiTier() {
        return "basic";
    }

    /**
     * API Machine (lot 2) : hauteur de la bande de l'inventaire du joueur sous la zone machine ({@link MachineMenu#INVENTORY} ; l'interface
     * standard v2 la met dans un cadre : {@code StandardLayout.INVENTORY}).
     */
    default int guiInventory() {
        return MachineMenu.INVENTORY;
    }

    /** API Machine (lot 2) : valeurs d'interface nommees (null : pas encore de noms, indices historiques). */
    @Nullable
    default UiValues uiValues() {
        return null;
    }

    /**
     * API Machine (lot 4) : elements de l'interface standard v2 (jauges, ecran, cases, reglages...), relatifs au panneau ; le banc les exporte
     * (screens.json) et {@code tools/check_machine_api.py} verifie qu'ils tiennent dans la zone machine sans se chevaucher. Vide : aucun.
     */
    default List<StandardLayout.Part> guiParts() {
        return List.of();
    }

    /** Nombre de valeurs synchronisees, et chacune (cote serveur). */
    int guiValueCount();

    long guiValue(int index);

    @Nullable
    SideConfig getSideConfig();

    /** Distance maximale (blocs) entre le joueur et le bloc pour garder l'interface ouverte. */
    default double guiReach() {
        return 8;
    }

    /** Action propre a la machine (>= {@link #CUSTOM}). */
    default void handleCustom(ServerPlayer player, int action) {
    }

    /**
     * 1.0.2-beta : reglage a valeur (curseur de l'interface standard) : {@code setting} = numero du reglage propre a la machine, {@code value}
     * = valeur choisie (a BORNER ici : elle vient du client). Recu par {@code PacketMachineSetting}, apres {@code PacketGuard}.
     */
    default void handleSetting(ServerPlayer player, int setting, int value) {
    }

    @Override
    default void handleAction(ServerPlayer player, int action) {
        SideConfig config = getSideConfig();
        if (config != null && action >= SideConfig.CLEAR_TYPED && action < SideConfig.CLEAR_TYPED + 16) {
            config.clear(action - SideConfig.CLEAR_TYPED);
        } else if (config != null && action >= SideConfig.EJECT_TYPED && action < SideConfig.EJECT_TYPED + 16) {
            int channel = action - SideConfig.EJECT_TYPED;
            config.setEjectChannel(channel, !config.isEjectChannel(channel));
        } else if (config != null && action >= SideConfig.TYPED && action < SideConfig.TYPED + 16 * 8) {
            int local = action - SideConfig.TYPED;
            config.cycle(local / 16, RelativeSide.byIndex(local % 8), local % 16 >= 8);
        } else if (config != null && action >= 0 && action < 6) {
            config.cycle(RelativeSide.byIndex(action), false);
        } else if (config != null && action >= BACK && action < BACK + 6) {
            config.cycle(RelativeSide.byIndex(action - BACK), true);
        } else if (config != null && action == EJECT) {
            config.setEject(!config.isEject());
        } else if (action >= CUSTOM) {
            handleCustom(player, action);
        }
    }
}
