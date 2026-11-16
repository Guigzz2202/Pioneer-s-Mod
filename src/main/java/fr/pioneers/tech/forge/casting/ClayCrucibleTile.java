package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.core.Texts;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.ForgeTile;
import fr.pioneers.tech.forge.MoltenLoad;
import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.tech.forge.PiForge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creuset d'argile pose : contenu {@link MoltenLoad} (2 lingots), objets solides en attente de fusion, temperature propre.
 * <p>
 * Chauffe : cible = {@link ForgeHeat#heatBelowC} (feu de camp 1 100 degres, foyer...) + charbon de bois qui brule au pied
 * ({@link ForgeHeat#CHARCOAL_BONUS_C}, 80 s par charbon) + air ({@link #air} : bouffees du soufflet a main {@link ForgeHeat#HAND_PUFF_C}
 * qui retombent avec une constante de {@link #PUFF_TAU} ticks, ou soufflet mecanique tenu {@link #blast}, plafond
 * {@link ForgeHeat#BELLOWS_MAX_C}). Sans feu dessous : rien de tout ca, le creuset refroidit vers l'ambiante. La temperature monte d'au plus
 * 4 degres par tick (1 100 degres en une quinzaine de secondes) et descend doucement.
 * <p>
 * Fusion : un objet (minerai, fragment, lingot... {@link ForgeMetals#meltOf}) fond quand le creuset depasse sa temperature de fusion, en
 * {@link #MELT_TICKS} ticks par lingot ; le liquide est un melange ({@link Metals#resolve} : cuivre + etain = bronze). Usure : un point a
 * chaque nouvelle fonte dans un creuset vide (24 fusions) ; un creuset use se fend quand il est vide.
 * <p>
 * NBT : {@code load} (frozen), {@code crucible} (l'objet, usure comprise), {@code solids} (objets), {@code temp}, {@code fuel} (ticks de
 * charbon), {@code air}, {@code melt}.
 */
public class ClayCrucibleTile extends ForgeTile implements AirBlastReceiver {

    /** Ticks de fusion pour un lingot (au-dessus de la temperature de fusion). */
    public static final int MELT_TICKS = 60;
    /** Un charbon de bois brule 80 s (seulement sur un feu). */
    public static final int CHARCOAL_TICKS = 1600;
    public static final int MAX_FUEL = 4 * CHARCOAL_TICKS;
    /** Constante de temps de la retombee de l'air (ticks). */
    public static final float PUFF_TAU = 100F;
    /** Objets solides au plus (fragments). */
    public static final int MAX_SOLIDS = 24;

    protected MoltenLoad load = new MoltenLoad();
    private ItemStack crucible = ItemStack.EMPTY;
    private final List<ItemStack> solids = new ArrayList<>();
    private float temp = ForgeMetals.AMBIENT_C;
    private int fuel;
    private float air;
    private double blastSum;
    private int melt;
    private int heatBelow;
    /** Le creuset part dans une pince : la casse du bloc ne lache rien. */
    private boolean taken;
    // Dernier etat envoye au client
    private float sentTemp = -1000;
    private boolean sentFuel, sentFire;
    private int sentAir;
    // Etat client (metal et point de coulee mis en cache a la reception : le rendu n'appelle pas Metals.resolve a chaque image)
    private boolean clientFire;
    private String clientMetal = "";
    private int clientPour;

    public ClayCrucibleTile(BlockPos pos, BlockState state) {
        super(PiForge.CLAY_CRUCIBLE_TILE.get(), pos, state);
    }

    public MoltenLoad load() {
        return load;
    }

    public float temperature() {
        return temp;
    }

    public List<ItemStack> solids() {
        return Collections.unmodifiableList(solids);
    }

    public boolean hasFuel() {
        return fuel > 0;
    }

    public boolean onFire() {
        return level != null && level.isClientSide ? clientFire : heatBelow > 0;
    }

    public float air() {
        return air;
    }

    /** Vrai si le liquide coule (rendu, pince). */
    public boolean molten() {
        if (level != null && level.isClientSide) {
            return !clientMetal.isEmpty() && temp >= clientPour;
        }
        return !load.isEmpty() && temp >= load.pourC();
    }

    /** Metal du liquide cote client (vide s'il n'y en a pas). */
    public String clientMetal() {
        return clientMetal;
    }

    /** L'objet creuset (usure comprise) ; un creuset neuf si la tuile n'en a pas (pose par commande). */
    public ItemStack crucibleItem() {
        return crucible.isEmpty() ? new ItemStack(PiForge.CLAY_CRUCIBLE.getBlock()) : crucible;
    }

    /** Reprend un creuset (pose a la main ou reposee par la pince) : objet, solides (dans son NBT {@code solids}), contenu. */
    public void setCrucible(ItemStack stack, @Nullable MoltenLoad contents) {
        crucible = stack.copyWithCount(1);
        solids.clear();
        CompoundTag tag = crucible.getTag();
        if (tag != null && tag.contains("solids", Tag.TAG_LIST)) {
            ListTag list = tag.getList("solids", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack s = ItemStack.of(list.getCompound(i));
                if (!s.isEmpty()) {
                    solids.add(s);
                }
            }
            tag.remove("solids");
            if (tag.isEmpty()) {
                crucible.setTag(null);
            }
        }
        if (contents != null) {
            load = contents.copy();
            if (level != null) {
                temp = Math.max(ForgeMetals.AMBIENT_C, load.temperatureAt(level.getGameTime(), MoltenLoad.TONGS_TAU_TICKS));
            }
        }
        changed();
        markClient();
    }

    /** L'objet creuset a mettre dans la pince : usure + solides en attente (NBT {@code solids}). */
    private ItemStack crucibleForTongs() {
        ItemStack out = crucibleItem().copy();
        if (!solids.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack s : solids) {
                list.add(s.save(new CompoundTag()));
            }
            out.getOrCreateTag().put("solids", list);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Air
    // ------------------------------------------------------------------

    /** Soufflet mecanique ou souffleur (appele a chaque tick tant que l'air arrive). */
    @Override
    public void blast(double strength) {
        blastSum += strength;
    }

    /** Une bouffee du soufflet a main. */
    public void puff() {
        air = Math.min(ForgeHeat.BELLOWS_MAX_C, air + ForgeHeat.HAND_PUFF_C);
    }

    /** Temperature visee maintenant (degres C). */
    public float targetC() {
        if (heatBelow <= 0) {
            return ForgeMetals.AMBIENT_C;
        }
        return heatBelow + (fuel > 0 ? ForgeHeat.CHARCOAL_BONUS_C : 0) + air;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if ((now + worldPosition.asLong()) % 10 == 0) {
            heatBelow = ForgeHeat.heatBelowC(level, worldPosition);
        }
        float mech = (float) Math.min(1.0, blastSum) * ForgeHeat.BELLOWS_MAX_C;
        blastSum = 0;
        air = Math.max(mech, air * (1F - 1F / PUFF_TAU));
        if (air < 0.5F) {
            air = 0;
        }
        if (heatBelow > 0 && fuel > 0) {
            fuel--;
        }
        float target = targetC();
        float d = target - temp;
        if (d > 0) {
            temp += Math.min(d, Math.min(4F, 0.2F + d * 0.05F));
        } else if (d < 0) {
            temp += Math.max(d, Math.max(-3F, d / 600F - 0.05F));
        }
        load.setTemperature(temp, now);
        meltSolids(level);
        if (Math.abs(temp - sentTemp) >= 15 || sentFuel != (fuel > 0) || sentFire != (heatBelow > 0) || Math.abs(sentAir - (int) air) >= 20) {
            markClient();
        }
        if (now % 40 == 0 && (d != 0 || fuel > 0)) {
            changed();
        }
        flushClient(level, 5);
    }

    private void meltSolids(ServerLevel level) {
        if (solids.isEmpty()) {
            melt = 0;
            return;
        }
        int index = -1;
        GeneratedMaterials.Melt m = null;
        for (int i = 0; i < solids.size(); i++) {
            GeneratedMaterials.Melt candidate = ForgeMetals.meltOf(solids.get(i));
            if (candidate == null) {
                //Objet qui ne fond plus (recettes rechargees) : rendu tel quel
                Block.popResource(level, worldPosition.above(), solids.remove(i));
                markClient();
                return;
            }
            if (temp >= ForgeMetals.meltingC(candidate.metal())) {
                index = i;
                m = candidate;
                break;
            }
        }
        if (m == null) {
            melt = 0;
            return;
        }
        int need = Math.max(10, MELT_TICKS * m.mb() / ForgeMetals.INGOT_MB);
        if (++melt < need) {
            return;
        }
        melt = 0;
        solids.remove(index);
        if (load.isEmpty()) {
            wear();
        }
        load.add(m.metal(), m.mb());
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6F, 0.8F + level.random.nextFloat() * 0.3F);
        BronzeEvents.award(level, worldPosition, "crucible_melt");
        changed();
        markClient();
    }

    /** Use le creuset d'une fusion. */
    private void wear() {
        ItemStack c = crucibleItem().copy();
        c.setDamageValue(Math.min(c.getMaxDamage(), c.getDamageValue() + 1));
        crucible = c;
    }

    /** Vrai si le creuset a fait toutes ses fusions (il se fend quand il est vide). */
    public static boolean worn(ItemStack crucible) {
        return crucible.isDamageableItem() && crucible.getDamageValue() >= crucible.getMaxDamage();
    }

    @Override
    public void clientTick() {
        if (level == null) {
            return;
        }
        RandomSource r = level.random;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY(), z = worldPosition.getZ() + 0.5;
        if (molten()) {
            if (r.nextFloat() < 0.12F) {
                level.addParticle(ParticleTypes.SMOKE, x + (r.nextDouble() - 0.5) * 0.3, y + 0.55, z + (r.nextDouble() - 0.5) * 0.3, 0, 0.03, 0);
            }
            if (r.nextFloat() < 0.012F) {
                level.playLocalSound(x, y + 0.4, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.4F, 0.9F + r.nextFloat() * 0.3F, false);
            }
            if (r.nextFloat() < 0.008F) {
                level.playLocalSound(x, y + 0.4, z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.25F, 1.2F, false);
            }
        }
        if (clientFire && air > 30 && r.nextFloat() < air / 600F) {
            //Le feu aspire l'air du soufflet : flammes qui jaillissent sous le pot
            level.addParticle(ParticleTypes.FLAME, x + (r.nextDouble() - 0.5) * 0.6, y - 0.4, z + (r.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
        }
        if (clientFire && fuel > 0 && r.nextFloat() < 0.05F) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.55, y + 0.05, z + (r.nextDouble() - 0.5) * 0.55, 0, 0.01, 0);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        if (level == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean client = level.isClientSide;
        if (held.getItem() instanceof CrucibleTongsItem) {
            if (!client) {
                take(player, held);
            }
            return InteractionResult.sidedSuccess(client);
        }
        //1.0.1 : notre charbon de bois (tout le tag minecraft:coals), plus l'objet vanilla remplace
        if (held.is(ItemTags.COALS)) {
            if (!client) {
                addFuel(player, held);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!held.isEmpty() && ForgeMetals.meltOf(held) != null) {
            if (!client) {
                insert(player, held);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (!client) {
                if (player.isShiftKeyDown() && !solids.isEmpty()) {
                    ItemStack back = solids.remove(solids.size() - 1);
                    if (!player.getInventory().add(back)) {
                        player.drop(back, false);
                    }
                    melt = 0;
                    changed();
                    markClient();
                } else if (player.isShiftKeyDown() && !load.isEmpty()) {
                    //1.0.3-beta (lot 7b) : sortie de secours « Vider le bain » : le contenu en lingots, pepites et scorie
                    int mb = load.total();
                    for (ItemStack s : load.spill()) {
                        if (!player.getInventory().add(s)) {
                            player.drop(s, false);
                        }
                    }
                    level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
                    player.displayClientMessage(Component.translatable("foundry.pioneers.dumped", mb).withStyle(ChatFormatting.GOLD), true);
                    changed();
                    markClient();
                } else {
                    player.displayClientMessage(status(), true);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        return InteractionResult.PASS;
    }

    private int used() {
        int mb = load.total();
        for (ItemStack s : solids) {
            GeneratedMaterials.Melt m = ForgeMetals.meltOf(s);
            mb += m == null ? 0 : m.mb();
        }
        return mb;
    }

    private void insert(Player player, ItemStack held) {
        int n = 0;
        do {
            GeneratedMaterials.Melt m = ForgeMetals.meltOf(held);
            if (m == null) {
                break;
            }
            if (!ForgeMetals.crucible(m.metal())) {
                player.displayClientMessage(Component.translatable("casting.pioneers.crucible.cannot", Metals.name(m.metal()))
                      .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (used() + m.mb() > MoltenLoad.CLAY_CRUCIBLE_MB || solids.size() >= MAX_SOLIDS) {
                if (n == 0) {
                    player.displayClientMessage(Component.translatable("casting.pioneers.crucible.full", MoltenLoad.CLAY_CRUCIBLE_MB)
                          .withStyle(ChatFormatting.YELLOW), true);
                }
                break;
            }
            solids.add(held.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            n++;
        } while (player.isShiftKeyDown() && !held.isEmpty());
        if (n > 0 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
            changed();
            markClient();
        }
    }

    private void addFuel(Player player, ItemStack held) {
        if (fuel + CHARCOAL_TICKS > MAX_FUEL) {
            player.displayClientMessage(Component.translatable("casting.pioneers.crucible.fuel_full").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        fuel += CHARCOAL_TICKS;
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
        }
        changed();
        markClient();
    }

    /** La pince prend le creuset (contenu, temperature, solides) ; le bloc disparait. */
    private void take(Player player, ItemStack tongs) {
        if (CrucibleTongsItem.hasCrucible(tongs)) {
            player.displayClientMessage(Component.translatable("casting.pioneers.tongs.full").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (level == null) {
            return;
        }
        MoltenLoad out = load.copy();
        out.setTemperature(temp, level.getGameTime());
        CrucibleTongsItem.setCrucible(tongs, crucibleForTongs(), out);
        taken = true;
        level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.8F, 0.8F);
        level.removeBlock(worldPosition, false);
    }

    /** Message d'etat (barre d'action). */
    public Component status() {
        MutableComponent c = Component.translatable("casting.pioneers.crucible.temp", Texts.grouped(Math.round(temp)))
              .withStyle(ForgeMetals.glow(temp) != 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        if (heatBelow <= 0) {
            part(c, Component.translatable("casting.pioneers.crucible.no_fire").withStyle(ChatFormatting.RED));
        } else {
            if (fuel > 0) {
                part(c, Component.translatable("casting.pioneers.crucible.charcoal", ForgeHeat.CHARCOAL_BONUS_C));
            }
            if (air >= 1) {
                part(c, Component.translatable("casting.pioneers.crucible.air", Math.round(air)));
            }
        }
        if (!load.isEmpty()) {
            Component metal = Metals.nameOrMixed(load.metal());
            part(c, molten()
                  ? Component.translatable("casting.pioneers.crucible.liquid", load.total(), metal).withStyle(ChatFormatting.GOLD)
                  : Component.translatable("casting.pioneers.crucible.solidified", load.total(), metal, Texts.grouped(load.pourC())));
            if (load.metal().isEmpty()) {
                //1.0.3-beta (lot 7b) : bain qui n'est pas exactement un alliage : ce qui coulera, ce qui restera
                part(c, Metals.planLine(load.plan(m -> 0)).copy().withStyle(ChatFormatting.YELLOW));
            }
        }
        if (!solids.isEmpty()) {
            GeneratedMaterials.Melt m = ForgeMetals.meltOf(solids.get(0));
            if (m != null) {
                int need = ForgeMetals.meltingC(m.metal());
                part(c, Component.translatable("casting.pioneers.crucible.melting", solids.size(), Metals.name(m.metal()), Texts.grouped(need)));
                if (targetC() < need) {
                    part(c, Component.translatable("casting.pioneers.crucible.needs",
                          Component.translatable("casting.pioneers.heat." + ForgeMetals.heatToMelt(m.metal()).name().toLowerCase(Locale.ROOT)))
                          .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        if (load.isEmpty() && solids.isEmpty()) {
            part(c, Component.translatable("casting.pioneers.crucible.empty"));
        }
        return c;
    }

    private static void part(MutableComponent c, Component p) {
        c.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(p);
    }

    // ------------------------------------------------------------------
    // Casse
    // ------------------------------------------------------------------

    /** Casse a la main : le creuset (s'il n'est pas use), les solides, le metal fige en lingots et pepites. */
    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        if (taken) {
            return;
        }
        for (ItemStack s : solids) {
            drop.accept(s.copy());
        }
        solids.clear();
        if (!load.isEmpty()) {
            //1.0.3-beta (lot 7b) : chaque metal du bain en lingots et pepites (un bain melange etait perdu)
            load.spill().forEach(drop);
        }
        ItemStack c = crucibleItem();
        if (!worn(c)) {
            drop.accept(c.copy());
        }
        crucible = ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("load", load.save());
        tag.put("crucible", crucible.save(new CompoundTag()));
        tag.put("solids", saveSolids());
        tag.putFloat("temp", temp);
        tag.putInt("fuel", fuel);
        tag.putFloat("air", air);
        tag.putInt("melt", melt);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        load = MoltenLoad.load(tag.getCompound("load"));
        crucible = ItemStack.of(tag.getCompound("crucible"));
        loadSolids(tag.getList("solids", Tag.TAG_COMPOUND));
        temp = tag.contains("temp") ? tag.getFloat("temp") : ForgeMetals.AMBIENT_C;
        fuel = tag.getInt("fuel");
        air = tag.getFloat("air");
        melt = tag.getInt("melt");
    }

    private ListTag saveSolids() {
        ListTag list = new ListTag();
        for (ItemStack s : solids) {
            list.add(s.save(new CompoundTag()));
        }
        return list;
    }

    private void loadSolids(ListTag list) {
        solids.clear();
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.of(list.getCompound(i));
            if (!s.isEmpty()) {
                solids.add(s);
            }
        }
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("load", load.save());
        tag.put("solids", saveSolids());
        tag.putFloat("temp", temp);
        tag.putBoolean("fuel", fuel > 0);
        tag.putBoolean("fire", heatBelow > 0);
        tag.putFloat("air", air);
        sentTemp = temp;
        sentFuel = fuel > 0;
        sentFire = heatBelow > 0;
        sentAir = (int) air;
    }

    @Override
    protected void readClient(CompoundTag tag) {
        load = MoltenLoad.load(tag.getCompound("load"));
        clientMetal = load.isEmpty() ? "" : load.pourMetal();
        clientPour = load.isEmpty() ? 0 : load.pourC();
        loadSolids(tag.getList("solids", Tag.TAG_COMPOUND));
        temp = tag.getFloat("temp");
        fuel = tag.getBoolean("fuel") ? 1 : 0;
        clientFire = tag.getBoolean("fire");
        air = tag.getFloat("air");
    }
}
