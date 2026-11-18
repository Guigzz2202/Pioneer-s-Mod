package fr.pioneers.gametest;

import fr.pioneers.core.profile.KnownPlayers;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.contract.Contract;
import fr.pioneers.society.economy.contract.ContractData;
import fr.pioneers.society.economy.contract.ContractRules;
import fr.pioneers.society.economy.contract.ContractService;
import fr.pioneers.society.economy.mail.Letters;
import fr.pioneers.society.paperwork.PaperBinKind;
import fr.pioneers.society.paperwork.PaperBinMenu;
import fr.pioneers.society.paperwork.PaperBinRules;
import fr.pioneers.society.paperwork.PaperBinTile;
import fr.pioneers.society.paperwork.PiPaperwork;
import fr.pioneers.world.ruins.LoreItem;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 16 (retours de jeu 4 : le Carnet, GameTests) : TRI DU BAC A LETTRES (date, expediteur, sorte ; filtre ; non lues ;
 * une lettre lue reste lue) ; RANGEMENT DU BAC D'ARCHIVES (pose, « Tout ranger » ne prend que les ecrits du Codex, sauvegarde relue, place
 * bornee) ; CONTRAT AVEC CORPS DE LETTRE (enregistre, relu tel quel apres sauvegarde, lignes gardees ; trop long : refuse ; ancien contrat sans
 * corps : sa description en tient lieu) ; REFUS D'UN PSEUDO INCONNU (« client_unknown ») et registre des joueurs deja vus (selecteur).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot16Tests {

    private PatchP105Lot16Tests() {
    }

    private static ItemStack letter(String kind, String from, String title, long date, boolean read) {
        ItemStack s = Letters.make(kind, from, title.isEmpty() ? 0 : 7, title, "total", 100, 0, date, ZoneId.of("UTC"));
        if (read) {
            Letters.markRead(s);
        }
        return s;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_16_letters", timeoutTicks = 20)
    public static void p105_16_tri_du_bac_a_lettres(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        List<PaperBinRules.Paper> all = new ArrayList<>();
        ItemStack[] papers = {letter("transfer", "Mireille", "", 3_000, false), letter("contract_proposed", "Joran", "Charpente", 1_000, true),
              letter("contract_accepted", "Ernest", "Forge", 2_000, true), new ItemStack(fr.pioneers.world.ruins.RuinItems.LORE_PAGE.get())};
        for (int i = 0; i < papers.length; i++) {
            PaperBinRules.Paper p = PaperBinRules.paper(i, papers[i]);
            if (p != null) {
                all.add(p);
            }
        }
        Gt.equal(3, all.size(), "une page de lore n'est pas une lettre");
        List<PaperBinRules.Paper> byDate = PaperBinRules.sorted(all, PaperBinRules.Sort.DATE, "", false);
        Gt.equal("Mireille", byDate.get(0).sender(), "tri par date : la plus recente en tete");
        Gt.equal("Joran", byDate.get(2).sender(), "tri par date : la plus ancienne en dernier");
        List<PaperBinRules.Paper> bySender = PaperBinRules.sorted(all, PaperBinRules.Sort.SENDER, "", false);
        Gt.equal("Ernest", bySender.get(0).sender(), "tri par expediteur : ordre alphabetique");
        List<PaperBinRules.Paper> unread = PaperBinRules.sorted(all, PaperBinRules.Sort.DATE, "", true);
        Gt.check(unread.size() == 1 && unread.get(0).sender().equals("Mireille"), "non lues seulement");
        Gt.equal(1, PaperBinRules.sorted(all, PaperBinRules.Sort.KIND, "charp", false).size(), "filtre sur l'objet");
        //le bac pose : « Tout ranger » prend lettres et facture, pas la page ; lire marque la lettre lue
        BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlock(pos, PiPaperwork.LETTER_BIN.getBlock().defaultBlockState(), 3);
        PaperBinTile tile = (PaperBinTile) h.getLevel().getBlockEntity(pos);
        Gt.check(tile != null && tile.kind == PaperBinKind.LETTERS, "tuile du bac a lettres");
        SimpleContainer inv = new SimpleContainer(papers.length);
        for (int i = 0; i < papers.length; i++) {
            inv.setItem(i, papers[i].copy());
        }
        Gt.equal(3, PaperBinMenu.deposit(inv, tile.items, PaperBinKind.LETTERS), "trois lettres rangees");
        Gt.check(!inv.getItem(3).isEmpty(), "la page de lore reste dans l'inventaire");
        Gt.equal(54, tile.items.getContainerSize(), "54 places");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_16_archives", timeoutTicks = 20)
    public static void p105_16_rangement_du_bac_d_archives(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlock(pos, PiPaperwork.ARCHIVE_BIN.getBlock().defaultBlockState(), 3);
        PaperBinTile tile = (PaperBinTile) h.getLevel().getBlockEntity(pos);
        Gt.check(tile != null && tile.kind == PaperBinKind.ARCHIVES, "tuile du bac d'archives");
        SimpleContainer inv = new SimpleContainer(4);
        inv.setItem(0, LoreItem.page("ch01_smith_colors", false));
        inv.setItem(1, LoreItem.note("note_01"));
        inv.setItem(2, letter("transfer", "Mireille", "", 1, false));
        inv.setItem(3, new ItemStack(fr.pioneers.world.ruins.RuinItems.LORE_PAGE.get()));          //page sans entree : refusee
        Gt.equal(2, PaperBinMenu.deposit(inv, tile.items, PaperBinKind.ARCHIVES), "page et note rangees");
        Gt.check(!inv.getItem(2).isEmpty() && !inv.getItem(3).isEmpty(), "lettre et page vierge restent dans l'inventaire");
        Gt.equal("ch01_smith_colors", PaperBinRules.loreId(tile.items.getItem(0)), "l'ecrit garde son entree du Codex");
        //sauvegarde relue
        CompoundTag saved = tile.saveWithoutMetadata();
        PaperBinTile copy = new PaperBinTile(pos, tile.getBlockState());
        copy.load(saved);
        Gt.equal("ch01_smith_colors", PaperBinRules.loreId(copy.items.getItem(0)), "contenu relu (NBT)");
        Gt.equal("note_01", PaperBinRules.loreId(copy.items.getItem(1)), "note relue (NBT)");
        Gt.equal(192, tile.items.getContainerSize(), "192 places (lot 19 : tout le Codex, 176 ecrits)");
        //acces : proprietaire, son clan, operateur ; un etranger non
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), clan = UUID.randomUUID();
        Gt.check(PaperBinRules.mayUse(a, a, null, null, false), "le proprietaire");
        Gt.check(PaperBinRules.mayUse(a, b, clan, clan, false), "un membre de son clan");
        Gt.check(!PaperBinRules.mayUse(a, b, clan, UUID.randomUUID(), false) && !PaperBinRules.mayUse(a, b, null, null, false), "un etranger");
        Gt.check(PaperBinRules.mayUse(a, b, null, null, true), "un operateur");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_16_letter_contract", timeoutTicks = 20)
    public static void p105_16_contrat_avec_lettre_sauvegardee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee (interrupteur economy)");
        ServerPlayer pv = s.player("gtlpv", "Anselme", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gtlcl", "Mireille", new BlockPos(3, 1, 1));
        long t0 = PatchP104Lot7Tests.base();
        String body = "Bonjour Mireille,\n\nJe garde la mine cette nuit.\nRondes toutes les heures.";
        ContractService.Saved sv = ContractService.save(pv, 0, cl.getGameProfile().getName(), "Garde de la mine", body,
              PatchP104Lot7Tests.fields(t0), PatchP104Lot7Tests.fields(t0 + 4 * ContractRules.HOUR), false, 400, false, t0 - ContractRules.HOUR);
        Gt.check(sv.error() == null, "brouillon refuse : " + sv.error());
        ContractData data = ContractData.get(s.server);
        Contract c = data.get(sv.id());
        try {
            Gt.equal(body, c.body, "corps de la lettre enregistre tel quel (lignes gardees)");
            Contract back = Contract.load(c.save());
            Gt.equal(body, back.body, "corps relu apres sauvegarde");
            CompoundTag old = c.save();
            old.remove("body");
            old.putString("note", "Ronde de nuit");
            Gt.equal("Ronde de nuit", Contract.load(old).body, "contrat d'avant sans corps : sa description en tient lieu");
            String tooLong = "x".repeat(ContractRules.BODY_MAX + 1);
            Gt.equal("body_long", ContractRules.bodyProblem(tooLong), "lettre trop longue refusee");
            Gt.equal("body_lines", ContractRules.bodyProblem("a\n".repeat(ContractRules.BODY_LINES)), "trop de lignes refusees");
            Gt.equal(null, ContractRules.bodyProblem(body), "lettre bornee acceptee");
            Gt.equal("abc", ContractRules.cleanBody("a§bc  \n\n"), "codes de couleur et lignes vides finales retires");
        } finally {
            data.remove(c.id);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_16_unknown", timeoutTicks = 20)
    public static void p105_16_refus_d_un_pseudo_inconnu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gtupv", "Anselme", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gtucl", "Mireille", new BlockPos(3, 1, 1));
        long t0 = PatchP104Lot7Tests.base();
        ContractService.Saved sv = ContractService.save(pv, 0, "Personne_Inconnue_42", "Garde", "", PatchP104Lot7Tests.fields(t0),
              PatchP104Lot7Tests.fields(t0 + ContractRules.HOUR), false, 100, false, t0 - ContractRules.HOUR);
        Gt.equal("client_unknown", sv.error(), "un pseudo inconnu du serveur est refuse");
        //le registre du selecteur : le joueur connecte est connu, l'inconnu non ; la liste exclut soi-meme et se filtre
        KnownPlayers reg = KnownPlayers.get(s.server);
        reg.seen(cl.getUUID(), cl.getGameProfile().getName(), "Mireille", System.currentTimeMillis());
        Gt.check(reg.byAccount(cl.getGameProfile().getName()) != null, "joueur deja vu connu du registre");
        Gt.check(reg.byAccount("Personne_Inconnue_42") == null, "pseudo inconnu absent du registre");
        List<KnownPlayers.Known> l = KnownPlayers.list(List.of(new KnownPlayers.Known(pv.getUUID(), pv.getGameProfile().getName(), "Anselme", 1),
              new KnownPlayers.Known(cl.getUUID(), cl.getGameProfile().getName(), "Mireille", 2)), Set.of(cl.getUUID()), pv.getUUID(), "gtuc", false);
        Gt.check(l.size() == 1 && l.get(0).id().equals(cl.getUUID()), "liste : sans soi-meme, filtree sur le compte");
        //1.0.5-beta (lot 19) : le nom de pionnier ne filtre plus (il revelerait le lien compte <-> personnage, prive au clan)
        Gt.check(KnownPlayers.list(List.of(new KnownPlayers.Known(cl.getUUID(), cl.getGameProfile().getName(), "Mireille", 2)), Set.of(cl.getUUID()),
              pv.getUUID(), "mire", false).isEmpty(), "liste : pas de recherche par le nom de pionnier");
        Gt.check(KnownPlayers.list(List.of(new KnownPlayers.Known(cl.getUUID(), "x", "Mireille", 2)), Set.of(), pv.getUUID(), "", true).isEmpty(),
              "virement : joueurs connectes seulement");
        s.done();
    }
}
