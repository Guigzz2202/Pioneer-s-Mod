package fr.pioneers.forge.automation;

import net.minecraft.world.item.Item;

/**
 * Matrice en acier (lingot, plaque, tige, engrenage) : remplace le moule d'argile dans la machine de coulee et la coulee continue ; jamais
 * consommee. {@link #shape} = forme du moule correspondant ({@code ingot}, {@code plate}, {@code rod}, {@code gear}).
 * PROPRIETAIRE : ingenieur « automation ».
 */
public class DieItem extends Item {

    public final String shape;

    public DieItem(String shape, Properties properties) {
        super(properties);
        this.shape = shape;
    }
}
