package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.ui.FarmLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Composteur. 1.6 (unification vanilla, demande de Guillaume : « virer le composteur de Minecraft, ameliorer le notre ») : il fait TOUT ce que
 * faisait le composteur vanilla, qui n'est plus fabricable ({@link ComposterTakeover} convertit ceux deja poses).
 * <ul>
 * <li>Accepte tout ce que {@link ComposterBlock#COMPOSTABLES} contient (vanilla + mod, {@link CompostRules}) avec la MEME chance qu'en vanilla
 * d'ajouter une couche (la premiere couche est sure) ; l'objet est toujours consomme.</li>
 * <li>{@link #LAYERS} couches, puis il se ferme et fermente {@link #FERMENT} ticks (fumee, bulles, couvercle qui se souleve), puis donne
 * {@link #YIELD} compost (engrais du mod, poudre d'os comprise).</li>
 * <li>Entonnoirs : dechets par le haut et les cotes, compost par le bas ; comparateur : 0..7 couches, 7 en fermentation, 8 pret (comme vanilla).</li>
 * <li>Lieu de travail des fermiers des villages (etats enregistres comme le composteur vanilla : {@link ComposterTakeover}).</li>
 * </ul>
 * Rendu : {@code client/farm/device/DeviceRenderer.compost} (couches visibles, du vert frais au brun, fermentation animee).
 */
public class CompostBinTile extends FarmDeviceTile {

    public static final int LAYERS = 7, FERMENT = 2400, YIELD = 4;
    private int layers, ferment = -1;
    /** Derniere valeur du comparateur envoyee aux voisins. */
    private int lastSignal = -1;

    public CompostBinTile(BlockPos pos, BlockState state) {
        super(pos, state, 3, 0);
    }

    /**
     * 1.0.5-beta (lot 41) : case 1 les dechets (une pile : ils entrent dans le bac un a un tant qu'il en prend), case 0 le compost mur, case 2
     * le blanc de champignon de Paris trouve dans le compost (avant : donne quand on vidait le bac a la main).
     */
    public static final int COMPOST = 0, WASTE = 1, SPORES = 2;

    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().inputs(new int[]{WASTE}, 1).arrow(false)
              .outputs(new int[]{COMPOST, SPORES}, 1, "gui.pioneers.farm.slot.compost", "gui.pioneers.farm.slot.spores").build();
    }

    @Override
    protected boolean acceptsInput(int slot, ItemStack stack) {
        return slot == WASTE && chance(stack) > 0;
    }

    @Override
    protected int guiLimit(int slot) {
        return 64;
    }

    @Override
    public float arrowProgress() {
        return ready() ? 1F : ferment >= 0 ? ferment / (float) FERMENT : layers / (float) LAYERS;
    }

    /** Couches de dechets (0..LAYERS). */
    public int layers() {
        return layers;
    }

    /** 1.0.3 (lot 13) : chance de trouver des spores de champignon de Paris en vidant le compost mur a la main. */
    public static final float SPORE_CHANCE = 0.25F;
    /** -1 : se remplit ; 0..FERMENT : fermente ; ready() : compost pret. */
    public int ferment() {
        return ferment;
    }

    public boolean ready() {
        return !items.get(0).isEmpty();
    }

    /** Chance vanilla qu'un objet ajoute une couche (0 : ne se composte pas). */
    public static float chance(ItemStack stack) {
        return stack.isEmpty() ? 0F : ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
    }

    /** Accepte-t-il un dechet maintenant ? */
    public boolean accepts(ItemStack stack) {
        return chance(stack) > 0 && ferment < 0 && !ready() && layers < LAYERS;
    }

    /** Composte UN objet (consomme meme si la couche n'est pas gagnee, comme vanilla) ; vrai si une couche s'ajoute. */
    private boolean compost(ItemStack stack) {
        float c = chance(stack);
        if (level == null || c <= 0 || !accepts(stack)) {
            return false;
        }
        boolean success = layers == 0 || level.random.nextDouble() < c;
        if (success) {
            layers++;
            if (layers >= LAYERS) {
                ferment = 0;
                sound(SoundEvents.COMPOSTER_READY, 0.8F, 0.7F);
                PiFarmDevices.award(level, worldPosition, "compost");
            }
        }
        //Son et particules du composteur vanilla (meme evenement : 1500)
        level.levelEvent(LevelEvent.COMPOSTER_FILL, worldPosition, success ? 1 : 0);
        return success;
    }

    @Override
    public void serverTick() {
        if (ferment >= 0 && level != null) {
            ferment++;
            if (ferment >= FERMENT) {
                ferment = -1;
                layers = 0;
                items.set(0, new ItemStack(PiFarmDevices.COMPOST.get(), YIELD));
                //1.0.3 (lot 13) : le compost mur porte souvent du blanc de champignon de Paris (spores aux genes sauvages) ; lot 41 : dans sa case
                if (level.random.nextFloat() < SPORE_CHANCE && fr.pioneers.farm.PiFarm.SEEDS.containsKey("button_mushroom")) {
                    ItemStack spores = fr.pioneers.farm.crop.Genes.wild(level.random).apply(new ItemStack(fr.pioneers.farm.PiFarm.SEEDS.get("button_mushroom").get()));
                    store(spores, SPORES, SPORES + 1, 64);
                }
                sound(SoundEvents.COMPOSTER_READY, 1F, 1F);
                itemsChanged();
            } else if (ferment % 200 == 0) {
                sync();
            }
        }
    }

    /** Client : angle du couvercle (degres, 0 = ferme), amorti vers sa cible ; NaN tant qu'il n'a pas ete pose. */
    private float lid = Float.NaN, lidBefore = Float.NaN;

    @Override
    public void clientTick() {
        float target = ferment >= 0 ? 0F : 100F;
        if (Float.isNaN(lid)) {
            lid = target;
        }
        lidBefore = lid;
        lid += (target - lid) * 0.12F;
    }

    /** Angle du couvercle pour le rendu (degres). */
    public float lid(float partial) {
        if (Float.isNaN(lid)) {
            return ferment >= 0 ? 0F : 100F;
        }
        return Mth.lerp(partial, Float.isNaN(lidBefore) ? lid : lidBefore, lid);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == WASTE && side != Direction.DOWN && accepts(stack);
    }

    @Override
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return (slot == COMPOST || slot == SPORES) && side != Direction.UP;
    }

    @Override
    protected int slotLimit(int slot) {
        return slot == 1 ? 1 : 64;
    }

    @Override
    protected void itemsChanged() {
        //1.0.5-beta (lot 41) : la pile de dechets entre tant que le bac en prend (comme Maj + clic droit avant) ; le reste attend dans la case
        ItemStack in = items.get(WASTE);
        while (!in.isEmpty() && accepts(in)) {
            compost(in);
            in.shrink(1);
        }
        if (in.isEmpty()) {
            items.set(WASTE, ItemStack.EMPTY);
        }
        super.itemsChanged();
    }

    /** Synchro + comparateur des voisins quand le niveau change. */
    @Override
    protected void sync() {
        super.sync();
        int signal = comparator();
        if (level != null && !level.isClientSide && signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** Signal de comparateur, comme le composteur vanilla : couches 0..7, 7 en fermentation, 8 compost pret. */
    public int comparator() {
        return ready() ? 8 : ferment >= 0 ? LAYERS : layers;
    }

    /**
     * Reprise d'un composteur vanilla (niveau 0..8) : couches (7 : la fermentation demarre) ou compost pret (8).
     */
    public void takeOver(int vanillaLevel) {
        if (vanillaLevel >= 8) {
            items.set(0, new ItemStack(PiFarmDevices.COMPOST.get(), YIELD));
            layers = 0;
            ferment = -1;
        } else {
            layers = Mth.clamp(vanillaLevel, 0, LAYERS);
            ferment = layers >= LAYERS ? 0 : -1;
        }
        sync();
    }

    @Override
    public void particles(RandomSource random) {
        if (level == null) {
            return;
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.95, z = worldPosition.getZ() + 0.5;
        if (ferment >= 0) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5,
                      0, 0.01, 0);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.MYCELIUM, x + (random.nextDouble() - 0.5) * 0.8, y + 0.1, z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
            }
        } else if (ready() && random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.COMPOSTER, x + (random.nextDouble() - 0.5) * 0.6, y - 0.1, z + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
        }
    }

    /** Couleur du tas (rendu) : 0 = frais (vert), 0..1 = fermente (brunit), 2 = compost mur. */
    public float ripeness() {
        return ready() ? 2F : ferment >= 0 ? ferment / (float) FERMENT : 0F;
    }

    @Override
    protected int accent() {
        return 0x7A5A30;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        //1.6 (fiches synthetiques) : une ligne : pret, fermente, ou se remplit (libelle court, valeur alignee)
        if (ready()) {
            card.add(FarmCard.Line.gauge(FarmCard.label("compost"), 1F, FarmCard.word("ready_n", items.get(0).getCount()).withStyle(s -> s.withColor(FarmCard.OK)),
                  0x8A6A40));
        } else if (ferment >= 0) {
            card.add(FarmCard.Line.gauge(FarmCard.label("ferment"), ferment / (float) FERMENT, FarmCard.duration((FERMENT - ferment) / 20), 0xD08040));
        } else {
            card.add(FarmCard.Line.gauge(FarmCard.label("heap"), layers / (float) LAYERS, FarmCard.ratio(layers, LAYERS), 0x7CC860));
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("layers", layers);
        tag.putInt("ferment", ferment);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        //Avant 1.6 : matiere sur 100 points
        layers = tag.contains("layers") ? tag.getInt("layers") : Mth.clamp(Math.round(tag.getInt("matter") * LAYERS / 100F), 0, LAYERS);
        ferment = tag.contains("ferment") ? tag.getInt("ferment") : -1;
    }
}
