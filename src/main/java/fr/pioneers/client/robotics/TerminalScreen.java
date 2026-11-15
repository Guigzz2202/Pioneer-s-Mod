package fr.pioneers.client.robotics;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.RecipePicker;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.network.PacketRecipeLock;
import fr.pioneers.network.PacketTerminal;
import fr.pioneers.network.PiNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Terminal logistique : les objets disponibles dans le reseau (recherche), un clic pour en commander ; les unites les livrent dans la
 * reserve du terminal. A droite, les commandes en cours et leur avancement. MAJ Recettes (lot 7) : onglet « Machines » : les machines a
 * selecteur de recette servies par le reseau, leur verrou, et le selecteur pour le regler a distance (clic droit : effacer).
 */
public class TerminalScreen extends RoboticsScreen {

    private static final int W = 330, H = 228, COLUMNS = 9, ROWS = 7, CELL = 18, SIDE = 112;

    private record Stock(ItemStack item, long count) {}

    private record Order(int index, ItemStack item, long requested, long remaining) {}

    /** MAJ Recettes (lot 7) : machine a selecteur servie par le reseau. */
    private record Machine(BlockPos pos, ItemStack item, String source, int tier, @Nullable ResourceLocation lock) {}

    private static final int TAB_W = 62;

    private final BlockPos pos;
    private List<Stock> stock = List.of();
    private List<Order> orders = List.of();
    private boolean covered = true, loaded;
    private int hubs;
    private EditBox search;
    private int scroll, ticks;
    /** MAJ Recettes (lot 7) : onglet des machines, selecteur ouvert pour l'une d'elles. */
    private List<Machine> machines = List.of();
    private boolean machineView;
    private final RecipePicker picker = new RecipePicker();
    @Nullable
    private Machine picking;
    private final RecipePicker.Host host = new RecipePicker.Host() {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
            return TerminalScreen.this.over(mouseX, mouseY, x, y, w, h);
        }

        @Override
        public void click(int x, int y, int w, int h, Runnable action) {
            TerminalScreen.this.click(x, y, w, h, action);
        }

        @Override
        public void tooltip(List<Component> lines) {
            TerminalScreen.this.tooltip(lines);
        }

