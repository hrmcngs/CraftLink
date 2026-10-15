package dev.craftlink.classroom;

import dev.craftlink.input.RemoteState;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Pose;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

/** Remote-controlled humanoid; all intent is applied on the integrated server thread. */
public final class ClassroomBot extends PathfinderMob {
    private static final ChatFormatting[] COLORS = {ChatFormatting.RED,ChatFormatting.BLUE,ChatFormatting.GREEN,ChatFormatting.YELLOW,ChatFormatting.LIGHT_PURPLE,ChatFormatting.AQUA,ChatFormatting.GOLD,ChatFormatting.WHITE};
    private static final String[] NAMES = {"あか","あお","みどり","きいろ","むらさき","みずいろ","オレンジ","しろ"};
    private static final EntityDataAccessor<Integer> NUMBER = SynchedEntityData.defineId(ClassroomBot.class, EntityDataSerializers.INT);
    private Set<String> held = Set.of();
    private boolean jumpPulse;
    public ClassroomBot(EntityType<? extends ClassroomBot> type, Level level) {
        super(type, level);
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                float x = (held.contains("left") ? 1 : 0) - (held.contains("right") ? 1 : 0);
                float z = (held.contains("forward") ? 1 : 0) - (held.contains("back") ? 1 : 0);
                float length = Math.max(1, (float)Math.sqrt(x*x+z*z));
                setSpeed(held.contains("sneak") ? .055f : held.contains("sprint") ? .20f : .12f);
                setXxa(x/length); setZza(z/length);
            }
        };
        jumpControl = new JumpControl(this) {
            @Override public void tick() { setJumping(held.contains("jump") || jumpPulse); jumpPulse = false; }
        };
    }
    public ClassroomBot(ServerLevel level, int number) {
        this(ClassroomEntities.BOT.get(), level);
        if (number < 1 || number > NAMES.length) throw new IllegalArgumentException("Participant number");
        entityData.set(NUMBER, number);
        setInvulnerable(true); setCanPickUpLoot(false);
        setCustomName(Component.literal(number + "号 " + NAMES[number-1]).withStyle(COLORS[number-1])); setCustomNameVisible(true);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(NUMBER, 1); }
    public int participantNumber() { return entityData.get(NUMBER); }
    @Override public void aiStep() { updateSwingTime(); super.aiStep(); }
    public void accept(RemoteState.Snapshot input) {
        held = input.held(); jumpPulse = input.presses().contains("jump");
        setYRot(getYRot() + (float)input.yaw()); setYHeadRot(getYRot());
        setXRot(net.minecraft.util.Mth.clamp(getXRot() + (float)input.pitch(), -90, 90));
        setShiftKeyDown(held.contains("sneak")); setSprinting(held.contains("sprint") && !isShiftKeyDown());
        setPose(isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING);
        if (held.isEmpty()) setDeltaMovement(0, getDeltaMovement().y, 0);
        if (input.presses().contains("use")) {
            swing(InteractionHand.MAIN_HAND);
            ((ServerLevel)level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY()+2, getZ(), 8,.35,.25,.35,0);
        }
    }
    @Override protected void customServerAiStep() { /* No autonomous goals: remote intent controls movement. */ }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void checkFallDamage(double distance, boolean grounded, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        fallDistance = 0; // No farmland trampling or fall-triggered block changes from demo NPCs.
    }
}
