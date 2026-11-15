package fr.pioneers.logistics.warehouse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.tech.kinetics.client.WarehouseVisuals;
import fr.pioneers.core.RenderClock;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.Parcel;
import fr.pioneers.logistics.warehouse.ParcelRoute;
import fr.pioneers.logistics.warehouse.LineNode;
import fr.pioneers.logistics.warehouse.WarehouseLines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 1) : rendu des COLIS d'un comptoir d'entrepot (vues client extrapolees : la verite reste au serveur). Chaque colis est
 * place sur sa course ({@link ParcelRoute}, meme geometrie qu'au serveur), tourne dans le sens de la corde, balance doucement (plus fort a
 * l'arret et au depart, penche vers l'exterieur dans les tours de roue), descend au bout de son filin aux stations, s'ouvre au comptoir et
 * laisse monter l'objet qui passe dans les cases. Lumiere lue a la place du colis.
 */
public class CounterRenderer implements BlockEntityRenderer<CounterTile> {

    /**
     * Ou se pose le colis (px sous le dessus de la station) : au comptoir, sur le plateau (0) ; a une caisse, sous le plancher du puits de la
     * trappe (21,4). 1.0.4-beta (lot 27) : la descente se compte depuis la vraie corde de la station (petite poulie, roue montee de 2 px).
     * 1.0.5-beta (lot 13, retour de Guillaume : « bug de texture quand le colis descend dans le comptoir ») : le colis se posait SUR le
     * plateau (fond coplanaire au dessus du comptoir : scintillement ; sangles et coins d'acier 0,05 a 0,12 px DANS le bois) : il descend
     * maintenant DANS le bac (fond a 14,7 px, elargi a 6,8 x 6,6 px par T:warehouse/warehouse.py) : fond de la caisse a 15 px, sangles
     * (gonflees par KDraw) a 14,75 px au plus bas, rien dans le plan d'une face du comptoir (controle tools/warehouse_bench/landing.py).
     */
    public static final float LAND_COUNTER = 1.0F, LAND_CRATE = 21.4F;
    /** Fond du bac du comptoir (px, repere du bloc) : rien du colis ne descend plus bas (controle {@code tools/check_p105_13.py}). */
    public static final float TRAY_FLOOR = 14.7F;
    /** Bas du colis sous la corde (px) : celui du dessin ({@link WarehouseVisuals#ROPE_Y} - {@link WarehouseVisuals#PARCEL_Y0}). */
    private static final float HANG = WarehouseVisuals.ROPE_Y - WarehouseVisuals.PARCEL_Y0;
    private final double[] pt = new double[7];

    public CounterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull CounterTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        CounterClient.seen(tile);
        BlockPos origin = tile.getBlockPos();
        float time = (float) RenderClock.time(partial);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        for (Parcel p : tile.counter().parcels()) {
            if (p.route.size() < 2 && p.route.isEmpty()) {
                continue;
            }
            draw(level, origin, p, partial, time, pose, buffers, overlay);
        }
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * Descente (px) sous l'etrier a la station de la roue {@code station}, la corde etant a {@code rope} (blocs, hauteur absolue) : jusqu'au
     * plateau du comptoir ou dans le puits de la trappe, plus 16 px par bloc de vide entre la roue et la station (lot 2 : descente au filin).
     */
    private static float depth(Level level, BlockPos station, double rope) {
        WarehouseLines.Station st = WarehouseLines.station(level, station);
        float above = (float) ((rope - station.getY()) * 16) - HANG;
        if (st == null) {
            return Math.max(0, above);
        }
        return Math.max(0, above + 16F * st.gap() + (st.counter() ? LAND_COUNTER : LAND_CRATE));
    }

