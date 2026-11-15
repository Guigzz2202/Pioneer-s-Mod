package fr.pioneers.client.economy;

import fr.pioneers.client.rp.ParchmentScreen;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.5-beta (lot 26, retour 14) : lecture d'un DOCUMENT IMPRIME ({@code economy/contract/InvoiceItem}, NBT {@code document}, clic droit ou bac
 * a lettres) : la feuille mise en forme ({@link DocumentPaper}), qui defile ; une facture d'avant migree garde son instantane : bouton
 * « Facture d'origine » ({@link InvoiceScreen}). Rien n'est demande au serveur : c'est un instantane.
 */
public class DocumentScreen extends ParchmentScreen {

    public static final int PAPER_X = 40, PAPER_Y = 12, PAPER_W = 304, PAPER_H = 206, BUTTON_W = 120, BUTTON_Y = 222, SLOT = 0;
    private final CompoundTag t;
    @Nullable
    private final net.minecraft.client.gui.screens.Screen parent;

    public DocumentScreen(CompoundTag t, @Nullable net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("item.pioneers.contract_invoice"));
        this.t = t;
        this.parent = parent;
    }

    public static void open(CompoundTag t) {
        Minecraft.getInstance().setScreen(new DocumentScreen(t, null));
    }

    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        DocumentPaper.draw(this, g, mx, my, t, PAPER_X, PAPER_Y, PAPER_W, PAPER_H, SLOT, -1);
        if (t.contains("legacy")) {
            CompoundTag legacy = t.getCompound("legacy");
            //pastille (libelle borne par chip / fitCentered)
            chip(g, mx, my, (PAGE_W - BUTTON_W) / 2, BUTTON_Y, BUTTON_W, 13, Component.translatable("document.pioneers.original_invoice"), false, true,
                  () -> Minecraft.getInstance().setScreen(new InvoiceScreen(legacy, this)));
        }
    }
}
