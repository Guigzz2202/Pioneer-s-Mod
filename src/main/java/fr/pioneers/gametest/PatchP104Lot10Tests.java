package fr.pioneers.gametest;

import com.mojang.authlib.GameProfile;
import fr.pioneers.logistics.caravan.CaravanData;
import fr.pioneers.logistics.caravan.CaravanRules;
import fr.pioneers.logistics.caravan.Caravans;
import fr.pioneers.logistics.caravan.DromedaryEntity;
import fr.pioneers.logistics.caravan.GeneratedCaravan;
import fr.pioneers.logistics.caravan.PacketCaravanRoute;
import fr.pioneers.logistics.caravan.PiCaravan;
import fr.pioneers.logistics.caravan.RelayTile;
import fr.pioneers.logistics.caravan.RelayUnit;
import fr.pioneers.logistics.caravan.RouteSheetItem;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HerdData;
import fr.pioneers.farm.herd.HerdRules;
import fr.pioneers.farm.herd.PiHerd;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 10) : GameTests des CARAVANES : chargement / dechargement au relais selon l'ordre et le filtre ; capacite du bat selon
 * l'espece et la race (chameau > cheval de trait > ane > poney ; chameau et dromadaire dans leurs bornes) ; rien sans proprietaire autorise
 * (reglages du relais, etape de feuille de route, itineraire d'une caravane d'autrui) ; caravane qui s'arrete quand un relais de son
 * itineraire est retire (et ordre des etapes : boucle, aller-retour).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot10Tests {

    private PatchP104Lot10Tests() {
    }

    private static int id(Item it) {
        return BuiltInRegistries.ITEM.getId(it);
    }

    private static int count(CaravanData d, Item it) {
        int n = 0;
        for (int i = 0; i < d.slots; i++) {
            ItemStack s = d.items.getStackInSlot(i);
            n += s.is(it) ? s.getCount() : 0;
        }
        return n;
    }

    private static int count(ChestBlockEntity c, Item it) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).is(it) ? c.getItem(i).getCount() : 0;
        }
        return n;
    }

    private static RelayTile relay(GameTestHelper h, BlockPos p) {
        h.setBlock(p, PiCaravan.RELAY.getBlock().defaultBlockState());
        return (RelayTile) h.getBlockEntity(p);
    }

    private static FakePlayer fake(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    private static Horse packedHorse(GameTestHelper h, BlockPos p) {
        Horse horse = h.spawn(EntityType.HORSE, p);
        horse.setTamed(true);
        Gt.check(Caravans.equip(null, horse, PiCaravan.get(horse), new ItemStack(PiCaravan.PACK_SADDLE.get())), "bat pose sur un cheval apprivoise");
        return horse;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104j_relay", timeoutTicks = 40)
    public static void p104_10_relais_charge_decharge_filtre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rp = new BlockPos(4, 2, 4);
        RelayTile relay = relay(h, rp);
        h.setBlock(rp.east(), Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(rp.east());
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        chest.setItem(1, new ItemStack(Items.COBBLESTONE, 10));
        Gt.check(relay.container() != null, "le relais voit le coffre voisin");
        Horse horse = packedHorse(h, rp.north(2));
        CaravanData d = PiCaravan.get(horse);
        //Charger, filtre = fer : seul le fer monte
        relay.handleSetting(null, RelayUnit.SETTING_ORDER, CaravanRules.LOAD);
        relay.handleSetting(null, RelayUnit.SETTING_FILTER, id(Items.IRON_INGOT));
        for (int k = 0; k < 4; k++) {
            Caravans.serve(relay, List.of(horse));
        }
        Gt.equal(10, count(d, Items.IRON_INGOT), "le fer est charge");
        Gt.equal(0, count(d, Items.COBBLESTONE), "la pierre (hors filtre) reste");
        Gt.equal(10, count(chest, Items.COBBLESTONE), "la pierre est toujours au coffre");
        //Les deux, filtre = pierre : le fer (non retenu) descend, la pierre (retenue) monte
        relay.handleSetting(null, RelayUnit.SETTING_FILTER, id(Items.COBBLESTONE));
        Gt.check(relay.relay().filterIds()[0] == id(Items.COBBLESTONE), "le meme objet ne compte qu'une fois dans le filtre");
        relay.handleSetting(null, RelayUnit.SETTING_ORDER, CaravanRules.BOTH);
        for (int k = 0; k < 4; k++) {
            Caravans.serve(relay, List.of(horse));
        }
        Gt.equal(0, count(d, Items.IRON_INGOT), "les deux : le fer non retenu est decharge");
        Gt.equal(10, count(d, Items.COBBLESTONE), "les deux : la pierre retenue est chargee");
        //Decharger, filtre vide : tout descend
        relay.handleSetting(null, RelayUnit.SETTING_FILTER, 0);
        relay.handleSetting(null, RelayUnit.SETTING_ORDER, CaravanRules.UNLOAD);
        for (int k = 0; k < 4; k++) {
            Caravans.serve(relay, List.of(horse));
        }
        Gt.equal(0, d.used(), "decharger sans filtre : le bat se vide");
        Gt.check(count(chest, Items.IRON_INGOT) == 10 && count(chest, Items.COBBLESTONE) == 10, "tout est revenu au coffre");
        //Passer : rien ne bouge
        relay.handleSetting(null, RelayUnit.SETTING_ORDER, CaravanRules.PASS);
        Gt.check(!Caravans.serve(relay, List.of(horse)), "passer : aucun transfert");
        //Rations : 1 ble = 1 ration, 1 botte de foin = 9 ; le cactus est pour les chameaux
        relay.manger().get(0).setStack(new ItemStack(Items.CACTUS, 1));
        Gt.equal(0, relay.rations(false), "le cactus ne nourrit pas un cheval");
        Gt.equal(2, relay.rations(true), "le cactus nourrit un chameau (2 rations)");
        relay.manger().get(1).setStack(new ItemStack(Items.HAY_BLOCK, 1));
        Gt.check(relay.takeRation(false) && relay.rations(false) == 8, "une botte de foin = 9 rations");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104j_capacity", timeoutTicks = 40)
    public static void p104_10_capacite_selon_espece(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int camel = -1, drom = -1, perch = -1, donkey = -1, pony = -1;
        for (int i = 0; i < GeneratedHerd.HORSES.length; i++) {
            switch (GeneratedHerd.HORSES[i].id()) {
                case "bactrian" -> camel = i;
                case "dromedary" -> drom = i;
                case "percheron" -> perch = i;
                case "donkey" -> donkey = i;
                case "pony" -> pony = i;
                default -> {
                }
            }
        }
        Gt.check(camel >= 0 && drom >= 0 && perch >= 0 && donkey >= 0 && pony >= 0, "races du chameau, du dromadaire, du percheron, de l'ane, du poney");
        int[] idx = {camel, drom, perch, donkey, pony};
        int[] slots = new int[idx.length];
        for (int k = 0; k < idx.length; k++) {
            GeneratedHerd.HorseBreed b = GeneratedHerd.HORSES[idx[k]];
            slots[k] = CaravanRules.slots((b.packMin() + b.packMax()) / 2);
        }
        Gt.check(slots[0] >= slots[1] && slots[1] > slots[2] && slots[2] > slots[3] && slots[3] > slots[4],
              "chameau >= dromadaire > percheron > ane > poney : " + java.util.Arrays.toString(slots));
        Gt.check(slots[4] >= GeneratedCaravan.MIN_SLOTS && slots[0] <= GeneratedCaravan.MAX_SLOTS, "cases bornees");
        //Chameau vanilla (Bactriane) et dromadaire : race et charge de bat a l'arrivee
        Camel c = h.spawn(EntityType.CAMEL, new BlockPos(2, 2, 2));
        DromedaryEntity dr = h.spawn(PiCaravan.DROMEDARY.get(), new BlockPos(6, 2, 6));
        HerdData hc = PiHerd.get(c), hd = PiHerd.get(dr);
        Gt.check(hc != null && hc.init && hc.horse().kind() == 3, "le chameau est un chameau de Bactriane (kind 3)");
        Gt.check(hd != null && hd.init && hd.horse().kind() == 4, "le dromadaire a sa race (kind 4)");
        Gt.check(PiHerd.packKg(c) >= GeneratedHerd.HORSES[camel].packMin() && PiHerd.packKg(dr) >= GeneratedHerd.HORSES[drom].packMin(),
              "charge de bat dans les bornes");
        CaravanData dc = PiCaravan.get(c);
        Gt.check(Caravans.equip(null, c, dc, new ItemStack(PiCaravan.PACK_SADDLE.get())), "bat pose sur le chameau");
        Gt.equal(CaravanRules.slots(PiHerd.packKg(c)), dc.slots, "cases du bat = charge de bat du chameau");
        Gt.equal(3, PiCaravan.style(dr), "dromadaire : caisses etroites");
        Gt.equal(2, PiCaravan.style(c), "chameau : caisses");
        //Un poulain ne porte pas de bat ; un bat trop charge pour la bete est refuse
        Horse foal = h.spawn(EntityType.HORSE, new BlockPos(4, 2, 2));
        foal.setTamed(true);
        foal.setBaby(true);
        Gt.check(!Caravans.canWear(foal), "un poulain ne porte pas de bat");
        Horse small = h.spawn(EntityType.HORSE, new BlockPos(2, 2, 6));
        small.setTamed(true);
        HerdRules.rollHorse(PiHerd.get(small), pony, new Random(3));
        ItemStack heavy = Caravans.unequip(null, c, dc);
        Gt.check(PiCaravan.get(c).pack == false, "cisailles : le bat est retire");
        Gt.check(fr.pioneers.logistics.caravan.PackSaddleItem.stacks(heavy) == 0, "bat vide retire vide");
        var fill = fr.pioneers.logistics.caravan.PackSaddleItem.contents(heavy);
        for (int i = 0; i < 20; i++) {
            fill.setStackInSlot(i, new ItemStack(Items.DIRT));
        }
        fr.pioneers.logistics.caravan.PackSaddleItem.store(heavy, fill);
        Gt.check(!Caravans.equip(null, small, PiCaravan.get(small), heavy), "20 piles : trop pour un poney");
        Gt.check(Caravans.equip(null, c, PiCaravan.get(c), heavy) && count(PiCaravan.get(c), Items.DIRT) == 20 * 1, "le contenu revient avec le bat");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104j_owner", timeoutTicks = 40)
    public static void p104_10_rien_sans_proprietaire(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        FakePlayer owner = fake(h, "proprietaire_l10"), other = fake(h, "intrus_l10");
        RelayTile a = relay(h, new BlockPos(2, 2, 2)), b = relay(h, new BlockPos(6, 2, 6));
        a.placedBy(owner);
        b.placedBy(owner);
        //Reglages : refuses a l'intrus, acceptes du proprietaire
        a.handleSetting(other, RelayUnit.SETTING_ORDER, CaravanRules.UNLOAD);
        Gt.equal(CaravanRules.LOAD, a.relay().order, "l'intrus ne regle pas le relais");
        a.handleCustom(other, RelayTile.WAIT_UP);
        Gt.equal(GeneratedCaravan.WAIT_DEFAULT, a.relay().wait, "l'intrus ne change pas l'attente");
        a.handleSetting(owner, RelayUnit.SETTING_ORDER, CaravanRules.UNLOAD);
        Gt.equal(CaravanRules.UNLOAD, a.relay().order, "le proprietaire regle le relais");
        //Feuille de route : l'intrus ne peut pas y mettre le relais d'autrui
        ItemStack sheet = new ItemStack(PiCaravan.ROUTE_SHEET.get());
        Gt.equal(RouteSheetItem.NOT_YOURS, RouteSheetItem.addStop(other, sheet, a), "relais d'autrui refuse");
        Gt.equal(RouteSheetItem.ADDED, RouteSheetItem.addStop(owner, sheet, a), "le proprietaire ajoute son relais");
        Gt.equal(RouteSheetItem.ALREADY, RouteSheetItem.addStop(owner, sheet, a), "pas deux fois de suite le meme relais");
        Gt.equal(RouteSheetItem.ADDED, RouteSheetItem.addStop(owner, sheet, b), "deuxieme etape");
        //Caravane du proprietaire : l'intrus ne peut ni la rediriger ni l'arreter
        Horse horse = packedHorse(h, new BlockPos(4, 2, 4));
        CaravanData d = PiCaravan.get(horse);
        Gt.check(Caravans.assign(owner, horse, d, sheet), "le proprietaire lance la caravane");
        Gt.check(d.role == CaravanData.HEAD && d.state == CaravanData.TRAVEL && owner.getUUID().equals(d.owner), "caravane en route a son proprietaire");
        Gt.check(!Caravans.assign(other, horse, d, sheet) && owner.getUUID().equals(d.owner), "l'intrus ne prend pas la caravane");
        Caravans.stop(other, horse, d);
        Gt.equal(CaravanData.TRAVEL, d.state, "l'intrus n'arrete pas la caravane");
        Gt.check(!Caravans.mayEdit(other, owner.getUUID()) && Caravans.mayEdit(owner, owner.getUUID()) && Caravans.mayEdit(other, null),
              "droits : proprietaire oui, intrus non, sans proprietaire oui");
        Caravans.stop(owner, horse, d);
        Gt.check(d.state == CaravanData.IDLE && d.role == CaravanData.NONE, "le proprietaire arrete sa caravane");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104j_gone", timeoutTicks = 40)
    public static void p104_10_caravane_arretee_relais_retire(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        FakePlayer owner = fake(h, "caravanier_l10");
        BlockPos pa = new BlockPos(1, 2, 1), pb = new BlockPos(7, 2, 7);
        relay(h, pa).placedBy(owner);
        relay(h, pb).placedBy(owner);
        ItemStack sheet = new ItemStack(PiCaravan.ROUTE_SHEET.get());
        RouteSheetItem.addStop(owner, sheet, (RelayTile) h.getBlockEntity(pa));
        RouteSheetItem.addStop(owner, sheet, (RelayTile) h.getBlockEntity(pb));
        Horse horse = packedHorse(h, new BlockPos(1, 2, 4));
        CaravanData d = PiCaravan.get(horse);
        Gt.check(Caravans.assign(owner, horse, d, sheet), "caravane lancee");
        d.next = 1;
        d.state = CaravanData.TRAVEL;
        Caravans.tickHead(h.getLevel(), horse, d);
        Gt.equal(CaravanData.TRAVEL, d.state, "relais present : la caravane roule");
        h.setBlock(pb, Blocks.AIR.defaultBlockState());
        Caravans.tickHead(h.getLevel(), horse, d);
        Gt.equal(CaravanData.STOPPED, d.state, "relais retire : la caravane s'arrete");
        Gt.equal(CaravanData.REASON_RELAY_GONE, d.reason, "raison : relais disparu");
        Gt.check(!fr.pioneers.logistics.caravan.CaravanTickets.holds(horse.getUUID()), "plus de ticket de chunks a l'arret");
        //Ordre des etapes : boucle et aller-retour ; feuille : monter, retirer, mode
        Gt.check(CaravanRules.next(3, 2, 1, true)[0] == 0, "boucle : apres la derniere, la premiere");
        int[] n = CaravanRules.next(3, 2, 1, false);
        Gt.check(n[0] == 1 && n[1] == -1, "aller-retour : demi-tour a la derniere");
        n = CaravanRules.next(3, 0, -1, false);
        Gt.check(n[0] == 1 && n[1] == 1, "aller-retour : demi-tour a la premiere");
        PacketCaravanRoute.apply(sheet, PacketCaravanRoute.UP, 1);
        Gt.check(RouteSheetItem.Route.of(sheet).stops().get(0).equals(h.absolutePos(pb)), "monter une etape");
        PacketCaravanRoute.apply(sheet, PacketCaravanRoute.MODE, 0);
        Gt.check(!RouteSheetItem.Route.of(sheet).loop(), "boucle -> aller-retour");
        PacketCaravanRoute.apply(sheet, PacketCaravanRoute.REMOVE, 0);
        Gt.equal(1, RouteSheetItem.Route.of(sheet).stops().size(), "retirer une etape");
        Gt.check(!CaravanRules.legOk(0, 0, GeneratedCaravan.MAX_LEG + 1, 0) && CaravanRules.legOk(0, 0, GeneratedCaravan.MAX_LEG, 0), "etape bornee");
        s.done();
    }
}
