package fr.pioneers.logistics.pneumatic.client;

import fr.pioneers.logistics.pneumatic.network.PneumaticNet;
import fr.pioneers.logistics.pneumatic.network.TubeStationTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Ecran d'une station de tubes pneumatiques : son nom (envoye a la fermeture) et la liste des stations nommees joignables, avec la
 * longueur du trajet et l'air qu'il coute ; un clic part vers la station choisie (bouton grise si l'air manque). Molette : faire defiler.
 */
public class TubeStationScreen extends Screen {

    private static final int W = 280, BG = 0xF0141A20, FRAME = 0xFF3C4A58, ACCENT = 0xFFC9A03C, TEXT = 0xFFE0E6EC, DIM = 0xFF8C98A4,
          GOOD = 0xFF6CD48A, BAD = 0xFFE0A050;
    private static final int ROWS = 7, ROW = 18, LIST_Y = 62;
    private final PneumaticNet.StationData data;
    private final List<Button> rows = new ArrayList<>();
    private EditBox name;
    private int left, top, panelH, scroll;
    private boolean departed;

    public TubeStationScreen(PneumaticNet.StationData data) {
        super(Component.translatable("block.pioneers.pneumatic_tube_station"));
        this.data = data;
    }

    @Override
    protected void init() {
        panelH = LIST_Y + ROWS * ROW + 44;
        left = (width - W) / 2;
        top = (height - panelH) / 2;
        name = addRenderableWidget(new EditBox(font, left + 10, top + 30, W - 20, 14, Component.translatable("screen.pioneers.tube_station.name")));
        name.setMaxLength(TubeStationTile.NAME_LENGTH);
        name.setValue(data.name());
        rows.clear();
        for (int i = 0; i < ROWS; i++) {
            int index = i;
            Button button = Button.builder(Component.empty(), b -> travel(index)).bounds(left + 10, top + LIST_Y + i * ROW, 130, 16).build();
            rows.add(addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W / 2 - 40, top + panelH - 22, 80, 16)
              .build());
        refreshRows();
        setInitialFocus(name);
    }

    private void refreshRows() {
        for (int i = 0; i < ROWS; i++) {
            Button button = rows.get(i);
            int index = scroll + i;
            if (index < data.destinations().size()) {
                PneumaticNet.Destination d = data.destinations().get(index);
                button.visible = true;
                button.active = d.affordable();
                button.setMessage(Component.literal(d.name()));
                button.setTooltip(d.affordable() ? null : Tooltip.create(Component.translatable("screen.pioneers.tube_station.too_expensive")));
            } else {
                button.visible = false;
            }
        }
    }

    private void travel(int row) {
        int index = scroll + row;
        if (index >= data.destinations().size()) {
            return;
        }
        PneumaticNet.CHANNEL.sendToServer(new PneumaticNet.StationEdit(data.pos(), name.getValue().trim()));
        PneumaticNet.CHANNEL.sendToServer(new PneumaticNet.Travel(data.pos(), data.destinations().get(index).pos()));
        departed = true;
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, data.destinations().size() - ROWS);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            refreshRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left - 1, top - 1, left + W + 1, top + panelH + 1, FRAME);
        graphics.fill(left, top, left + W, top + panelH, BG);
        graphics.fill(left, top, left + W, top + 2, ACCENT);
        graphics.drawString(font, getTitle(), left + 10, top + 8, ACCENT, false);
        graphics.drawString(font, Component.translatable("screen.pioneers.tube_station.name"), left + 10, top + 20, DIM, false);
        graphics.drawString(font, Component.translatable("screen.pioneers.tube_station.destinations"), left + 10, top + LIST_Y - 11, DIM, false);
        if (data.destinations().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.pioneers.tube_station.none"), left + 10, top + LIST_Y + 4, DIM, false);
        }
        for (int i = 0; i < ROWS && scroll + i < data.destinations().size(); i++) {
            PneumaticNet.Destination d = data.destinations().get(scroll + i);
            int y = top + LIST_Y + i * ROW + 4;
            Component distance = Component.translatable("screen.pioneers.tube_station.distance", d.distance(), Math.max(1, Math.round(d.distance() / 25F)));
            Component cost = Component.translatable("screen.pioneers.tube_station.cost", d.cost());
            graphics.drawString(font, distance, left + 146, y, TEXT, false);
            graphics.drawString(font, cost, left + W - 10 - font.width(cost), y, d.affordable() ? GOOD : BAD, false);
        }
        if (data.destinations().size() > ROWS) {
            //Barre de defilement
            int trackTop = top + LIST_Y, trackH = ROWS * ROW - 2;
            int barH = Math.max(8, trackH * ROWS / data.destinations().size());
            int barY = trackTop + (trackH - barH) * scroll / Math.max(1, data.destinations().size() - ROWS);
            graphics.fill(left + W - 4, trackTop, left + W - 2, trackTop + trackH, 0xFF22303C);
            graphics.fill(left + W - 4, barY, left + W - 2, barY + barH, ACCENT);
        }
        Component pressure = Component.translatable("screen.pioneers.tube_station.air", String.format(Locale.ROOT, "%.2f", data.pressure()), data.air());
        boolean pressurized = data.pressure() >= TubeStationTile.MIN_PRESSURE;
        graphics.drawString(font, pressure, left + 10, top + panelH - 38, pressurized ? TEXT : BAD, false);
        if (!pressurized) {
            Component low = Component.translatable("screen.pioneers.tube_station.low_pressure");
            graphics.drawString(font, low, left + W - 10 - font.width(low), top + panelH - 38, BAD, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (!departed) {
            PneumaticNet.CHANNEL.sendToServer(new PneumaticNet.StationEdit(data.pos(), name.getValue().trim()));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
