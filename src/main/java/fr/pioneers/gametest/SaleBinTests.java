package fr.pioneers.gametest;

import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.EconomyBlocks;
import fr.pioneers.society.economy.Values;
import fr.pioneers.society.economy.bin.SaleBinBlock;
import fr.pioneers.society.economy.bin.SaleBinRules;
import fr.pioneers.society.economy.bin.SaleBinTile;
import fr.pioneers.society.village.VillageData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.10.1 : bac de vente du village ({@code economy/bin}) : vente de minuit (70 % du prix d'un villageois, deux fois son quota par sorte
 * d'objet, invendables laisses dans le bac), jetons credites, reputation du village (+1 par 200 jetons, plafond par nuit), un seul bac par
 * joueur et par village, pose refusee loin de la cloche ; proprietaire absent : credit mis de cote et applique a sa connexion.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class SaleBinTests {

    private SaleBinTests() {
    }

    /** Premier objet de la table qu'un Colporteur achete, de la classe de quota demandee (ou qu'il refuse, pour {@code refused}). */
    @Nullable
    static Item pick(String quota, boolean refused) {
        for (Item it : BuiltInRegistries.ITEM) {
            Values.Entry e = Values.entry(it);
            if (e != null && e.value() > 0 && quota.equals(e.quota()) && Values.peddlerBuys(it) != refused && it.getMaxStackSize() == 64) {
                return it;
            }
        }
        return null;
    }

    static SaleBinTile bin(Gt.Scope s, BlockPos rel) {
        s.h.setBlock(rel, EconomyBlocks.SALE_BIN.getBlock().defaultBlockState());
        BlockPos abs = s.h.absolutePos(rel);
        s.onCleanup(() -> s.level.removeBlock(abs, false));
        Gt.check(s.level.getBlockEntity(abs) instanceof SaleBinTile, "bac de vente sans tuile");
        return (SaleBinTile) s.level.getBlockEntity(abs);
    }

    /** Vente de minuit : prix, limite de la nuit, invendables, jetons, reputation du village, un bac par village. */
    @GameTest(template = Gt.LARGE, batch = "pioneers_bac_de_vente", timeoutTicks = 100)
    public static void bac_de_vente_minuit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee (interrupteur economy)");
        BlockPos bellRel = new BlockPos(10, 2, 10);
        EconomyVillageTests.bell(s, bellRel);
        h.runAfterDelay(5, () -> {
            List<Villager> vs = new ArrayList<>();
            VillageData.Village village = EconomyVillageTests.village(s, bellRel, vs, 3);
            ServerPlayer p = s.player("gtbin", "Marchande", new BlockPos(10, 2, 6));
            UUID me = p.getUUID();
            BlockPos binRel = new BlockPos(14, 2, 14);
            SaleBinTile tile = bin(s, binRel);
            Gt.check(SaleBinBlock.take(s.level, s.h.absolutePos(binRel), p, tile), "bac refuse sur la place du village");
            Gt.equal(me, tile.owner(), "proprietaire du bac");
            //un deuxieme bac du meme joueur dans le meme village : refuse
            SaleBinTile other = bin(s, new BlockPos(6, 2, 14));
            Gt.check(!SaleBinBlock.take(s.level, s.h.absolutePos(new BlockPos(6, 2, 14)), p, other), "deux bacs dans le meme village");
            Item raw = pick("raw", false), comp = pick("component", true);
            Gt.check(raw != null, "aucun objet brut vendable dans la table");
            for (int i = 0; i < 3; i++) {
                tile.setItem(i, new ItemStack(raw, 64));
            }
            if (comp != null) {
                tile.setItem(3, new ItemStack(comp, 4));
            }
            int limit = Values.quota("raw") * SaleBinRules.quotaFactor();
            long unit = SaleBinRules.unit(Values.value(new ItemStack(raw)), Values.partner("villager", "buys", 0.5), SaleBinRules.share());
            long expected = unit * Math.min(192, limit) / 100;
            long before = EconomyApi.balance(p);
            int avg = village.average(me);
            SaleBinTile.Night n = tile.midnight(s.level);
            Gt.check(n.refusal() == null, "vente refusee : " + n.refusal());
            Gt.equal(Math.min(192, limit), n.sold(), "objets vendus (limite de la nuit)");
            Gt.equal(expected, n.tokens(), "jetons de la nuit");
            Gt.equal(before + expected, EconomyApi.balance(p), "solde du proprietaire");
            Gt.equal(192 - Math.min(192, limit) + (comp != null ? 4 : 0), tile.count(), "restes dans le bac (limite et invendables)");
            int pts = SaleBinRules.points(expected, SaleBinRules.perPoint(), SaleBinRules.maxPoints());
            Gt.equal(pts, n.points(), "points de reputation");
            Gt.equal(Math.min(100, avg + pts), village.average(me), "reputation du village");
            Gt.check(pts <= SaleBinRules.maxPoints(), "plafond de la nuit depasse");
            //prix du bac < prix d'un villageois : le commerce direct rapporte plus
            Gt.check(unit < Math.round(Values.value(new ItemStack(raw)) * Values.partner("villager", "buys", 0.5)), "le bac paie autant qu'un villageois");
            s.done();
        });
    }

    /** Pose loin de la cloche : refusee. Proprietaire absent a minuit : credit mis de cote, applique a sa connexion. */
    @GameTest(template = Gt.LARGE, batch = "pioneers_bac_de_vente_absent", timeoutTicks = 100)
    public static void bac_de_vente_proprietaire_absent(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos bellRel = new BlockPos(10, 2, 10);
        EconomyVillageTests.bell(s, bellRel);
        h.runAfterDelay(5, () -> {
            List<Villager> vs = new ArrayList<>();
            EconomyVillageTests.village(s, bellRel, vs, 2);
            BlockPos at = new BlockPos(14, 2, 14);
            ServerPlayer p = s.player("gtbinoff", "Absente", at);
            UUID id = p.getUUID();
            String account = p.getGameProfile().getName();
            SaleBinTile tile = bin(s, at);
            Gt.check(SaleBinBlock.take(s.level, s.h.absolutePos(at), p, tile), "bac refuse sur la place du village");
            Item raw = pick("raw", false);
            Gt.check(raw != null, "aucun objet brut vendable dans la table");
            tile.setItem(0, new ItemStack(raw, 64));
            long before = EconomyApi.balance(p);
            Gt.disconnect(s.server, id);
            SaleBinTile.Night n = tile.midnight(s.level);
            Gt.check(n.refusal() == null && n.tokens() > 0, "rien vendu en l'absence du proprietaire (refus " + n.refusal() + ", jetons " + n.tokens()
                  + ", objet " + raw + ", vendable " + fr.pioneers.society.economy.Trade.sellable(new ItemStack(raw)) + ", nuit " + n + ")");
            ServerPlayer q = s.reconnect(id, account, at);
            Gt.equal(before + n.tokens(), EconomyApi.balance(q), "credit du bac applique a la connexion");
            Gt.equal(SaleBinRules.night(s.level.getDayTime() + 24_000L), SaleBinRules.night(s.level.getDayTime()) + 1, "nuit suivante");
            s.done();
        });
    }
}
