package fr.pioneers.client.cosmetic;

import fr.pioneers.Pioneers;
import fr.pioneers.cosmetic.CosmeticActions;
import fr.pioneers.cosmetic.CosmeticCap;
import fr.pioneers.cosmetic.CosmeticItem;
import fr.pioneers.cosmetic.CosmeticNet;
import fr.pioneers.cosmetic.CosmeticPiece;
import fr.pioneers.cosmetic.Wardrobe;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.12.0 (lot E4) : section cosmetique de l'inventaire de base. Petit panneau attache au bord GAUCHE de l'inventaire vanilla
 * ({@link InventoryScreen}, survie) : 4 cases (coiffe, haut, bas, chaussures) alignees sur les cases d'armure, et a gauche de chaque case
 * l'interrupteur « montrer le cosmetique » (tee-shirt) / « montrer l'armure » (bouclier).
 * <p>
 * Choix (documente dans CLAUDE.md §3 sexatricies) : PAS de cases ajoutees au {@code InventoryMenu} (sans mixin ni transformateur d'acces,
 * ajouter des cases au menu de l'inventaire casserait l'onglet inventaire du mode creatif, qui recopie toutes les cases du menu, et les mods
 * qui comptent sur ses 46 cases). Les cases du panneau sont dessinees ici et chaque geste part au serveur comme une intention
 * ({@link CosmeticNet.Action}) : clic = echange avec l'objet tenu par la souris (celui du menu de l'inventaire, synchronise par le jeu),
 * Maj + clic = ranger dans l'inventaire, Maj + clic sur une piece de l'inventaire = l'enfiler, clic sur l'interrupteur = basculer.
 * Livre de recettes ouvert : le panneau passe a sa gauche (cache si l'ecran est trop etroit). JEI s'ecarte du panneau
 * ({@code PiJeiPlugin.registerGuiHandlers} -> {@link #areas}). Memes chiffres que la maquette tools/gc/cosmetics/gen.py.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class CosmeticPanel {

    public static final ResourceLocation TEXTURE = Pioneers.rl("textures/gui/cosmetic/panel.png");
    public static final int PANEL_W = 36, PANEL_H = 92, PANEL_DY = 3, SLOT_X = 15, SLOT_DY = 5, TOGGLE_X = 3, TOGGLE_DY = 8, TOGGLE = 10;
    /** Sprites de l'atlas 64 x 64 (tools/gc/cosmetics/gen.py PANEL_SPRITES). */
    private static final int TOGGLE_U = 0, TOGGLE_V = 0, EMPTY_V = 32, HANGER_U = 24, HANGER_V = 0;
    /** Clic commence sur le panneau : son relachement est aussi pour nous (le menu ne doit pas le voir). */
    private static boolean pressed;

    private CosmeticPanel() {
    }

    /** Coin haut gauche du panneau, ou null s'il n'a pas sa place (pas l'inventaire de survie, livre de recettes sur un ecran etroit). */
    @Nullable
    static int[] origin(Screen screen) {
        if (!(screen instanceof InventoryScreen inv) || Minecraft.getInstance().player == null || Minecraft.getInstance().player.isSpectator()) {
            return null;
        }
        int x = inv.getGuiLeft() - PANEL_W;
        if (inv.getRecipeBookComponent().isVisible()) {
            if (screen.width < 379) {
                return null;
            }
            x = (screen.width - 147) / 2 - 86 - PANEL_W;
        }
        return x < 0 ? null : new int[]{x, inv.getGuiTop() + PANEL_DY};
    }

    /** Zones occupees (JEI s'en ecarte). */
    public static List<Rect2i> areas(Screen screen) {
        int[] o = origin(screen);
        return o == null ? List.of() : List.of(new Rect2i(o[0], o[1], PANEL_W, PANEL_H));
    }

    private static int slotX(int[] o) {
        return o[0] + SLOT_X;
    }

    private static int slotY(int[] o, int i) {
        return o[1] + SLOT_DY + i * 18;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @SubscribeEvent
    public static void background(ContainerScreenEvent.Render.Background event) {
        int[] o = origin(event.getContainerScreen());
        LocalPlayer player = Minecraft.getInstance().player;
        if (o == null || player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int mx = event.getMouseX(), my = event.getMouseY();
        int x = o[0], y = o[1];
        //Cadre au style de l'inventaire vanilla
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFF000000);
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + PANEL_W - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + PANEL_H - 2, 0xFFFFFFFF);
        g.fill(x + 2, y + PANEL_H - 2, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF555555);
        g.fill(x + PANEL_W - 2, y + 2, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF555555);
        Wardrobe w = CosmeticCap.get(player);
        for (CosmeticPiece piece : CosmeticPiece.ALL) {
            int i = piece.ordinal();
            int sx = slotX(o), sy = slotY(o, i);
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
            g.fill(sx - 1, sy - 1, sx + 16, sy, 0xFF373737);
            g.fill(sx - 1, sy - 1, sx, sy + 16, 0xFF373737);
            g.fill(sx, sy + 16, sx + 17, sy + 17, 0xFFFFFFFF);
            g.fill(sx + 16, sy, sx + 17, sy + 17, 0xFFFFFFFF);
            ItemStack stack = w.get(i);
            if (stack.isEmpty()) {
                g.blit(TEXTURE, sx, sy, i * 16, EMPTY_V, 16, 16, 64, 64);
            } else {
                g.renderItem(stack, sx, sy);
                g.renderItemDecorations(Minecraft.getInstance().font, stack, sx, sy);
            }
            if (over(mx, my, sx, sy, 16, 16)) {
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
            }
            int tx = x + TOGGLE_X, ty = o[1] + TOGGLE_DY + i * 18;
            boolean hover = over(mx, my, tx, ty, TOGGLE, TOGGLE);
            int u = TOGGLE_U + (w.armorShown(i) ? TOGGLE : 0), v = TOGGLE_V + (hover ? TOGGLE : 0);
            g.blit(TEXTURE, tx, ty, u, v, TOGGLE, TOGGLE, 64, 64);
        }
        g.blit(TEXTURE, slotX(o) + 3, slotY(o, 4) + 4, HANGER_U, HANGER_V, 11, 8, 64, 64);
    }

    @SubscribeEvent
    public static void tooltips(ScreenEvent.Render.Post event) {
        int[] o = origin(event.getScreen());
        LocalPlayer player = Minecraft.getInstance().player;
        if (o == null || player == null || !player.containerMenu.getCarried().isEmpty()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int mx = event.getMouseX(), my = event.getMouseY();
        Wardrobe w = CosmeticCap.get(player);
        for (CosmeticPiece piece : CosmeticPiece.ALL) {
            int i = piece.ordinal();
            Component name = Component.translatable("cosmetic.pioneers.piece." + piece.key);
            if (over(mx, my, slotX(o), slotY(o, i), 16, 16)) {
                List<Component> lines = new ArrayList<>();
                ItemStack stack = w.get(i);
                if (stack.isEmpty()) {
                    lines.add(Component.translatable("gui.pioneers.cosmetic.slot", name));
                    lines.add(Component.translatable("gui.pioneers.cosmetic.slot_hint").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
                    lines.add(Component.translatable("gui.pioneers.cosmetic.take_hint").withStyle(ChatFormatting.DARK_GRAY));
                }
                g.renderComponentTooltip(Minecraft.getInstance().font, lines, mx, my);
            } else if (over(mx, my, o[0] + TOGGLE_X, o[1] + TOGGLE_DY + i * 18, TOGGLE, TOGGLE)) {
                g.renderComponentTooltip(Minecraft.getInstance().font, List.of(
                      Component.translatable(w.armorShown(i) ? "gui.pioneers.cosmetic.show_armor" : "gui.pioneers.cosmetic.show_cosmetic", name),
                      Component.translatable("gui.pioneers.cosmetic.toggle_hint").withStyle(ChatFormatting.GRAY)), mx, my);
            }
        }
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        int[] o = origin(screen);
        LocalPlayer player = Minecraft.getInstance().player;
        if (o == null || player == null || event.getButton() > 1) {
            return;
        }
        double mx = event.getMouseX(), my = event.getMouseY();
        if (over(mx, my, o[0], o[1], PANEL_W, PANEL_H)) {
            for (int i = 0; i < Wardrobe.SIZE; i++) {
                if (over(mx, my, slotX(o) - 1, slotY(o, i) - 1, 18, 18)) {
                    CosmeticNet.toServer(Screen.hasShiftDown() ? CosmeticActions.QUICK_OUT : CosmeticActions.CLICK, i);
                } else if (over(mx, my, o[0] + TOGGLE_X, o[1] + TOGGLE_DY + i * 18, TOGGLE, TOGGLE)) {
                    CosmeticNet.toServer(CosmeticActions.TOGGLE, i);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                          net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
                }
            }
            pressed = true;
            event.setCanceled(true);
            return;
        }
        //Maj + clic sur une piece cosmetique de l'inventaire : l'enfiler
        if (Screen.hasShiftDown() && event.getButton() == 0 && screen instanceof InventoryScreen inv) {
            Slot slot = inv.getSlotUnderMouse();
            if (slot != null && slot.container == player.getInventory() && slot.getContainerSlot() < player.getInventory().items.size()
                  && slot.getItem().getItem() instanceof CosmeticItem) {
                CosmeticNet.toServer(CosmeticActions.QUICK_IN, slot.getContainerSlot());
                pressed = true;
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (pressed) {
            pressed = false;
            event.setCanceled(true);
        }
    }
}
