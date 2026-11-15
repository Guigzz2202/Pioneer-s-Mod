package fr.pioneers.society.economy.contract;

import fr.pioneers.core.tooltip.Tooltips;
import java.time.ZoneId;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 7) : facture d'un contrat, imprimee depuis le Carnet (onglet Contrats, une feuille de papier) : INSTANTANE du contrat au
 * moment de l'impression (NBT {@code invoice} : numero, parties, periode, heures, taux, facture, paye, reste, date d'impression, fuseau).
 * Clic droit : la lecture (ecran {@code client/economy/InvoiceScreen}). Elle se remet en main a l'autre partie, ne vaut que son papier (valeur
 * de la feuille, `gen_values`) et ne change rien au contrat.
 * <p>
 * 1.0.5-beta (lot 26, onglet Documents) : l'objet devient le DOCUMENT IMPRIME de tout type (facture, contrat, lettre, devis...) : NBT
 * {@link #DOCUMENT} ({@code economy/document/Document} + jour d'impression), lu par {@code client/economy/DocumentScreen} (papier mis en
 * forme). Une facture imprimee d'avant (NBT {@link #TAG} seul) est MIGREE au premier passage dans un inventaire de joueur ou a la lecture
 * ({@link #migrate} : {@code DocumentMigration.fromInvoice}) : le NBT d'origine est garde a cote, rien n'est perdu.
 */
public class InvoiceItem extends Item {

    public static final String TAG = "invoice";
    /** 1.0.5-beta (lot 26) : document imprime (nouveau modele). */
    public static final String DOCUMENT = "document";

    public InvoiceItem(Properties properties) {
        super(properties);
    }

    /** Facture du contrat {@code c} imprimee a {@code now}. */
    public static ItemStack make(Contract c, long now, ZoneId zone) {
        ItemStack s = new ItemStack(ContractItems.INVOICE.get());
        CompoundTag t = ContractService.card(c, now);
        t.putLong("printed", now);
        t.putString("zone", zone.getId());
        s.getOrCreateTag().put(TAG, t);
        return s;
    }

    @Nullable
    public static CompoundTag data(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(TAG) ? t.getCompound(TAG) : null;
    }

    /** 1.0.5-beta (lot 26) : document imprime (nouveau modele, ou migre d'une facture d'avant), ou {@code null}. */
    @Nullable
    public static CompoundTag document(ItemStack stack) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains(DOCUMENT)) {
            return t.getCompound(DOCUMENT);
        }
        CompoundTag inv = data(stack);
        if (inv == null) {
            return null;
        }
        CompoundTag d = fr.pioneers.society.economy.document.DocumentMigration.fromInvoice(inv).save();
        d.putLong("printed", -1);
        return d;
    }

    /** 1.0.5-beta (lot 26) : migre une facture d'avant (ajoute le document, garde l'instantane d'origine) ; vrai si l'objet a change. */
    public static boolean migrate(ItemStack stack) {
        CompoundTag t = stack.getTag();
        if (t == null || t.contains(DOCUMENT) || !t.contains(TAG)) {
            return false;
        }
        t.put(DOCUMENT, document(stack));
        return true;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull net.minecraft.world.entity.Entity entity, int slot,
          boolean selected) {
        if (!level.isClientSide) {
            migrate(stack);
        }
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        CompoundTag d = document(stack);
        if (d != null) {
            return Component.translatable("document.pioneers.numbered", Component.translatable("document.pioneers.type." + d.getString("type")),
                  d.getInt("id"));
        }
        return super.getName(stack);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            migrate(stack);
        }
        CompoundTag t = document(stack);
        if (t == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.society.economy.client.DocumentScreen.open(t));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag d = document(stack);
        if (d != null) {
            fr.pioneers.society.economy.document.Document doc = fr.pioneers.society.economy.document.Document.load(d);
            tips.custom(Component.literal(doc.subject));
            tips.stat("document.pioneers.label.from", doc.author.name());
            tips.stat("document.pioneers.label.to", doc.recipient.name());
            if (doc.total() > 0) {
                tips.stat("document.pioneers.label.total", Component.translatable("economy.pioneers.tokens", Long.toString(doc.total())));
            }
            if (doc.owed() > 0) {
                tips.stat("document.pioneers.label.owed", Component.translatable("economy.pioneers.tokens", Long.toString(doc.owed())));
            }
            tips.stat("document.pioneers.label.status", Component.translatable("document.pioneers.status." + doc.status.key()));
            tips.hint("item.pioneers.contract_invoice.hint");
            return;
        }
        CompoundTag t = data(stack);
        if (t == null) {
            tips.desc("item.pioneers.contract_invoice.desc");
            return;
        }
        tips.custom(Component.literal(t.getString("title")));
        tips.stat("contract.pioneers.label.provider", t.getString("pvn"));
        tips.stat("contract.pioneers.label.client", t.getString("cln"));
        tips.stat("contract.pioneers.label.billed", Component.translatable("economy.pioneers.tokens", Long.toString(t.getLong("billed"))));
        tips.stat("contract.pioneers.label.paid", Component.translatable("economy.pioneers.tokens", Long.toString(t.getLong("paid"))));
        tips.stat("contract.pioneers.label.owed", Component.translatable("economy.pioneers.tokens", Long.toString(t.getLong("owed"))));
        tips.hint("item.pioneers.contract_invoice.hint");
    }
}
