package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.machine.resource.ResourceStore;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.equipment.gear.GearEvents;
import fr.pioneers.equipment.gear.GearUpgradeRecipe;
import fr.pioneers.equipment.gear.Modules;
import fr.pioneers.equipment.gear.electric.ElectricCharge;
import fr.pioneers.equipment.gear.electric.ElectricEvents;
import fr.pioneers.equipment.gear.electric.FlamethrowerItem;
import fr.pioneers.equipment.gear.electric.HydrogenJetpackItem;
import fr.pioneers.equipment.gear.electric.VoltaicArmorItem;
import fr.pioneers.equipment.gear.electric.VoltaicBatonItem;
import fr.pioneers.equipment.gear.electric.VoltaicToolItem;
import fr.pioneers.core.registries.PiBasicItems;
import fr.pioneers.equipment.gear.PiElectricGear;
import fr.pioneers.equipment.gear.PiGear;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import fr.pioneers.logistics.transport.buffer.BufferType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 9 (GameTests) : equipement electrique. Charge et decharge (capacite d'energie, recharge seulement, debit, buffer
 * d'energie, Graviton qui garde l'energie de la piece Voltaique) ; armure et outils a sec (protection de l'acier, plus d'effet, outil au
 * poing, jamais casse) ; consommation du jetpack et arret quand le reservoir est vide ; lance-flammes qui consomme, enflamme et n'allume les
 * blocs qu'avec mobGriefing ; remplissage d'hydrogene (capacite de gaz, capsule, buffer de gaz).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot9Tests {

    private PatchP103Lot9Tests() {
    }

    private static ItemStack charged(ItemStack stack, long fe) {
        ElectricCharge.setEnergy(stack, fe);
        return stack;
    }

    private static double modifier(ItemStack stack, EquipmentSlot slot, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        double sum = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(slot).get(attribute)) {
            sum += m.getAmount();
        }
        return sum;
    }

    // ================================================================== charge et decharge
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_9_charge", timeoutTicks = 200)
    public static void p103_9_charge_decharge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack chestStack = new ItemStack(PiElectricGear.CHESTPLATE.get());
        IEnergyStorage cap = chestStack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        Gt.check(cap != null && cap.canReceive() && !cap.canExtract(), "capacite d'energie : recharge seulement");
        Gt.equal(VoltaicArmorItem.CAPACITY, cap.getMaxEnergyStored(), "reserve d'une piece");
        Gt.equal(VoltaicArmorItem.CAPACITY / 100, cap.receiveEnergy(1_000_000, false), "debit de recharge (1 % par tick)");
        Gt.equal(VoltaicArmorItem.CAPACITY / 100, ElectricCharge.energy(chestStack), "energie entree dans le NBT");
        Gt.equal(0, cap.extractEnergy(1000, false), "on ne vide pas une piece");
        ElectricCharge.setEnergy(chestStack, 10 * VoltaicArmorItem.CAPACITY);
        Gt.equal(VoltaicArmorItem.CAPACITY, ElectricCharge.energy(chestStack), "jamais au-dela de la reserve");
        Gt.check(ElectricCharge.use(chestStack, 1000) && !ElectricCharge.use(new ItemStack(PiElectricGear.DRILL.get()), 1), "use : tout ou rien");
        //Graviton fait du plastron Voltaique : l'energie passe (gear_upgrade)
        Recipe<?> r = s.level.getRecipeManager().byKey(Pioneers.rl("gear/graviton_chestplate")).orElse(null);
        Gt.check(r instanceof GearUpgradeRecipe, "recette Graviton : pioneers:gear_upgrade");
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
        ItemStack plating = new ItemStack(PiGear.PARTS.get("graviton_plating").get()), cell = new ItemStack(PiGear.PARTS.get("graviton_cell").get());
        String[] pattern = {"PCP", "PXP", "PPP"};
        ItemStack base = charged(new ItemStack(PiElectricGear.CHESTPLATE.get()), 123_456);
        for (int i = 0; i < 9; i++) {
            char c = pattern[i / 3].charAt(i % 3);
            grid.setItem(i, c == 'P' ? plating.copy() : c == 'C' ? cell.copy() : base.copy());
        }
        GearUpgradeRecipe up = (GearUpgradeRecipe) r;
        Gt.check(up.matches(grid, s.level), "la recette Graviton accepte le plastron Voltaique");
        ItemStack out = up.assemble(grid, s.level.registryAccess());
        Gt.equal(PiGear.GEAR.get("graviton_chestplate").get(), out.getItem(), "resultat");
        Gt.equal(123_456, Modules.energy(out), "le plastron Graviton garde l'energie du Voltaique");
        //Buffer d'energie MKI : la case du haut recharge l'objet
        BlockPos rel = new BlockPos(4, 1, 4);
        h.setBlock(rel, PiTransport.BUFFERS.get(BufferType.ENERGY).get(CableTier.BASIC).getBlock());
        BufferTile buffer = (BufferTile) h.getBlockEntity(rel);
        ((ResourceStore.Energy) buffer.unit().store).buffer.setStored(1_000_000);
        ItemStack drill = new ItemStack(PiElectricGear.DRILL.get());
        buffer.getIoSlots().get(0).setStack(drill);
        h.runAtTickTime(40, () -> {
            ItemStack in = buffer.getIoSlots().get(0).getStack(), outSlot = buffer.getIoSlots().get(1).getStack();
            ItemStack d = in.isEmpty() ? outSlot : in;
            Gt.check(d.getItem() == PiElectricGear.DRILL.get() && ElectricCharge.energy(d) > 0, "le buffer d'energie a recharge la perceuse : "
                  + ElectricCharge.energy(d));
            Gt.check(((ResourceStore.Energy) buffer.unit().store).buffer.getStored() < 1_000_000, "l'energie vient du buffer");
            s.done();
        });
    }

    // ================================================================== armure et outils sans energie
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_9_flat", timeoutTicks = 40)
    public static void p103_9_armure_et_outils_sans_energie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack chest = new ItemStack(PiElectricGear.CHESTPLATE.get()), legs = new ItemStack(PiElectricGear.LEGGINGS.get());
        Gt.equal(VoltaicArmorItem.UNPOWERED[1], (long) modifier(chest, EquipmentSlot.CHEST, Attributes.ARMOR), "plastron a sec : 7");
        Gt.check(Math.abs(modifier(chest, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) - 1.0) < 1e-6, "robustesse a sec : 1");
        Gt.check(modifier(legs, EquipmentSlot.LEGS, Attributes.MOVEMENT_SPEED) == 0, "jambieres a sec : pas de vitesse");
        charged(chest, 1000);
        charged(legs, 1000);
        Gt.equal(VoltaicArmorItem.POWERED[1], (long) modifier(chest, EquipmentSlot.CHEST, Attributes.ARMOR), "plastron sous tension : 8");
        Gt.check(Math.abs(modifier(chest, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) - 2.5) < 1e-6, "robustesse sous tension : 2,5");
        Gt.check(Math.abs(modifier(legs, EquipmentSlot.LEGS, Attributes.MOVEMENT_SPEED) - 0.10) < 1e-6, "jambieres : +10 %");
        int powered = 0, flat = 0;
        for (int p = 0; p < 4; p++) {
            powered += VoltaicArmorItem.POWERED[p];
            flat += VoltaicArmorItem.UNPOWERED[p];
        }
        Gt.equal(21, powered, "armure complete sous tension");
        Gt.equal(18, flat, "armure complete a sec (l'acier)");
        Gt.check(!chest.isDamageableItem(), "jamais cassee");
        //Bottes : moitie de la chute evitee (100 FE par point), rien a sec
        ItemStack boots = charged(new ItemStack(PiElectricGear.BOOTS.get()), 10_000);
        float f = ElectricEvents.fallFactor(boots, 13, 1.0F);
        Gt.check(Math.abs(f - 0.5F) < 1e-4, "chute de 13 blocs : moitie evitee (" + f + ")");
        Gt.equal(10_000 - 5 * ElectricEvents.FALL_FE, ElectricCharge.energy(boots), "5 points evites = 500 FE");
        //Outils : vitesse 10 sous tension, poing a sec ; matraque 7 / 3
        ItemStack drill = new ItemStack(PiElectricGear.DRILL.get());
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        Gt.check(drill.getDestroySpeed(stone) == 1.0F, "perceuse a sec : poing");
        charged(drill, 1000);
        Gt.check(drill.getDestroySpeed(stone) == VoltaicToolItem.SPEED && drill.getDestroySpeed(dirt) == VoltaicToolItem.SPEED, "perceuse = pioche + pelle");
        Gt.check(drill.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()) && drill.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()),
              "niveau du diamant (minerai de diamant, obsidienne)");
        Gt.check(!drill.isCorrectToolForDrops(Blocks.OAK_LOG.defaultBlockState()), "la perceuse n'est pas une hache");
        Gt.check(!drill.isDamageableItem(), "outil jamais casse");
        ItemStack baton = new ItemStack(PiElectricGear.BATON.get());
        Gt.check(VoltaicBatonItem.damage(baton) == VoltaicBatonItem.DAMAGE_FLAT, "matraque a sec : 3");
        charged(baton, 1000);
        Gt.check(VoltaicBatonItem.damage(baton) == VoltaicBatonItem.DAMAGE, "matraque sous tension : 7");
        s.done();
    }

    // ================================================================== jetpack : consommation et arret a vide
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_9_jetpack", timeoutTicks = 40)
    public static void p103_9_jetpack_consomme_et_s_arrete(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        UUID id = UUID.randomUUID();
        ServerPlayer p = Gt.connect(s.server, s.level, id, Gt.account("jet9"));
        try {
            BlockPos abs = h.absolutePos(new BlockPos(4, 4, 4));
            p.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            ItemStack jet = new ItemStack(PiElectricGear.JETPACK.get());
            p.setItemSlot(EquipmentSlot.CHEST, jet);
            GearEvents.setThrusting(p, true, false);
            Gt.check(!GearEvents.thrusting(p), "jetpack vide : la poussee est refusee");
            ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST);
            ElectricCharge.setHydrogen(worn, 100);
            ElectricCharge.setEnergy(worn, 10_000);
            GearEvents.setThrusting(p, true, false);
            Gt.check(GearEvents.thrusting(p) && GearEvents.allowsAir(p), "poussee acceptee, joueur tolere en l'air");
            TickEvent.PlayerTickEvent tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p);
            GearEvents.playerTick(tick);
            Gt.equal(100 - HydrogenJetpackItem.THRUST_H2, ElectricCharge.hydrogen(worn), "un tick de poussee : 8 mB");
            Gt.equal(10_000 - HydrogenJetpackItem.THRUST_FE, ElectricCharge.energy(worn), "un tick de poussee : 25 FE");
            for (int i = 0; i < 20; i++) {
                GearEvents.playerTick(tick);
            }
            Gt.check(!GearEvents.thrusting(p), "reservoir vide : la poussee s'arrete");
            Gt.check(ElectricCharge.hydrogen(worn) < HydrogenJetpackItem.THRUST_H2, "il reste moins d'un tick d'hydrogene : " + ElectricCharge.hydrogen(worn));
            //Vol stationnaire : moitie moins
            ElectricCharge.setHydrogen(worn, 1000);
            long fe = ElectricCharge.energy(worn);
            GearEvents.setThrusting(p, true, true);
            GearEvents.playerTick(tick);
            Gt.equal(1000 - HydrogenJetpackItem.HOVER_H2, ElectricCharge.hydrogen(worn), "stationnaire : 4 mB par tick");
            Gt.equal(fe - HydrogenJetpackItem.HOVER_FE, ElectricCharge.energy(worn), "stationnaire : 12 FE par tick");
            GearEvents.setThrusting(p, false);
            Gt.check(!GearEvents.thrusting(p), "arret");
        } finally {
            GearEvents.setThrusting(p, false);
            Gt.disconnect(s.server, id);
        }
        s.done();
    }

    // ================================================================== lance-flammes
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_9_flame", timeoutTicks = 40)
    public static void p103_9_lance_flammes_consomme_et_enflamme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        UUID id = UUID.randomUUID();
        ServerPlayer p = Gt.connect(s.server, s.level, id, Gt.account("fire9"));
        GameRules.BooleanValue griefing = s.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean before = griefing.get();
        try {
            BlockPos abs = h.absolutePos(new BlockPos(1, 2, 4));
            p.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            ItemStack fl = new ItemStack(PiElectricGear.FLAMETHROWER.get());
            ElectricCharge.setHydrogen(fl, 1000);
            ElectricCharge.setEnergy(fl, 10_000);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, fl);
            ItemStack held = p.getMainHandItem();
            //Un tick de jet : 10 mB + 40 FE
            held.getItem().onUseTick(s.level, p, held, held.getUseDuration() - 1);
            Gt.equal(1000 - FlamethrowerItem.PER_TICK_H2, ElectricCharge.hydrogen(held), "jet : 10 mB par tick");
            Gt.equal(10_000 - FlamethrowerItem.PER_TICK_FE, ElectricCharge.energy(held), "jet : 40 FE par tick");
            //Un cochon a 3 blocs devant prend feu ; un autre hors du cone non
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
            Pig side = h.spawn(EntityType.PIG, new BlockPos(1, 2, 8));
            pig.setNoAi(true);
            side.setNoAi(true);
            Vec3 eye = p.getEyePosition();
            Vec3 dir = pig.getBoundingBox().getCenter().subtract(eye).normalize();
            int hit = FlamethrowerItem.burnEntities(s.level, p, eye, dir);
            Gt.check(hit == 1 && pig.isOnFire() && pig.getHealth() < pig.getMaxHealth(), "cochon du cone : en feu et blesse");
            Gt.check(!side.isOnFire(), "cochon hors du cone : epargne");
            //Les blocs : seulement avec mobGriefing, seulement un bloc inflammable
            BlockPos planks = new BlockPos(4, 1, 4), stone = new BlockPos(1, 1, 6);
            h.setBlock(planks, Blocks.OAK_PLANKS);
            h.setBlock(stone, Blocks.STONE);
            Vec3 target = Vec3.atCenterOf(h.absolutePos(planks)).add(0, 0.5, 0);
            Vec3 down = target.subtract(eye).normalize();
            pig.discard();
            griefing.set(false, s.server);
            Gt.check(FlamethrowerItem.igniteBlock(s.level, p, eye, down, held) == null, "mobGriefing faux : aucun feu pose");
            griefing.set(true, s.server);
            BlockPos lit = FlamethrowerItem.igniteBlock(s.level, p, eye, down, held);
            Gt.check(lit != null && s.level.getBlockState(lit).is(Blocks.FIRE), "mobGriefing vrai : feu devant les planches");
            Gt.check(s.level.getBlockState(h.absolutePos(planks)).is(Blocks.OAK_PLANKS), "le jet ne casse pas le bloc");
            s.level.setBlockAndUpdate(lit, Blocks.AIR.defaultBlockState());
            Vec3 toStone = Vec3.atCenterOf(h.absolutePos(stone)).add(0, 0.5, 0).subtract(eye).normalize();
            Gt.check(FlamethrowerItem.igniteBlock(s.level, p, eye, toStone, held) == null, "la pierre ne s'allume pas");
            //Reservoir vide : le jet s'arrete
            ElectricCharge.setHydrogen(held, 5);
            p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            held.getItem().onUseTick(s.level, p, held, held.getUseDuration() - 2);
            Gt.check(!p.isUsingItem() && ElectricCharge.hydrogen(held) == 5, "reservoir vide : arret sans rien prelever");
        } finally {
            griefing.set(before, s.server);
            Gt.disconnect(s.server, id);
        }
        s.done();
    }

    // ================================================================== remplissage d'hydrogene
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_9_fill", timeoutTicks = 200)
    public static void p103_9_remplissage_hydrogene(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack jetStack = new ItemStack(PiElectricGear.JETPACK.get());
        IGasHandler gas = jetStack.getCapability(PioneersAPI.GAS_HANDLER).resolve().orElse(null);
        Gt.check(gas != null, "le jetpack expose un reservoir de gaz");
        GasStack rest = gas.insertGas(GasStack.of(PiGasTypes.HYDROGEN.get(), 30_000), Action.EXECUTE);
        Gt.equal(HydrogenJetpackItem.HYDROGEN, ElectricCharge.hydrogen(jetStack), "24 000 mB au plus");
        Gt.equal(30_000 - HydrogenJetpackItem.HYDROGEN, rest.getAmount(), "le reste revient");
        Gt.check(gas.extractGas(1000, Action.EXECUTE).isEmpty(), "on ne vide pas le reservoir");
        ItemStack flStack = new ItemStack(PiElectricGear.FLAMETHROWER.get());
        IGasHandler flGas = flStack.getCapability(PioneersAPI.GAS_HANDLER).resolve().orElse(null);
        Gt.check(flGas != null && !flGas.insertGas(GasStack.of(PiGasTypes.OXYGEN.get(), 1000), Action.EXECUTE).isEmpty() && ElectricCharge.hydrogen(flStack) == 0,
              "l'oxygene est refuse");
        ItemStack drillStack = new ItemStack(PiElectricGear.DRILL.get());
        Gt.check(drillStack.getCapability(PioneersAPI.GAS_HANDLER).resolve().isEmpty(), "la perceuse n'a pas de reservoir");
        //Capsule d'hydrogene posee sur l'objet : 1 000 mB, la capsule vide revient au curseur
        ItemStack capsule = GasCapsuleItem.filled(PiBasicItems.GAS_CAPSULE.get(), PiGasTypes.HYDROGEN.get());
        ItemStack[] cursor = {capsule};
        SlotAccess access = new SlotAccess() {
            @Override
            public ItemStack get() {
                return cursor[0];
            }

            @Override
            public boolean set(ItemStack stack) {
                cursor[0] = stack;
                return true;
            }
        };
        Player mock = h.makeMockPlayer();
        Gt.check(ElectricCharge.capsuleOnto(flStack, capsule, ClickAction.PRIMARY, mock, access), "capsule acceptee");
        Gt.equal(GasCapsuleItem.CAPACITY, ElectricCharge.hydrogen(flStack), "1 000 mB verses");
        Gt.check(cursor[0].getItem() == PiBasicItems.GAS_CAPSULE.get() && GasCapsuleItem.getGas(cursor[0]) == null, "capsule vide au curseur");
        //Buffer de gaz MKI : la case du haut remplit le lance-flammes, sans jamais le vider
        BlockPos rel = new BlockPos(4, 1, 4);
        h.setBlock(rel, PiTransport.BUFFERS.get(BufferType.GAS).get(CableTier.BASIC).getBlock());
        BufferTile buffer = (BufferTile) h.getBlockEntity(rel);
        buffer.getGasTank().insert(GasStack.of(PiGasTypes.HYDROGEN.get(), 20_000), Action.EXECUTE, Automation.INTERNAL);
        ItemStack empty = new ItemStack(PiElectricGear.FLAMETHROWER.get());
        buffer.getIoSlots().get(0).setStack(empty);
        h.runAtTickTime(60, () -> {
            ItemStack in = buffer.getIoSlots().get(0).getStack(), out = buffer.getIoSlots().get(1).getStack();
            ItemStack f = in.isEmpty() ? out : in;
            long got = ElectricCharge.hydrogen(f);
            Gt.check(got > 0, "le buffer de gaz a rempli le lance-flammes");
            Gt.equal(20_000 - got, buffer.getGasTank().getStack().getAmount(), "l'hydrogene vient du buffer (conservation)");
            s.done();
        });
    }
}
