package fr.pioneers.client.gui.core;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataTank;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Texts;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.data.DataUnits;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import java.util.ArrayList;
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
import org.jetbrains.annotations.Nullable;

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

    // ------------------------------------------------------------------
    // 1.0.6 : jauges de RESSOURCES des machines de recettes (liquides et gaz traites de la meme facon)
    // ------------------------------------------------------------------

    /**
     * 1.0.6 (Guillaume, 17/11/2026 : « fusionner les jauges de gaz et de liquide ; pour chaque ressource indiquer en infobulle s'il s'agit de
     * liquide ou de gaz afin de savoir ce qu'on attend exactement ») : les jauges d'un cote (entrees ou sorties). Partagees, la jauge i
     * montre le reservoir de liquide i ou celui de gaz i (celui qui a du contenu) ; une jauge vide montre en fantome la ressource que la
     * recette verrouillee attend (ou produit) a cette place. Infobulle : sens, nom, « Liquide » ou « Gaz » (ou ce que la jauge accepte),
     * ressource attendue, quantite.
     */
    void resourceRow(GuiGraphics graphics, int mouseX, int mouseY, List<Rect> fluidRects, List<Rect> gasRects, List<? extends FluidTank> fluids,
          List<BasicGasTank> gases, boolean paired, boolean output, @Nullable RecipeChoice locked) {
        int nf = Math.min(fluidRects.size(), fluids.size()), ng = Math.min(gasRects.size(), gases.size());
        Want[] want = expected(locked, fluids, gases, paired, output);
        if (paired) {
            for (int i = 0; i < nf; i++) {
                resourceGauge(graphics, mouseX, mouseY, fluidRects.get(i), fluids.get(i), i < gases.size() ? gases.get(i) : null, output, want[i]);
            }
            return;
        }
        for (int i = 0; i < nf; i++) {
            resourceGauge(graphics, mouseX, mouseY, fluidRects.get(i), fluids.get(i), null, output, want[i]);
        }
        for (int i = 0; i < ng; i++) {
            resourceGauge(graphics, mouseX, mouseY, gasRects.get(i), null, gases.get(i), output, want[fluids.size() + i]);
        }
    }

    /**
     * Ressource attendue par jauge vide (FluidStack ou GasStack, sinon null) : celles de la recette verrouillee qu'aucune jauge ne tient,
     * dans l'ordre (liquides puis gaz), posees sur les jauges vides (partagees : n'importe laquelle ; sinon de leur sorte).
     */
    /** Ressource attendue par une jauge vide : un liquide ou un gaz (l'autre null). */
    record Want(@Nullable FluidStack fluid, @Nullable GasStack gas) {
    }

    static Want[] expected(@Nullable RecipeChoice c, List<? extends FluidTank> fluids, List<BasicGasTank> gases, boolean paired, boolean output) {
        Want[] out = new Want[paired ? fluids.size() : fluids.size() + gases.size()];
        if (c == null) {
            return out;
        }
        List<Want> wf = new ArrayList<>(), wg = new ArrayList<>();
        if (output) {
            for (FluidStack f : c.fluidOutputs()) {
                if (!f.isEmpty() && fluids.stream().noneMatch(t -> !t.isEmpty() && t.getFluid().isFluidEqual(f))) {
                    wf.add(new Want(f, null));
                }
            }
            for (IndustrialRecipe.GasAmount a : c.gasOutputs()) {
                GasStack gs = a.stack();
                if (!gs.isEmpty() && gases.stream().noneMatch(t -> !t.getStack().isEmpty() && t.getStack().isSameGas(gs))) {
                    wg.add(new Want(null, gs));
                }
            }
        } else {
            for (IndustrialRecipe.FluidIn in : c.fluids()) {
                if (fluids.stream().noneMatch(t -> !t.isEmpty() && in.test(t.getFluid())) && !in.displayed().isEmpty()) {
                    wf.add(new Want(in.displayed().get(0), null));
                }
            }
            for (IndustrialRecipe.GasAmount a : c.gases()) {
                if (gases.stream().noneMatch(t -> !t.getStack().isEmpty() && a.test(t.getStack())) && !a.stack().isEmpty()) {
                    wg.add(new Want(null, a.stack()));
                }
            }
        }
        if (paired) {
            List<Want> all = new ArrayList<>(wf);
            all.addAll(wg);
            int k = 0;
            for (int i = 0; i < out.length && k < all.size(); i++) {
                if (fluids.get(i).isEmpty() && (i >= gases.size() || gases.get(i).getStack().isEmpty())) {
                    out[i] = all.get(k++);
                }
            }
            return out;
        }
        int k = 0;
        for (int i = 0; i < fluids.size() && k < wf.size(); i++) {
            if (fluids.get(i).isEmpty()) {
                out[i] = wf.get(k++);
            }
        }
        k = 0;
        for (int i = 0; i < gases.size() && k < wg.size(); i++) {
            if (gases.get(i).getStack().isEmpty()) {
                out[fluids.size() + i] = wg.get(k++);
            }
        }
        return out;
    }

    /** Une jauge de ressource : liquide, gaz, ou vide (fantome de la ressource attendue). */
    private void resourceGauge(GuiGraphics graphics, int mouseX, int mouseY, Rect r, @Nullable FluidTank fluid, @Nullable BasicGasTank gas,
          boolean output, @Nullable Want want) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        boolean hasFluid = fluid != null && !fluid.isEmpty(), hasGas = gas != null && !gas.getStack().isEmpty();
        if (hasFluid) {
            FluidStack stack = fluid.getFluid();
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
            content(graphics, x, y, w, h, stack.getAmount() / (double) Math.max(1, fluid.getCapacity()), GuiMachine.atlas(ext.getStillTexture(stack)),
                  ext.getTintColor(stack) | 0xFF000000);
        } else if (hasGas) {
            GasStack stack = gas.getStack();
            content(graphics, x, y, w, h, stack.getAmount() / (double) Math.max(1, gas.getCapacity()), GuiMachine.atlas(stack.getGas().getIcon()),
                  stack.getGas().getTint() | 0xFF000000);
        } else {
            gui.frame(graphics, x, y, w, h);
            if (want != null && want.fluid() != null) {
                IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(want.fluid().getFluid());
                GuiMachine.ghost(graphics, GuiMachine.atlas(ext.getStillTexture(want.fluid())), ext.getTintColor(want.fluid()), x + 1, y + 1, w - 2, h - 2);
            } else if (want != null && want.gas() != null) {
                GuiMachine.ghost(graphics, GuiMachine.atlas(want.gas().getGas().getIcon()), want.gas().getGas().getTint(), x + 1, y + 1, w - 2, h - 2);
            }
            GuiMachine.ticks(graphics, x, y, w, h);
        }
        if (!gui.over(mouseX, mouseY, x, y, w, h)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(output ? "gui.pioneers.machine.out" : "gui.pioneers.machine.in").withStyle(ChatFormatting.GRAY));
        long amount = 0, capacity = fluid != null ? fluid.getCapacity() : gas != null ? gas.getCapacity() : 0;
        if (hasFluid) {
            lines.add(fluid.getFluid().getDisplayName());
            lines.add(kind(true));
            amount = fluid.getFluidAmount();
        } else if (hasGas) {
            lines.add(gas.getStack().getGas().getName());
            lines.add(kind(false));
            amount = gas.getStack().getAmount();
            capacity = gas.getCapacity();
        } else {
            lines.add(tr("buffer.empty"));
            lines.add(Component.translatable(fluid != null && gas != null ? "gui.pioneers.resource.both" : fluid != null ? "gui.pioneers.resource.liquid"
                  : "gui.pioneers.resource.gas").withStyle(fluid != null && gas != null ? ChatFormatting.WHITE : fluid != null ? LIQUID : GAS));
            if (want != null) {
                boolean liquid = want.fluid() != null;
                Component name = liquid ? want.fluid().getDisplayName() : want.gas().getGas().getName();
                lines.add(Component.translatable(output ? "gui.pioneers.resource.makes" : "gui.pioneers.resource.expected", name,
                      Component.translatable(liquid ? "gui.pioneers.resource.liquid_word" : "gui.pioneers.resource.gas_word"))
                      .withStyle(liquid ? LIQUID : GAS));
            }
        }
        lines.add(tr("buffer.amount", Texts.format(amount), Texts.format(capacity), "mB").withStyle(ChatFormatting.GRAY));
        gui.tooltip(lines);
    }

    /** Couleurs de la sorte de ressource dans les infobulles (liquide bleu, gaz jaune pale). */
    private static final ChatFormatting LIQUID = ChatFormatting.AQUA, GAS = ChatFormatting.YELLOW;

    private static Component kind(boolean liquid) {
        return Component.translatable(liquid ? "gui.pioneers.resource.liquid" : "gui.pioneers.resource.gas").withStyle(liquid ? LIQUID : GAS);
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
