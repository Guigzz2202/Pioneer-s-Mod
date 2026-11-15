package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.home.bag.BagInterfaceTile;
import fr.pioneers.home.bag.BagKind;
import fr.pioneers.home.bag.BagView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.0.5-beta (lot 45, retour 65) : vue {@code bag_interface} de l'INTERFACE DE SAC (interface standard v2, memes cartes que l'entree
 * d'entrepot) : a gauche la case du sac (fantome du sac a dos), son nom et l'etat (objets, cases ou sortes, « sur les six faces ») ; a droite
 * le CONTENU du sac en lecture seule (grille de 6 x 6 lue dans le NBT du sac que le menu envoie, quantites abregees, « +n » au-dela) ; en bas
 * la ligne d'aide. Aucune donnee propre : tout vient du sac de la case.
 */
final class BagInterfaceViews {

    private static final int GREEN = 0xFF6EDC5A, ORANGE = 0xFFF0B040, CELL_BG = 0xFF161D24, CELL_EMPTY = 0xFF10161C, CELL_HOVER = 0xFF2A3440;

    private BagInterfaceViews() {
    }

    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            BagInterfaceViews.draw(gui, g, mouseX, mouseY, tile);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            rows.add(new StandardView.InfoRow("age", Component.translatable("tooltip.pioneers.api.age.bronze"), StandardView.ageColor("bronze")));
            BagInterfaceTile t = StorageViews.as(BagInterfaceTile.class, tile);
            BagView v = t == null ? null : t.contents();
            rows.add(new StandardView.InfoRow("bag", v == null || v.kind() == null ? Component.translatable("gui.pioneers.bag_interface.no_bag")
                                                                                   : t.bag().getHoverName(), PiMenuScreen.TEXT));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.bag_interface.info.help";
        }
    };

    static void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
        BagInterfaceTile tile = StorageViews.as(BagInterfaceTile.class, be);
        if (tile == null) {
            return;
        }
        List<Component> tip = null;
        StandardLayout.Rect card = BagInterfaceTile.BAG_CARD, st = BagInterfaceTile.BAG_STATUS, side = BagInterfaceTile.BAG_SIDE,
              ln = BagInterfaceTile.BAG_LINE;
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.slot"), card.x() + 3, card.y() + 3, 0.62F, PiMenuScreen.DIM,
              card.w() - 4);
        BagView view = tile.contents();
        BagKind kind = view.kind();
        ItemStack bag = tile.bag();
        //Nom du sac sous sa case
        Component name = kind == null ? Component.translatable("gui.pioneers.bag_interface.no_bag") : bag.getHoverName();
        GuiDraw.center(g, gui.font(), name, BagInterfaceTile.BAG_X + 8, BagInterfaceTile.BAG_Y + 22, 0.55F, kind == null ? PiMenuScreen.DIM : PiMenuScreen.TEXT,
              card.w() - 2);
        if (gui.over(mouseX, mouseY, card.x(), card.y(), card.w(), BagInterfaceTile.BAG_Y + 30 - card.y())) {
            tip = List.of(Component.translatable("gui.pioneers.bag_interface.slot.tip"));
        }
        //Etat : objets, cases (sortes), faces
        gui.fieldAt(g, st.x(), st.y(), st.w(), st.h());
        int x = st.x() + 5, w = st.w() - 8;
        if (kind == null) {
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.empty"), x, st.y() + 4, 0.55F, ORANGE, w, 6, 6.5F);
        } else {
            int used = view.used(), slots = view.getSlots();
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.items", WarehouseViews.compact(view.count())), x, st.y() + 4,
                  0.55F, PiMenuScreen.TEXT, w);
            GuiDraw.text(g, gui.font(), Component.translatable(kind.storage() ? "gui.pioneers.bag_interface.sorts" : "gui.pioneers.bag_interface.cases",
                  used, slots), x, st.y() + 11, 0.55F, used >= slots ? ORANGE : PiMenuScreen.TEXT, w);
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.faces"), x, st.y() + 20, 0.55F, GREEN, w, 4, 6.5F);
        }
        if (gui.over(mouseX, mouseY, st.x(), st.y(), st.w(), st.h())) {
            tip = List.of(Component.translatable("gui.pioneers.bag_interface.faces.tip"));
        }
        //Contenu du sac (lecture seule)
        gui.screenAt(g, side.x(), side.y(), side.w(), side.h());
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.contents"), side.x() + 4, side.y() + 4, 0.62F, PiMenuScreen.TEXT,
              side.w() - 8);
        int n = view.getSlots(), cells = BagInterfaceTile.GRID_COLS * BagInterfaceTile.GRID_ROWS, cell = BagInterfaceTile.CELL;
        for (int i = 0; i < cells; i++) {
            int cx = BagInterfaceTile.GRID_X + (i % BagInterfaceTile.GRID_COLS) * cell, cy = BagInterfaceTile.GRID_Y + (i / BagInterfaceTile.GRID_COLS) * cell;
            if (i >= n) {
                g.fill(cx, cy, cx + cell - 1, cy + cell - 1, CELL_EMPTY);
                continue;
            }
            ItemStack s = view.getStackInSlot(i);
            boolean hover = gui.over(mouseX, mouseY, cx, cy, cell - 1, cell - 1);
            g.fill(cx, cy, cx + cell - 1, cy + cell - 1, hover ? CELL_HOVER : CELL_BG);
            if (s.isEmpty()) {
                continue;
            }
            g.renderItem(s, cx + 1, cy + 1);
            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            GuiDraw.right(g, gui.font(), Component.literal(WarehouseViews.compact(s.getCount())), cx + cell - 1.5F, cy + cell - 6.5F, 0.5F, 0xFFFFFFFF);
            g.pose().popPose();
            if (hover) {
                List<Component> lines = new ArrayList<>();
                lines.add(s.getHoverName().copy().withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("gui.pioneers.bag_interface.cell", s.getCount()).withStyle(ChatFormatting.GRAY));
                tip = lines;
            }
        }
        if (n > cells) {
            GuiDraw.right(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.more", n - cells), side.x() + side.w() - 4, side.y() + 4, 0.55F,
                  PiMenuScreen.DIM);
        }
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bag_interface.help"), ln.x() + 1, ln.y(), 0.6F, PiMenuScreen.DIM, ln.w() - 2);
        if (tip != null) {
            gui.tooltip(tip);
        }
    }
}
