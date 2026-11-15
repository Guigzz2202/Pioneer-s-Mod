package fr.pioneers.client.robotics;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.network.PacketRoboticsMap;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.robotics.LogisticResource;
import fr.pioneers.robotics.Resource;
import fr.pioneers.robotics.logistics.MarkerRole;
import fr.pioneers.robotics.logistics.PointConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Reglages d'un point logistique (balise ou coffre logistique), sur place ou a distance depuis la carte, de haut en bas :
 * <ul>
 *     <li>nom et role (cinq cartes de couleur, la description du role choisi en dessous) ;</li>
 *     <li>face d'acces (balise), nommee par rapport a la machine (avant, arriere, gauche, droite, dessus, dessous) ;</li>
 *     <li>les ressources en liste : une ligne par ressource avec sa quantite (demandes) et sa face (menu deroulant), un clic sur l'icone
 *     pour la changer, une croix pour l'enlever, « + Ajouter » en bas ;</li>
 *     <li>options : priorite, mode machine, condition sur les stocks du reseau.</li>
 * </ul>
 */
public class PointEditScreen extends RoboticsScreen {

    private static final int ROW = 20, VISIBLE_ROWS = 5;

    private final BlockPos pos;
    /** Bloc servi (machine, coffre) : ses faces sont nommees par rapport a son avant. */
    private final BlockPos served;
    private final PointConfig config;
    private final ItemStack icon;
    private final String blockName;
    private final boolean marker;
    private EditBox nameBox, thresholdBox;
    private final EditBox[] amountBoxes = new EditBox[PointConfig.MAX_ENTRIES];
    private int listScroll;
    /** Menu deroulant des faces ouvert sur cette ligne (-1 : ferme). */
    private int faceMenu = -1;
    private int faceMenuX, faceMenuY;
    private int listX, listY, listW;
    /** Defilement de toute la page (1.0) : sur un petit ecran, le panneau ne tient pas en hauteur. */
    private int pageScroll, contentHeight = 400, bodyTop, bodyBottom;

    public PointEditScreen(@Nullable Screen parent, BlockPos pos, BlockPos served, PointConfig config, ItemStack icon, String blockName,
          boolean marker) {
        super(Component.translatable("robotics.pioneers.point.title"), parent, 330, 300);
        this.pos = pos;
        this.served = served;
        this.config = config;
        this.icon = icon;
        this.blockName = blockName;
        this.marker = marker;
        dark = true;
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, left + 32, top + 20, 150, 13, Component.translatable("robotics.pioneers.point.name"));
        nameBox.setMaxLength(PointConfig.NAME_LENGTH);
        nameBox.setValue(config.name);
        nameBox.setHint(Component.literal(blockName).withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(text -> config.name = text.trim());
        addRenderableWidget(nameBox);
        for (int i = 0; i < amountBoxes.length; i++) {
            int index = i;
            EditBox box = new EditBox(font, 0, 0, 50, 12, Component.translatable("robotics.pioneers.edit.amount"));
            box.setMaxLength(12);
            box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
            box.setResponder(text -> {
                if (index < config.entries.size()) {
                    config.entries.set(index, config.entries.get(index).withAmount(text.isEmpty() ? 0 : Long.parseLong(text)));
                }
            });
            box.visible = false;
            amountBoxes[i] = box;
            addRenderableWidget(box);
        }
        thresholdBox = new EditBox(font, 0, 0, 56, 12, Component.translatable("robotics.pioneers.point.threshold"));
        thresholdBox.setMaxLength(12);
        thresholdBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        thresholdBox.setValue(config.condition == null ? "0" : Long.toString(config.condition.threshold()));
        thresholdBox.setResponder(text -> {
            if (config.condition != null) {
                config.condition = new PointConfig.Condition(config.condition.res(), config.condition.below(), text.isEmpty() ? 0 : Long.parseLong(text));
            }
        });
        thresholdBox.visible = false;
        addRenderableWidget(thresholdBox);
        syncAmounts();
    }

