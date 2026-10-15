/*
 * Ported from src/Lawn/GameObject.h:33-52 and src/Lawn/GameObject.cpp:29-55.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.sexy.DataSync;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/GameObject.h:33-52, impl src/Lawn/GameObject.cpp:29-55.
 *
 * <p>Base entity of the lawn: position/size, visibility, board row and the z-order used by the
 * board's render list. There is no virtual {@code Update}/{@code Draw} on the base (upstream has
 * none either); each subclass is invoked through its concrete type by {@link Board}.
 *
 * <p>Field names, defaults and the draw helpers are 1:1 with upstream:
 * {@code mX=mY=mWidth=mHeight=0}, {@code mVisible=true}, {@code mRow=-1},
 * {@code mRenderOrder=RenderLayer::RENDER_LAYER_TOP} (GameObject.cpp:31-39).
 *
 * <p>{@code mApp}/{@code mBoard} are relinked from the process-wide app instance
 * (upstream {@code gLawnApp}/{@code gLawnApp->mBoard}, GameObject.cpp:31-32); the extra null guard
 * only exists so entities can be constructed in headless tests where no app is running.
 *
 * <p>{@link #mAllocId} and {@link #nextAllocId()} are a Java-only addition: upstream breaks
 * render-order ties with a raw pointer comparison (Board.cpp:5926-5931), which is not reproducible
 * in Java, so a monotonically increasing id (docs/API-CONTRACTS.md §6) is the deterministic
 * substitute. Entities that do not derive from {@code GameObject} (LawnMower, GridItem) must take
 * their id from {@link #nextAllocId()} too.
 */
public class GameObject {

    /** Ported from src/Lawn/GameObject.h:37 ({@code LawnApp* mApp}). */
    public LawnApp mApp;

    /** Ported from src/Lawn/GameObject.h:38 ({@code Board* mBoard}). */
    public Board mBoard;

    /** Ported from src/Lawn/GameObject.h:39. */
    public int mX;

    /** Ported from src/Lawn/GameObject.h:40. */
    public int mY;

    /** Ported from src/Lawn/GameObject.h:41. */
    public int mWidth;

    /** Ported from src/Lawn/GameObject.h:42. */
    public int mHeight;

    /** Ported from src/Lawn/GameObject.h:43. */
    public boolean mVisible;

    /** Ported from src/Lawn/GameObject.h:44. */
    public int mRow;

    /** Ported from src/Lawn/GameObject.h:45. */
    public int mRenderOrder;

    /** Java-only monotonic identity used as the deterministic render-order tie-break. */
    public long mAllocId;

    private static long sNextAllocId = 1L;

    /** Java-only: hands out the monotonic id used by {@link #mAllocId}. */
    public static long nextAllocId() {
        return sNextAllocId++;
    }

    /** Ported from src/Lawn/GameObject.cpp:29-39 (GameObject::GameObject). */
    public GameObject() {
        mApp = LawnApp.gLawnApp;
        mBoard = mApp != null ? mApp.mBoard : null;
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
        mVisible = true;
        mRow = -1;
        mRenderOrder = RenderLayer.RENDER_LAYER_TOP;
        mAllocId = nextAllocId();
    }

    /**
     * Ported from src/Lawn/GameObject.cpp:41-47 (GameObject::BeginDraw).
     *
     * <p>Returns false when invisible, otherwise translates the graphics into object space.
     */
    public boolean beginDraw(Graphics g) {
        if (!mVisible) {
            return false;
        }

        g.translate(mX, mY);
        return true;
    }

    /** Ported from src/Lawn/GameObject.cpp:49-52 (GameObject::EndDraw). */
    public void endDraw(Graphics g) {
        g.translate(-mX, -mY);
    }

    /** Ported from src/Lawn/GameObject.cpp:54-57 (GameObject::MakeParentGraphicsFrame). */
    public void makeParentGraphicsFrame(Graphics g) {
        g.translate(-mX, -mY);
    }

    /**
     * Ported from src/Lawn/System/SaveGame.cpp:535-543 (SyncGameObjectPortable) with the field
     * order fixed by docs/research/03-entities.md §1 / docs/research/03-save.md §6.3:
     * {@code mX(i32), mY(i32), mWidth(i32), mHeight(i32), mVisible(bool), mRow(i32),
     * mRenderOrder(i32)}.
     *
     * <p>Reads when the sync is backed by a reader, writes when backed by a writer; the int32
     * fields use {@link DataSync#syncUInt32(int)} because the wire payload is the same four bytes
     * as upstream {@code SyncInt32}.
     */
    public void syncGameObjectPortable(DataSync theSync) {
        mX = theSync.syncUInt32(mX);
        mY = theSync.syncUInt32(mY);
        mWidth = theSync.syncUInt32(mWidth);
        mHeight = theSync.syncUInt32(mHeight);
        mVisible = theSync.syncBool(mVisible);
        mRow = theSync.syncUInt32(mRow);
        mRenderOrder = theSync.syncUInt32(mRenderOrder);
    }
}
