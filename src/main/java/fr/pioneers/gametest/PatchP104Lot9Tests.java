package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.farm.device.DeviceBlock;
import fr.pioneers.farm.device.HutchTile;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HerdData;
import fr.pioneers.farm.herd.HerdRules;
import fr.pioneers.farm.herd.PiHerd;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.4-beta, lot 9 (GameTests) : haut fourneau primitif a TROIS cases (chaque case n'accepte que son role, au joueur comme aux
 * entonnoirs ; une charge complete coule de l'acier comme avant ; migration d'une sauvegarde a une case de charge) ; laisse (cordes + anneau
 * de fer a l'outilleur) ; lapins (race et stats a l'arrivee, belier sans fuite, heredite) ; clapier (entree, nourriture, portees, produits,
 * abattage, sauvegarde, faces) ; chevaux (race et stats dans leurs bornes, ane, poulain).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot9Tests {

    private PatchP104Lot9Tests() {
    }

    private static ItemStack item(String id, int n) {
        return new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)), n);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104i_blast_slots", timeoutTicks = 120)
    public static void p104_9_haut_fourneau_trois_cases(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Direction forward = Direction.SOUTH;
        BlockPos anchor = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, forward);
        BlockPos c = anchor.relative(forward);
        BlockEntity be = h.getBlockEntity(c);
        Gt.check(be instanceof BlastFurnaceTile, "pas de haut fourneau en " + c);
        BlastFurnaceTile tile = (BlastFurnaceTile) be;
        ItemStack iron = new ItemStack(PiVanillaMaterials.item("raw_iron")), coal = new ItemStack(PiVanillaMaterials.CHARCOAL.get()),
              lime = new ItemStack(PiBronze.QUICKLIME.get());
        ItemStack[] byRole = {iron, coal, lime};
        for (int r = 0; r < 3; r++) {
            Gt.equal(r, BlastFurnaceTile.role(byRole[r]), "role de " + byRole[r]);
            for (int other = 0; other < 3; other++) {
                boolean ok = tile.input(other).canInsert(byRole[r], Automation.EXTERNAL) && tile.input(other).canInsert(byRole[r], Automation.MANUAL);
                Gt.check(ok == (r == other), "case " + BlastFurnaceTile.ROLE_KEYS[other] + " et " + byRole[r] + " : " + ok);
            }
        }
        Gt.check(!tile.input(0).canInsert(new ItemStack(Items.DIRT), Automation.MANUAL), "la terre n'entre nulle part");
        //Entonnoir : la capacite exposee range chaque objet dans SA case, quelle que soit la case visee
        IItemHandler cap = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElse(null);
        Gt.check(cap != null, "capacite d'objets du haut fourneau");
        for (int r = 0; r < 3; r++) {
            int landed = -1;
            for (int slot = 0; slot < cap.getSlots(); slot++) {
                ItemStack rest = cap.insertItem(slot, byRole[r].copy(), true);
                if (rest.isEmpty()) {
                    Gt.check(landed < 0, "un seul emplacement accepte " + byRole[r]);
                    landed = slot;
                }
            }
            Gt.equal(r, landed, "entonnoir : " + byRole[r] + " dans la case de son role");
        }
        Gt.check(tile.steelSlot().insert(new ItemStack(Items.IRON_INGOT), Action.SIMULATE, Automation.EXTERNAL).getCount() == 1,
              "la sortie d'acier ne prend rien de l'exterieur");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104i_blast_steel", timeoutTicks = 1100)
    public static void p104_9_haut_fourneau_coule_l_acier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Direction forward = Direction.SOUTH;
        BlockPos anchor = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, forward);
        BlockPos c = anchor.relative(forward);
        h.runAfterDelay(60, () -> {
            BlastFurnaceTile tile = (BlastFurnaceTile) h.getBlockEntity(c);
            Gt.check(tile.formed(), "haut fourneau forme");
            tile.input(0).setStack(new ItemStack(PiVanillaMaterials.item("raw_iron"), 8));
            tile.input(1).setStack(new ItemStack(PiVanillaMaterials.CHARCOAL.get(), 8));
            tile.input(2).setStack(new ItemStack(PiBronze.QUICKLIME.get(), 4));
            h.onEachTick(() -> tile.testTemperature(1650));
            h.runAfterDelay(20, () -> {
                int[] res = tile.reserves();
                Gt.check(res[0] == 32 && res[1] == 8 && res[2] == 4, "charge absorbee dans les reserves : " + java.util.Arrays.toString(res));
                Gt.check(tile.input(0).isEmpty() && tile.input(1).isEmpty() && tile.input(2).isEmpty(), "les trois cases se vident dans la cuve");
            });
            h.succeedWhen(() -> {
                Gt.check(tile.steelSlot().getCount() >= 1, "pas encore d'acier");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104i_blast_migration", timeoutTicks = 100)
    public static void p104_9_haut_fourneau_migration(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos anchor = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, Direction.SOUTH);
        BlastFurnaceTile tile = (BlastFurnaceTile) h.getBlockEntity(anchor.relative(Direction.SOUTH));
        //Sauvegarde d'avant le lot 9 : une case de charge (0), l'acier (1), le laitier (2), pas de slots_v
        CompoundTag tag = tile.saveWithoutMetadata();
        tag.remove("slots_v");
        ListTag old = new ListTag();
        int i = 0;
        for (ItemStack st : new ItemStack[]{new ItemStack(PiVanillaMaterials.CHARCOAL.get(), 5), item("pioneers:steel_ingot", 3), new ItemStack(PiBronze.SLAG.get())}) {
            CompoundTag e = st.save(new CompoundTag());
            e.putShort("Slot", (short) i++);
            old.add(e);
        }
        tag.put("slots", old);
        tile.load(tag);
        Gt.check(tile.input(0).isEmpty() && tile.input(2).isEmpty(), "rien dans les cases fer et fondant");
        Gt.equal(5, tile.input(1).getCount(), "charbon de l'ancienne case de charge -> case carbone");
        Gt.equal(3, tile.steelSlot().getCount(), "acier garde");
        Gt.equal(1, tile.slagSlot().getCount(), "laitier garde");
        //Reecrite au nouveau format : relue a l'identique
        CompoundTag again = tile.saveWithoutMetadata();
        Gt.equal(2, again.getInt("slots_v"), "version des cases");
        tile.load(again.copy());
        Gt.equal(5, tile.input(1).getCount(), "aller-retour au nouveau format");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104i_lead", timeoutTicks = 20)
    public static void p104_9_laisse_cordes_et_anneau(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(Pioneers.rl("vanilla/lead")).orElse(null);
        Gt.check(r != null, "recette de la laisse");
        Gt.check(r.getResultItem(h.getLevel().registryAccess()).is(Items.LEAD), "la recette donne la laisse");
        boolean rope = false, ring = false, slime = false;
        for (var ing : r.getIngredients()) {
            rope |= ing.test(item("pioneers:fiber_rope", 1));
            ring |= ing.test(item("pioneers:iron_ring", 1));
            slime |= ing.test(new ItemStack(Items.SLIME_BALL));
        }
        Gt.check(rope && ring && !slime, "laisse = cordes du mod + anneau de fer, sans boule de slime");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104i_rabbits", timeoutTicks = 40)
    public static void p104_9_lapins_races(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Rabbit r = h.spawn(EntityType.RABBIT, new BlockPos(4, 2, 4));
        HerdData d = PiHerd.get(r);
        Gt.check(d != null && d.init && d.kind == HerdData.RABBIT, "un lapin recoit ses donnees a l'arrivee");
        Gt.check(d.breed >= 0 && d.breed < GeneratedHerd.RABBITS.length, "race du lapin : " + d.breed);
        GeneratedHerd.RabbitBreed b = d.rabbit();
        Gt.check(Math.abs(d.meat / b.meat() - 1) <= 0.101 && Math.abs(d.litter / b.litter() - 1) <= 0.101, "stats a 10 % de la race");
        //Belier : calme, plus aucun but de fuite
        int lop = -1;
        for (int i = 0; i < GeneratedHerd.RABBITS.length; i++) {
            lop = GeneratedHerd.RABBITS[i].calm() ? i : lop;
        }
        Rabbit calm = EntityType.RABBIT.create(h.getLevel());
        calm.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5, 2, 5)));
        HerdRules.rollRabbit(PiHerd.get(calm), lop, new Random(1));
        h.getLevel().addFreshEntity(calm);
        Gt.check(calm.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof AvoidEntityGoal<?>), "le belier ne fuit pas");
        Gt.check(r.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof AvoidEntityGoal<?>) || d.rabbit().calm(),
              "un lapin ordinaire fuit toujours");
        //Heredite : race d'un parent, stats bornees
        Random rnd = new Random(7);
        HerdData a = new HerdData(), c = new HerdData();
        HerdRules.rollRabbit(a, 0, rnd);
        HerdRules.rollRabbit(c, 2, rnd);
        for (int k = 0; k < 200; k++) {
            HerdData kid = HerdRules.rabbitChild(a, c, rnd);
            Gt.check(kid.breed == 0 || kid.breed == 2, "race d'un des parents");
            GeneratedHerd.RabbitBreed kb = GeneratedHerd.RABBITS[kid.breed];
            Gt.check(kid.meat >= kb.meat() * 0.75F * 0.9F - 1e-4 && kid.meat <= kb.meat() * 1.25F * 1.1F + 1e-4, "viande bornee");
            Gt.check((kb.wool() > 0) == (kid.wool > 0), "laine seulement pour l'angora");
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104i_hutch", timeoutTicks = 40)
    public static void p104_9_clapier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(4, 1, 4);
        h.setBlock(at, PiFarmDevices.DEVICES.get(DeviceBlock.Kind.RABBIT_HUTCH).get().defaultBlockState());
        HutchTile hutch = (HutchTile) h.getBlockEntity(at);
        Random rnd = new Random(3);
        HerdData m = new HerdData(), f = new HerdData();
        HerdRules.rollRabbit(m, 4, rnd);
        HerdRules.rollRabbit(f, 4, rnd);
        Gt.check(hutch.admit(m, true, 0) && hutch.admit(f, true, 0), "deux adultes entrent");
        //Faces : nourriture par le dessus seulement, produits par le dessous seulement
        IItemHandler top = hutch.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
        IItemHandler bottom = hutch.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).resolve().orElseThrow();
        Gt.check(top.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "la terre n'est pas une nourriture");
        Gt.check(top.insertItem(0, new ItemStack(Items.HAY_BLOCK, 4), false).isEmpty(), "le foin entre par le dessus");
        Gt.check(bottom.insertItem(0, new ItemStack(Items.CARROT), true).getCount() == 1, "rien n'entre par le dessous");
        //Une portee (temps simule) : nourris, deux adultes ; lapereaux du Neo-zelandais
        int before = hutch.rabbits().size();
        for (int t = 0; t < GeneratedHerd.HUTCH_BREED_SECONDS * 2 && hutch.rabbits().size() == before; t++) {
            hutch.second(rnd);
        }
        Gt.check(hutch.rabbits().size() > before, "une portee au clapier");
        Gt.check(hutch.rabbits().size() <= GeneratedHerd.HUTCH_CAPACITY, "jamais plus que la capacite");
        Gt.check(hutch.rabbits().stream().filter(r -> !r.adult).allMatch(r -> r.data.breed == 4), "les lapereaux ont la race des parents");
        //Produits : fumier et peaux tombent dans les cases du dessous
        for (int t = 0; t < GeneratedHerd.HUTCH_PELT_SECONDS * 2; t++) {
            hutch.second(rnd);
        }
        boolean hide = false, manure = false;
        for (int i = HutchTile.OUT_FROM; i < HutchTile.SLOTS; i++) {
            hide |= hutch.items().get(i).is(Items.RABBIT_HIDE);
            manure |= hutch.items().get(i).is(item("pioneers:compost", 1).getItem());
        }
        Gt.check(hide && manure, "peaux et fumier produits");
        Gt.check(!bottom.extractItem(HutchTile.OUT_FROM, 1, true).isEmpty() || !bottom.extractItem(HutchTile.OUT_FROM + 1, 1, true).isEmpty(),
              "les produits sortent par le dessous");
        //Abattage : jamais sous deux adultes
        while (hutch.adults() > 2) {
            hutch.slaughter(null, ItemStack.EMPTY);
        }
        Gt.check(!hutch.slaughter(null, ItemStack.EMPTY), "on garde deux adultes");
        //Sauvegarde : aller-retour
        CompoundTag tag = hutch.saveWithoutMetadata();
        int n = hutch.rabbits().size(), food = hutch.food();
        hutch.load(tag.copy());
        Gt.equal(n, hutch.rabbits().size(), "lapins relus");
        Gt.equal(food, hutch.food(), "nourriture relue");
        Gt.equal(tag, hutch.saveWithoutMetadata(), "sauvegarde a l'identique");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104i_horses", timeoutTicks = 40)
    public static void p104_9_chevaux_races(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Horse horse = h.spawn(EntityType.HORSE, new BlockPos(4, 2, 4));
        HerdData d = PiHerd.get(horse);
        Gt.check(d != null && d.init && d.kind == HerdData.HORSE, "un cheval recoit sa race a l'arrivee");
        GeneratedHerd.HorseBreed b = d.horse();
        Gt.equal(0, b.kind(), "un cheval a une race de cheval");
        double speed = horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), jump = horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH);
        Gt.check(speed >= b.speedMin() - 1e-4 && speed <= b.speedMax() + 1e-4, "vitesse dans les bornes de la race : " + speed);
        Gt.check(jump >= b.jumpMin() - 1e-4 && jump <= b.jumpMax() + 1e-4, "saut dans les bornes de la race : " + jump);
        Gt.check(horse.getMaxHealth() >= b.healthMin() - 0.5 && horse.getMaxHealth() <= b.healthMax() + 0.5, "sante dans les bornes");
        Gt.check(d.draft >= b.draftMin() && d.draft <= b.draftMax() && d.pack >= b.packMin() && d.pack <= b.packMax(), "trait et bat de la race");
        Gt.check(PiHerd.draftKg(horse) == d.draft && PiHerd.packKg(horse) == d.pack, "trait et bat lisibles (caravanes)");
        Donkey donkey = h.spawn(EntityType.DONKEY, new BlockPos(2, 2, 2));
        Gt.equal(1, PiHerd.get(donkey).horse().kind(), "l'ane a la race de l'ane");
        //Poulain : race d'un parent, trait et bat dans les bornes (elargies de 10 %)
        Random rnd = new Random(11);
        HerdData p1 = new HerdData(), p2 = new HerdData();
        HerdRules.rollHorse(p1, 0, rnd);
        HerdRules.rollHorse(p2, 2, rnd);
        for (int k = 0; k < 200; k++) {
            int breed = HerdRules.childBreed(p1.breed, p2.breed, rnd);
            HerdData foal = HerdRules.horseChild(p1, p2, breed, rnd);
            GeneratedHerd.HorseBreed fb = GeneratedHerd.HORSES[breed];
            Gt.check(breed == 0 || breed == 2, "race d'un parent");
            Gt.check(foal.draft >= fb.draftMin() * 0.9F - 1 && foal.draft <= fb.draftMax() * 1.1F + 1, "trait du poulain borne");
        }
        s.done();
    }
}
