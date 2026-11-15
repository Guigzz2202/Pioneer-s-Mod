package fr.pioneers.client.gui.core;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataTank;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Texts;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.data.DataUnits;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * Lot 8c : plus de vues d'avant aiguillees par classe (la chaine stabilisateur / simulateur / absorbeur / etablis est retiree : chaque bloc a
 * interface standard a sa vue au registre {@link MachineScreens}). Reste ici, pour un ecran ouvert : les JAUGES COMMUNES de reservoirs
 * (gaz, fluides, donnees, caloporteur) et l'ETAT des vues qui en ont un (champs de saisie des bases, recherche et defilement des etablis).
 */
final class MachineViews {

    private final GuiMachine gui;
    private final BaseViews bases;
    private final BronzeViews bronze;
    /** 1.0.4-beta (lot 1) : etat de la vue du comptoir d'entrepot (recherche, tri, defilement). */
    private final WarehouseViews warehouse;

    MachineViews(GuiMachine gui) {
        this.gui = gui;
        bases = new BaseViews(gui, this);
        bronze = new BronzeViews(gui);
        warehouse = new WarehouseViews(gui);
    }

    WarehouseViews warehouse() {
        return warehouse;
    }

    /** Etat des vues des bases (nom de la station, du rover). */
    BaseViews bases() {
        return bases;
    }

    /** Etat de la vue des etablis (recherche, defilement). */
    BronzeViews bronze() {
        return bronze;
    }

    boolean scrolled(BlockEntity tile, double delta) {
        return bronze.scrolled(tile, delta) || warehouse.scrolled(tile, delta);
    }

    /** 1.0.4-beta (lot 3) : clic droit (coordonnees du panneau) dans une vue qui le prend (comptoir d'entrepot : panier). */
    boolean clicked(BlockEntity tile, double x, double y, int button) {
        //1.0.4-beta (lot 10) : clic droit sur une case du filtre du relais de caravane (la vide)
        return warehouse.clicked(tile, x, y, button) || CaravanViews.clicked(gui, tile, x, y, button);
    }

    void init() {
        bases.init(gui.getMenu().getTile());
        bronze.init(gui.getMenu().getTile());
        warehouse.init(gui.getMenu().getTile());
    }

    /** 1.0.5-beta (lot 17) : touche prise par une vue (comptoir : Entree = « Commander », Echap ferme un volet). */
    boolean keyPressed(BlockEntity tile, int key) {
        return warehouse.keyPressed(tile, key);
    }

    /** Echap : vide d'abord une barre de recherche non vide. */
    boolean clearSearch() {
        return bronze.clearSearch() || warehouse.clearSearch();
    }

    void removed() {
        bases.removed(gui.getMenu().getTile());
        warehouse.removed();
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.pioneers." + key, args);
    }

    // ------------------------------------------------------------------
    // Jauges de reservoirs (contenu synchronise par la tile)
    // ------------------------------------------------------------------

    void gasGauge(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, BasicGasTank tank, Component what) {
        GasStack stack = tank.getStack();
        double ratio = tank.getCapacity() <= 0 ? 0 : stack.getAmount() / (double) tank.getCapacity();
        TextureAtlasSprite sprite = stack.isEmpty() ? null : GuiMachine.atlas(stack.getGas().getIcon());
        content(graphics, x, y, w, h, ratio, sprite, stack.isEmpty() ? 0 : stack.getGas().getTint() | 0xFF000000);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(what, stack.isEmpty() ? tr("buffer.empty") : stack.getGas().getName(),
                  tr("buffer.amount", Texts.format(stack.getAmount()), Texts.format(tank.getCapacity()), "mB").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** 0.20.34 : reservoir de donnees (multibloc) : barre a la couleur du type, infobulle type et quantite. */
    void dataGauge(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, IDataTank tank) {
        DataStack stack = tank.getStack();
        double ratio = tank.getCapacity() <= 0 ? 0 : stack.getAmount() / (double) tank.getCapacity();
        content(graphics, x, y, w, h, ratio, null, stack.isEmpty() ? 0 : stack.getType().getColor() | 0xFF000000);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(Component.translatable("industry.pioneers.gui.data_in"), stack.isEmpty() ? Component.translatable("data.pioneers.tank.empty")
                                                                                                 : stack.getType().getName(),
                  Component.translatable("data.pioneers.gauge", DataUnits.format(stack.getAmount()), DataUnits.format(tank.getCapacity()))
                        .withStyle(ChatFormatting.GRAY)));
        }
    }

    /** 1.0 (0.19.12) : sortie du caloporteur : caloporteur chaud (fluide) ou vapeur d'eau (gaz), celui qui a du contenu. */
    void coolantOutGauge(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, FluidTank fluid, BasicGasTank steam) {
        if (fluid.isEmpty() && !steam.getStack().isEmpty()) {
            gasGauge(graphics, mouseX, mouseY, x, y, w, h, steam, Component.translatable("industry.pioneers.gui.steam_out"));
        } else {
            fluidGauge(graphics, mouseX, mouseY, x, y, w, h, fluid);
        }
    }

    void fluidGauge(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, FluidTank tank) {
        FluidStack stack = tank.getFluid();
        double ratio = tank.getCapacity() <= 0 ? 0 : stack.getAmount() / (double) tank.getCapacity();
        TextureAtlasSprite sprite = null;
        int tint = 0;
        if (!stack.isEmpty()) {
            IClientFluidTypeExtensions fluid = IClientFluidTypeExtensions.of(stack.getFluid());
            sprite = GuiMachine.atlas(fluid.getStillTexture(stack));
            tint = fluid.getTintColor(stack) | 0xFF000000;
        }
        content(graphics, x, y, w, h, ratio, sprite, tint);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(stack.isEmpty() ? tr("buffer.empty") : stack.getDisplayName(),
                  tr("buffer.amount", Texts.format(stack.getAmount()), Texts.format(tank.getCapacity()), "mB").withStyle(ChatFormatting.GRAY)));
        }
    }

    private void content(GuiGraphics graphics, int x, int y, int w, int h, double ratio, TextureAtlasSprite sprite, int tint) {
        gui.frame(graphics, x, y, w, h);
        int filled = (int) Math.round((h - 2) * Math.max(0, Math.min(1, ratio)));
        if (filled > 0 && sprite != null) {
            GuiMachine.tiled(graphics, sprite, tint, x + 1, y + h - 1 - filled, w - 2, filled);
            //1.0.4-beta (lot 31) : verre blanc de la salle blanche : niveau marque d'un filet sombre (un gaz clair reste lisible)
            graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, GuiMachine.lightSkin() ? 0x90405060 : 0x60FFFFFF);
        }
        GuiMachine.ticks(graphics, x, y, w, h);
    }
}