    /** Les champs de quantite suivent la liste (apres un ajout ou une suppression). */
    private void syncAmounts() {
        for (int i = 0; i < amountBoxes.length; i++) {
            if (amountBoxes[i] != null) {
                amountBoxes[i].setValue(i < config.entries.size() ? Long.toString(config.entries.get(i).amount()) : "");
            }
        }
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + 10, w = panelWidth - 20;
        //Hauteur du panneau : ce qu'il faut (mesure a l'image precedente), sans depasser l'ecran ; le reste defile
        int wanted = Math.min(contentHeight + 70, height - 8);
        if (wanted != panelHeight) {
            panelHeight = wanted;
            top = (height - panelHeight) / 2;
            nameBox.setY(top + 20);
        }
        bodyTop = top + 38;
        bodyBottom = top + panelHeight - 28;
        int room = bodyBottom - bodyTop;
        pageScroll = Math.max(0, Math.min(pageScroll, contentHeight - room + 4));
        //En-tete : icone, nom, bloc et position
        graphics.renderItem(icon, x, top + 18);
        GuiDraw.text(graphics, font, Component.literal(blockName + " · " + pos.toShortString()), left + 188, top + 23, 0.58F, D_FAINT, panelWidth - 196);
        beginClip(graphics, left + 1, bodyTop, left + panelWidth - 1, bodyBottom);
        int y = top + 40 - pageScroll;
        //Role
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.point.role"));
        MarkerRole[] roles = MarkerRole.values();
        int rw = (w - (roles.length - 1) * 3) / roles.length;
        for (MarkerRole role : roles) {
            int rx = x + role.ordinal() * (rw + 3);
            roleCard(graphics, mouseX, mouseY, rx, y, rw, role);
        }
        y += 26;
        y += wrapped(graphics, Component.translatable(config.role.getTranslationKey() + ".desc"), x, y, 0.6F, D_DIM, w, 6) + 2;
        y += wrapped(graphics, Component.translatable("robotics.pioneers.point.who"), x, y, 0.56F, 0xFF7FE6C0, w, 3) + 4;
        //Face d'acces
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.point.side"));
        if (marker) {
            y = faceRow(graphics, mouseX, mouseY, x, y, w, config.accessSide, true, side -> config.accessSide = side) + 4;
        } else {
            y += wrapped(graphics, Component.translatable("robotics.pioneers.point.side.chest"), x, y, 0.6F, D_DIM, w, 2) + 4;
        }
        //Ressources
        Component head = Component.translatable(config.role.requests ? "robotics.pioneers.point.requests" : "robotics.pioneers.point.filter");
        y = section(graphics, x, y, w, Component.literal(head.getString() + " (" + config.entries.size() + "/" + PointConfig.MAX_ENTRIES + ")"));
        y = drawList(graphics, mouseX, mouseY, x, y, w);
        //Options
        y = section(graphics, x, y + 4, w, Component.translatable("robotics.pioneers.point.options"));
        y = drawOptions(graphics, mouseX, mouseY, x, y, w);
        endClip(graphics);
        contentHeight = y + pageScroll - (top + 40);
        //Champs de saisie (dessines apres, hors du cadre) : caches s'ils sortent de la zone visible
        for (EditBox box : amountBoxes) {
            if (box.visible && (box.getY() < bodyTop || box.getY() + 12 > bodyBottom)) {
                box.visible = false;
            }
        }
        if (thresholdBox.visible && (thresholdBox.getY() < bodyTop || thresholdBox.getY() + 12 > bodyBottom)) {
            thresholdBox.visible = false;
        }
        if (contentHeight > room) {
            int track = room - 2, thumb = Math.max(10, track * room / contentHeight);
            int ty = bodyTop + 1 + (track - thumb) * pageScroll / Math.max(1, contentHeight - room + 4);
            graphics.fill(left + panelWidth - 4, ty, left + panelWidth - 2, ty + thumb, 0xFF4A5A68);
        }
        //Pied
        int by = top + panelHeight - 22;
        graphics.fill(left + 1, by - 5, left + panelWidth - 1, by - 4, D_LINE);
        Component problem = config.condition != null && config.condition.res().isAny() ? Component.translatable("robotics.pioneers.point.invalid") : null;
        if (problem != null) {
            GuiDraw.text(graphics, font, Component.literal("⚠ ").append(problem), x, by + 4, 0.62F, 0xFFFFB050, w - 150);
        }
        button(graphics, mouseX, mouseY, left + panelWidth - 146, by, 60, 15, Component.translatable("gui.cancel"), true, this::onClose);
        primary(graphics, mouseX, mouseY, left + panelWidth - 82, by, 72, 15, Component.translatable("robotics.pioneers.edit.save"), problem == null,
              this::save);
        //Menu deroulant des faces, par-dessus tout
        if (faceMenu >= 0 && faceMenu < config.entries.size()) {
            drawFaceMenu(graphics, mouseX, mouseY);
        } else {
            faceMenu = -1;
        }
    }