        @Override
        public void choose(@Nullable ResourceLocation id) {
            if (picking != null) {
                lockRemote(picking, id);
            }
        }
    };

    public TerminalScreen(BlockPos pos) {
        super(Component.translatable("block.pioneers.logistic_terminal"), null, W, H);
        this.pos = pos;
        this.dark = true;
        send(PacketTerminal.Action.REFRESH, ItemStack.EMPTY, 0);
    }

    public BlockPos pos() {
        return pos;
    }

    private void send(int action, ItemStack item, long count) {
        PiNetwork.sendToServer(new PacketTerminal.Action(pos, action, item, count));
    }

    public void receive(CompoundTag tag) {
        loaded = true;
        covered = tag.getBoolean("covered");
        hubs = tag.getInt("hubs");
        List<Stock> list = new ArrayList<>();
        for (Tag entry : tag.getList("stock", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) entry;
            list.add(new Stock(ItemStack.of(item.getCompound("item")), item.getLong("count")));
        }
        stock = list;
        List<Order> pending = new ArrayList<>();
        for (Tag entry : tag.getList("orders", Tag.TAG_COMPOUND)) {
            CompoundTag order = (CompoundTag) entry;
            pending.add(new Order(order.getInt("index"), ItemStack.of(order.getCompound("item")), order.getLong("requested"), order.getLong("remaining")));
        }
        orders = pending;
        List<Machine> found = new ArrayList<>();
        for (Tag entry : tag.getList("machines", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) entry;
            String lock = m.getString("lock");
            found.add(new Machine(BlockPos.of(m.getLong("pos")), ItemStack.of(m.getCompound("item")), m.getString("source"), m.getInt("tier"),
                  lock.isEmpty() ? null : ResourceLocation.tryParse(lock)));
        }
        machines = found;
    }

    /** Pose ou retire le verrou d'une machine servie par le reseau (le terminal sert de relais ; le serveur verifie tout). */
    private void lockRemote(Machine machine, @Nullable ResourceLocation id) {
        PiNetwork.sendToServer(new PacketRecipeLock(machine.pos(), pos, id == null ? "" : id.toString()));
        //Affichage tout de suite ; la reponse du serveur (rafraichissement) fait foi
        List<Machine> next = new ArrayList<>(machines);
        next.replaceAll(m -> m.pos().equals(machine.pos()) ? new Machine(m.pos(), m.item(), m.source(), m.tier(), id) : m);
        machines = next;
    }

    private List<RecipeChoice> choices(Machine machine) {
        return minecraft == null || minecraft.level == null ? List.of() : RecipeSources.choices(minecraft.level, machine.source());
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, left + 8, top + 20, W - SIDE - 24 - TAB_W, 13, Component.translatable("robotics.pioneers.terminal.search"));
        search.setHint(Component.translatable("robotics.pioneers.terminal.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(text -> scroll = 0);
        addRenderableWidget(search);
    }

    @Override
    public void tick() {
        super.tick();
        search.tick();
        picker.tick();
        if (++ticks % 20 == 0) {
            send(PacketTerminal.Action.REFRESH, ItemStack.EMPTY, 0);
        }
    }

    private List<Stock> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return stock;
        }
        List<Stock> list = new ArrayList<>();
        for (Stock entry : stock) {
            if (entry.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                list.add(entry);
            }
        }
        return list;
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + 8, y = top + 38;
        //Etat du terminal
        Component status = !loaded ? Component.translatable("robotics.pioneers.terminal.loading")
                                   : !covered ? Component.translatable("robotics.pioneers.terminal.no_network").withStyle(ChatFormatting.RED)
                                              : hubs == 0 ? Component.translatable("robotics.pioneers.terminal.no_hub").withStyle(ChatFormatting.GOLD)
                                                          : Component.translatable("robotics.pioneers.terminal.items", stock.size());
        GuiDraw.right(graphics, font, status, left + W - 8, top + 8, 0.6F, D_DIM);
        //MAJ Recettes (lot 7) : onglet Stocks / Machines (a droite de la recherche)
        int tx = left + 8 + W - SIDE - 24 - TAB_W + 4;
        button(graphics, mouseX, mouseY, tx, top + 20, TAB_W, 13, machineView ? Component.translatable("robotics.pioneers.terminal.stock_tab")
              : Component.translatable("robotics.pioneers.terminal.machines_tab", machines.size()), true, () -> {
            machineView = !machineView;
            picker.close();
            scroll = 0;
        });
        tooltip(mouseX, mouseY, tx, top + 20, TAB_W, 13, Component.translatable(machineView ? "robotics.pioneers.terminal.stock_tab.tooltip"
              : "robotics.pioneers.terminal.machines_tab.tooltip"));
        if (machineView) {
            drawMachines(graphics, mouseX, mouseY, x, y);
        } else {
            drawStock(graphics, mouseX, mouseY, x, y);
        }
        drawOrders(graphics, mouseX, mouseY);
        if (picker.isOpen() && picking != null && machineView) {
            picker.draw(graphics, host, mouseX, mouseY, left + 4, top + 36, W - SIDE - 8, H - 44,
                  new RecipePicker.Context(choices(picking), List.of(), List.of(), List.of(), picking.lock(), picking.tier()), 0x4FC3F7);
        }
    }

    /** Stocks du reseau (grille, un clic pour commander). */
    private void drawStock(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        List<Stock> list = filtered();
        int rows = (list.size() + COLUMNS - 1) / COLUMNS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - ROWS));
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            int index = scroll * COLUMNS + i;
            int sx = x + (i % COLUMNS) * CELL + 1, sy = y + (i / COLUMNS) * CELL + 1;
            darkSlot(graphics, sx, sy);
            if (index >= list.size()) {
                continue;
            }
            Stock entry = list.get(index);
            graphics.renderItem(entry.item(), sx, sy);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            GuiDraw.right(graphics, font, Component.literal(OrderTexts.compact(entry.count())), sx + 17, sy + 11, 0.55F, 0xFFFFFFFF);
            graphics.pose().popPose();
            if (over(mouseX, mouseY, sx, sy, 16, 16)) {
                graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF);
                tooltip(List.of(entry.item().getHoverName(), Component.translatable("robotics.pioneers.terminal.available", entry.count())
                      .withStyle(ChatFormatting.GRAY), Component.translatable("robotics.pioneers.terminal.click").withStyle(ChatFormatting.DARK_GRAY)));
            }
            ItemStack item = entry.item();
            long count = entry.count();
            clickBoth(sx, sy, 16, 16, () -> order(item, count));
        }
        if (rows > ROWS) {
            int track = ROWS * CELL, thumb = Math.max(10, track * ROWS / rows);
            int ty = y + (track - thumb) * scroll / Math.max(1, rows - ROWS);
            graphics.fill(x + COLUMNS * CELL + 2, ty, x + COLUMNS * CELL + 4, ty + thumb, 0xFF3C4A57);
        }
        if (loaded && list.isEmpty()) {
            GuiDraw.center(graphics, font, Component.translatable(stock.isEmpty() ? "robotics.pioneers.terminal.empty"
                                                                                   : "robotics.pioneers.terminal.no_match"), x + COLUMNS * CELL / 2F,
                  y + ROWS * CELL / 2F - 3, 0.65F, D_DIM, COLUMNS * CELL);
        }
        int by = y + ROWS * CELL + 6;
        wrapped(graphics, Component.translatable("robotics.pioneers.terminal.hint"), x, by, 0.56F, D_FAINT, COLUMNS * CELL + 6, 3);
    }

    /** MAJ Recettes (lot 7) : machines a selecteur servies par le reseau : verrou de chacune ; clic : choisir, clic droit : effacer. */
    private void drawMachines(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        String query = search == null ? "" : RecipeChoice.fold(search.getValue().trim());
        List<Machine> list = new ArrayList<>();
        for (Machine m : machines) {
            if (query.isEmpty() || RecipeChoice.fold(m.item().getHoverName().getString()).contains(query)) {
                list.add(m);
            }
        }
        int width = COLUMNS * CELL + 6, rows = ROWS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            Machine m = list.get(scroll + i);
            int ry = y + i * CELL;
            boolean hover = !picker.isOpen() && over(mouseX, mouseY, x, ry, width, CELL - 1);
            graphics.fill(x, ry, x + width, ry + CELL - 1, hover ? 0xFF1E2833 : 0xFF161D25);
            graphics.renderItem(m.item(), x + 1, ry + 1);
            GuiDraw.text(graphics, font, m.item().getHoverName(), x + 20, ry + 2, 0.6F, D_TEXT, 76);
            BlockPos p = m.pos();
            GuiDraw.text(graphics, font, Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()), x + 20, ry + 10, 0.5F, D_FAINT, 76);
            RecipeChoice locked = m.lock() == null ? null : RecipeLockable.find(choices(m), m.lock());
            int lx = x + 100;
            if (locked != null) {
                graphics.renderItem(locked.icon(), lx, ry + 1);
                GuiDraw.text(graphics, font, locked.name(), lx + 18, ry + 5, 0.55F, 0xFF8CF5C6, width - (lx - x) - 20);
            } else {
                GuiDraw.text(graphics, font, Component.translatable(m.lock() == null ? "robotics.pioneers.terminal.no_lock"
                      : "robotics.pioneers.terminal.lost_lock"), lx, ry + 5, 0.55F, m.lock() == null ? D_DIM : 0xFFE04848, width - (lx - x) - 2);
            }
            if (hover) {
                tooltip(List.of(m.item().getHoverName(), locked != null ? Component.translatable("gui.pioneers.recipe_lock.locked", locked.name())
                      .withStyle(ChatFormatting.GREEN) : Component.translatable("gui.pioneers.recipe_lock.none").withStyle(ChatFormatting.GRAY),
                      Component.translatable("robotics.pioneers.terminal.machine_hint").withStyle(ChatFormatting.DARK_GRAY)));
            }
            Machine target = m;
            clickBoth(x, ry, width, CELL - 1, () -> {
                boolean right = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
                if (right) {
                    if (target.lock() != null) {
                        lockRemote(target, null);
                    }
                } else {
                    picking = target;
                    picker.open();
                }
            });
        }
        if (list.size() > rows) {
            int track = rows * CELL, thumb = Math.max(10, track * rows / list.size());
            int ty = y + (track - thumb) * scroll / Math.max(1, list.size() - rows);
            graphics.fill(x + width + 2, ty, x + width + 4, ty + thumb, 0xFF3C4A57);
        }
        if (loaded && list.isEmpty()) {
            GuiDraw.center(graphics, font, Component.translatable(machines.isEmpty() ? "robotics.pioneers.terminal.no_machines"
                  : "robotics.pioneers.terminal.no_match"), x + width / 2F, y + ROWS * CELL / 2F - 3, 0.65F, D_DIM, width);
        }
        wrapped(graphics, Component.translatable("robotics.pioneers.terminal.machines_hint"), x, y + ROWS * CELL + 6, 0.56F, D_FAINT, width, 3);
    }

    /** Commandes en cours (colonne de droite). */
    private void drawOrders(GuiGraphics graphics, int mouseX, int mouseY) {
        //Commandes en cours
        int cx = left + W - SIDE, cw = SIDE - 8;
        graphics.fill(cx - 4, top + 18, cx - 3, top + H - 8, D_LINE);
        int cy = section(graphics, cx, top + 20, cw, Component.translatable("robotics.pioneers.terminal.orders", orders.size()));
        if (orders.isEmpty()) {
            cy += wrapped(graphics, Component.translatable("robotics.pioneers.terminal.no_orders"), cx, cy, 0.56F, D_FAINT, cw, 4);
        }
        for (Order order : orders) {
            if (cy > top + H - 46) {
                break;
            }
            graphics.renderItem(order.item(), cx, cy);
            long done = order.requested() - order.remaining();
            GuiDraw.text(graphics, font, Component.literal(done + " / " + order.requested()), cx + 19, cy + 1, 0.6F, D_TEXT, cw - 34);
            GuiDraw.bar(graphics, cx + 19, cy + 10, cw - 34, 3, order.requested() <= 0 ? 0 : done / (double) order.requested(), 0xFF4FC3F7);
            int index = order.index();
            button(graphics, mouseX, mouseY, cx + cw - 13, cy + 1, 13, 13, Component.literal("✕"), true,
                  () -> send(PacketTerminal.Action.CANCEL, ItemStack.EMPTY, index));
            tooltip(mouseX, mouseY, cx + cw - 13, cy + 1, 13, 13, Component.translatable("robotics.pioneers.terminal.cancel"));
            cy += 19;
        }
        primary(graphics, mouseX, mouseY, cx, top + H - 26, cw, 16, Component.translatable("robotics.pioneers.terminal.open"), true,
              () -> send(PacketTerminal.Action.OPEN, ItemStack.EMPTY, 0));
        tooltip(mouseX, mouseY, cx, top + H - 26, cw, 16, Component.translatable("robotics.pioneers.terminal.open.tooltip"));
    }

    /** Clic : 1 ; Maj + clic : une pile ; clic droit : 16 (sans depasser ce que le reseau a). */
    private void order(ItemStack item, long available) {
        boolean right = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long count = right ? 16 : hasShiftDown() ? item.getMaxStackSize() : 1;
        send(PacketTerminal.Action.ORDER, item, Math.min(count, available));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (picker.scrolled(delta)) {
            return true;
        }
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return picker.charTyped(c) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (picker.keyPressed(key, host)) {
            return true;
        }
        if (search.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(key, scan, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
