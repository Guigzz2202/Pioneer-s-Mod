package fr.pioneers.client.gui.core;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.DataBankUnit;
import fr.pioneers.api.machine.unit.StorageUnit;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.data.DataUnits;
import fr.pioneers.registries.PiFluids;
import fr.pioneers.tile.TileEntityDataBank;
import fr.pioneers.transport.buffer.BufferTile;
import fr.pioneers.transport.buffer.BufferType;
import fr.pioneers.transport.heat.ElectricHeaterTile;
import fr.pioneers.transport.pressure.AirCompressorTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 3) : VUES DE L'INTERFACE STANDARD v2 des buffers (fiches {@code buffer} et {@code item_buffer}), du radiateur electrique
 * ({@code heater}) et du compresseur d'air ({@code compressor}) : inventaire dans le cadre, titre sans tier + pastille de tier, onglets a
 * droite (faces, redstone, Infos), ecran d'etat de 52 px en bas de la zone machine ({@link StandardLayout#ZONE}). Les valeurs se lisent par
 * leur NOM ({@link UiValues#STORED}...), plus aucun indice ni {@code instanceof} : la vue est choisie par la fiche ({@link MachineScreens}),
 * la tile est lue par {@link #as} (classe attendue).
 * <p>
 * Disposition (relative au panneau de {@link StandardLayout#WIDTH}, places du lot 4 dans {@link StandardLayout}) :
 * <ul>
 *     <li>buffers a jauge : grande jauge {@link #GAUGE} (24 x 60), carte du contenu {@link #CARD} (quantite en grand, contenance, debit du
 *     tier), cases d'entree / sortie des recipients a droite ({@link StandardLayout#IO_X}), ecran d'etat {@link #SCREEN} (contenu,
 *     remplissage, barre, contenu / contenance, debit net) ;</li>
 *     <li>buffer d'objets : ses cases (places d'avant) et le compte des cases occupees dans le titre ;</li>
 *     <li>radiateur, compresseur : reserve d'energie et batterie dessous (place commune de l'interface v2), thermometre / manometre 12 x 60,
 *     reglages a droite, ecran d'etat dessous.</li>
 * </ul>
 */
final class StorageViews {

    /** Lot 4 : places communes de {@link StandardLayout} (le banc les exporte et verifie qu'elles ne se chevauchent pas). */
    static final Rect GAUGE = StandardLayout.BUFFER_GAUGE, CARD = StandardLayout.BUFFER_CARD, SCREEN = StandardLayout.screen(StandardLayout.WIDTH);
    /** Radiateur, compresseur : jauge de temperature / pression, colonne des reglages (legende, puis reglage de 14 px). */
    static final int TALL_X = StandardLayout.TALL.x(), SET_X = StandardLayout.SET_X, SET_W = StandardLayout.SET_W, STEP_A = StandardLayout.STEP_A,
          STEP_B = StandardLayout.STEP_B;
    private static final int CONSUMPTION = 0xFFFFFF55;

    private StorageViews() {
    }

    /** La tile si elle est de la classe attendue (la fiche choisit la vue), sinon null. */
    @Nullable
    static <T> T as(Class<T> type, BlockEntity be) {
        return type.isInstance(be) ? type.cast(be) : null;
    }

    // ------------------------------------------------------------------
    // Buffers a jauge
    // ------------------------------------------------------------------

    static final MachineScreens.View GAUGE_BUFFER = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            BufferTile buffer = as(BufferTile.class, be);
            if (buffer != null) {
                buffer(gui, g, mouseX, mouseY, buffer);
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            BufferTile buffer = as(BufferTile.class, be);
            if (buffer != null && !buffer.getIoSlots().isEmpty()) {
                GuiMachine.arrow(g, BufferTile.IO_X + 8, BufferTile.IN_Y + 20, BufferTile.OUT_Y - 4);
                gui.slotTip(mouseX, mouseY, BufferTile.IO_X, BufferTile.IN_Y, "gui.pioneers.machine.in");
                gui.slotTip(mouseX, mouseY, BufferTile.IO_X, BufferTile.OUT_Y, "gui.pioneers.machine.out");
            }
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Nullable
        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            BufferTile buffer = as(BufferTile.class, be);
            return buffer == null ? null : bufferInfo(gui, buffer);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_buffer";
        }
    };

    static final MachineScreens.View ITEM_BUFFER = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Nullable
        @Override
        public Component titleNote(GuiMachine gui, BlockEntity be) {
            //Compte court (le nom du buffer garde sa place) ; le detail est dans l'onglet Infos
            return GuiMachine.literal(gui.value(UiValues.SLOTS_USED) + " / " + gui.value(UiValues.SLOTS));
        }

        @Nullable
        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            BufferTile buffer = as(BufferTile.class, be);
            return buffer == null ? null : bufferInfo(gui, buffer);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_buffer";
        }
    };

    /** Lignes de l'onglet Infos d'un buffer : tier, contenance, debit du tier (ejection), piles par case. */
    private static List<StandardView.InfoRow> bufferInfo(GuiMachine gui, BufferTile buffer) {
        StorageUnit u = buffer.unit();
        BufferType type = u.type;
        List<StandardView.InfoRow> rows = new ArrayList<>();
        String unit = u.resource().unit;
        long capacity = type.capacity(u.tier);
        Component cap = switch (type) {
            case ITEM -> Component.translatable("gui.pioneers.api.info.slots_value", capacity, type.stackMultiplier(u.tier, buffer.creative()));
            case HEAT -> GuiMachine.literal(Texts.format(capacity) + " J/K");
            case PRESSURE -> GuiMachine.literal(Texts.format(capacity) + " L · " + String.format(Locale.ROOT, "%.0f bar", type.maxPressure(u.tier)));
            default -> GuiMachine.literal(Texts.format(capacity) + " " + unit);
        };
        rows.add(new StandardView.InfoRow("capacity", cap, PiMenuScreen.TEXT));
        if (type != BufferType.HEAT) {
            String per = type == BufferType.ITEM ? "/t" : " " + (type == BufferType.PRESSURE ? "L" : unit) + "/t";
            rows.add(new StandardView.InfoRow("tier_rate", GuiMachine.literal(GuiMachine.compact(u.tierRate()) + per), PiMenuScreen.TEXT));
        } else {
            rows.add(new StandardView.InfoRow("heat_loss", GuiMachine.literal(String.format(Locale.ROOT, "%.2f", type.heatLoss(u.tier)) + " J/t/K"),
                  PiMenuScreen.TEXT));
        }
        if (buffer.creative()) {
            rows.add(new StandardView.InfoRow("creative", Component.translatable("gui.pioneers.api.info.creative_value"), 0xFFB040FF));
        }
        return rows;
    }

    private static void buffer(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BufferTile buffer) {
        BufferType type = buffer.getBufferType();
        Rect s = SCREEN, c = StandardLayout.bufferCard(!buffer.getIoSlots().isEmpty()), q = GAUGE;
        List<GuiMachine.Row> rows = new ArrayList<>();
        switch (type) {
            case FLUID, GAS, ENERGY -> {
                long amount = gui.value(UiValues.STORED), capacity = gui.value(UiValues.STORED_MAX);
                double ratio = capacity <= 0 ? 0 : amount / (double) capacity, rate = gui.value(UiValues.FLOW) / 100.0;
                String unit = type == BufferType.ENERGY ? "FE" : "mB";
                Component name = contentName(buffer);
                int color = contentColor(buffer);
                rows.add(GuiMachine.Row.of("gui.pioneers.info.content", GuiMachine.literal(Texts.format(amount) + " / " + Texts.format(capacity) + " " + unit)));
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.flow"), GuiMachine.flow(rate, unit), GuiMachine.flowColor(rate)));
                List<Component> tip = List.of(name == null ? Component.translatable("gui.pioneers.buffer.empty") : name,
                      Component.translatable("gui.pioneers.buffer.amount", Texts.format(amount), Texts.format(capacity), unit));
                gauge(gui, g, mouseX, mouseY, q.x(), q.y(), q.w(), q.h(), ratio, buffer, tip);
                card(gui, g, c, GuiMachine.literal(type == BufferType.ENERGY ? Texts.energy(amount) : Texts.format(amount) + " " + unit),
                      Component.translatable("gui.pioneers.api.card.of", type == BufferType.ENERGY ? Texts.energy(capacity) : Texts.format(capacity) + " " + unit),
                      Component.translatable("gui.pioneers.api.card.tier_rate", GuiMachine.compact(buffer.unit().tierRate()) + " " + unit + "/t"), color);
                gui.info(g, s.x(), s.y(), s.w(), s.h(), name == null ? 0 : color, name == null ? Component.translatable("gui.pioneers.buffer.empty") : name,
                      GuiMachine.percent(ratio), ratio, color, rows, false);
            }
            case HEAT -> {
                double temperature = gui.value(UiValues.TEMPERATURE) / 100.0, rate = gui.value(UiValues.FLOW) / 100.0;
                double ratio = Math.max(0, Math.min(1, (temperature - 300) / 1_500));
                Component kelvin = GuiMachine.literal(String.format(Locale.ROOT, "%.0f K", temperature));
                rows.add(GuiMachine.Row.of("gui.pioneers.info.heat_capacity", GuiMachine.literal(Texts.format(gui.value(UiValues.HEAT_CAPACITY)) + " J/K")));
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.flow"), GuiMachine.flow(rate, "J"), GuiMachine.flowColor(rate)));
                gui.thermometer(g, mouseX, mouseY, q.x(), q.y(), q.w(), q.h(), temperature, -1, List.of(kelvin));
                card(gui, g, c, kelvin, Component.translatable("gui.pioneers.api.card.heat",
                            Texts.format(Math.round((temperature - 300) * gui.value(UiValues.HEAT_CAPACITY)))),
                      Component.translatable("gui.pioneers.api.card.heat_loss", String.format(Locale.ROOT, "%.2f", buffer.getBufferType().heatLoss(buffer.getTier()))),
                      0xFF5A1F);
                gui.info(g, s.x(), s.y(), s.w(), s.h(), 0xFF5A1F, Component.translatable("gui.pioneers.info.heat"), kelvin, ratio, 0xD2492A, rows, false);
            }
            case PRESSURE -> {
                double air = gui.value(UiValues.AIR) / 100.0, maxAir = gui.value(UiValues.AIR_MAX) / 100.0, rate = gui.value(UiValues.FLOW) / 100.0;
                double pressure = gui.value(UiValues.PRESSURE) / 1000.0, max = gui.value(UiValues.PRESSURE_MAX) / 1000.0;
                double ratio = max <= 0 ? 0 : pressure / max;
                Component bar = GuiMachine.literal(String.format(Locale.ROOT, "%.2f bar", pressure));
                rows.add(GuiMachine.Row.of("gui.pioneers.info.air", GuiMachine.literal(Texts.format(Math.round(air)) + " / " + Texts.format(Math.round(maxAir)) + " L")));
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.flow"), GuiMachine.flow(rate, "L"), GuiMachine.flowColor(rate)));
                gui.manometer(g, mouseX, mouseY, q.x(), q.y(), q.w(), q.h(), ratio, -1, List.of(bar));
                card(gui, g, c, bar, Component.translatable("gui.pioneers.api.card.of", String.format(Locale.ROOT, "%.0f bar", max)),
                      Component.translatable("gui.pioneers.api.card.tier_rate", GuiMachine.compact(buffer.unit().tierRate()) + " L/t"), 0x9FB8C8);
                gui.info(g, s.x(), s.y(), s.w(), s.h(), 0x9FB8C8, Component.translatable("gui.pioneers.info.compressed_air"), bar, ratio, 0x8FB0C8, rows, false);
            }
            default -> {
            }
        }
    }

    /** Carte du contenu : la valeur en grand, puis deux lignes (contenance, debit du tier). */
    private static void card(GuiMachine gui, GuiGraphics g, Rect r, Component big, Component line1, Component line2, int color) {
        gui.screenAt(g, r.x(), r.y(), r.w(), r.h());
        g.fill(r.x() + 5, r.y() + 5, r.x() + 8, r.y() + 8, 0xFF000000 | color);
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.info.content"), r.x() + 11, r.y() + 4.5F, 0.6F, PiMenuScreen.DIM, r.w() - 16);
        GuiDraw.center(g, gui.font(), big, r.x() + r.w() / 2F, r.y() + 16, 1.15F, PiMenuScreen.SCREEN_TEXT, r.w() - 8);
        g.fill(r.x() + 5, r.y() + 30, r.x() + r.w() - 5, r.y() + 31, 0xFF24333E);
        GuiDraw.center(g, gui.font(), line1, r.x() + r.w() / 2F, r.y() + 35, 0.62F, PiMenuScreen.TEXT, r.w() - 8);
        GuiDraw.center(g, gui.font(), line2, r.x() + r.w() / 2F, r.y() + 45, 0.62F, PiMenuScreen.DIM, r.w() - 8);
    }

    /** Jauge de contenu (fluide : sa texture et sa teinte ; gaz : sa texture teintee ; energie : rouge). */
    private static void gauge(GuiMachine gui, GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, double ratio, BufferTile buffer,
          List<Component> tip) {
        gui.frame(graphics, x, y, w, h);
        int filled = (int) Math.round((h - 2) * Math.max(0, Math.min(1, ratio)));
        FluidTank fluidTank = buffer.getFluidTank();
        BasicGasTank gasTank = buffer.getGasTank();
        if (filled > 0) {
            TextureAtlasSprite sprite = null;
            int tint = 0xFFC8383A;
            if (fluidTank != null && !fluidTank.isEmpty()) {
                FluidStack stack = fluidTank.getFluid();
                IClientFluidTypeExtensions fluid = IClientFluidTypeExtensions.of(stack.getFluid());
                sprite = GuiMachine.atlas(fluid.getStillTexture(stack));
                tint = fluid.getTintColor(stack) | 0xFF000000;
            } else if (gasTank != null && !gasTank.getStack().isEmpty()) {
                GasStack stack = gasTank.getStack();
                sprite = GuiMachine.atlas(stack.getGas().getIcon());
                tint = stack.getGas().getTint() | 0xFF000000;
            }
            if (sprite != null) {
                GuiMachine.tiled(graphics, sprite, tint, x + 1, y + h - 1 - filled, w - 2, filled);
            } else if (buffer.getBufferType() == BufferType.FLUID || buffer.getBufferType() == BufferType.GAS) {
                //Contenu pas encore recu par le client (la quantite arrive par le menu, la nature par la synchro du bloc) : rien a dessiner
                filled = 0;
            } else {
                graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, tint);
                graphics.fill(x + 1, y + h - 1 - filled, x + 3, y + h - 1, 0xFFE05A58);
            }
            if (filled > 0) {
                graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, 0x60FFFFFF);
            }
        }
        GuiMachine.ticks(graphics, x, y, w, h);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(tip);
        }
    }

    @Nullable
    private static Component contentName(BufferTile buffer) {
        if (buffer.getFluidTank() != null && !buffer.getFluidTank().isEmpty()) {
            return buffer.getFluidTank().getFluid().getDisplayName();
        }
        if (buffer.getGasTank() != null && !buffer.getGasTank().getStack().isEmpty()) {
            return buffer.getGasTank().getStack().getGas().getName();
        }
        return buffer.getBufferType() == BufferType.ENERGY ? Component.translatable("gui.pioneers.buffer.energy") : null;
    }

    /** Couleur de la pastille et de la barre : teinte du fluide / du gaz, rouge pour l'energie. */
    private static int contentColor(BufferTile buffer) {
        if (buffer.getFluidTank() != null && !buffer.getFluidTank().isEmpty()) {
            FluidStack stack = buffer.getFluidTank().getFluid();
            int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack) & 0xFFFFFF;
            //Teinte blanche (eau, lave, fluides du mod : couleur dans la texture) : couleur moyenne approchee
            int own = PiFluids.averageColor(stack.getFluid());
            if (tint == 0xFFFFFF && own != 0) {
                return own;
            }
            return tint == 0xFFFFFF ? stack.getFluid().getFluidType().getTemperature() > 1000 ? 0xE0631E : 0x3F76E4 : tint;
        }
        if (buffer.getGasTank() != null && !buffer.getGasTank().getStack().isEmpty()) {
            return buffer.getGasTank().getStack().getGas().getTint() & 0xFFFFFF;
        }
        //Rouge reserve a l'energie ; fluide/gaz pas encore synchronise : gris neutre
        return buffer.getBufferType() == BufferType.ENERGY ? 0xE04848 : 0x8A9099;
    }

    // ------------------------------------------------------------------
    // Banque de donnees (lot 4 : interface v2, valeurs nommees de DataBankUnit)
    // ------------------------------------------------------------------

    static final MachineScreens.View DATA_BANK = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            TileEntityDataBank bank = as(TileEntityDataBank.class, be);
            if (bank != null) {
                dataBank(gui, g, mouseX, mouseY, bank);
            }
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Nullable
        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            TileEntityDataBank bank = as(TileEntityDataBank.class, be);
            if (bank == null) {
                return null;
            }
            boolean creative = bank.getTier().isCreative();
            List<StandardView.InfoRow> rows = new ArrayList<>();
            rows.add(new StandardView.InfoRow("capacity", creative ? GuiMachine.literal("\u221e") : DataUnits.format(gui.value(UiValues.STORED_MAX)), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("tier_rate", DataUnits.format(gui.value(DataBankUnit.RATE)).copy().append("/t"), PiMenuScreen.TEXT));
            if (creative) {
                rows.add(new StandardView.InfoRow("creative", Component.translatable("gui.pioneers.api.info.creative_value"), 0xFFB040FF));
            }
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_buffer";
        }
    };

    /** Grande jauge de donnees (degrade de la couleur du type + trame de bits), carte du contenu, ecran d'etat. */
    private static void dataBank(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, TileEntityDataBank bank) {
        long stored = gui.value(UiValues.STORED), capacity = gui.value(UiValues.STORED_MAX), rate = gui.value(DataBankUnit.RATE);
        int id = (int) gui.value(DataBankUnit.DATA_TYPE);
        DataType type = id < 0 ? null : ((ForgeRegistry<DataType>) PioneersAPI.dataTypeRegistry()).getValue(id);
        boolean creative = bank.getTier().isCreative();
        double ratio = creative ? (type == null ? 0 : 1) : capacity <= 0 ? 0 : Math.min(1, stored / (double) capacity);
        int color = type == null ? 0x55606F : type.getColor() & 0xFFFFFF;
        Rect q = GAUGE;
        int gx = q.x(), gy = q.y(), gw = q.w(), gh = q.h();
        gui.frame(g, gx, gy, gw, gh);
        int filled = (int) Math.round((gh - 2) * ratio);
        for (int i = 0; i < filled; i++) {
            float k = 0.62F + 0.45F * i / Math.max(1, filled);
            g.fill(gx + 1, gy + gh - 2 - i, gx + gw - 1, gy + gh - 1 - i, 0xFF000000 | GuiDraw.darken(color, Math.min(1F, k)));
        }
        if (filled > 0) {
            for (int y = 0; y < filled; y += 16) {
                int h = Math.min(16, filled - y), top = gy + gh - 1 - y - h;
                GuiSprites.region(g, "gauge_data_bits", gx + 1, top, 12, h, 0, 16 - h, 12, h, 12, 16);
                GuiSprites.region(g, "gauge_data_bits", gx + 12, top, 11, h, 0, 16 - h, 11, h, 12, 16);
            }
            g.fill(gx + 1, gy + gh - 1 - filled, gx + gw - 1, gy + gh - filled, 0x60FFFFFF);
        }
        GuiMachine.ticks(g, gx, gy, gw, gh);
        Component name = type == null ? Component.translatable("data.pioneers.empty") : type.getName();
        Component inf = GuiMachine.literal("\u221e");
        if (gui.over(mouseX, mouseY, gx, gy, gw, gh)) {
            gui.tooltip(List.of(name.copy().withStyle(style -> style.withColor(color)),
                  Component.translatable("data.pioneers.gauge", DataUnits.format(stored), creative ? inf : DataUnits.format(capacity))
                        .withStyle(ChatFormatting.GRAY)));
        }
        card(gui, g, StandardLayout.bufferCard(false), creative && type != null ? inf : DataUnits.format(stored),
              Component.translatable("gui.pioneers.api.card.of", creative ? inf : DataUnits.format(capacity)),
              Component.translatable("gui.pioneers.api.card.tier_rate", DataUnits.format(rate).getString() + "/t"), color);
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(GuiMachine.Row.of("gui.pioneers.info.content", creative && type != null ? inf : DataUnits.format(stored)));
        rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.max_rate"), DataUnits.format(rate * 20).copy().append("/s"),
              GuiMachine.FLOW_UP_COLOR));
        Rect s = SCREEN;
        gui.info(g, s.x(), s.y(), s.w(), s.h(), type == null ? 0 : color, name, GuiMachine.percent(ratio), ratio, type == null ? 0x55606F : color, rows,
              false);
    }

    // ------------------------------------------------------------------
    // Radiateur et compresseur d'air
    // ------------------------------------------------------------------

    /** Reserve d'energie, batterie dessous (place commune de l'interface v2). */
    private static void energy(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY) {
        gui.energy(g, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
    }

    static final MachineScreens.View HEATER = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            energy(gui, g, mouseX, mouseY);
            double temperature = gui.value(UiValues.TEMPERATURE) / 100.0;
            int power = (int) gui.value(UiValues.POWER_SETTING), target = (int) gui.value(UiValues.TARGET);
            Component kelvin = GuiMachine.literal(String.format(Locale.ROOT, "%.0f K", temperature));
            gui.thermometer(g, mouseX, mouseY, TALL_X, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H, temperature, target,
                  List.of(Component.translatable("gui.pioneers.heater.temperature", String.format(Locale.ROOT, "%.0f", temperature), target)));
            caption(gui, g, "gui.pioneers.info.power", STEP_A);
            gui.stepper(g, mouseX, mouseY, SET_X, STEP_A, SET_W, Component.translatable("gui.pioneers.heater.power", power),
                  Component.translatable("gui.pioneers.info.power"), ElectricHeaterTile.POWER_DOWN, ElectricHeaterTile.POWER_UP);
            caption(gui, g, "gui.pioneers.info.target_temperature", STEP_B);
            gui.stepper(g, mouseX, mouseY, SET_X, STEP_B, SET_W, Component.translatable("gui.pioneers.heater.target", target),
                  Component.translatable("gui.pioneers.info.target_temperature"), ElectricHeaterTile.TARGET_DOWN, ElectricHeaterTile.TARGET_UP);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.produced"),
                  GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.PRODUCED) / 100.0) + " J/t"), 0xFFFFB050));
            rows.add(GuiMachine.Row.of("gui.pioneers.info.delivered", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.DELIVERED) / 100.0) + " J/t")));
            double ratio = target <= 300 ? 0 : (temperature - 300) / (target - 300.0);
            Rect s = SCREEN;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), 0xFF5A1F, Component.translatable("gui.pioneers.info.temperature"), kelvin, ratio, 0xD2492A, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.machine.battery");
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            rows.add(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.USAGE) / 100.0) + " FE/t"), CONSUMPTION));
            rows.add(new StandardView.InfoRow("max_temperature", GuiMachine.literal(gui.value(UiValues.MAX_TEMPERATURE) + " K"), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_heater";
        }
    };

    static final MachineScreens.View COMPRESSOR = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            energy(gui, g, mouseX, mouseY);
            double pressure = gui.value(UiValues.PRESSURE) / 1000.0, target = gui.value(UiValues.TARGET_PRESSURE) / 1000.0,
                  max = gui.value(UiValues.PRESSURE_MAX) / 1000.0;
            Component bar = GuiMachine.literal(String.format(Locale.ROOT, "%.2f bar", pressure));
            gui.manometer(g, mouseX, mouseY, TALL_X, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H, max <= 0 ? 0 : pressure / max,
                  max <= 0 ? -1 : target / max, List.of(Component.translatable("gui.pioneers.compressor.pressure", String.format(Locale.ROOT, "%.2f", pressure),
                        String.format(Locale.ROOT, "%.1f", target))));
            caption(gui, g, "gui.pioneers.info.target_pressure", STEP_A);
            gui.stepper(g, mouseX, mouseY, SET_X, STEP_A, SET_W, GuiMachine.literal(String.format(Locale.ROOT, "%.1f bar", target)),
                  Component.translatable("gui.pioneers.info.target_pressure"), AirCompressorTile.TARGET_DOWN, AirCompressorTile.TARGET_UP);
            //Contenu du volume d'air (sous le reglage)
            gui.screenAt(g, SET_X, STEP_B - 6, SET_W, 26);
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.info.air"), SET_X + 5, STEP_B - 2, 0.6F, PiMenuScreen.DIM, 60);
            Component air = GuiMachine.literal(Texts.format(Math.round(gui.value(UiValues.AIR) / 100.0)) + " L");
            GuiDraw.right(g, gui.font(), air, SET_X + SET_W - 5, STEP_B - 2.5F, 0.7F, PiMenuScreen.SCREEN_TEXT);
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.info.max_pressure"), SET_X + 5, STEP_B + 9, 0.6F, PiMenuScreen.DIM, 60);
            GuiDraw.right(g, gui.font(), GuiMachine.literal(String.format(Locale.ROOT, "%.0f bar", max)), SET_X + SET_W - 5, STEP_B + 8.5F, 0.7F,
                  PiMenuScreen.TEXT);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.compressed"),
                  GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.PRODUCED) / 100.0) + " L/t"), 0xFFA8E4F0));
            rows.add(GuiMachine.Row.of("gui.pioneers.info.delivered", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.DELIVERED) / 100.0) + " L/t")));
            Rect s = SCREEN;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), 0x9FB8C8, Component.translatable("gui.pioneers.info.pressure"), bar, target <= 0 ? 0 : pressure / target,
                  0x8FB0C8, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.machine.battery");
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            rows.add(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.USAGE) / 100.0) + " FE/t"), CONSUMPTION));
            rows.add(new StandardView.InfoRow("max_pressure", GuiMachine.literal(String.format(Locale.ROOT, "%.0f bar", gui.value(UiValues.PRESSURE_MAX) / 1000.0)),
                  PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_compressor";
        }
    };

    /** Legende d'un reglage (au-dessus de lui). */
    private static void caption(GuiMachine gui, GuiGraphics g, String key, int stepY) {
        GuiDraw.text(g, gui.font(), Component.translatable(key), SET_X + 1, stepY - 7, 0.6F, PiMenuScreen.DIM, SET_W);
    }
}
