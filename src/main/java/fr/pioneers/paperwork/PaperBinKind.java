package fr.pioneers.paperwork;

import fr.pioneers.economy.contract.InvoiceItem;
import fr.pioneers.economy.mail.LetterItem;
import fr.pioneers.ruins.LoreItem;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.5-beta (lot 16, retours de Guillaume : « bac a lettres pour ranger et trier les lettres », « bac d'archives pour stocker les morceaux de
 * lore ») : les deux bacs a papiers. Le BAC A LETTRES range les lettres de la boite aux lettres et les factures imprimees ; le BAC D'ARCHIVES
 * range les ecrits du Codex (notes de parchemin, pages, modules de memoire). Capacite raisonnable : une boite aux lettres double (54) et un
 * peu plus que tout le Codex (1.0.5-beta lot 19 : 192 ; 128 avant, alors que le Codex compte 176 ecrits : la collection ne tenait pas dans un
 * bac ; l'Armoire d'archives des ruines est un butin a fouiller, pas un rangement : elle reste ce qu'elle est).
 */
public enum PaperBinKind {
    LETTERS("letter_bin", 54),
    ARCHIVES("archive_bin", 192);

    public final String id;
    public final int slots;

    PaperBinKind(String id, int slots) {
        this.id = id;
        this.slots = slots;
    }

    /** Objet accepte : lettre ou facture (bac a lettres), ecrit du Codex qui porte son entree (bac d'archives). */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case LETTERS -> stack.getItem() instanceof LetterItem || stack.getItem() instanceof InvoiceItem;
            case ARCHIVES -> stack.getItem() instanceof LoreItem && LoreItem.id(stack) != null;
        };
    }

    public static PaperBinKind byOrdinal(int i) {
        return values()[Math.max(0, Math.min(values().length - 1, i))];
    }
}
