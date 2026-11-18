package fr.pioneers.world.pet.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.world.pet.GeneratedPets;
import fr.pioneers.world.pet.PacketPet;
import fr.pioneers.world.pet.PetMenu;
import fr.pioneers.world.pet.PetMode;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * 1.0.5-beta (lot 5, specification §5.4) : INTERFACE D'UN ANIMAL (gabarit sombre du mod, {@link PiMenuScreen}) : la bete en vue (suit la
 * souris), race, maitre, sante, niche / panier lie (coordonnees), aptitudes en barres ; chien et chat : boutons des modes (Autonome, Suivre,
 * Assis, Chasse pour le chien ; le bouton Chasse « mis en avant » d'un liseré pulse quand le lot 10 le demande), lier a la niche la plus
 * proche / delier ; case « Autoriser les autres a l'adopter » ; monture : race, maitre, adoption, bouton Inventaire. Lecture seule pour qui
 * n'est ni le maitre ni (sans maitre) le proprietaire. Geometrie recopiee par la maquette de la planche (scratchpad/p105/lot5/tools).
 */
public class PetScreen extends PiMenuScreen<PetMenu> {

    public static final int W = 232, VIEW_X = 8, VIEW_Y = 18, VIEW_W = 64, VIEW_H = 80, INFO_X = 78, INFO_W = 146, APT_Y = 102, APT_ROW = 9,
          APT_LABEL = 84, BTN_H = 13, MODE_GAP = 6, H_MOUNT = 144;
    static final String[] DOG_APT = {"scent", "speed", "strength", "cold"}, CAT_APT = {"fishing", "ratting", "independence"};

    public PetScreen(PetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, menu.mount() ? H_MOUNT : height(menu.aptitudes.length));
        inventoryLabelY = -1;
    }

    /** Cadre des aptitudes (une ligne par aptitude), puis les modes, puis lier / delier, puis la case d'adoption. */
    public static int aptH(int n) {
        return 14 + n * APT_ROW;
    }

    public static int modeY(int n) {
        return APT_Y + aptH(n) + MODE_GAP;
    }

    public static int linkY(int n) {
        return modeY(n) + BTN_H + 5;
    }

    public static int adoptY(int n) {
        return linkY(n) + BTN_H + 5;
    }

    public static int height(int n) {
        return adoptY(n) + 9 + 7;
    }

    private void send(int action, int arg) {
        PiNetwork.sendToServer(new PacketPet(menu.entityId, action, arg));
    }

    LivingEntity entity() {
        Entity e = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(menu.entityId);
        return e instanceof LivingEntity le ? le : null;
    }

    Component breedName() {
        if (menu.breed >= 0) {
            String id = menu.cat() ? GeneratedPets.CATS[menu.breed].id() : GeneratedPets.DOGS[menu.breed].id();
            return Component.translatable("pet.pioneers.breed." + id);
        }
        return Component.translatable("entity." + menu.type.replace(':', '.'));
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mx, int my) {
        screen(g, VIEW_X, VIEW_Y, VIEW_W, VIEW_H);
        LivingEntity e = entity();
        if (e != null) {
            float scale = Mth.clamp(36F / Math.max(0.5F, Math.max(e.getBbHeight(), e.getBbWidth())), 10F, 48F);
            //drawBackground est deja translate au coin du panneau : coordonnees relatives
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, VIEW_X + VIEW_W / 2, VIEW_Y + VIEW_H - 8, (int) scale, VIEW_X + VIEW_W / 2F - mx,
                  VIEW_Y + 20F - my, e);
        }
        screen(g, INFO_X, VIEW_Y, INFO_W, VIEW_H);
        int y = VIEW_Y + 4, x = INFO_X + 4, w = INFO_W - 8;
        line(g, "pet.pioneers.gui.breed", breedName(), x, y, w);
        y += 11;
        Component m = menu.hasMaster ? Component.literal(menu.master.isEmpty() ? "?" : menu.master)
              : Component.translatable("pet.pioneers.gui.no_master").withStyle(ChatFormatting.GRAY);
        line(g, "pet.pioneers.gui.master", m, x, y, w);
        y += 11;
        float hp = menu.get(PetMenu.HEALTH) / 10F, max = Math.max(1, menu.get(PetMenu.MAX_HEALTH) / 10F);
        line(g, "pet.pioneers.gui.health", Component.literal(String.format(java.util.Locale.ROOT, "%.0f / %.0f", hp, max)), x, y, w);
        GuiDraw.bar(g, x, y + 9, w, 2, hp / max, 0xFFE04848);
        y += 15;
        if (!menu.mount()) {
            BlockPos h = menu.home();
            Component home = h == null ? Component.translatable("pet.pioneers.gui.no_home").withStyle(ChatFormatting.GRAY)
                  : menu.get(PetMenu.LINKED) == 2 ? Component.translatable("pet.pioneers.gui.home_elsewhere")
                  : Component.literal(h.getX() + " " + h.getY() + " " + h.getZ());
            line(g, menu.cat() ? "pet.pioneers.gui.basket" : "pet.pioneers.gui.kennel", home, x, y, w);
            y += 11;
            line(g, "pet.pioneers.gui.mode", Component.translatable(menu.mode().key()), x, y, w);
            y += 11;
            if (!menu.canEdit) {
                GuiDraw.text(g, font, Component.translatable("pet.pioneers.gui.read_only"), x, y, 0.7F, DIM, w);
            }
            int n = menu.aptitudes.length, ly = linkY(n), half = (W - 16 - 4) / 2;
            aptitudes(g);
            modes(g, mx, my, modeY(n));
            boolean linked = h != null;
            button(g, mx, my, 8, ly, half, BTN_H, Component.translatable(menu.cat() ? "pet.pioneers.gui.link_basket"
                  : "pet.pioneers.gui.link_kennel"), menu.canEdit, () -> send(PacketPet.LINK, 0));
            button(g, mx, my, 12 + half, ly, half, BTN_H, Component.translatable("pet.pioneers.gui.unlink"), menu.canEdit && linked,
                  () -> send(PacketPet.UNLINK, 0));
            if (over(mx, my, 8, ly, half, BTN_H)) {
                tooltip(List.of(Component.translatable(menu.cat() ? "pet.pioneers.gui.link_basket.tip" : "pet.pioneers.gui.link_kennel.tip")));
            }
            adopt(g, mx, my, 8, adoptY(n));
        } else {
            y += 2;
            if (!menu.canEdit) {
                GuiDraw.text(g, font, Component.translatable("pet.pioneers.gui.read_only"), x, y, 0.7F, DIM, w);
            }
            button(g, mx, my, W - 8 - 90, APT_Y, 90, BTN_H, Component.translatable(menu.packed ? "pet.pioneers.gui.packs" : "pet.pioneers.gui.inventory"),
                  menu.canEdit, () -> send(PacketPet.INVENTORY, 0));
            adopt(g, mx, my, 8, APT_Y + 2);
            GuiDraw.wrapped(g, font, Component.translatable("pet.pioneers.gui.mount_hint"), 8, APT_Y + 20, 0.7F, DIM, W - 16, 2, 7.5F);
        }
    }

    void line(GuiGraphics g, String label, Component value, int x, int y, int w) {
        GuiDraw.text(g, font, Component.translatable(label), x, y, 0.75F, DIM, 52);
        GuiDraw.text(g, font, value, x + 54, y, 0.75F, TEXT, w - 54);
    }

    void aptitudes(GuiGraphics g) {
        String[] keys = menu.cat() ? CAT_APT : DOG_APT;
        int n = menu.aptitudes.length;
        screen(g, 8, APT_Y, W - 16, aptH(n));
        GuiDraw.text(g, font, Component.translatable("pet.pioneers.gui.aptitudes"), 12, APT_Y + 3, 0.7F, ACCENT, 100);
        for (int i = 0; i < keys.length && i < n; i++) {
            int y = APT_Y + 12 + i * APT_ROW;
            GuiDraw.text(g, font, Component.translatable("pet.pioneers.aptitude." + keys[i]), 12, y, 0.7F, TEXT, APT_LABEL);
            GuiDraw.bar(g, 12 + APT_LABEL + 4, y + 1, W - 16 - APT_LABEL - 12, 5, menu.aptitudes[i], 0xFF3FD0A0);
        }
    }

    void modes(GuiGraphics g, int mx, int my, int modeY) {
        PetMode[] modes = menu.cat() ? new PetMode[]{PetMode.AUTONOMOUS, PetMode.FOLLOW, PetMode.SIT}
                                     : new PetMode[]{PetMode.AUTONOMOUS, PetMode.FOLLOW, PetMode.SIT, PetMode.HUNT};
        int n = modes.length, gap = 4, bw = (W - 16 - gap * (n - 1)) / n;
        PetMode cur = menu.mode();
        for (int i = 0; i < n; i++) {
            PetMode m = modes[i];
            int x = 8 + i * (bw + gap);
            boolean enabled = menu.canEdit && m != cur && (m != PetMode.AUTONOMOUS || menu.home() != null);
            //1.0.5-beta (lot 31, retour 43) : bouton Chasse en SURBRILLANCE seulement si une chasse est a portee de son flair (serveur, donnee
            //HUNT_LIT du menu) ; sinon libelle grise et infobulle « Aucune chasse a portee » (le mode reste choisissable : gibier qu'il sent)
            boolean hunt = m == PetMode.HUNT, lit = hunt && menu.get(PetMenu.HUNT_LIT) != 0;
            button(g, mx, my, x, modeY, bw, BTN_H, hunt && !lit ? Component.translatable(m.key()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                                                                : Component.translatable(m.key()), enabled, () -> send(PacketPet.MODE, m.ordinal()));
            if (m == cur) {
                g.renderOutline(x - 1, modeY - 1, bw + 2, BTN_H + 2, ACCENT);
            }
            if (lit) {
                long t = net.minecraft.Util.getMillis() / 120 % 8;
                g.renderOutline(x - 2, modeY - 2, bw + 4, BTN_H + 4, t < 4 ? 0xFFFFC040 : 0xFFE08020);
            }
            if (over(mx, my, x, modeY, bw, BTN_H)) {
                if (hunt) {
                    tooltip(lit ? List.of(Component.translatable(m.key() + ".tip"),
                                          Component.translatable("pet.pioneers.gui.hunt_ready").withStyle(net.minecraft.ChatFormatting.GOLD))
                                : List.of(Component.translatable("pet.pioneers.gui.hunt_none").withStyle(net.minecraft.ChatFormatting.GRAY),
                                          Component.translatable("pet.pioneers.gui.hunt_none.tip").withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                                          Component.translatable(m.key() + ".tip")));
                } else {
                    tooltip(List.of(Component.translatable(m.key() + ".tip")));
                }
            }
        }
    }

    void adopt(GuiGraphics g, int mx, int my, int x, int y) {
        boolean on = menu.get(PetMenu.ADOPT) != 0, enabled = menu.canEdit && menu.hasMaster;
        g.fill(x, y, x + 9, y + 9, SCREEN);
        g.renderOutline(x, y, 9, 9, enabled ? 0xFF6F8EA6 : EDGE);
        if (on) {
            g.fill(x + 2, y + 2, x + 7, y + 7, ACCENT);
        }
        GuiDraw.text(g, font, Component.translatable("pet.pioneers.gui.adopt"), x + 12, y + 1, 0.7F, enabled ? TEXT : DIM, 86);
        if (enabled) {
            click(x, y, 98, 9, () -> send(PacketPet.ADOPT, on ? 0 : 1));
        }
        if (over(mx, my, x, y, 98, 9)) {
            tooltip(List.of(Component.translatable(menu.hasMaster ? "pet.pioneers.gui.adopt.tip" : "pet.pioneers.gui.adopt.need_master")));
        }
    }
}
