package fr.pioneers.paperwork;

import fr.pioneers.economy.contract.InvoiceItem;
import fr.pioneers.economy.mail.Letters;
import fr.pioneers.ruins.LoreItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.5-beta (lot 16) : regles des bacs a papiers, communes au serveur, a l'ecran et aux GameTests : fiche de chaque papier (sorte,
 * expediteur, objet, date, lu), tri (date, expediteur, sorte), filtre, acces (proprietaire, membres de son clan, operateurs).
 */
public final class PaperBinRules {

    /** Tris du bac a lettres. */
    public enum Sort {
        DATE, SENDER, KIND
    }

    /** Fiche d'un papier rangé : case, sorte (cle courte), expediteur, objet (texte brut), date (ms, 0 = inconnue), lu. */
    public record Paper(int slot, String kind, String sender, String subject, long date, boolean read) {
    }

    private PaperBinRules() {
    }

    /** Fiche d'une lettre ou d'une facture (null pour un autre objet). */
    @Nullable
    public static Paper paper(int slot, ItemStack stack) {
        CompoundTag l = Letters.data(stack);
        if (l != null) {
            return new Paper(slot, "letter." + l.getString("k"), l.getString("f"), l.getInt("n") > 0 ? l.getString("t") : "", l.getLong("d"),
                  l.getBoolean("r"));
        }
        CompoundTag inv = InvoiceItem.data(stack);
        if (inv != null) {
            return new Paper(slot, "invoice", inv.getString("pvn"), inv.getString("title"), inv.getLong("printed"), true);
        }
        //1.0.5-beta (lot 26) : document imprime de l'onglet Documents (auteur, objet, instant d'impression)
        CompoundTag doc = stack.getTag() != null && stack.getTag().contains(InvoiceItem.DOCUMENT) ? stack.getTag().getCompound(InvoiceItem.DOCUMENT) : null;
        if (doc != null) {
            return new Paper(slot, "document", doc.getCompound("author").getString("n"), doc.getString("subject"), doc.getLong("printedMs"), true);
        }
        if (stack.getItem() instanceof fr.pioneers.economy.mail.LetterItem) {
            return new Paper(slot, "letter.blank", "", "", 0, true);
        }
        return null;
    }

    /** Fiches du bac triees ({@code sort}), filtrees (texte dans l'expediteur ou l'objet, non lues seulement). PUR. */
    public static List<Paper> sorted(List<Paper> all, Sort sort, String filter, boolean unreadOnly) {
        String q = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<Paper> out = new ArrayList<>();
        for (Paper p : all) {
            if (unreadOnly && p.read()) {
                continue;
            }
            if (!q.isEmpty() && !p.sender().toLowerCase(Locale.ROOT).contains(q) && !p.subject().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(p);
        }
        Comparator<Paper> byDate = Comparator.comparingLong(Paper::date).reversed().thenComparingInt(Paper::slot);
        out.sort(switch (sort) {
            case SENDER -> Comparator.comparing((Paper p) -> p.sender().toLowerCase(Locale.ROOT)).thenComparing(byDate);
            case KIND -> Comparator.comparing(Paper::kind).thenComparing(byDate);
            default -> byDate;
        });
        return out;
    }

    /** Entree du Codex d'un ecrit (son identifiant) ou null. */
    @Nullable
    public static String loreId(ItemStack stack) {
        return stack.getItem() instanceof LoreItem ? LoreItem.id(stack) : null;
    }

    /** Ouvrir / prendre / deposer : le proprietaire (ou personne encore), un membre de son clan, un operateur. */
    public static boolean mayUse(@Nullable UUID owner, UUID player, @Nullable UUID ownerClan, @Nullable UUID playerClan, boolean op) {
        return owner == null || owner.equals(player) || op || (ownerClan != null && ownerClan.equals(playerClan));
    }
}