    private void roleCard(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, MarkerRole role) {
        boolean selected = config.role == role, hover = over(mouseX, mouseY, x, y, w, 22);
        int color = 0xFF000000 | role.color;
        graphics.fill(x, y, x + w, y + 22, selected ? 0xFF000000 | GuiDraw.darken(role.color, 0.45F) : hover ? 0xFF26303A : 0xFF1A222A);
        graphics.fill(x, y, x + w, y + 3, color);
        graphics.renderOutline(x, y, w, 22, selected ? 0xFFFFFFFF : hover ? 0xFF4A5A68 : 0xFF2C3740);
        GuiDraw.center(graphics, font, Component.translatable(role.getTranslationKey()), x + w / 2F, y + 9, 0.68F, selected ? 0xFFFFFFFF : D_TEXT, w - 4);
        click(x, y, w, 22, () -> {
            config.role = role;
            if (!role.requests) {
                config.machineMode = false;
            }
        });
        tooltip(mouseX, mouseY, x, y, w, 22, Component.translatable(role.getTranslationKey()),
              Component.translatable(role.getTranslationKey() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** Ligne de faces : Auto, puis les six faces nommees par rapport a la machine. Renvoie le y suivant. */
    private int faceRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, @Nullable Direction current, boolean access,
          Consumer<Direction> set) {
        Direction[] faces = orderedFaces();
        int cw = (w - 6 * 2) / 7;
        chip(graphics, mouseX, mouseY, x, y, cw, 13, Component.translatable("robotics.pioneers.point.side.auto"), current == null, 0x2E9E78,
              () -> set.accept(null));
        tooltip(mouseX, mouseY, x, y, cw, 13, Component.translatable(access ? "robotics.pioneers.point.side.auto.tooltip"
                                                                         : "robotics.pioneers.point.entry_side.auto"));
        for (int i = 0; i < faces.length; i++) {
            Direction side = faces[i];
            int fx = x + (i + 1) * (cw + 2);
            chip(graphics, mouseX, mouseY, fx, y, cw, 13, faceLabel(side), current == side, 0x2E9E78, () -> set.accept(side));
            tooltip(mouseX, mouseY, fx, y, cw, 13, faceLabel(side), side(side).copy().withStyle(ChatFormatting.GRAY));
        }
        return y + 16;
    }

    /** Les six faces, dans l'ordre avant, arriere, gauche, droite, dessus, dessous (ou nord, sud, ouest, est sans orientation). */
    private Direction[] orderedFaces() {
        Direction front = facing();
        if (front == null || front.getAxis().isVertical()) {
            return new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
        }
        return new Direction[]{front, front.getOpposite(), front.getClockWise(), front.getCounterClockWise(), Direction.UP, Direction.DOWN};
    }

    /** Liste des ressources (5 lignes visibles, molette pour le reste). */
    private int drawList(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        listX = x;
        listY = y;
        listW = w;
        int rows = config.entries.size() + (config.entries.size() < PointConfig.MAX_ENTRIES ? 1 : 0);
        int visible = Math.min(VISIBLE_ROWS, Math.max(1, rows));
        listScroll = Math.max(0, Math.min(listScroll, rows - visible));
        int h = visible * ROW;
        graphics.fill(x, y, x + w, y + h, 0xFF10171D);
        for (EditBox box : amountBoxes) {
            box.visible = false;
        }
        beginClip(graphics, x, Math.max(y, bodyTop), x + w, Math.max(Math.max(y, bodyTop), Math.min(y + h, bodyBottom)));
        for (int r = 0; r < rows; r++) {
            int ry = y + (r - listScroll) * ROW;
            if (ry + ROW <= y || ry >= y + h) {
                continue;
            }
            if (r < config.entries.size()) {
                drawEntry(graphics, mouseX, mouseY, r, x, ry, w);
            } else {
                boolean hover = over(mouseX, mouseY, x, ry, w, ROW);
                if (hover) {
                    graphics.fill(x, ry, x + w, ry + ROW, 0xFF1C2A30);
                }
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.point.entry.add"), x + 8, ry + 6, 0.7F,
                      hover ? 0xFFFFFFFF : 0xFF7FE6C0, w - 16);
                click(x, ry, w, ROW, () -> minecraft.setScreen(new ResourcePickerScreen(this, Resource.ITEMS, Set.of(), true, res -> {
                    config.entries.add(new PointConfig.Entry(res, defaultAmount(res)));
                    syncAmounts();
                    listScroll = config.entries.size();
                })));
            }
        }
        //Fin du cadre de la liste : celui de la page reprend (0.20.36 : avant, la page etait rouverte une seconde fois et un cadre restait actif
        //apres l'ecran, d'ou l'interface figee a l'ecran et les saccades)
        endClip(graphics);
        graphics.renderOutline(x, y, w, h, 0xFF2C3640);
        if (rows > visible) {
            int track = h - 2, thumb = Math.max(8, track * visible / rows);
            int ty = y + 1 + (track - thumb) * listScroll / (rows - visible);
            graphics.fill(x + w - 3, ty, x + w - 1, ty + thumb, 0xFF4A5A68);
        }
        y += h + 3;
        y += wrapped(graphics, Component.translatable(config.role.requests ? "robotics.pioneers.point.requests.hint"
                                                                           : "robotics.pioneers.point.filter.hint"), x, y, 0.58F, D_FAINT, w, 2);
        return y;
    }

    private void drawEntry(GuiGraphics graphics, int mouseX, int mouseY, int index, int x, int y, int w) {
        PointConfig.Entry entry = config.entries.get(index);
        if (index % 2 == 1) {
            graphics.fill(x, y, x + w, y + ROW, 0xFF131C23);
        }
        //Icone : clic pour changer de ressource
        darkSlot(graphics, x + 3, y + 2);
        ResourceIcon.draw(graphics, entry.res(), x + 3, y + 2);
        click(x + 2, y + 1, 18, 18, () -> minecraft.setScreen(new ResourcePickerScreen(this, entry.res().type, Set.of(), true,
              res -> config.entries.set(index, new PointConfig.Entry(res, entry.amount(), entry.side())))));
        if (over(mouseX, mouseY, x + 2, y + 1, 18, 18)) {
            List<Component> lines = new ArrayList<>(resourceLines(entry.res()));
            lines.add(Component.translatable("robotics.pioneers.point.entry.change").withStyle(ChatFormatting.GRAY));
            tooltip(lines);
        }
        int right = x + w - 4;
        //Supprimer
        right -= 13;
        button(graphics, mouseX, mouseY, right, y + 4, 13, 12, Component.literal("✕"), true, () -> {
            config.entries.remove(index);
            faceMenu = -1;
            syncAmounts();
        });
        tooltip(mouseX, mouseY, right, y + 4, 13, 12, Component.translatable("robotics.pioneers.point.entry.remove"));
        //Face (balise) : menu deroulant
        if (marker) {
            right -= 54;
            int menuX = right - 2;
            Component face = entry.side() == null ? Component.translatable("robotics.pioneers.point.side.auto") : faceLabel(entry.side());
            chip(graphics, mouseX, mouseY, right, y + 4, 52, 12, Component.literal(face.getString() + " ▾"), entry.side() != null, 0x3A7FC0, () -> {
                faceMenu = faceMenu == index ? -1 : index;
                faceMenuX = menuX;
                faceMenuY = y + 16;
            });
            tooltip(mouseX, mouseY, right, y + 4, 52, 12, Component.translatable("robotics.pioneers.point.entry_side"),
                  Component.translatable("robotics.pioneers.point.entry_side.hint").withStyle(ChatFormatting.GRAY));
            right -= 3;
        }
        //Quantite (demandes)
        if (config.role.requests) {
            String symbol = Component.translatable("robotics.pioneers.symbol." + entry.res().type.name().toLowerCase(Locale.ROOT)).getString();
            int sw = (int) (font.width(symbol) * 0.6F) + 3;
            right -= sw;
            GuiDraw.text(graphics, font, Component.literal(symbol), right + 1, y + 7, 0.6F, D_FAINT);
            right -= 52;
            EditBox box = amountBoxes[index];
            box.setX(right);
            box.setY(y + 4);
            box.visible = y + 4 >= listY && y + 16 <= listY + VISIBLE_ROWS * ROW && y + 4 >= bodyTop && y + 16 <= bodyBottom;
        }
        GuiDraw.text(graphics, font, entry.res().name(), x + 24, y + 6, 0.68F, D_TEXT, right - x - 28);
    }

    private void drawFaceMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = faceMenu;
        PointConfig.Entry entry = config.entries.get(index);
        Direction[] faces = orderedFaces();
        int w = 58, h = 7 * 12 + 2;
        int x = faceMenuX, y = Math.min(faceMenuY, height - h - 2);
        //Un clic ailleurs referme le menu
        click(0, 0, width, height, () -> faceMenu = -1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x, y, x + w, y + h, 0xF0101820);
        graphics.renderOutline(x, y, w, h, 0xFF3FD0A0);
        for (int i = 0; i < 7; i++) {
            Direction side = i == 0 ? null : faces[i - 1];
            int iy = y + 1 + i * 12;
            boolean hover = over(mouseX, mouseY, x + 1, iy, w - 2, 12), selected = entry.side() == side;
            if (hover || selected) {
                graphics.fill(x + 1, iy, x + w - 1, iy + 12, selected ? 0xFF2E5A4C : 0xFF26303A);
            }
            Component label = side == null ? Component.translatable("robotics.pioneers.point.side.auto") : faceLabel(side);
            GuiDraw.text(graphics, font, label, x + 5, iy + 3, 0.65F, selected ? 0xFFFFFFFF : D_TEXT, w - 8);
            click(x + 1, iy, w - 2, 12, () -> {
                config.entries.set(index, entry.withSide(side));
                faceMenu = -1;
            });
        }
        graphics.pose().popPose();
    }

