package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.DefinitionArrayDef;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.lawn.EmitterType;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:131 (class PvzpEmitterDefinition).
 *
 * <p>Every field keeps its upstream name and type. Array-typed members use {@link DefinitionArrayDef}
 * (the Java replacement for upstream's pointer+count pair, per DefinitionCore's contract): for
 * {@code mParticleFields}/{@code mSystemFields} the typed array is {@code ParticleField[]}.
 *
 * <p>{@code PvzpEmitterDefinitionConstructor} (src/PvzpLib/Definition.cpp:187): the whole struct is
 * zeroed, then {@code mImageFrames=1}, {@code mEmitterType=Box}, {@code mName=""},
 * {@code mOnDuration=""}, {@code mImageRow=0}, {@code mImageCol=0}, {@code mAnimated=0},
 * {@code mImage=null}, {@code mParticleFields.count=0}, {@code mParticleFlags=0}.
 */
public class PvzpEmitterDefinition {

    public Image mImage = null;
    public int mImageCol = 0;
    public int mImageRow = 0;
    public int mImageFrames = 1;
    public int mAnimated = 0;
    public int mParticleFlags = 0;
    public int mEmitterType = EmitterType.EMITTER_BOX;
    public String mName = "";
    public String mOnDuration = "";
    public FloatParameterTrack mSystemDuration = new FloatParameterTrack();
    public FloatParameterTrack mCrossFadeDuration = new FloatParameterTrack();
    public FloatParameterTrack mSpawnRate = new FloatParameterTrack();
    public FloatParameterTrack mSpawnMinActive = new FloatParameterTrack();
    public FloatParameterTrack mSpawnMaxActive = new FloatParameterTrack();
    public FloatParameterTrack mSpawnMaxLaunched = new FloatParameterTrack();
    public FloatParameterTrack mEmitterRadius = new FloatParameterTrack();
    public FloatParameterTrack mEmitterOffsetX = new FloatParameterTrack();
    public FloatParameterTrack mEmitterOffsetY = new FloatParameterTrack();
    public FloatParameterTrack mEmitterBoxX = new FloatParameterTrack();
    public FloatParameterTrack mEmitterBoxY = new FloatParameterTrack();
    public FloatParameterTrack mEmitterSkewX = new FloatParameterTrack();
    public FloatParameterTrack mEmitterSkewY = new FloatParameterTrack();
    public FloatParameterTrack mEmitterPath = new FloatParameterTrack();
    public FloatParameterTrack mParticleDuration = new FloatParameterTrack();
    public FloatParameterTrack mLaunchSpeed = new FloatParameterTrack();
    public FloatParameterTrack mLaunchAngle = new FloatParameterTrack();
    public FloatParameterTrack mSystemRed = new FloatParameterTrack();
    public FloatParameterTrack mSystemGreen = new FloatParameterTrack();
    public FloatParameterTrack mSystemBlue = new FloatParameterTrack();
    public FloatParameterTrack mSystemAlpha = new FloatParameterTrack();
    public FloatParameterTrack mSystemBrightness = new FloatParameterTrack();
    public DefinitionArrayDef mParticleFields = new DefinitionArrayDef(new ParticleField[0]);
    public DefinitionArrayDef mSystemFields = new DefinitionArrayDef(new ParticleField[0]);
    public FloatParameterTrack mParticleRed = new FloatParameterTrack();
    public FloatParameterTrack mParticleGreen = new FloatParameterTrack();
    public FloatParameterTrack mParticleBlue = new FloatParameterTrack();
    public FloatParameterTrack mParticleAlpha = new FloatParameterTrack();
    public FloatParameterTrack mParticleBrightness = new FloatParameterTrack();
    public FloatParameterTrack mParticleSpinAngle = new FloatParameterTrack();
    public FloatParameterTrack mParticleSpinSpeed = new FloatParameterTrack();
    public FloatParameterTrack mParticleScale = new FloatParameterTrack();
    public FloatParameterTrack mParticleStretch = new FloatParameterTrack();
    public FloatParameterTrack mCollisionReflect = new FloatParameterTrack();
    public FloatParameterTrack mCollisionSpin = new FloatParameterTrack();
    public FloatParameterTrack mClipTop = new FloatParameterTrack();
    public FloatParameterTrack mClipBottom = new FloatParameterTrack();
    public FloatParameterTrack mClipLeft = new FloatParameterTrack();
    public FloatParameterTrack mClipRight = new FloatParameterTrack();
    public FloatParameterTrack mAnimationRate = new FloatParameterTrack();

    /** Ported from src/PvzpLib/Definition.cpp:187 ({@code PvzpEmitterDefinitionConstructor}). */
    public static PvzpEmitterDefinition pvzpEmitterDefinitionConstructor() {
        return new PvzpEmitterDefinition();
    }
}
