package fr.pioneers.logistics.robotics.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.network.PacketRobotUnit;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.RobotTask;
import fr.pioneers.logistics.robotics.RobotUnitEntity;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.UnitCargo;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Fiche d'une unite (Maj + clic droit sur elle) : nom, etat, travail en cours, soute, rappel au hub.
 */
public class UnitScreen extends RoboticsScreen {

    private static final int W = 214, H = 232, COLUMNS = 9, MAX_ROWS = 3;

    private CompoundTag info;
    private final UnitCargo cargo = new UnitCargo(54, 0, 0);
    private EditBox nameBox;
    private String sentName;
    private int ticks;
    private boolean recalled;

    public UnitScreen(CompoundTag info) {
        super(Component.translatable("robotics.pioneers.unit_screen.title"), null, W, H);
        this.dark = true;
        update(info);
    }

    public int entityId() {
        return info.getInt("id");
    }

    public void update(CompoundTag tag) {
        this.info = tag;
        cargo.load(tag.getCompound("cargo"));
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setValue(tag.getString("name"));
            sentName = tag.getString("name");
        }
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, left + 30, top + 20, panelWidth - 38, 14, Component.translatable("robotics.pioneers.unit_screen.name"));
        nameBox.setMaxLength(PacketRobotUnit.NAME_LENGTH);
        nameBox.setValue(info.getString("name"));
        nameBox.setHint(Component.translatable("robotics.pioneers.unit_screen.name_hint"));
        sentName = info.getString("name");
        addRenderableWidget(nameBox);
    }

    @Override
    public void tick() {
        super.tick();
        nameBox.tick();
        if (++ticks % 20 == 0) {
            send(PacketRobotUnit.Action.REFRESH, "");
        }
        //Plus d'unite (rentree, detruite) : la fiche se ferme
        if (minecraft.level != null && !(minecraft.level.getEntity(entityId()) instanceof RobotUnitEntity)) {
            onClose();
        }
    }

    private void send(int action, String name) {
        PiNetwork.sendToServer(new PacketRobotUnit.Action(entityId(), action, name));
    }

    private void commitName() {
        String value = nameBox.getValue().trim();
        if (!value.equals(sentName)) {
            sentName = value;
            send(PacketRobotUnit.Action.RENAME, value);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            commitName();
            nameBox.setFocused(false);
            setFocused(null);
            return true;
        }
        if (nameBox.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            return nameBox.keyPressed(key, scan, modifiers) || nameBox.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        commitName();
        super.onClose();
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UnitKind kind = UnitKind.values()[Math.min(info.getByte("kind"), UnitKind.values().length - 1)];
        UnitTier tier = UnitTier.values()[Math.min(info.getByte("tier"), UnitTier.values().length - 1)];
        ItemStack icon = new ItemStack(PiRobotics.UNITS.get(kind).get(tier).get());
        int x = left + 8, w = panelWidth - 16;
        darkSlot(graphics, x + 1, top + 19);
        graphics.renderItem(icon, x + 1, top + 19);
        int y = top + 40;
        //Modele et etat
        GuiDraw.text(graphics, font, icon.getHoverName(), x, y, 0.7F, D_DIM, w);
        y += 9;
        RobotTask task = RobotTask.values()[Math.min(info.getByte("task"), RobotTask.values().length - 1)];
        String phase = PHASES[Math.min(info.getByte("phase"), PHASES.length - 1)];
        Component state = Component.translatable(task.getTranslationKey()).append(" · ")
              .append(Component.translatable("robotics.pioneers.unit.phase." + phase));
        GuiDraw.text(graphics, font, state, x, y, 0.7F, D_TEXT, w);
        y += 11;
        //Batterie
        float battery = info.getFloat("battery");
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.unit_screen.battery", Math.round(battery * 100)), x, y, 0.65F, D_DIM, 70);
        int bx = x + 64, bw = w - 64;
        graphics.fill(bx, y, bx + bw, y + 6, 0xFF0E1419);
        int color = battery > 0.5F ? 0xFF3CC596 : battery > 0.2F ? 0xFFFFB74D : 0xFFE57373;
        graphics.fill(bx, y, bx + Math.round(bw * Math.max(0, Math.min(1, battery))), y + 6, color);
        graphics.renderOutline(bx, y, bw, 6, D_LINE);
        y += 12;
        //Travail en cours
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.unit_screen.job"));
        Component job = Component.Serializer.fromJson(info.getString("job"));
        y += wrapped(graphics, job == null ? Component.literal("-") : job, x, y, 0.7F, D_TEXT, w, 3) + 1;
        int orderId = info.getInt("order");
        if (orderId >= 0) {
            RoboticOrder order = ClientRobotics.orders().stream().filter(o -> o.id == orderId).findFirst().orElse(null);
            if (order != null) {
                y += wrapped(graphics, Component.translatable("robotics.pioneers.unit_screen.order", OrderTexts.title(order)), x, y, 0.65F, D_DIM, w, 2);
            }
        }
        if (info.contains("target")) {
            BlockPos target = BlockPos.of(info.getLong("target"));
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.unit_screen.target", target.getX(), target.getY(), target.getZ()), x, y,
                  0.65F, D_FAINT, w);
            y += 8;
        }
        y += 3;
        //Soute
        int stacks = Math.max(1, info.getInt("stacks"));
        long used = cargo.items.stream().filter(stack -> !stack.isEmpty()).count();
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.unit_screen.cargo", used, stacks));
        int shown = Math.min(stacks, COLUMNS * MAX_ROWS);
        int gx = x + (w - COLUMNS * 18) / 2 + 1;
        for (int i = 0; i < shown; i++) {
            int sx = gx + (i % COLUMNS) * 18, sy = y + (i / COLUMNS) * 18 + 1;
            darkSlot(graphics, sx, sy);
            if (i < cargo.items.size()) {
                ItemStack stack = cargo.items.get(i);
                graphics.renderItem(stack, sx, sy);
                graphics.renderItemDecorations(font, stack, sx, sy);
                if (over(mouseX, mouseY, sx, sy, 16, 16)) {
                    tooltip(getTooltipFromItem(minecraft, stack));
                }
            }
        }
        y += ((shown + COLUMNS - 1) / COLUMNS) * 18 + 2;
        if (used > shown) {
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.unit_screen.more", used - shown), x, y, 0.6F, D_DIM, w);
            y += 8;
        }
        //Fluide, gaz, energie
        StringBuilder extra = new StringBuilder();
        if (!cargo.fluid.isEmpty()) {
            extra.append(cargo.fluid.getDisplayName().getString()).append(' ').append(cargo.fluid.getAmount()).append(" mB  ");
        }
        if (!cargo.gas.isEmpty()) {
            extra.append(cargo.gas.getGas().getName().getString()).append(' ').append(cargo.gas.getAmount()).append(" mB  ");
        }
        if (cargo.energy > 0) {
            extra.append(String.format(Locale.ROOT, "%,d FE  ", cargo.energy));
        }
        if (cargo.air > 0) {
            extra.append(String.format(Locale.ROOT, "%,d L", cargo.air));
        }
        if (!extra.isEmpty()) {
            GuiDraw.text(graphics, font, Component.literal(extra.toString().trim()), x, y, 0.65F, D_TEXT, w);
        }
        //Boutons
        int by = top + panelHeight - 20;
        boolean home = info.getByte("phase") == RobotUnitEntity.Phase.RETURNING.ordinal();
        button(graphics, mouseX, mouseY, x, by, 60, 14, Component.translatable("gui.done"), true, this::onClose);
        primary(graphics, mouseX, mouseY, x + w - 110, by, 110, 14,
              Component.translatable(recalled || home ? "robotics.pioneers.unit_screen.recalled" : "robotics.pioneers.unit_screen.recall"),
              !recalled && !home, () -> {
                  recalled = true;
                  send(PacketRobotUnit.Action.RECALL, "");
              });
        if (!recalled && !home) {
            tooltip(mouseX, mouseY, x + w - 110, by, 110, 14, Component.translatable("robotics.pioneers.unit_screen.recall.tooltip"));
        }
    }

    private static final String[] PHASES = Arrays.stream(RobotUnitEntity.Phase.values()).map(p -> p.name().toLowerCase(Locale.ROOT))
          .toArray(String[]::new);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!nameBox.isMouseOver(mouseX, mouseY) && nameBox.isFocused()) {
            commitName();
            nameBox.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
