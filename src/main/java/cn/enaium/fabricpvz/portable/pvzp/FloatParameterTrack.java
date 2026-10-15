/*
 * Ported from src/PvzpLib/PvzpParticle.h:53-58 (class FloatParameterTrack).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:53-58 (class FloatParameterTrack).
 *
 * <p>Each track describes how one emitter attribute varies over time and its allowed range. Upstream
 * stores {@code FloatParameterTrackNode* mNodes} + {@code int32_t mCountNodes}; in Java the nodes are
 * an array whose {@link #mCountNodes} mirrors the array length (upstream's raw pointer becomes the
 * array itself). {@code mNodes} is {@code null} exactly when {@code mCountNodes == 0}, matching the
 * upstream "no nodes" state produced by {@code DefinitionAlloc}/{@code FloatTrackSetDefault}.
 */
public class FloatParameterTrack {
    /** Upstream {@code mNodes}: the stage nodes, {@code null} when there are none. */
    public FloatParameterTrackNode[] mNodes;

    /** Upstream {@code mCountNodes}: number of stage nodes. */
    public int mCountNodes;

    public FloatParameterTrack() {
    }

    public FloatParameterTrack(FloatParameterTrackNode[] theNodes) {
        mNodes = theNodes;
        mCountNodes = theNodes != null ? theNodes.length : 0;
    }

    /**
     * Ported from the upstream {@code mNodes}/{@code mCountNodes} invariant (Definition.cpp:1030,
     * 1387): keeps the count in sync with the backing array.
     */
    public void setNodes(FloatParameterTrackNode[] theNodes) {
        mNodes = theNodes;
        mCountNodes = theNodes != null ? theNodes.length : 0;
    }
}
