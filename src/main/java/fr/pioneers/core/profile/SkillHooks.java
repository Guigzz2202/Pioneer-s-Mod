package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 2) : crochets generaux des skills et des vocations sur les evenements de Forge (minage, pose, combat, elevage, repas,
 * portee, vision). Les crochets propres a une machine sont poses dans sa classe (forge, peche, cuisine, rail, espace, radiation...) et
 * appellent {@link PioneerApi}. Proprietaire des blocs du mod : pose ici ({@link PiBlockEntity#setOwner}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class SkillHooks {

    public static final TagKey<Block> DERELICT = TagKey.create(Registries.BLOCK, Pioneers.rl("derelict"));
    public static final TagKey<EntityType<?>> SENTINELS = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("sentinels"));
    public static final TagKey<net.minecraft.world.item.Item> COMPONENTS = TagKey.create(Registries.ITEM, Pioneers.rl("components"));
    private static final UUID REACH = UUID.fromString("5f2b7b1e-6c55-4d0e-9a51-1d1b0c9a7e21");
    /** Derniere pose ou casse de bloc par joueur (defaut du Batisseur : faim pendant le chantier). */
    private static final Map<UUID, Long> LAST_BUILD = new HashMap<>();
    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();

    private SkillHooks() {
    }

    static void init() {
    }

    /** Rend {@code points} de durabilite a l'objet tenu au tick suivant (usure reduite : on rend apres coup). */
    public static void refundWear(ServerPlayer player, InteractionHand hand, ItemStack stack, int points) {
        if (points <= 0 || !stack.isDamageableItem()) {
            return;
        }
        player.getServer().tell(new TickTask(player.getServer().getTickCount() + 1, () -> {
            ItemStack now = player.getItemInHand(hand);
            if (now == stack || ItemStack.isSameItem(now, stack)) {
                now.setDamageValue(Math.max(0, now.getDamageValue() - points));
            }
        }));
    }

    /** Usure reduite d'une fraction {@code reduction} (0.15 = 15 % d'usure en moins) : rend un point avec cette probabilite. */
    public static void reduceWear(ServerPlayer player, InteractionHand hand, ItemStack stack, double reduction) {
        if (reduction > 0 && player.getRandom().nextDouble() < reduction) {
            refundWear(player, hand, stack, 1);
        }
    }

    // ------------------------------------------------------------------ etablis
    /**
     * Usure des outils d'un etabli pour une fabrication : 1 point, 0 avec la probabilite de l'Ingenierie (« Usure des outils », jusqu'a
     * 25 %), 2 avec la probabilite du defaut de l'Eleveur-Pecheur a l'etabli de montage (« Lent a la technique » : la lenteur d'un geste
     * instantane se paie en usure, 10 %).
     */
    public static int benchWear(@Nullable ServerPlayer player, boolean assembly, net.minecraft.util.RandomSource random) {
        if (player == null) {
            return 1;
        }
        int wear = 1;
        double less = -PioneerApi.modifier(player, "engineering.tool_wear");
        if (less > 0 && random.nextDouble() < less) {
            wear = 0;
        }
        double more = assembly ? PioneerApi.modifier(player, "herder.assembly_time") : 0;
        if (more > 0 && random.nextDouble() < more) {
            wear++;
        }
        return wear;
    }

    /** XP d'Ingenierie pour chaque composant fabrique a la main (tag {@code pioneers:components}). */
    public static void benchCrafted(@Nullable ServerPlayer player, ItemStack result, int made) {
        if (player == null || made <= 0) {
            return;
        }
        if (result.is(COMPONENTS)) {
            PioneerApi.gain(player, "engineering.component", made, null);
        }
        //Robotique : un robot monte a la main
        if (result.getItem() instanceof fr.pioneers.logistics.robotics.ItemRobotUnit) {
            PioneerApi.gain(player, "robotics.robot", made, null);
        }
    }

    // ------------------------------------------------------------------ pose
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PiBlockEntity pbe && pbe.owner() == null) {
            pbe.setOwner(player.getUUID());
            if (be instanceof fr.pioneers.core.machine.MachineTile) {
                PioneerApi.gain(player, "engineering.machine", 1, pos);
            }
        }
        LAST_BUILD.put(player.getUUID(), level.getGameTime());
        BlockState placed = event.getPlacedBlock();
        //Chutes reemployees (Batisseur) : un bloc simple pose sur dix est rendu
        double saving = PioneerApi.modifier(player, "builder.material_saving");
        if (saving > 0 && be == null && !player.getAbilities().instabuild && level.random.nextDouble() < saving) {
            ItemStack refund = new ItemStack(placed.getBlock().asItem());
            if (!refund.isEmpty()) {
                player.getServer().tell(new TickTask(player.getServer().getTickCount() + 1, () -> {
                    if (!player.getInventory().add(refund)) {
                        player.drop(refund, false);
                    }
                }));
            }
        }
        if (placed.getBlock() instanceof CropBlock || placed.is(BlockTags.CROPS)) {
            PioneerApi.gain(player, "farming.sow", 1, pos);
        } else {
            PioneerApi.gain(player, "building.place", 1, pos);
        }
    }

    // ------------------------------------------------------------------ casse
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState state = event.getState();
        float speed = event.getNewSpeed();
        if (state.is(DERELICT)) {
            speed *= (float) PioneerApi.factor(player, "salvage.break_speed");
        } else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && player.getMainHandItem().isCorrectToolForDrops(state)) {
            speed *= (float) (PioneerApi.factor(player, "mining.speed") * PioneerApi.factor(player, "farmer.mining_speed"));
        }
        event.setNewSpeed(speed);
    }

    private static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(TagKey.create(Registries.BLOCK, Pioneers.rl("outcrops")));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        LAST_BUILD.put(player.getUUID(), level.getGameTime());
        ItemStack tool = player.getMainHandItem();
        if (isOre(state)) {
            PioneerApi.gain(player, pos.getY() < 0 ? "mining.deep" : "mining.ore", 1, pos);
            double nugget = PioneerApi.modifier(player, "miner.extra_nugget");
            if (nugget > 0 && level.random.nextDouble() < nugget && tool.isCorrectToolForDrops(state)) {
                List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
                if (!drops.isEmpty()) {
                    Block.popResource(level, pos, drops.get(0).copyWithCount(1));
                }
            }
        } else if (state.is(BlockTags.BASE_STONE_OVERWORLD) && tool.isCorrectToolForDrops(state)) {
            PioneerApi.gain(player, "mining.stone", 1, pos);
        }
        if (tool.canPerformAction(ToolActions.PICKAXE_DIG)) {
            reduceWear(player, InteractionHand.MAIN_HAND, tool, -PioneerApi.modifier(player, "mining.pick_wear"));
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
            && !(state.getBlock() instanceof fr.pioneers.farm.crop.FarmCropBlock)) {
            cropHarvested(player, level, pos, Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool));
            if (quickHands(player, "farming.hand_speed")) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = pos.relative(d);
                    BlockState ns = level.getBlockState(n);
                    if (ns.getBlock() == crop && crop.isMaxAge(ns)) {
                        level.destroyBlock(n, true, player);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Recolte a la main d'une culture mure (cassee ou cueillie) : XP d'Agriculture, Main verte du Cultivateur (une recolte et une graine
     * de plus, au hasard).
     */
    public static void cropHarvested(ServerPlayer player, ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        PioneerApi.gain(player, "farming.harvest", 1, pos);
        double extra = PioneerApi.modifier(player, "farmer.extra_harvest");
        if (extra > 0 && level.random.nextDouble() < extra) {
            drops.stream().filter(s -> !s.isEmpty() && !s.is(Tags.Items.SEEDS)).findFirst()
                  .ifPresent(s -> Block.popResource(level, pos, s.copyWithCount(1)));
        }
        double seeds = PioneerApi.modifier(player, "farmer.extra_seeds");
        if (seeds > 0 && level.random.nextDouble() < seeds) {
            drops.stream().filter(s -> !s.isEmpty() && s.is(Tags.Items.SEEDS)).findFirst()
                  .ifPresent(s -> Block.popResource(level, pos, s.copyWithCount(1)));
        }
    }

    /**
     * Gestes plus rapides a la main ({@code farming.hand_speed}, {@code husbandry.care_speed}, {@code cooking.hand_speed}) : un geste
     * instantane ne peut pas aller plus vite, il en fait parfois deux. Vrai avec la probabilite du modificateur.
     */
    public static boolean quickHands(ServerPlayer player, String key) {
        double p = PioneerApi.modifier(player, key);
        return p > 0 && player.getRandom().nextDouble() < p;
    }

    @SubscribeEvent
    public static void onTill(BlockEvent.BlockToolModificationEvent event) {
        if (!event.isSimulated() && event.getToolAction() == ToolActions.HOE_TILL && event.getPlayer() instanceof ServerPlayer player) {
            reduceWear(player, InteractionHand.MAIN_HAND, event.getHeldItemStack(), -PioneerApi.modifier(player, "farming.hoe_wear"));
        }
    }

    // ------------------------------------------------------------------ combat
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity src = event.getSource().getEntity();
        float amount = event.getAmount();
        if (src instanceof Player attacker) {
            amount *= (float) (PioneerApi.factor(attacker, "combat.damage") * PioneerApi.factor(attacker, "logistician.damage"));
        }
        if (event.getEntity() instanceof Player victim) {
            amount *= (float) PioneerApi.factor(victim, "engineer.damage_taken");
            if (src != null && src.getType().is(SENTINELS)) {
                amount *= (float) PioneerApi.factor(victim, "combat.sentinel_damage_taken");
            }
        }
        event.setAmount(amount);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            LivingEntity dead = event.getEntity();
            if (dead.getType().is(SENTINELS)) {
                PioneerApi.gain(killer, "combat.sentinel");
            } else if (dead instanceof Enemy) {
                PioneerApi.gain(killer, "combat.hostile");
            }
        }
    }

    /** Pas de loup (Eclaireur) et Voyant (Commercant) : distance a laquelle les creatures hostiles et les Sentinelles repèrent le joueur. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player player && event.getLookingEntity() != null
            && (event.getLookingEntity() instanceof Enemy || event.getLookingEntity().getType().is(SENTINELS))) {
            double k = PioneerApi.factor(player, "scout.detection") * PioneerApi.factor(player, "trader.detection");
            if (k != 1) {
                event.modifyVisibility(k);
            }
        }
    }

    // ------------------------------------------------------------------ elevage
    @SubscribeEvent
    public static void onBaby(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player) || !(event.getParentA() instanceof Animal a)
            || !(event.getParentB() instanceof Animal b)) {
            return;
        }
        PioneerApi.gain(player, "husbandry.birth");
        ServerLevel level = player.serverLevel();
        double twins = PioneerApi.modifier(player, "herder.twins");
        if (twins > 0 && level.random.nextDouble() < twins && event.getChild() != null) {
            var extra = a.getBreedOffspring(level, b);
            if (extra != null) {
                extra.setBaby(true);
                extra.moveTo(a.getX(), a.getY(), a.getZ(), 0, 0);
                level.addFreshEntity(extra);
            }
        }
        //Delai entre naissances : les parents se remettent plus tot (age remis a 6 000 par Minecraft juste apres cet evenement)
        double k = PioneerApi.factor(player, "husbandry.breed_delay");
        if (k < 1) {
            level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> {
                for (Animal p : new Animal[]{a, b}) {
                    if (p.isAlive() && p.getAge() > 0) {
                        p.setAge((int) (p.getAge() * k));
                    }
                }
            }));
        }
    }

    // ------------------------------------------------------------------ repas
    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack food = event.getItem();
        if (!(food.getItem() instanceof fr.pioneers.farm.food.FarmFoodItem)) {
            return;
        }
        double longer = PioneerApi.modifier(player, "cooking.effect_duration");
        if (longer > 0) {
            for (MobEffectInstance e : player.getActiveEffects()) {
                if (e.getDuration() > 0 && e.getDuration() < 20 * 60 * 30) {
                    player.addEffect(new MobEffectInstance(e.getEffect(), (int) (e.getDuration() * (1 + longer)), e.getAmplifier(), e.isAmbient(),
                          e.isVisible(), e.showIcon()));
                }
            }
        }
        double palate = PioneerApi.modifier(player, "astronaut.food");
        FoodProperties props = food.getFoodProperties(player);
        if (palate < 0 && props != null) {
            int lose = (int) Math.round(props.getNutrition() * -palate);
            if (lose > 0) {
                player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - lose));
            }
        }
    }

    // ------------------------------------------------------------------ joueur (toutes les secondes)
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        //Appetit de chantier : +15 % de la fatigue alimentaire tant qu'on pose ou casse des blocs (5 s apres le dernier bloc)
        float ex = player.getFoodData().getExhaustionLevel();
        Float last = LAST_EXHAUSTION.put(player.getUUID(), ex);
        double hunger = PioneerApi.modifier(player, "builder.hunger");
        Long built = LAST_BUILD.get(player.getUUID());
        if (hunger > 0 && last != null && ex > last && built != null && player.level().getGameTime() - built < 100) {
            player.getFoodData().addExhaustion((float) ((ex - last) * hunger));
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        //Portee de pose (Construction)
        AttributeInstance reach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (reach != null) {
            double want = PioneerApi.modifier(player, "building.reach");
            AttributeModifier cur = reach.getModifier(REACH);
            if (cur == null || Math.abs(cur.getAmount() - want) > 1e-6) {
                reach.removeModifier(REACH);
                if (want != 0) {
                    reach.addTransientModifier(new AttributeModifier(REACH, "Pioneer building reach", want, AttributeModifier.Operation.ADDITION));
                }
            }
        }
        //Gueule noire (Mineur-Fondeur) : vision nocturne legere sous le niveau 40, dans le noir
        if (PioneerApi.modifier(player, "miner.night_vision") > 0 && player.getY() < 40 && !player.level().canSeeSky(player.blockPosition())) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
    }

    @SubscribeEvent
    public static void onShear(PlayerInteractEvent.EntityInteract event) {
        //Patience (Eleveur-Pecheur) : les cisailles ne s'usent pas
        if (event.getEntity() instanceof ServerPlayer player && event.getItemStack().is(Items.SHEARS)
            && PioneerApi.modifier(player, "herder.no_wear") > 0) {
            refundWear(player, event.getHand(), event.getItemStack(), 1);
        }
    }

    // ------------------------------------------------------------------ Batisseur
    /** Liste de ce qui manque a un plan projete (six lignes au plus, les plus nombreux d'abord). */
    public static void missingList(ServerPlayer player, Map<Block, Integer> missing) {
        if (missing.isEmpty()) {
            player.sendSystemMessage(Component.translatable("pioneer.pioneers.builder.complete").withStyle(ChatFormatting.GREEN));
            return;
        }
        player.sendSystemMessage(Component.translatable("pioneer.pioneers.builder.missing").withStyle(ChatFormatting.GOLD));
        missing.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6).forEach(e -> player.sendSystemMessage(
              Component.literal("  " + e.getValue() + " × ").append(e.getKey().getName()).withStyle(ChatFormatting.GRAY)));
        if (missing.size() > 6) {
            player.sendSystemMessage(Component.translatable("pioneer.pioneers.builder.more", missing.size() - 6).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------ machines (XP automatique)
    /**
     * Fin d'une operation d'atelier ou d'un lot de multibloc : le proprietaire apprend un peu (20 %, plafond journalier) de ce que la
     * machine fait. Dans l'ordre (MAJ Recettes, lot 9a ; tools/gc/industry/machine_skills.py) : recyclage = Recuperation ; etape d'une voie
     * de metallurgie (recettes {@code processes/}) = Forge et metallurgie, XP x (1 + tier de la recette) ; machine nouvelle ou refondue de la
     * MAJ = son action ({@link IndustryMachine#xpAction()}), XP x (1 + tier) ; sinon, multiblocs seulement (regle de la MAJ RP) : composants =
     * Ingenierie, fours (habillage thermique) = Forge, fermes (vivant) = Agriculture ; les autres machines n'apprennent rien.
     */
    public static void autoRecipe(ServerLevel level, BlockPos pos, @Nullable UUID owner, IndustryMachine machine, IndustrialRecipe r,
          boolean multiblock) {
        if (owner == null) {
            return;
        }
        String action;
        double count = 1;
        ItemStack result = r.results().isEmpty() ? ItemStack.EMPTY : r.results().get(0).stack();
        if (r.draws() != null) {
            action = "salvage.sort";
        } else if (r.getId().getPath().startsWith("processes/")) {
            action = IndustryMachine.ROUTE_XP;
            count = 1 + Math.max(0, r.tier());
        } else if (r.family() != null && IndustryMachine.xpActionOf(r.family()) != null) {
            //1.0.4-beta (lot 11) : recette recue d'une machine absorbee : l'action de la machine d'origine (four de traitement thermique, verrerie)
            action = IndustryMachine.xpActionOf(r.family());
            count = 1 + Math.max(0, r.tier());
        } else if (machine.xpAction() != null) {
            action = machine.xpAction();
            count = 1 + Math.max(0, r.tier());
        } else if (!multiblock) {
            return;
        } else {
            String style = machine.style.name();
            action = !result.isEmpty() && result.is(COMPONENTS) ? "engineering.component"
                  : "THERMAL".equals(style) ? "smithing.smelt" : "LIVING".equals(style) ? "farming.harvest" : null;
        }
        if (action != null) {
            PioneerApi.gainAuto(level, pos, owner, action, count);
        }
    }

    // ------------------------------------------------------------------ espace
    private static final Map<UUID, Integer> APNEA = new HashMap<>();

    /** Respiration economisee (skill Spatial, « Consommation d'oxygene ») : vrai = pas de prelevement cette seconde. */
    public static boolean oxygenSaved(ServerPlayer player, boolean hasOxygen) {
        double p = -PioneerApi.modifier(player, "space.oxygen");
        return hasOxygen && p > 0 && player.getRandom().nextDouble() < p;
    }

    public static void breathed(ServerPlayer player) {
        APNEA.remove(player.getUUID());
    }

    /** Apnee de l'Astronaute : vrai tant que les secondes sans air n'ont pas depasse son avantage (appele une fois par seconde). */
    public static boolean holdBreath(ServerPlayer player) {
        double s = PioneerApi.modifier(player, "astronaut.breath_seconds");
        if (s <= 0) {
            return false;
        }
        int held = APNEA.merge(player.getUUID(), 1, Integer::sum);
        return held <= s;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        APNEA.remove(event.getEntity().getUUID());
        LAST_BUILD.remove(event.getEntity().getUUID());
        LAST_EXHAUSTION.remove(event.getEntity().getUUID());
    }

    /** Id d'objet utile aux crochets (decor). */
    static ResourceLocation id(String path) {
        return Pioneers.rl(path);
    }
}
