package fr.pioneers.tech.kinetics.rope;

import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticEvents;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.logistics.warehouse.LineNode;
import fr.pioneers.logistics.warehouse.WarehouseLines;
import fr.pioneers.logistics.warehouse.WarehouseNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Poulie du mod : des cordes vers d'autres poulies (rapport des diametres). 1.0.4-beta (lot 2) : une poulie a AXE VERTICAL est un noeud de
 * ligne d'entrepot ({@link LineNode}). Rapport d'une corde = rayon primitif ici / rayon la-bas ({@link #pitchPx} : 4 px par taille).
 * <p>
 * 1.0.4-beta (lot 27, Guillaume : « On doit pouvoir connecter plus d'une corde a une poulie a condition que le systeme tourne dans le meme
 * sens » ; « Supprime la poulie d'entrepot. Les poulies de bases sont suffisantes ») :
 * <ul>
 *     <li>jusqu'a {@link #MAX_ROPES} cordes par poulie, quel que soit l'axe (petite, normale, grande) : chaque corde est un lien cinetique
 *     (vitesse partagee par le reseau, rendement de chaque corde multiplie : la charge se cumule) ; une nouvelle corde est REFUSEE si elle
 *     ferait tourner les systemes relies en sens contraire ou si elle bouclait un reseau a un autre rapport ({@link RopeItem#directionProblem}) ;
 *     </li>
 *     <li>deux gorges, cordes rangees en alternance : basse {@link #LOW_ROPE} / haute {@link #HIGH_ROPE} (le long de l'axe ; une corde seule
 *     au milieu) ;</li>
 *     <li>la PETITE poulie a axe vertical est aussi la STATION d'une trappe ou d'un comptoir pose dessous (jusqu'a 8 blocs de vide) : elle
 *     remplace la poulie d'entrepot, dont les blocs poses deviennent une petite poulie avec leurs cordes (lecture de l'ancien format
 *     {@code links}).</li>
 * </ul>
 * Sauvegarde : la 1re corde garde les cles d'avant (partner, link, crossed, master, slip, wear), les suivantes les memes suffixees de 2, 3, 4.
 */
public class PulleyTile extends KineticTile implements LineNode {

    /** Cordes au plus par poulie (une jonction de lignes d'entrepot en croix). */
    public static final int MAX_ROPES = 4;

    /** Une corde de la poulie. */
    private static final class Rope {
        BlockPos to;
        LinkType type = LinkType.FIBER_ROPE;
        boolean crossed, master, slipping, sentSlip;
        /** 1.0.4-beta (lot 27) : corde reprise d'une poulie d'entrepot : qui la dessine se decide au premier tick (l'autre bout charge). */
        boolean legacy;
        /** Ticks de patinage restants avant rupture. */
        double wear;
    }

    private final Rope[] ropes = new Rope[MAX_ROPES];
    /** 1.0.3-beta (lot 11), client : dernier angle dessine et defilement cumule de la corde (px, modulo 16). */
    private float ropeAngle = Float.NaN;
    private double ropeTravel;

    public PulleyTile(BlockPos pos, BlockState state) {
        super(PiKinetics.PULLEY_TILE.get(), pos, state);
        for (int i = 0; i < MAX_ROPES; i++) {
            ropes[i] = new Rope();
        }
    }

    public int size() {
        return getBlockState().getBlock() instanceof PulleyBlock pulley ? pulley.size : 1;
    }

    public Direction.Axis axis() {
        return AxisKineticBlock.axis(getBlockState());
    }

    /** Nombre de cordes possibles ({@link #MAX_ROPES}, tous les axes : 1.0.4-beta lot 27). */
    public int capacity() {
        return MAX_ROPES;
    }

    /** Nombre de cordes posees (elles occupent les premieres gorges). */
    public int ropeCount() {
        int n = 0;
        for (Rope r : ropes) {
            if (r.to != null) {
                n++;
            }
        }
        return n;
    }

    /** Encore une place pour une corde ? */
    public boolean hasRoom() {
        return ropeCount() < capacity();
    }

    /** Premiere corde : partenaire, ou null (aucune corde). */
    @Nullable
    public BlockPos partner() {
        return ropes[0].to;
    }

    /** Partenaire de la corde {@code i} (0..{@link #MAX_ROPES} - 1), ou null. */
    @Nullable
    public BlockPos partner(int i) {
        return i >= 0 && i < MAX_ROPES ? ropes[i].to : null;
    }

    public LinkType link() {
        return ropes[0].type;
    }

    public LinkType link(int i) {
        return ropes[i].type;
    }

    public boolean crossed() {
        return ropes[0].crossed;
    }

    public boolean crossed(int i) {
        return ropes[i].crossed;
    }

    /** Cette poulie dessine la premiere corde (une seule des deux). */
    public boolean master() {
        return ropes[0].master;
    }

    public boolean master(int i) {
        return ropes[i].master;
    }

    /** Rang de la corde vers {@code other}, ou -1. */
    public int indexOf(BlockPos other) {
        for (int i = 0; i < MAX_ROPES; i++) {
            if (other.equals(ropes[i].to)) {
                return i;
            }
        }
        return -1;
    }

    /** Cette poulie dessine-t-elle la corde vers {@code other} ? */
    public boolean masterTo(BlockPos other) {
        int i = indexOf(other);
        return i >= 0 && ropes[i].master;
    }

    public boolean slipping() {
        for (Rope r : ropes) {
            if (r.slipping) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1.0.3-beta (lot 11), client : defilement de la corde (px, 0..16) pour l'angle d'affichage {@code angle} (degres, modulo 360) de cette
     * poulie : on cumule l'ecart d'angle depuis la derniere image (ramene dans -180..180) en longueur d'arc de la gorge de rayon
     * {@code ropeRadius} px : la corde avance exactement comme la jante, sans saut quand l'angle repasse par 0.
     */
    public float ropeTravel(float angle, double ropeRadius) {
        if (Float.isNaN(ropeAngle)) {
            ropeTravel = RopeMotion.travel(angle, ropeRadius);
        } else {
            float da = angle - ropeAngle;
            da -= 360F * Math.round(da / 360F);
            ropeTravel += RopeMotion.travel(da, ropeRadius);
        }
        ropeAngle = angle;
        ropeTravel = ((ropeTravel % 16) + 16) % 16;
        return (float) ropeTravel;
    }

    // ------------------------------------------------------------------ noeud de ligne d'entrepot (lots 2 et 27)

    @Override
    public boolean lineNode() {
        return axis() == Direction.Axis.Y;
    }

    /**
     * 1.0.4-beta (lot 34) : une roue de ligne qui arrive (chunk charge, poulie posee) peut completer le reseau d'un comptoir deja calcule
     * (avant : le comptoir charge avant la caisse gardait un reseau sans elle jusqu'au prochain changement de corde).
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && lineNode()) {
            WarehouseNetwork.changed();
        }
    }

    /** 1.0.4-beta (lot 27) : seule la PETITE poulie a axe vertical dessert une station (sa gorge passe au-dessus de l'ouverture). */
    @Override
    public boolean stationWheel() {
        return lineNode() && size() == 1;
    }

    /** Rayon primitif (px) : celui du dessin de la corde, proportionnel a la taille (le rapport des diametres reste celui d'avant). */
    @Override
    public float pitchPx() {
        return size() * 4F;
    }

    /**
     * Hauteurs (px, le long de l'axe) des deux gorges : les cordes s'y rangent en alternance (1re, 3e : basse ; 2e, 4e : haute) et se croisent
     * l'une au-dessus de l'autre sans se toucher (1,25 px d'ecart pour 1,2 px de corde) ; la plus haute laisse passer l'etrier des colis sous
     * la joue basse. Une corde seule : au milieu (8 px).
     */
    public static final float LOW_ROPE = 7.15F, HIGH_ROPE = 8.4F;
    /**
     * 1.0.4-beta (lot 27) : la petite poulie a axe vertical (roue des stations) est montee {@code STATION_LIFT} px plus haut dans son bloc :
     * ses colis pendent tout entiers dans son bloc, sans entrer dans un comptoir ou une caisse pose juste dessous.
     * <p>1.0.4-beta (lot 34, Guillaume : « mon comptoir ne se connecte pas au reseau ») : 2 px ne suffisaient pas : corde a 10 px, colis
     * jusqu'a 8,9 px dessous, et la fleche de la corde (~2 px par bloc depuis la roue) le faisait toucher le BORD DU DESSUS de la caisse
     * dont la poulie est posee sur la trappe : toute corde vers une caisse etait refusee par le degagement des colis (reseau sans caisse).
     * 4 px : corde a 12 px (11,15 / 12,4 a deux cordes), roue de 9 a 15 px (reste dans son bloc), le colis passe au-dessus d'une caisse
     * 3 x 3 vue de n'importe quel cote (marge 0,5 px au bord, fleche comprise).
     */
    public static final float STATION_LIFT = 4.0F;

    /** Decalage (px) de la roue et de ses gorges le long de l'axe : {@link #STATION_LIFT} pour la petite poulie a axe vertical, 0 sinon. */
    public float wheelLift() {
        return stationWheel() ? STATION_LIFT : 0F;
    }

    @Override
    public float ropeYPx() {
        return (ropeCount() > 1 ? LOW_ROPE : 8F) + wheelLift();
    }

    /** Hauteur de la gorge de la corde {@code i}. */
    public float ropeYPx(int i) {
        if (ropeCount() < 2) {
            return 8F + wheelLift();
        }
        return (i % 2 == 0 ? LOW_ROPE : HIGH_ROPE) + wheelLift();
    }

    @Override
    public float ropeYPx(BlockPos other) {
        int i = indexOf(other);
        return ropeYPx(Math.max(0, i));
    }

    @Override
    public List<BlockPos> lineLinks() {
        List<BlockPos> out = new ArrayList<>(MAX_ROPES);
        for (Rope r : ropes) {
            if (r.to != null && !r.crossed) {
                out.add(r.to);
            }
        }
        return out;
    }

    @Override
    public boolean linkedTo(BlockPos other) {
        return indexOf(other) >= 0;
    }

    @Override
    public LinkType lineType(BlockPos other) {
        int i = indexOf(other);
        return i >= 0 ? ropes[i].type : LinkType.FIBER_ROPE;
    }

    @Override
    public double lineSpeed() {
        return speed;
    }

    /**
     * 1.0.4-beta (lot 27) : ce que dessert la poulie (petite, axe vertical) : la trappe d'une caisse ou un comptoir pose dessous ou plus bas,
     * colonne vide jusqu'a {@link WarehouseLines#MAX_DROP} blocs ; sinon null.
     */
    @Nullable
    public Object station() {
        if (!stationWheel()) {
            return null;
        }
        WarehouseLines.Station st = WarehouseLines.station(level, worldPosition);
        return st == null ? null : st.tile();
    }

    // ------------------------------------------------------------------ cordes

    /** Pose une corde vers {@code other} dans la premiere gorge libre ({@code isMaster} : cette poulie la dessine et la surveille). */
    public void connect(BlockPos other, LinkType type, boolean cross, boolean isMaster) {
        if (linkedTo(other)) {
            return;
        }
        for (Rope r : ropes) {
            if (r.to == null) {
                r.to = other.immutable();
                r.type = type;
                r.crossed = cross;
                r.master = isMaster;
                r.legacy = false;
                r.wear = type.life;
                r.slipping = false;
                changedRopes();
                return;
            }
        }
    }

    private void changedRopes() {
        ropeBox = null;
        markClientsDirty();
        invalidateNetwork();
        setChanged();
        if (lineNode()) {
            WarehouseNetwork.changed();
        }
    }

    /** Decroche toutes les cordes ; {@code drop} : chaque corde tombe en objet a mi-chemin. */
    public void unlink(boolean drop) {
        for (int i = MAX_ROPES - 1; i >= 0; i--) {
            if (ropes[i].to != null) {
                unlink(ropes[i].to, drop);
            }
        }
    }

    /** Decroche la corde vers {@code other} des deux poulies ; {@code drop} : la corde tombe en objet a mi-chemin. */
    public void unlink(BlockPos other, boolean drop) {
        if (level == null) {
            return;
        }
        int i = indexOf(other);
        if (i < 0) {
            return;
        }
        LinkType type = ropes[i].type;
        forget(i);
        if (level.getBlockEntity(other) instanceof PulleyTile tile) {
            tile.forgetTo(worldPosition);
        }
        WarehouseNetwork.changed();
        if (drop && !level.isClientSide) {
            Vec3 mid = Vec3.atCenterOf(worldPosition).add(Vec3.atCenterOf(other)).scale(0.5);
            Block.popResource(level, BlockPos.containing(mid), RopeItem.stack(type));
        }
    }

    /** Oublie la corde vers {@code other} sans prevenir l'autre bout (appele par l'autre bout). */
    public void forgetTo(BlockPos other) {
        int i = indexOf(other);
        if (i >= 0) {
            forget(i);
        }
    }

    /** Retire la corde {@code i} ; les suivantes remontent d'une gorge (partner() == null veut toujours dire « aucune corde »). */
    private void forget(int i) {
        Rope gone = ropes[i];
        for (int k = i; k < MAX_ROPES - 1; k++) {
            ropes[k] = ropes[k + 1];
        }
        gone.to = null;
        gone.slipping = false;
        gone.legacy = false;
        ropes[MAX_ROPES - 1] = gone;
        changedRopes();
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == axis() ? 1 : 0;
    }

    @Override
    public void collectLinks(LinkSink sink) {
        shaftLinks(sink);
        if (level == null) {
            return;
        }
        for (Rope r : ropes) {
            if (r.to == null || !level.isLoaded(r.to)) {
                continue;
            }
            if (level.getBlockEntity(r.to) instanceof PulleyTile other && other.linkedTo(worldPosition)) {
                //Meme vitesse lineaire de la corde : w_autre = w_ici x d_ici / d_autre
                sink.link(r.to, (r.crossed ? -1 : 1) * size() / (double) other.size());
            }
        }
    }

    @Override
    public double efficiency() {
        double e = 1;
        for (Rope r : ropes) {
            if (r.to != null && r.master) {
                e *= r.slipping ? r.type.efficiency * 0.7 : r.type.efficiency;
            }
        }
        return e;
    }

    @Override
    public double maxTorque() {
        return 1e9;                                              //Une corde patine au lieu de casser (voir serverTick)
    }

    @Override
    public double maxSpeed() {
        return 400;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        for (Rope r : ropes) {
            if (r.to == null) {
                r.slipping = false;
                continue;
            }
            if (r.legacy && level.isLoaded(r.to)) {
                settleLegacy(level, r);
            }
            if (!r.master) {
                r.slipping = false;
                continue;
            }
            //Couple transmis par la corde : celui du reseau ramene a cette poulie (diametre compris)
            double limit = r.type.maxTorque * size();
            r.slipping = torque > limit && Math.abs(speed) > 0.5;
            if (r.slipping) {
                r.wear -= Math.min(5, torque / limit);
                if (level.getGameTime() % 10 == 0) {
                    Vec3 mid = Vec3.atCenterOf(worldPosition).add(Vec3.atCenterOf(r.to)).scale(0.5);
                    level.sendParticles(ParticleTypes.SMOKE, mid.x, mid.y, mid.z, 3, 0.3, 0.1, 0.3, 0.01);
                    level.playSound(null, worldPosition, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.5F, 0.6F);
                }
                if (r.wear <= 0) {
                    level.playSound(null, worldPosition, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1F, 0.7F);
                    KineticEvents.award(level, worldPosition, "rope_snapped");
                    unlink(r.to, false);
                    return;
                }
            } else if (r.wear < r.type.life) {
                r.wear = Math.min(r.type.life, r.wear + 0.05);
            }
        }
        if (ropes[0].to != null && Math.abs(speed) > 1 && level.getGameTime() % 200 == 0) {
            KineticEvents.award(level, worldPosition, "rope_drive");
        }
    }

    /**
     * 1.0.4-beta (lot 27) : corde reprise d'une poulie d'entrepot remappee : la poulie d'entrepot dessinait toujours une corde vers une poulie
     * de base, et entre deux poulies d'entrepot la plus petite position la dessinait. Une seule des deux poulies la dessine maintenant.
     */
    private void settleLegacy(ServerLevel level, Rope r) {
        r.legacy = false;
        boolean draw = true;
        if (level.getBlockEntity(r.to) instanceof PulleyTile other) {
            int j = other.indexOf(worldPosition);
            if (j >= 0) {
                Rope o = other.ropes[j];
                draw = o.legacy ? worldPosition.asLong() < r.to.asLong() : !o.master;
                if (o.legacy) {
                    o.legacy = false;
                    o.master = !draw;
                    other.changedRopes();
                }
            }
        }
        r.master = draw;
        changedRopes();
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        for (Rope r : ropes) {
            if (r.to != null) {
                lines.add(Component.translatable("kinetics.pioneers.info.rope", Component.translatable("item.pioneers." + r.type.key()),
                      Math.round(r.type.maxTorque * size()), Math.round(Math.max(0, r.wear) * 100 / r.type.life))
                      .withStyle(r.slipping ? ChatFormatting.RED : ChatFormatting.AQUA));
            }
        }
        lines.add(Component.translatable("kinetics.pioneers.info.ropes", ropeCount(), capacity()).withStyle(ChatFormatting.GRAY));
        //1.0.4-beta (lots 2 et 27) : role dans l'entrepot (relais, station d'une petite poulie)
        if (lineNode() && level != null) {
            WarehouseLines.Station st = stationWheel() ? WarehouseLines.station(level, worldPosition) : null;
            if (st != null) {
                lines.add((st.gap() == 0 ? Component.translatable(st.counter() ? "warehouse.pioneers.pulley.station_counter" : "warehouse.pioneers.pulley.station_crate")
                      : Component.translatable(st.counter() ? "warehouse.pioneers.pulley.drop_counter" : "warehouse.pioneers.pulley.drop_crate", st.gap()))
                      .withStyle(ChatFormatting.GOLD));
            } else {
                lines.add(Component.translatable("warehouse.pioneers.base.relay").withStyle(ChatFormatting.GRAY));
                Component why = WarehouseLines.relayProblem(level, worldPosition);
                if (why != null) {
                    lines.add(why.copy().withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    @Override
    protected double renderRadius() {
        return 2;
    }

    // 0.20.46 (audit) : boite des cordes gardee tant que les partenaires ne changent pas (demandee a chaque image)
    @Nullable
    private AABB ropeBox;

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        if (ropeBox == null) {
            AABB box = super.getRenderBoundingBox();
            for (Rope r : ropes) {
                if (r.to != null && r.master) {
                    box = box.minmax(new AABB(worldPosition, r.to).inflate(1.5));
                }
            }
            ropeBox = box;
        }
        return ropeBox;
    }

    @Override
    protected boolean clientDirty() {
        boolean dirty = false;
        for (Rope r : ropes) {
            dirty |= r.slipping != r.sentSlip;
            r.sentSlip = r.slipping;
        }
        return dirty;
    }

    /**
     * 1.0.4-beta (lot 27, audit de fluidite) : les cordes ne changent qu'a la pose, au decrochage et au patinage, chacun envoye a part
     * ({@link #markClientsDirty}, {@link #clientDirty}) : pas de paquet de bloc a chaque synchronisation de la rotation du reseau.
     */
    @Override
    protected boolean hasOwnClientData() {
        return false;
    }

    private static String suffix(int i) {
        return i == 0 ? "" : Integer.toString(i + 1);
    }

    private static void write(CompoundTag tag, Rope r, String s) {
        if (r.to != null) {
            tag.putLong("partner" + s, r.to.asLong());
        }
        tag.putInt("link" + s, r.type.ordinal());
        tag.putBoolean("crossed" + s, r.crossed);
        tag.putBoolean("master" + s, r.master);
        tag.putBoolean("slip" + s, r.slipping);
    }

    private static void read(CompoundTag tag, Rope r, String s) {
        r.to = tag.contains("partner" + s) ? BlockPos.of(tag.getLong("partner" + s)) : null;
        r.type = LinkType.byOrdinal(tag.getInt("link" + s));
        r.crossed = tag.getBoolean("crossed" + s);
        r.master = tag.getBoolean("master" + s);
        r.slipping = tag.getBoolean("slip" + s);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        for (int i = 0; i < MAX_ROPES; i++) {
            if (i == 0 || ropes[i].to != null) {
                write(tag, ropes[i], suffix(i));
            }
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        for (int i = 0; i < MAX_ROPES; i++) {
            read(tag, ropes[i], suffix(i));
        }
        ropeBox = null;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeClient(tag);
        for (int i = 0; i < MAX_ROPES; i++) {
            if (i == 0 || ropes[i].to != null) {
                tag.putDouble("wear" + suffix(i), ropes[i].wear);
            }
            if (ropes[i].legacy) {
                tag.putBoolean("legacy" + suffix(i), true);
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("links", Tag.TAG_LIST) && !tag.contains("partner")) {
            //1.0.4-beta (lot 27) : ancienne poulie d'entrepot (tile remappee) : ses cordes « links » [{p, t}] deviennent celles de la poulie
            ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
            for (int i = 0; i < MAX_ROPES; i++) {
                Rope r = ropes[i];
                r.to = i < list.size() ? BlockPos.of(list.getCompound(i).getLong("p")) : null;
                r.type = i < list.size() ? LinkType.byOrdinal(list.getCompound(i).getInt("t")) : LinkType.FIBER_ROPE;
                r.crossed = false;
                r.master = r.to != null;
                r.legacy = r.to != null;
                r.slipping = false;
                r.wear = r.type.life;
            }
            ropeBox = null;
            return;
        }
        readClient(tag);
        for (int i = 0; i < MAX_ROPES; i++) {
            String s = suffix(i);
            ropes[i].wear = tag.contains("wear" + s) ? tag.getDouble("wear" + s) : ropes[i].type.life;
            ropes[i].legacy = tag.getBoolean("legacy" + s);
        }
    }
}
