package fr.pioneers.equipment.cosmetic.client;

import fr.pioneers.Pioneers;
import fr.pioneers.society.rp.client.RpClientConfig;
import fr.pioneers.equipment.cosmetic.CosmeticCap;
import fr.pioneers.equipment.cosmetic.CosmeticPiece;
import fr.pioneers.equipment.cosmetic.Wardrobe;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.12.0 (lot E4) : cote client de la garde-robe.
 * <ul>
 * <li>reception ({@link #receive}) : la garde-robe d'un joueur visible est recopiee dans SA capacite (attachee aussi cote client) ;</li>
 * <li>armure masquee : juste avant le rendu d'un joueur (RenderPlayerEvent.Pre, priorite la plus basse, jamais si un autre mod a annule
 * le rendu), la piece d'armure de chaque emplacement montre en cosmetique est retiree de la liste d'armure du joueur COTE CLIENT, puis
 * remise juste apres (Post) : le calque d'armure vanilla ne la dessine pas, rien n'est change cote serveur (protection intacte). Les elytres
 * restent visibles. Filet de securite : tout ce qui n'aurait pas ete remis l'est au tick client suivant.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class CosmeticClient {

    /** Armures mises de cote pendant le rendu d'un joueur (rendu jamais imbrique pour un meme joueur). */
    private static final Map<Player, ItemStack[]> STASH = new IdentityHashMap<>();

    private CosmeticClient() {
    }

    public static void receive(int entity, ItemStack[] items, int mask) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(entity);
        if (e instanceof Player p) {
            restore(p);
            Wardrobe w = CosmeticCap.get(p);
            for (int i = 0; i < Wardrobe.SIZE && i < items.length; i++) {
                w.set(i, Wardrobe.fits(i, items[i]) ? items[i] : ItemStack.EMPTY);
            }
            w.setMask(mask);
        }
    }

    /** Joueur dont les cosmetiques ne sont pas dessines (options client {@code hideOwnCosmetics} / {@code hideOthersCosmetics}). */
    static boolean hidden(Player player) {
        return player == Minecraft.getInstance().player ? RpClientConfig.get(RpClientConfig.HIDE_OWN_COSMETICS, false)
              : RpClientConfig.get(RpClientConfig.HIDE_OTHERS_COSMETICS, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        restore(p);
        if (hidden(p)) {
            return;
        }
        Wardrobe w = CosmeticCap.get(p);
        ItemStack[] stash = null;
        for (CosmeticPiece piece : CosmeticPiece.ALL) {
            int i = piece.ordinal();
            if (!w.shows(i)) {
                continue;
            }
            int slot = piece.slot.getIndex();
            ItemStack armor = p.getInventory().armor.get(slot);
            if (armor.isEmpty() || armor.getItem() instanceof ElytraItem) {
                continue;
            }
            if (stash == null) {
                stash = new ItemStack[Wardrobe.SIZE];
            }
            stash[slot] = armor;
            p.getInventory().armor.set(slot, ItemStack.EMPTY);
        }
        if (stash != null) {
            STASH.put(p, stash);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void post(RenderPlayerEvent.Post event) {
        restore(event.getEntity());
    }

    private static void restore(Player p) {
        ItemStack[] stash = STASH.remove(p);
        if (stash == null) {
            return;
        }
        for (int slot = 0; slot < stash.length; slot++) {
            if (stash[slot] != null && p.getInventory().armor.get(slot).isEmpty()) {
                p.getInventory().armor.set(slot, stash[slot]);
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !STASH.isEmpty()) {
            for (Player p : STASH.keySet().toArray(new Player[0])) {
                restore(p);
            }
        }
    }
}
