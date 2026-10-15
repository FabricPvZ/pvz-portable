package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.DataReader;
import cn.enaium.fabricpvz.portable.sexy.DataSync;
import cn.enaium.fabricpvz.portable.sexy.DataWriter;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Tests for {@link GameObject}: the constructor defaults and draw-space helpers of
 * src/Lawn/GameObject.cpp:29-55, the deterministic allocation id, and the base payload
 * round trip of {@code SyncGameObjectPortable} (src/Lawn/System/SaveGame.cpp:535-543).
 */
class GameObjectTest {

    @Test
    void constructorAppliesUpstreamDefaults() {
        GameObject anObject = new GameObject();

        assertEquals(0, anObject.mX);
        assertEquals(0, anObject.mY);
        assertEquals(0, anObject.mWidth);
        assertEquals(0, anObject.mHeight);
        assertTrue(anObject.mVisible);
        assertEquals(-1, anObject.mRow); // GameObject.cpp:38
        assertEquals(RenderLayer.RENDER_LAYER_TOP, anObject.mRenderOrder); // = 400000
        assertEquals(400000, anObject.mRenderOrder);
        assertSame(LawnApp.gLawnApp, anObject.mApp);
    }

    @Test
    void allocIdsAreMonotonicAndDistinct() {
        GameObject a = new GameObject();
        GameObject b = new GameObject();

        assertTrue(a.mAllocId > 0);
        assertTrue(b.mAllocId > a.mAllocId);
        assertNotEquals(a.mAllocId, b.mAllocId);

        long aNext = GameObject.nextAllocId();
        assertTrue(aNext > b.mAllocId);
        assertEquals(aNext + 1, GameObject.nextAllocId());
    }

    @Test
    void beginDrawTranslatesIntoObjectSpace() {
        GameObject anObject = new GameObject();
        anObject.mX = 10;
        anObject.mY = -20;
        Graphics aGraphics = new Graphics();

        assertTrue(anObject.beginDraw(aGraphics));
        assertEquals(10.0f, aGraphics.mTransX, 0.0f);
        assertEquals(-20.0f, aGraphics.mTransY, 0.0f);

        anObject.endDraw(aGraphics);
        assertEquals(0.0f, aGraphics.mTransX, 0.0f);
        assertEquals(0.0f, aGraphics.mTransY, 0.0f);
    }

    @Test
    void beginDrawIsSkippedWhileInvisible() {
        GameObject anObject = new GameObject();
        anObject.mX = 7;
        anObject.mY = 9;
        anObject.mVisible = false;
        Graphics aGraphics = new Graphics();

        assertFalse(anObject.beginDraw(aGraphics));
        assertEquals(0.0f, aGraphics.mTransX, 0.0f);
        assertEquals(0.0f, aGraphics.mTransY, 0.0f);
    }

    @Test
    void makeParentGraphicsFrameUndoesTheObjectTranslation() {
        GameObject anObject = new GameObject();
        anObject.mX = 5;
        anObject.mY = 6;
        Graphics aGraphics = new Graphics();
        aGraphics.translate(anObject.mX, anObject.mY);

        anObject.makeParentGraphicsFrame(aGraphics);

        assertEquals(0.0f, aGraphics.mTransX, 0.0f);
        assertEquals(0.0f, aGraphics.mTransY, 0.0f);
    }

    @Test
    void basePayloadRoundTripsThroughDataSync() {
        GameObject aSource = new GameObject();
        aSource.mX = 11;
        aSource.mY = -22;
        aSource.mWidth = 33;
        aSource.mHeight = 44;
        aSource.mVisible = false;
        aSource.mRow = 3;
        aSource.mRenderOrder = 302000;

        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        aSource.syncGameObjectPortable(new DataSync(aWriter));
        byte[] aBytes = aWriter.getDataPtr();

        // i32 x4 + bool + i32 x2 = 25 bytes, in the exact upstream order.
        assertEquals(25, aBytes.length);

        DataReader aReader = new DataReader();
        aReader.openMemory(aBytes);
        GameObject aTarget = new GameObject();
        aTarget.mX = -1;
        aTarget.mY = -1;
        aTarget.mWidth = -1;
        aTarget.mHeight = -1;
        aTarget.mVisible = true;
        aTarget.mRow = -1;
        aTarget.mRenderOrder = -1;

        aTarget.syncGameObjectPortable(new DataSync(aReader));

        assertEquals(aSource.mX, aTarget.mX);
        assertEquals(aSource.mY, aTarget.mY);
        assertEquals(aSource.mWidth, aTarget.mWidth);
        assertEquals(aSource.mHeight, aTarget.mHeight);
        assertEquals(aSource.mVisible, aTarget.mVisible);
        assertEquals(aSource.mRow, aTarget.mRow);
        assertEquals(aSource.mRenderOrder, aTarget.mRenderOrder);
        assertEquals(25, aReader.getDataPos());
    }

    @Test
    void basePayloadIsLittleEndianFieldOrder() {
        GameObject aSource = new GameObject();
        aSource.mX = 0x01020304;
        aSource.mVisible = true;
        aSource.mRow = -1;
        aSource.mRenderOrder = 1;

        DataWriter aWriter = new DataWriter();
        aWriter.openMemory();
        aSource.syncGameObjectPortable(new DataSync(aWriter));
        byte[] aBytes = aWriter.getDataPtr();

        assertEquals(0x04, aBytes[0] & 0xFF);
        assertEquals(0x03, aBytes[1] & 0xFF);
        assertEquals(0x02, aBytes[2] & 0xFF);
        assertEquals(0x01, aBytes[3] & 0xFF);
        assertEquals(1, aBytes[16] & 0xFF); // mVisible, one byte at offset 16
        assertEquals(0xFF, aBytes[17] & 0xFF); // mRow = -1
        assertEquals(0xFF, aBytes[18] & 0xFF);
        assertEquals(0xFF, aBytes[19] & 0xFF);
        assertEquals(0xFF, aBytes[20] & 0xFF);
    }
}
