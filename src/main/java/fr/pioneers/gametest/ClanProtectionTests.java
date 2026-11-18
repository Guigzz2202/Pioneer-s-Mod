package fr.pioneers.gametest;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.world.ruins.RuinBlocks;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 4, protection d'un territoire de clan. Matrice de 29 gestes (vrais gestes du joueur, ou les evenements
 * Forge que le jeu lance pour eux) x 4 acteurs (membre, recrue, allie, etranger) x 2 zones (Membres, Ouverte), comparee aux regles de la
 * specification (decision 39 pour l'allie en zone Ouverte) ; pietinement refuse a tous ; explosion sans degat de bloc.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class ClanProtectionTests {

    private ClanProtectionTests() {
    }

    /** Un geste : remet sa case en etat, agit au nom de l'acteur, dit si le geste a abouti (ou n'a pas ete refuse). */
    interface Gesture {

        boolean run(GameTestHelper h, ServerPlayer actor, BlockPos rel);
    }

    record Act(String name, ClanApi.Action cls, Gesture g) {
    }

    // ------------------------------------------------------------------ gestes
    static BlockHitResult top(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    static BlockHitResult side(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    /** Vrai clic droit main vide sur un bloc (evenement Forge, puis usage du bloc). */
    static void use(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, side(h, rel));
    }

    /** Vrai clic droit avec un objet sur le dessus du bloc (pose). */
    static void place(GameTestHelper h, ServerPlayer p, BlockPos floor, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.gameMode.useItemOn(p, h.getLevel(), stack, InteractionHand.MAIN_HAND, top(h, floor));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Conteneur ouvert pour de vrai ? (puis referme) */
    static boolean opens(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        use(h, p, rel);
        boolean open = p.containerMenu != p.inventoryMenu;
        p.closeContainer();
        return open;
    }

    /** Evenement de clic droit seul (ce que la protection decide pour l'usage du bloc). */
    static boolean rightClick(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        PlayerInteractEvent.RightClickBlock e = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, h.absolutePos(rel), side(h, rel));
        return !e.isCanceled() && e.getUseBlock() != Event.Result.DENY;
    }

    static boolean posted(Event e) {
        return !MinecraftForge.EVENT_BUS.post(e);
    }

    static <T extends Entity> T unplaced(GameTestHelper h, EntityType<T> type, BlockPos rel) {
        T e = type.create(h.getLevel());
        Gt.check(e != null, "entite " + type + " non creee");
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(rel));
        e.setPos(v.x, v.y, v.z);
        return e;
    }

    static Block machine() {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation("pioneers", IndustryMachine.RECYCLER.atelierId(CableTier.BASIC)));
        Gt.check(b != Blocks.AIR, "recycleur MKI absent");
        return b;
    }

    static List<Act> acts() {
        List<Act> l = new ArrayList<>();
        ClanApi.Action BREAK = ClanApi.Action.BREAK, PLACE = ClanApi.Action.PLACE, CONTAINER = ClanApi.Action.CONTAINER,
              MACHINE = ClanApi.Action.MACHINE, INTERACT = ClanApi.Action.INTERACT, ENTITY = ClanApi.Action.ENTITY,
              ATTACK = ClanApi.Action.ATTACK_ANIMAL, VEHICLE = ClanApi.Action.VEHICLE;
        l.add(new Act("casser de la pierre", BREAK, (h, p, r) -> {
            h.setBlock(r, Blocks.STONE);
            p.gameMode.destroyBlock(h.absolutePos(r));
            return h.getBlockState(r).isAir();
        }));
        l.add(new Act("casser un bloc du mod", BREAK, (h, p, r) -> {
            h.setBlock(r, RuinBlocks.get("casing", 1));
            p.gameMode.destroyBlock(h.absolutePos(r));
            return h.getBlockState(r).isAir();
        }));
        l.add(new Act("poser un bloc", PLACE, (h, p, r) -> {
            h.setBlock(r, Blocks.AIR);
            place(h, p, r.below(), new ItemStack(Items.COBBLESTONE));
            return h.getBlockState(r).is(Blocks.COBBLESTONE);
        }));
        l.add(new Act("poser une torche", PLACE, (h, p, r) -> {
            h.setBlock(r, Blocks.AIR);
            place(h, p, r.below(), new ItemStack(Items.TORCH));
            return h.getBlockState(r).is(Blocks.TORCH);
        }));
        l.add(new Act("vider un seau", BREAK, (h, p, r) -> {
            h.setBlock(r, Blocks.AIR);
            return posted(new FillBucketEvent(p, new ItemStack(Items.WATER_BUCKET), h.getLevel(), top(h, r.below())));
        }));
        l.add(new Act("ouvrir un coffre", CONTAINER, (h, p, r) -> {
            h.setBlock(r, Blocks.CHEST);
            return opens(h, p, r);
        }));
        l.add(new Act("ouvrir un tonneau", CONTAINER, (h, p, r) -> {
            h.setBlock(r, Blocks.BARREL);
            return opens(h, p, r);
        }));
        l.add(new Act("ouvrir un four", CONTAINER, (h, p, r) -> {
            h.setBlock(r, Blocks.FURNACE);
            return opens(h, p, r);
        }));
        l.add(new Act("ouvrir un entonnoir", CONTAINER, (h, p, r) -> {
            h.setBlock(r, Blocks.HOPPER);
            return opens(h, p, r);
        }));
        l.add(new Act("utiliser un lutrin", CONTAINER, (h, p, r) -> {
            h.setBlock(r, Blocks.LECTERN);
            return rightClick(h, p, r);
        }));
        l.add(new Act("utiliser un juke-box", CONTAINER, (h, p, r) -> {
            h.setBlock(r, Blocks.JUKEBOX);
            return rightClick(h, p, r);
        }));
        l.add(new Act("utiliser une machine du mod", MACHINE, (h, p, r) -> {
            h.setBlock(r, machine());
            return rightClick(h, p, r);
        }));
        l.add(new Act("utiliser un buffer du mod", MACHINE, (h, p, r) -> {
            h.setBlock(r, PiTransport.BUFFERS.get(BufferType.ITEM).get(CableTier.BASIC).getBlock());
            return rightClick(h, p, r);
        }));
        l.add(new Act("ouvrir une porte", INTERACT, (h, p, r) -> {
            h.setBlock(r, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            h.setBlock(r.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            use(h, p, r);
            return h.getBlockState(r).getValue(DoorBlock.OPEN);
        }));
        l.add(new Act("appuyer sur un bouton", INTERACT, (h, p, r) -> {
            h.setBlock(r, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
            use(h, p, r);
            return h.getBlockState(r).getValue(ButtonBlock.POWERED);
        }));
        l.add(new Act("actionner un levier", INTERACT, (h, p, r) -> {
            h.setBlock(r, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
            use(h, p, r);
            return h.getBlockState(r).getValue(LeverBlock.POWERED);
        }));
        l.add(new Act("utiliser un etabli", INTERACT, (h, p, r) -> {
            h.setBlock(r, Blocks.CRAFTING_TABLE);
            return opens(h, p, r);
        }));
        l.add(new Act("regler un comparateur", PLACE, (h, p, r) -> {
            h.setBlock(r, Blocks.COMPARATOR);
            BlockState before = h.getBlockState(r);
            use(h, p, r);
            return h.getBlockState(r).getValue(ComparatorBlock.MODE) != before.getValue(ComparatorBlock.MODE);
        }));
        //1.0.3-beta (lot 16) : labour et engrais sont des gestes de culture (FARMLAND : pour un membre, batir, comme avant)
        l.add(new Act("labourer a la houe", ClanApi.Action.FARMLAND, (h, p, r) -> {
            h.setBlock(r, Blocks.DIRT);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
            UseOnContext ctx = new UseOnContext(p, InteractionHand.MAIN_HAND, top(h, r));
            boolean ok = posted(new BlockEvent.BlockToolModificationEvent(h.getBlockState(r), ctx, ToolActions.HOE_TILL, false));
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return ok;
        }));
        l.add(new Act("mettre de l'engrais", ClanApi.Action.FARMLAND, (h, p, r) -> {
            h.setBlock(r, Blocks.GRASS_BLOCK);
            return posted(new BonemealEvent(p, h.getLevel(), h.absolutePos(r), h.getBlockState(r), new ItemStack(Items.BONE_MEAL)));
        }));
        l.add(new Act("frapper un bloc", BREAK, (h, p, r) -> {
            h.setBlock(r, Blocks.STONE);
            return !ForgeHooks.onLeftClickBlock(p, h.absolutePos(r), Direction.UP).isCanceled();
        }));
        l.add(new Act("caresser une vache", ENTITY, (h, p, r) ->
              posted(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, unplaced(h, EntityType.COW, r)))));
        l.add(new Act("frapper une vache", ATTACK, (h, p, r) -> posted(new AttackEntityEvent(p, unplaced(h, EntityType.COW, r)))));
        l.add(new Act("monter dans un bateau", VEHICLE, (h, p, r) ->
              posted(new EntityMountEvent(p, unplaced(h, EntityType.BOAT, r), h.getLevel(), true))));
        l.add(new Act("ouvrir un wagonnet a coffre", VEHICLE, (h, p, r) ->
              posted(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, unplaced(h, EntityType.CHEST_MINECART, r)))));
        l.add(new Act("ouvrir un support d'armure", CONTAINER, (h, p, r) -> {
            Entity stand = unplaced(h, EntityType.ARMOR_STAND, r);
            return posted(new PlayerInteractEvent.EntityInteractSpecific(p, InteractionHand.MAIN_HAND, stand, new Vec3(0, 1, 0)));
        }));
        l.add(new Act("casser un cadre", BREAK, (h, p, r) ->
              posted(new AttackEntityEvent(p, new ItemFrame(h.getLevel(), h.absolutePos(r), Direction.NORTH)))));
        l.add(new Act("tirer sur un bloc", BREAK, (h, p, r) -> {
            h.setBlock(r, Blocks.STONE);
            Arrow a = new Arrow(h.getLevel(), p);
            return posted(new ProjectileImpactEvent(a, side(h, r)));
        }));
        l.add(new Act("tirer sur un bouton", INTERACT, (h, p, r) -> {
            h.setBlock(r, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
            Arrow a = new Arrow(h.getLevel(), p);
            return posted(new ProjectileImpactEvent(a, side(h, r)));
        }));
        return l;
    }

    /** Regles de la specification (permissions des roles de {@code roles.json}, zone Ouverte et decision 39 pour l'allie). */
    static boolean expected(ClanApi.Action cls, String who, boolean open) {
        return switch (who) {
            case "membre" -> true;
            case "recrue" -> cls != ClanApi.Action.CONTAINER;
            case "allie" -> open && (cls == ClanApi.Action.INTERACT || cls == ClanApi.Action.VEHICLE || cls == ClanApi.Action.CONTAINER
                                     || cls == ClanApi.Action.MACHINE);
            default -> open && (cls == ClanApi.Action.INTERACT || cls == ClanApi.Action.VEHICLE);
        };
    }

    /** Chunks qui couvrent la structure (32 x 32). */
    static Set<Long> chunks(GameTestHelper h, int size) {
        Set<Long> out = new LinkedHashSet<>();
        for (int x = 0; x < size; x += 4) {
            for (int z = 0; z < size; z += 4) {
                out.add(Gt.chunk(h.absolutePos(new BlockPos(x, 2, z))));
            }
            out.add(Gt.chunk(h.absolutePos(new BlockPos(x, 2, size - 1))));
        }
        out.add(Gt.chunk(h.absolutePos(new BlockPos(size - 1, 2, size - 1))));
        return out;
    }

    /**
     * Matrice de protection : 29 gestes x membre, recrue, allie, etranger, en zone Membres puis en zone Ouverte (232 cas), plus le
     * pietinement refuse a tous dans un claim.
     */
    @GameTest(template = Gt.LARGE, batch = "pioneers_clan_matrice", timeoutTicks = 200)
    public static void matrice_de_protection(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        BlockPos stand = new BlockPos(16, 2, 28);
        ServerPlayer rep = s.player("gtrep", "Representante", stand);
        ServerPlayer member = s.player("gtmemb", "Membre", stand);
        ServerPlayer recruit = s.player("gtrecr", "Recrue", stand);
        ServerPlayer allyRep = s.player("gtally", "Alliee", stand);
        ServerPlayer stranger = s.player("gtstra", "Etrangere", stand);
        Clan a = s.clan("GtMatrice" + Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff), rep, member, recruit);
        Clan b = s.clan("GtAllie" + Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff), allyRep);
        s.role(a, member, "member");
        Gt.equal(ClanRoles.RECRUIT, a.member(recruit.getUUID()).role, "role de la recrue");
        //hypotheses des roles (roles.json) : membre = batir, conteneurs, machines ; recrue = batir, machines
        Gt.check(ClanRoles.has(a, member.getUUID(), Permission.BUILD) && ClanRoles.has(a, member.getUUID(), Permission.CONTAINERS)
                 && ClanRoles.has(a, member.getUUID(), Permission.MACHINES), "permissions du role Membre");
        Gt.check(ClanRoles.has(a, recruit.getUUID(), Permission.BUILD) && !ClanRoles.has(a, recruit.getUUID(), Permission.CONTAINERS)
                 && ClanRoles.has(a, recruit.getUUID(), Permission.MACHINES), "permissions du role Recrue");
        s.ally(a, rep, b, allyRep);
        Set<Long> chunks = chunks(h, Gt.LARGE_SIZE);
        for (long c : chunks) {
            ClanTerritory.put(s.server, a, ClanApi.dim(level), c, new Clan.Claim());
        }
        List<Act> acts = acts();
        Gt.check(acts.size() >= 25, "au moins 25 gestes");
        List<String> faults = new ArrayList<>();
        String[] names = {"membre", "recrue", "allie", "etranger"};
        ServerPlayer[] actors = {member, recruit, allyRep, stranger};
        int cases = 0;
        for (boolean open : new boolean[]{false, true}) {
            if (open) {
                for (long c : chunks) {
                    ClanTerritory.zone(rep, level, c, Clan.Zone.OPEN, 0);
                    Gt.check(a.claim(ClanApi.dim(level), c).zone == Clan.Zone.OPEN, "zone Ouverte non posee");
                }
            }
            for (int i = 0; i < acts.size(); i++) {
                Act act = acts.get(i);
                BlockPos r = new BlockPos(2 + (i % 8) * 3, 2, 2 + (i / 8) * 3);
                for (int k = 0; k < actors.length; k++) {
                    h.setBlock(r.above(), Blocks.AIR);
                    boolean got = act.g().run(h, actors[k], r);
                    boolean want = expected(act.cls(), names[k], open);
                    cases++;
                    if (got != want) {
                        faults.add((open ? "[Ouverte] " : "[Membres] ") + act.name() + " / " + names[k] + " : " + (got ? "permis" : "refuse")
                                   + ", attendu " + (want ? "permis" : "refuse"));
                    }
                    h.setBlock(r, Blocks.AIR);
                    h.setBlock(r.above(), Blocks.AIR);
                }
            }
        }
        //pietinement : refuse a tous dans un claim, membres compris (T8)
        BlockPos farm = new BlockPos(26, 2, 20);
        h.setBlock(farm, Blocks.FARMLAND);
        for (int k = 0; k < actors.length; k++) {
            BlockEvent.FarmlandTrampleEvent e = new BlockEvent.FarmlandTrampleEvent(level, h.absolutePos(farm), h.getBlockState(farm), 1.5F, actors[k]);
            cases++;
            if (posted(e)) {
                faults.add("pietinement / " + names[k] + " : permis, attendu refuse");
            }
        }
        Gt.check(faults.isEmpty(), faults.size() + " cas faux sur " + cases + " : " + String.join(" ; ", faults));
        s.done();
    }

    /** Pour tous, dans un claim : une explosion ne casse aucun bloc ; hors claim, elle casse. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_clan_explosion", timeoutTicks = 60)
    public static void explosion_sans_degat_dans_un_claim(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer rep = s.player("gtboom", "Artificiere", new BlockPos(2, 2, 2));
        Clan a = s.clan("GtBoom" + Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff), rep);
        int x = Gt.borderX(h, Gt.BORDER_SIZE);
        //le chunk de x est reclame, celui de x + 1 reste sauvage
        s.claim(a, h.absolutePos(new BlockPos(x, 2, 10)));
        BlockPos inClaim = new BlockPos(x, 2, 10), wild = new BlockPos(x + 1, 2, 10);
        for (BlockPos r : List.of(inClaim, wild)) {
            h.setBlock(r, Blocks.STONE);
        }
        //explosion pres de chaque bloc (le claim n'est pas touche, la nature si)
        Vec3 c1 = h.absoluteVec(new Vec3(x - 0.5, 2.5, 10.5)), c2 = h.absoluteVec(new Vec3(x + 2.5, 2.5, 10.5));
        s.level.explode(null, c1.x, c1.y, c1.z, 4.0F, Level.ExplosionInteraction.TNT);
        s.level.explode(null, c2.x, c2.y, c2.z, 4.0F, Level.ExplosionInteraction.TNT);
        h.assertBlockPresent(Blocks.STONE, inClaim);
        h.assertBlockNotPresent(Blocks.STONE, wild);
        s.done();
    }
}