    private void draw(Level level, BlockPos origin, Parcel p, float partial, float time, PoseStack pose, MultiBufferSource buffers, int overlay) {
        BlockPos start = p.startStation(), end = p.endStation();
        int idx = 0;
        ParcelRoute known = p.geometryOrNull();
        if (p.phase == Parcel.Phase.TRAVEL && known != null) {
            idx = known.pulleyAt(p.clientS);
        } else if (p.phase != Parcel.Phase.RISE) {
            idx = p.route.size() - 1;
        }
        LineNode wheel = LineNode.at(level, p.route.get(Math.max(0, Math.min(p.route.size() - 1, idx))));
        int spin = wheel == null || wheel.spin() == 0 ? known != null ? known.spin() : 1 : wheel.spin();
        boolean moving = wheel != null && wheel.spin() != 0;
        ParcelRoute geo = p.geometry(level, spin);
        double s = switch (p.phase) {
            case RISE -> 0;
            case TRAVEL -> Math.min(geo.length(), p.clientS + (moving ? wheel.ropeSpeed() * partial : 0));
            default -> geo.length();
        };
        double speed = moving ? wheel.ropeSpeed() : 0;
        double[] place = WarehouseVisuals.place(geo, s, spin, speed, time, p.id, p.phase == Parcel.Phase.TRAVEL, p.phase == Parcel.Phase.OPEN, pt);
        float drop = 0, lid = 0;
        float step = moving ? partial : 0;
        switch (p.phase) {
            case RISE -> drop = depth(level, start, pt[6]) * (1 - ease((p.clientTimer + step) / Parcel.riseTicks(WarehouseLines.gap(level, start))));
            case LOWER -> drop = depth(level, end, pt[6]) * ease((p.clientTimer + step) / Parcel.lowerTicks(WarehouseLines.gap(level, end)));
            case OPEN -> {
                drop = depth(level, end, pt[6]);
                float t = (p.clientTimer + partial) / Parcel.OPEN_TICKS;
                lid = ease(Math.min(1, t * 2.2F));
            }
            default -> {
            }
        }
        double wx = place[0], wz = place[1];
        float yaw = (float) place[2], swing = (float) place[3], lean = (float) place[4];
        //Lot 13 : le balancement s'eteint pendant la pose (le colis entre droit dans le bac du comptoir)
        if (p.phase == Parcel.Phase.LOWER) {
            swing *= 1 - ease((p.clientTimer + step) / Parcel.lowerTicks(WarehouseLines.gap(level, end)));
        } else if (p.phase == Parcel.Phase.OPEN) {
            swing = 0;
        }
        //Lot 2 : hauteur de la corde au point (pente entre deux gorges de hauteurs differentes) ; le repere du colis met la gorge a ROPE_Y
        double y = pt[6] - WarehouseVisuals.ROPE_Y / 16.0;
        //Fleche de la corde : le colis suit la corde qui pend entre deux roues
        float sag = 0;
        if (pt[4] >= 0) {
            int seg = (int) pt[4];
            LineNode from = LineNode.at(level, p.route.get(seg));
            fr.pioneers.tech.kinetics.rope.LinkType type = from == null ? fr.pioneers.tech.kinetics.rope.LinkType.FIBER_ROPE : from.lineType(p.route.get(seg + 1));
            sag = WarehouseVisuals.sag(geo.span(seg), pt[5], type);
        }
        //Lot 13 : lumiere au milieu de la caisse ; dans un bloc plein (caisse, comptoir), celle du bloc au-dessus (sinon 0 : colis noir)
        BlockPos at = BlockPos.containing(wx, y + (WarehouseVisuals.PARCEL_Y0 + WarehouseVisuals.PARCEL_Y1) / 32.0 - drop / 16.0, wz);
        if (level.getBlockState(at).isSolidRender(level, at)) {
            at = at.above();
        }
        int light = LevelRenderer.getLightColor(level, at);
        pose.pushPose();
        pose.translate(wx - origin.getX() - 0.5, y - origin.getY() - sag / 16.0, wz - origin.getZ() - 0.5);
        pose.translate(0.5, WarehouseVisuals.ROPE_Y / 16.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(swing));
        pose.mulPose(Axis.ZP.rotationDegrees(lean));
        pose.translate(-0.5, -WarehouseVisuals.ROPE_Y / 16.0, -0.5);
        KDraw d = new KDraw(pose, buffers, light, overlay);
        WarehouseVisuals.parcel(d, drop, lid, !p.empty());
        if (p.phase == Parcel.Phase.OPEN && !p.empty()) {
            //L'objet monte hors du colis puis file dans le comptoir
            float t = Math.max(0, Math.min(1, (p.clientTimer + partial) / Parcel.OPEN_TICKS * 1.6F - 0.45F));
            float rise = t < 0.6F ? ease(t / 0.6F) * 5F : 5F - ease((t - 0.6F) / 0.4F) * 9F;
            float scale = t < 0.75F ? 0.34F : 0.34F * (1 - ease((t - 0.75F) / 0.25F));
            if (scale > 0.01F) {
                pose.pushPose();
                pose.translate(0.5, (WarehouseVisuals.PARCEL_Y1 - drop + 1.5F + rise) / 16.0, 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(time * 6F));
                pose.scale(scale, scale, scale);
                Minecraft.getInstance().getItemRenderer().renderStatic(p.proto, ItemDisplayContext.FIXED, light, overlay, pose, buffers, level, p.id);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull CounterTile tile) {
        return !tile.counter().parcels().isEmpty();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRender(@NotNull CounterTile tile, @NotNull Vec3 camera) {
        return true;
    }
}
