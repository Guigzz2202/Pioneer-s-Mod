package fr.pioneers.world.hunt;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 1.0.5-beta (lot 10) : munitions dans l'inventaire du joueur (main gauche d'abord, puis inventaire). */
public final class Ammo {

    private Ammo() {
    }

    /** Index de la case qui porte un objet du tag (40 = main gauche), -1 sinon. */
    public static int find(Player p, TagKey<Item> tag) {
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty() && off.is(tag)) {
            return 40;
        }
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (!s.isEmpty() && s.is(tag)) {
                return i;
            }
        }
        return -1;
    }

    /** Nombre d'objets du tag. */
    public static int count(Player p, TagKey<Item> tag) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(tag)) {
                n += s.getCount();
            }
        }
        return n;
    }
}
