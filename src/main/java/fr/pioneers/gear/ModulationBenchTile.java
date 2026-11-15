package fr.pioneers.gear;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.registries.PiGear;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.15 : etabli de modulation : on y pose un objet modulaire (armure, outil, lame, fusil) pour installer, retirer, allumer ou couper ses
 * modules, et le recharger (reserve de 2 MFE, recharge de l'objet jusqu'a 20 000 FE/t ; batterie dessous).
 * <p>
 * Cases : 0 objet modulaire, 1 module a installer, 2 batterie. Actions : {@link #INSTALL} (le module de la case 1, un niveau),
 * {@link #REMOVE} + i (un niveau du i-eme module installe, rendu dans la case 1 ou a l'inventaire), {@link #TOGGLE} + i (marche / arret).
 * Valeurs de l'interface : 4 recharge de l'objet en cours (FE/t).
 * <p>
 * API Machine (lot 8c) : logique dans {@link ModulationUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class ModulationBenchTile extends MachineTile {

    public static final int INSTALL = CUSTOM, REMOVE = CUSTOM + 100, TOGGLE = CUSTOM + 200;
    /** Lot 4 : places de l'interface standard v2 (StandardLayout : colonne a droite de l'energie, batterie a la place commune). */
    public static final int GEAR_X = 34, GEAR_Y = 30, MODULE_X = 34, MODULE_Y = 64, BATTERY_X = StandardLayout.BATTERY_X,
          BATTERY_Y = StandardLayout.BATTERY_Y;
    /** Bouton Installer (sous le module) et ecran des modules installes (a droite). */
    public static final StandardLayout.Rect INSTALL_BUTTON = new StandardLayout.Rect(26, 86, 32, 14),
          MODULES = new StandardLayout.Rect(64, StandardLayout.TOP, StandardLayout.WIDTH - 8 - 64, StandardLayout.SCREEN_Y + StandardLayout.SCREEN_H - StandardLayout.TOP);
    /** Valeur nommee : energie versee a l'objet au dernier tick. */
    public static final UiValues.Key CHARGING = UiValues.Key.of("charging");
    /** API Machine (lot 8c) : la logique (recharge, modules) est dans l'unite portee. */
    private final ModulationUnit unit;

    public ModulationBenchTile(BlockPos pos, BlockState state) {
        super(PiGear.BENCH_TILE.get(), pos, state, 2_000_000L, 40_000);
        unit = carry(new ModulationUnit(access()));
    }

    @Override
    protected SideMode initialMode() {
        return SideMode.INPUT;
    }

    public ItemStack gear() {
        return unit.gear.getStack();
    }

    @Override
    protected void tick() {
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        unit.handleCustom(player, action);
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.serviceParts(true, new StandardLayout.Part("slot_in", StandardLayout.slotFrame(GEAR_X, GEAR_Y)),
              new StandardLayout.Part("slot_in", StandardLayout.slotFrame(MODULE_X, MODULE_Y)), INSTALL_BUTTON).stream()
              .map(p -> p.role().equals("screen") ? new StandardLayout.Part("screen", MODULES) : p).toList();
    }
}
