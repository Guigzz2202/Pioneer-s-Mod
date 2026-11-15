package fr.pioneers.client.economy;

import fr.pioneers.Pioneers;
import fr.pioneers.economy.EconomyBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 3) : etals cote client : enregistrement du rendu, lecture de la vue envoyee par le serveur ({@code STALL_VIEW}) et
 * ouverture ou mise a jour de l'ecran (configuration ou achat). Aucune valeur n'est calculee ici : tout chiffre vient du serveur.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StallClient {

    private StallClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EconomyBlocks.STALL_TILE.get(), StallRenderer::new);
    }

    /** Une annonce telle que le serveur la decrit (min / max = -1 : aucun prix entier possible pour ce lot). */
    public record Entry(int index, boolean locked, ItemStack stack, int lot, long price, long value, boolean valued, boolean priced, long min,
                        long max, int pct, int stock, int lots, int[] choices, long[] totals, byte[] ok) {

        public boolean empty() {
            return stack.isEmpty();
        }
    }

    /** La vue complete d'un etal. */
    public record View(long pos, boolean edit, boolean counter, String owner, boolean mine, long balance, int slots, int base, int bonus, int tax,
                       int minPct, int maxPct, boolean open, List<Entry> entries, Component message) {

        public static View read(CompoundTag t) {
            List<Entry> list = new ArrayList<>();
            for (Tag x : t.getList("listings", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) x;
                ItemStack s = e.contains("stack") ? ItemStack.of(e.getCompound("stack")) : ItemStack.EMPTY;
                list.add(new Entry(e.getInt("i"), e.getBoolean("locked"), s, e.getInt("lot"), e.getLong("price"), e.getLong("value"),
                      e.getBoolean("valued"), e.getBoolean("priced"), e.contains("min") ? e.getLong("min") : -1,
                      e.contains("max") ? e.getLong("max") : -1, e.getInt("pct"), e.getInt("stock"), e.getInt("lots"), e.getIntArray("n"),
                      e.getLongArray("tot"), e.getByteArray("ok")));
            }
            Component msg = Component.empty();
            if (t.contains("msg")) {
                List<Object> args = new ArrayList<>();
                t.getList("args", Tag.TAG_STRING).forEach(a -> args.add(a.getAsString()));
                msg = Component.translatable(t.getString("msg"), args.toArray());
            }
            return new View(t.getLong("pos"), t.getBoolean("edit"), t.getBoolean("counter"), t.getString("owner"), t.getBoolean("mine"),
                  t.getLong("balance"), t.getInt("slots"), t.getInt("base"), t.getInt("bonus"), t.getInt("tax"), t.getInt("minPct"),
                  t.getInt("maxPct"), t.getBoolean("open"), list, msg);
        }

        /** Titre : « Etal de X » / « Comptoir de X ». */
        public Component title() {
            return Component.translatable(counter ? "stall.pioneers.title.counter" : "stall.pioneers.title.stall", owner);
        }
    }

    /** Vue recue du serveur : met a jour l'ecran ouvert sur le meme etal, sinon ouvre le bon ecran. */
    public static void view(CompoundTag t) {
        View v = View.read(t);
        Minecraft mc = Minecraft.getInstance();
        if (v.edit()) {
            if (mc.screen instanceof StallEditScreen s && s.pos() == v.pos()) {
                s.read(v);
            } else {
                mc.setScreen(new StallEditScreen(v));
            }
        } else {
            if (mc.screen instanceof StallBuyScreen s && s.pos() == v.pos()) {
                s.read(v);
            } else {
                mc.setScreen(new StallBuyScreen(v));
            }
        }
    }

    /** Montant en centiemes de jeton, ecrit a la facon de la langue (« 6,40 » / « 6.40 »). */
    public static String centi(long centi) {
        long whole = centi / 100, frac = Math.abs(centi % 100);
        if (frac == 0) {
            return Long.toString(whole);
        }
        return I18n.get("stall.pioneers.decimal", Long.toString(whole), (frac < 10 ? "0" : "") + frac);
    }

    /** Pourcentage de la valeur de base, avec l'ecart signe (« 120 % (+20) », « 80 % (−20) », « 100 % ») : la couleur n'est jamais seule. */
    public static Component percent(int pct) {
        if (pct == 100) {
            return Component.translatable("stall.pioneers.pct.base");
        }
        return Component.translatable(pct > 100 ? "stall.pioneers.pct.above" : "stall.pioneers.pct.below", String.valueOf(pct),
              String.valueOf(Math.abs(pct - 100)));
    }

    /** Couleur du pourcentage pour un acheteur : vert sous la base, rouge au-dessus, neutre a la base. */
    public static int percentColor(int pct) {
        return pct < 100 ? 0xFF6FCF7F : pct > 100 ? 0xFFE3735F : 0xFFB8C2CC;
    }
}
