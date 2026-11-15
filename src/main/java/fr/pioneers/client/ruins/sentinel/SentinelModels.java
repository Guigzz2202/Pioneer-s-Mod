package fr.pioneers.client.ruins.sentinel;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 1.9 (MAJ RP, bloc 1) : maquettes des cinq Sentinelles (Gardien, Patrouilleur, Tourelle, Reparateur, Contremaitre).
 * FICHIER GENERE par tools/gc/ruins/sentinel_art.py : la meme description produit ce code et les textures entity/sentinel/*.png
 * (ne pas modifier a la main, relancer le generateur).
 */
public final class SentinelModels {

    private SentinelModels() {
    }

    /** Couche du role ({@code guardian}, {@code patroller}, {@code turret}, {@code repairer}, {@code foreman}). */
    public static LayerDefinition create(String role) {
        return switch (role) {
            case "guardian" -> guardian();
            case "patroller" -> patroller();
            case "turret" -> turret();
            case "repairer" -> repairer();
            case "foreman" -> foreman();
            default -> throw new IllegalArgumentException(role);
        };
    }

    private static LayerDefinition guardian() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-2.5F, 0F, -2.5F, 5F, 6F, 5F)
              .texOffs(20, 0).addBox(-3F, 5F, -3F, 6F, 6F, 6F)
              .texOffs(44, 0).addBox(-3.5F, 11F, -4.5F, 7F, 2F, 8F),
              PartPose.offset(-4F, 11F, 0F)));
        parts.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create()
              .texOffs(74, 0).addBox(-2.5F, 0F, -2.5F, 5F, 6F, 5F)
              .texOffs(94, 0).addBox(-3F, 5F, -3F, 6F, 6F, 6F)
              .texOffs(0, 12).addBox(-3.5F, 11F, -4.5F, 7F, 2F, 8F),
              PartPose.offset(4F, 11F, 0F)));
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create()
              .texOffs(30, 12).addBox(-5F, -3F, -3F, 10F, 3F, 6F)
              .texOffs(62, 12).addBox(-4F, -7F, -2.5F, 8F, 4F, 5F)
              .texOffs(0, 22).addBox(-7F, -18F, -4.5F, 14F, 11F, 9F)
              .texOffs(46, 22).addBox(-6F, -16F, -5.5F, 12F, 7F, 1F)
              .texOffs(72, 22).addBox(-1.5F, -14F, -6F, 3F, 3F, 1F)
              .texOffs(80, 22).addBox(-5F, -17F, 4.5F, 10F, 8F, 3F),
              PartPose.offset(0F, 11F, 0F)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create()
              .texOffs(0, 42).addBox(-3F, -5F, -3F, 6F, 5F, 6F)
              .texOffs(24, 42).addBox(-2.5F, -3F, -3.5F, 5F, 2F, 1F)
              .texOffs(36, 42).addBox(-4F, -1F, -2F, 8F, 2F, 6F),
              PartPose.offset(0F, -18F, -1F)));
        parts.put("right_arm", parts.get("body").addOrReplaceChild("right_arm", CubeListBuilder.create()
              .texOffs(64, 42).addBox(-3F, -3F, -3.5F, 6F, 5F, 7F)
              .texOffs(90, 42).addBox(-2F, 2F, -2F, 4F, 7F, 4F)
              .texOffs(106, 42).addBox(-2.5F, 9F, -3F, 5F, 8F, 6F)
              .texOffs(0, 56).addBox(-3F, 17F, -3F, 6F, 3F, 6F)
              .texOffs(24, 56).addBox(-0.5F, -2F, -4F, 1F, 1F, 1F),
              PartPose.offset(-8.5F, -16F, 0F)));
        parts.put("left_arm", parts.get("body").addOrReplaceChild("left_arm", CubeListBuilder.create()
              .texOffs(28, 56).addBox(-3F, -3F, -3.5F, 6F, 5F, 7F)
              .texOffs(54, 56).addBox(-2F, 2F, -2F, 4F, 7F, 4F)
              .texOffs(70, 56).addBox(-2.5F, 9F, -3F, 5F, 8F, 6F)
              .texOffs(92, 56).addBox(-3F, 17F, -3F, 6F, 3F, 6F)
              .texOffs(116, 56).addBox(-0.5F, -2F, -4F, 1F, 1F, 1F),
              PartPose.offset(8.5F, -16F, 0F)));
        parts.put("shield", parts.get("left_arm").addOrReplaceChild("shield", CubeListBuilder.create()
              .texOffs(0, 70).addBox(-4F, 6F, -5F, 8F, 13F, 1F),
              PartPose.offset(0F, 0F, 0F)));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static LayerDefinition patroller() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-3F, -2F, -3F, 6F, 4F, 6F)
              .texOffs(24, 0).addBox(-2F, -3F, -2F, 4F, 1F, 4F)
              .texOffs(40, 0).addBox(-1.5F, -1F, -3.5F, 3F, 2F, 1F)
              .texOffs(0, 10).addBox(-2.5F, 2F, -2.5F, 5F, 1F, 5F)
              .texOffs(20, 10).addBox(0.5F, -5F, 0.5F, 1F, 2F, 1F)
              .texOffs(24, 10).addBox(0.5F, -6F, 0.5F, 1F, 1F, 1F)
              .texOffs(28, 10).addBox(3F, -0.5F, -0.5F, 2F, 1F, 1F)
              .texOffs(34, 10).addBox(-5F, -0.5F, -0.5F, 2F, 1F, 1F)
              .texOffs(40, 10).addBox(-0.5F, -0.5F, 3F, 1F, 1F, 2F)
              .texOffs(46, 10).addBox(-0.5F, -0.5F, -5F, 1F, 1F, 2F),
              PartPose.offset(0F, 19F, 0F)));
        parts.put("hub0", parts.get("body").addOrReplaceChild("hub0", CubeListBuilder.create()
              .texOffs(52, 10).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
              PartPose.offset(6F, 0F, 0F)));
        parts.put("rotor0", parts.get("body").addOrReplaceChild("rotor0", CubeListBuilder.create()
              .texOffs(0, 16).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F)
              .texOffs(12, 16).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F),
              PartPose.offset(6F, -1.2F, 0F)));
        parts.put("hub1", parts.get("body").addOrReplaceChild("hub1", CubeListBuilder.create()
              .texOffs(24, 16).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
              PartPose.offset(-6F, 0F, 0F)));
        parts.put("rotor1", parts.get("body").addOrReplaceChild("rotor1", CubeListBuilder.create()
              .texOffs(32, 16).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F)
              .texOffs(44, 16).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F),
              PartPose.offset(-6F, -1.2F, 0F)));
        parts.put("hub2", parts.get("body").addOrReplaceChild("hub2", CubeListBuilder.create()
              .texOffs(56, 16).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
              PartPose.offset(0F, 0F, 6F)));
        parts.put("rotor2", parts.get("body").addOrReplaceChild("rotor2", CubeListBuilder.create()
              .texOffs(0, 21).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F)
              .texOffs(12, 21).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F),
              PartPose.offset(0F, -1.2F, 6F)));
        parts.put("hub3", parts.get("body").addOrReplaceChild("hub3", CubeListBuilder.create()
              .texOffs(24, 21).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
              PartPose.offset(0F, 0F, -6F)));
        parts.put("rotor3", parts.get("body").addOrReplaceChild("rotor3", CubeListBuilder.create()
              .texOffs(32, 21).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F)
              .texOffs(44, 21).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F),
              PartPose.offset(0F, -1.2F, -6F)));
        parts.put("gun", parts.get("body").addOrReplaceChild("gun", CubeListBuilder.create()
              .texOffs(56, 21).addBox(-1F, -1F, -1F, 2F, 2F, 2F)
              .texOffs(0, 26).addBox(-0.5F, -0.5F, -5F, 1F, 1F, 4F),
              PartPose.offset(0F, 2.5F, -1F)));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition turret() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("base", root.addOrReplaceChild("base", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-7F, -3F, -7F, 14F, 3F, 14F)
              .texOffs(56, 0).addBox(-5F, -5F, -5F, 10F, 2F, 10F)
              .texOffs(96, 0).addBox(-2.5F, -11F, -2.5F, 5F, 6F, 5F)
              .texOffs(116, 0).addBox(-6F, -2F, -7.5F, 2F, 1F, 1F),
              PartPose.offset(0F, 24F, 0F)));
        parts.put("head", root.addOrReplaceChild("head", CubeListBuilder.create()
              .texOffs(0, 17).addBox(-4F, -2F, -4F, 8F, 2F, 8F)
              .texOffs(32, 17).addBox(-5F, -9F, -5F, 10F, 7F, 10F)
              .texOffs(72, 17).addBox(-6F, -8F, -3F, 1F, 5F, 6F)
              .texOffs(86, 17).addBox(5F, -8F, -3F, 1F, 5F, 6F)
              .texOffs(100, 17).addBox(-1.5F, -8F, -5.5F, 3F, 2F, 1F)
              .texOffs(0, 34).addBox(-3F, -10F, -3F, 6F, 1F, 6F),
              PartPose.offset(0F, 13F, 0F)));
        parts.put("gun", parts.get("head").addOrReplaceChild("gun", CubeListBuilder.create()
              .texOffs(24, 34).addBox(-3F, -2F, -2F, 6F, 4F, 2F)
              .texOffs(40, 34).addBox(-2.5F, -1F, -10F, 2F, 2F, 8F)
              .texOffs(60, 34).addBox(0.5F, -1F, -10F, 2F, 2F, 8F),
              PartPose.offset(0F, -4F, -5F)));
        parts.put("lens", parts.get("gun").addOrReplaceChild("lens", CubeListBuilder.create()
              .texOffs(80, 34).addBox(-1F, -4F, -6F, 2F, 2F, 5F)
              .texOffs(94, 34).addBox(-0.5F, -3.5F, -6.5F, 1F, 1F, 1F),
              PartPose.offset(0F, 0F, 0F)));
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static LayerDefinition repairer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-1F, 0F, -1F, 2F, 7F, 2F)
              .texOffs(8, 0).addBox(-1.5F, 7F, -2F, 3F, 2F, 4F),
              PartPose.offset(-2F, 15F, 0F)));
        parts.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create()
              .texOffs(22, 0).addBox(-1F, 0F, -1F, 2F, 7F, 2F)
              .texOffs(30, 0).addBox(-1.5F, 7F, -2F, 3F, 2F, 4F),
              PartPose.offset(2F, 15F, 0F)));
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create()
              .texOffs(44, 0).addBox(-3F, -2F, -2F, 6F, 2F, 4F)
              .texOffs(0, 9).addBox(-4F, -10F, -3F, 8F, 8F, 6F)
              .texOffs(28, 9).addBox(-2F, -8F, -3.5F, 4F, 3F, 1F)
              .texOffs(38, 9).addBox(-3F, -9F, 3F, 6F, 6F, 3F),
              PartPose.offset(0F, 15F, 0F)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create()
              .texOffs(56, 9).addBox(-1F, -1F, -1F, 2F, 1F, 2F)
              .texOffs(0, 23).addBox(-2.5F, -5F, -2.5F, 5F, 4F, 5F)
              .texOffs(20, 23).addBox(-1F, -4F, -3F, 2F, 2F, 1F)
              .texOffs(26, 23).addBox(1.5F, -7F, 0F, 1F, 2F, 1F),
              PartPose.offset(0F, -10F, -0.5F)));
        parts.put("right_arm", parts.get("body").addOrReplaceChild("right_arm", CubeListBuilder.create()
              .texOffs(30, 23).addBox(-1F, -2F, -1.5F, 2F, 3F, 3F)
              .texOffs(40, 23).addBox(-1F, 1F, -1F, 2F, 6F, 2F),
              PartPose.offset(-5F, -9F, 0F)));
        parts.put("right_tool", parts.get("right_arm").addOrReplaceChild("right_tool", CubeListBuilder.create()
              .texOffs(48, 23).addBox(-1F, 0F, -1F, 2F, 4F, 2F)
              .texOffs(56, 23).addBox(-0.5F, 4F, -0.5F, 1F, 2F, 1F),
              PartPose.offset(0F, 7F, 0F)));
        parts.put("left_arm", parts.get("body").addOrReplaceChild("left_arm", CubeListBuilder.create()
              .texOffs(0, 32).addBox(-1F, -2F, -1.5F, 2F, 3F, 3F)
              .texOffs(10, 32).addBox(-1F, 1F, -1F, 2F, 6F, 2F),
              PartPose.offset(5F, -9F, 0F)));
        parts.put("left_tool", parts.get("left_arm").addOrReplaceChild("left_tool", CubeListBuilder.create()
              .texOffs(18, 32).addBox(-1F, 0F, -1F, 2F, 1F, 2F)
              .texOffs(26, 32).addBox(-1.5F, 1F, -1F, 1F, 3F, 2F)
              .texOffs(32, 32).addBox(0.5F, 1F, -1F, 1F, 3F, 2F),
              PartPose.offset(0F, 7F, 0F)));
        parts.put("aux", parts.get("body").addOrReplaceChild("aux", CubeListBuilder.create()
              .texOffs(38, 32).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F)
              .texOffs(42, 32).addBox(-1F, -5F, -3F, 2F, 1F, 4F)
              .texOffs(54, 32).addBox(-0.5F, -4F, -3.5F, 1F, 1F, 1F),
              PartPose.offset(0F, -9F, 4F)));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition foreman() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-3F, -1F, -3F, 6F, 4F, 6F)
              .texOffs(24, 0).addBox(-3.5F, 2F, -3.5F, 7F, 8F, 7F)
              .texOffs(52, 0).addBox(-3F, 9F, -3F, 6F, 8F, 6F)
              .texOffs(76, 0).addBox(-4.5F, 17F, -6F, 9F, 3F, 10F),
              PartPose.offset(-6F, 4F, 0F)));
        parts.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create()
              .texOffs(114, 0).addBox(-3F, -1F, -3F, 6F, 4F, 6F)
              .texOffs(138, 0).addBox(-3.5F, 2F, -3.5F, 7F, 8F, 7F)
              .texOffs(166, 0).addBox(-3F, 9F, -3F, 6F, 8F, 6F)
              .texOffs(190, 0).addBox(-4.5F, 17F, -6F, 9F, 3F, 10F),
              PartPose.offset(6F, 4F, 0F)));
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create()
              .texOffs(0, 15).addBox(-8F, -4F, -4F, 16F, 4F, 8F)
              .texOffs(48, 15).addBox(-6F, -8F, -3.5F, 12F, 4F, 7F)
              .texOffs(86, 15).addBox(-11F, -22F, -6F, 22F, 14F, 12F)
              .texOffs(154, 15).addBox(-9F, -19F, -7F, 18F, 9F, 1F)
              .texOffs(192, 15).addBox(-2.5F, -17F, -7.5F, 5F, 4F, 1F)
              .texOffs(204, 15).addBox(-8F, -20F, 6F, 16F, 9F, 2F)
              .texOffs(0, 41).addBox(-6F, -24F, -4F, 12F, 2F, 8F),
              PartPose.offset(0F, 4F, 0F)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create()
              .texOffs(40, 41).addBox(-4F, -6F, -4F, 8F, 6F, 8F)
              .texOffs(72, 41).addBox(-3.5F, -4F, -4.5F, 7F, 2F, 1F)
              .texOffs(88, 41).addBox(-5F, -2F, -3F, 10F, 3F, 7F)
              .texOffs(122, 41).addBox(-0.5F, -9F, 1F, 1F, 3F, 1F)
              .texOffs(126, 41).addBox(-1F, -10F, 0.5F, 2F, 1F, 2F),
              PartPose.offset(0F, -24F, -1F)));
        parts.put("right_arm", parts.get("body").addOrReplaceChild("right_arm", CubeListBuilder.create()
              .texOffs(134, 41).addBox(-4F, -4F, -5F, 8F, 7F, 10F)
              .texOffs(170, 41).addBox(-2.5F, 3F, -2.5F, 5F, 9F, 5F)
              .texOffs(190, 41).addBox(-3.5F, 12F, -4F, 7F, 10F, 8F)
              .texOffs(220, 41).addBox(-4F, 22F, -4F, 8F, 4F, 8F)
              .texOffs(0, 59).addBox(-1F, -2F, -5.5F, 2F, 1F, 1F),
              PartPose.offset(-14F, -19F, 0F)));
        parts.put("left_arm", parts.get("body").addOrReplaceChild("left_arm", CubeListBuilder.create()
              .texOffs(6, 59).addBox(-4F, -4F, -5F, 8F, 7F, 10F)
              .texOffs(42, 59).addBox(-2.5F, 3F, -2.5F, 5F, 9F, 5F)
              .texOffs(62, 59).addBox(-3.5F, 12F, -4F, 7F, 10F, 8F)
              .texOffs(92, 59).addBox(-4F, 22F, -4F, 8F, 4F, 8F)
              .texOffs(124, 59).addBox(-1F, -2F, -5.5F, 2F, 1F, 1F),
              PartPose.offset(14F, -19F, 0F)));
        parts.put("right_gun", parts.get("body").addOrReplaceChild("right_gun", CubeListBuilder.create()
              .texOffs(130, 59).addBox(-2F, -3F, -2F, 4F, 3F, 5F)
              .texOffs(148, 59).addBox(-1F, -2.5F, -8F, 2F, 2F, 6F)
              .texOffs(164, 59).addBox(-0.5F, -4F, 0F, 1F, 1F, 1F),
              PartPose.offset(-8F, -22F, 2F)));
        parts.put("left_gun", parts.get("body").addOrReplaceChild("left_gun", CubeListBuilder.create()
              .texOffs(168, 59).addBox(-2F, -3F, -2F, 4F, 3F, 5F)
              .texOffs(186, 59).addBox(-1F, -2.5F, -8F, 2F, 2F, 6F)
              .texOffs(202, 59).addBox(-0.5F, -4F, 0F, 1F, 1F, 1F),
              PartPose.offset(8F, -22F, 2F)));
        return LayerDefinition.create(mesh, 256, 128);
    }
}
