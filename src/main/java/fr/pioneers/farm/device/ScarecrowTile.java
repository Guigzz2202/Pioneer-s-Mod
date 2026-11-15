package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.crow.Crow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tuile du bas de l'epouvantail : inscrit l'epouvantail dans {@link DeviceSites} (corbeaux, pietinement) ; fiche au regard. 1.0.5-beta (lot 41,
 * retour 60) : EXCEPTION ECRITE a la regle « interface et plus de bulle » : l'epouvantail est un decor sans contenu ni procede (sa tete se change
 * en monde comme un costume) ; sa fiche dit seulement ses portees.
 */
public class ScarecrowTile extends DeviceTile implements FarmCard.Source {

    public ScarecrowTile(BlockPos pos, BlockState state) {
        super(pos, state, 0, 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            DeviceSites.add(level, worldPosition, DeviceSites.Kind.SCARECROW);
            if (!level.isClientSide) {
                //1.4.2 : les corbeaux deja la (meme en plein repas) s'envolent des que l'epouvantail est pose
                //1.0.2-beta : ceux de la zone seulement, et jusqu'au bord de la zone (ils y roderont : Crow.EdgeGoal)
                Vec3 at = Vec3.atCenterOf(worldPosition);
                double r = ScarecrowBlock.SCARE_RADIUS;
                for (Crow crow : level.getEntitiesOfClass(Crow.class, new AABB(worldPosition).inflate(r, 10, r))) {
                    double dx = crow.getX() - at.x, dz = crow.getZ() - at.z;
                    if (dx * dx + dz * dz <= r * r) {
                        crow.shoo(at);
                    }
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            DeviceSites.remove(level, worldPosition, DeviceSites.Kind.SCARECROW);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 3, 2));
    }

    @Override
    protected int accent() {
        return 0xD8B060;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        //1.6 : portees en valeurs alignees, une seule indication courte
        card.add(FarmCard.Line.pair(FarmCard.label("crows"), Component.translatable("card.pioneers.blocks", ScarecrowBlock.SCARE_RADIUS), FarmCard.OK));
        card.add(FarmCard.Line.pair(FarmCard.label("trampling"), Component.translatable("card.pioneers.blocks", ScarecrowBlock.TRAMPLE_RADIUS), 0xB0B0B0));
        card.add(FarmCard.Line.hint(FarmCard.word("hint.scarecrow")));
    }
}