    private int drawOptions(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        //Priorite
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.point.priority"), x, y + 3, 0.65F, D_DIM, 54);
        String[] priorities = {"low", "normal", "high"};
        for (int p = -1; p <= 1; p++) {
            int value = p;
            chip(graphics, mouseX, mouseY, x + 56 + (p + 1) * 46, y, 44, 13, Component.translatable("robotics.pioneers.priority." + priorities[p + 1]),
                  config.priority == p, 0xC08A30, () -> config.priority = value);
        }
        tooltip(mouseX, mouseY, x + 56, y, 136, 13, Component.translatable("robotics.pioneers.point.priority.tooltip"));
        y += 17;
        //Mode machine
        if (config.role.requests) {
            toggle(graphics, mouseX, mouseY, x, y + 2, 200, Component.translatable("robotics.pioneers.point.machine"), config.machineMode,
                  () -> config.machineMode = !config.machineMode);
            tooltip(mouseX, mouseY, x, y, 200, 12, Component.translatable("robotics.pioneers.point.machine"),
                  Component.translatable("robotics.pioneers.point.machine.tooltip").withStyle(ChatFormatting.GRAY));
            y += 15;
        }
        //Condition
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.point.condition"), x, y + 3, 0.65F, D_DIM, 54);
        PointConfig.Condition condition = config.condition;
        String mode = condition == null ? "always" : condition.below() ? "below" : "above";
        chip(graphics, mouseX, mouseY, x + 56, y, 70, 13, Component.translatable("robotics.pioneers.point.condition." + mode), condition != null, 0x8A6FD0,
              () -> {
                  if (config.condition == null) {
                      config.condition = new PointConfig.Condition(LogisticResource.any(Resource.ITEMS), true, 0);
                      thresholdBox.setValue("0");
                  } else if (config.condition.below()) {
                      config.condition = new PointConfig.Condition(config.condition.res(), false, config.condition.threshold());
                  } else {
                      config.condition = null;
                  }
              });
        tooltip(mouseX, mouseY, x + 56, y, 70, 13, Component.translatable("robotics.pioneers.point.condition.tooltip"));
        thresholdBox.visible = condition != null;
        if (condition != null) {
            thresholdBox.setX(x + 130);
            thresholdBox.setY(y);
            int sx = x + 192;
            darkSlot(graphics, sx, y - 2);
            if (!condition.res().isAny()) {
                ResourceIcon.draw(graphics, condition.res(), sx, y - 2);
            }
            click(sx - 1, y - 3, 18, 18, () -> minecraft.setScreen(new ResourcePickerScreen(this, condition.res().type, Set.of(), false,
                  res -> config.condition = new PointConfig.Condition(res, config.condition == null || config.condition.below(),
                        config.condition == null ? 0 : config.condition.threshold()))));
            tooltip(mouseX, mouseY, sx - 1, y - 3, 18, 18, condition.res().isAny() ? Component.translatable("robotics.pioneers.edit.item.pick")
                                                                                    : condition.res().name());
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.in_network"), sx + 21, y + 3, 0.6F, D_FAINT, x + w - sx - 21);
        }
        return y + 18;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta));
            faceMenu = -1;
            return true;
        }
        if (mouseY >= bodyTop && mouseY < bodyBottom) {
            pageScroll = Math.max(0, pageScroll - (int) (delta * 16));
            faceMenu = -1;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editing()) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void save() {
        if (config.condition == null || !config.condition.res().isAny()) {
            PiNetwork.sendToServer(new PacketRoboticsMap.PointEdit(pos, config.save()));
            onClose();
        }
    }

    private static long defaultAmount(LogisticResource res) {
        return switch (res.type) {
            case ITEMS -> res.isAny() ? 64 : Math.max(1, res.item().getMaxStackSize());
            case FLUIDS, GAS -> 8000;
            case ENERGY -> 1_000_000;
            case PRESSURE -> 10_000;
            case DATA -> 1000;
        };
    }

    /** Orientation du bloc servi (sa face avant), s'il en a une. */
    @Nullable
    private Direction facing() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockState state = minecraft.level.getBlockState(served);
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty direction && property.getName().equals("facing")) {
                return state.getValue(direction);
            }
        }
        return null;
    }

    /** Nom d'une face par rapport a la machine (avant, arriere, gauche, droite, dessus, dessous), comme le configurateur de Mekanism. */
    private Component faceLabel(Direction side) {
        String key;
        Direction front = facing();
        if (side == Direction.UP) {
            key = "top";
        } else if (side == Direction.DOWN) {
            key = "bottom";
        } else if (front == null || front.getAxis().isVertical()) {
            key = side.getName();
        } else if (side == front) {
            key = "front";
        } else if (side == front.getOpposite()) {
            key = "back";
        } else if (side == front.getClockWise()) {
            key = "left";
        } else {
            key = "right";
        }
        return Component.translatable("robotics.pioneers.face." + key);
    }
}
