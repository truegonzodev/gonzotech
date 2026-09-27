package com.gonzotech.core.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Модель «альт-жителя» — точная копия {@code VillagerModel} 1.21.4.
 *
 * <p>Меша НЕ копируется кодом: рендерер печёт ванильный слой
 * {@code ModelLayers.VILLAGER}, поэтому геометрия (head/hat/hat_rim/nose/
 * body/jacket/arms/legs) идентична вилладжеру побайтово. Здесь только части и
 * математика анимации, переписанная со {@code VillagerModel.setupAnim}:
 * поворот головы на {@code yRot/xRot} состояния и махи ног
 * {@code cos(pos * 0.6662) * 1.4 * speed * 0.5}. «Недовольство» (виляние носом)
 * опущено — без АИ и торговли этот флаг не возникает.</p>
 */
public class AltVillagerModel extends EntityModel<AltVillagerRenderState> implements HeadedModel {

    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart hatRim;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public AltVillagerModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.hat = this.head.getChild("hat");
        this.hatRim = this.hat.getChild("hat_rim");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /** Идентично {@code VillagerModel.setupAnim} минус виляние носом при торговле. */
    @Override
    public void setupAnim(AltVillagerRenderState state) {
        super.setupAnim(state); // resetPose()
        this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
        this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
        this.head.zRot = 0.0F;

        this.rightLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * state.walkAnimationSpeed * 0.5F;
        this.leftLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.4F * state.walkAnimationSpeed * 0.5F;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }

    /** Идентично {@code VillagerModel.hatVisible}. */
    public void hatVisible(boolean visible) {
        this.head.visible = visible;
        this.hat.visible = visible;
        this.hatRim.visible = visible;
    }
}
