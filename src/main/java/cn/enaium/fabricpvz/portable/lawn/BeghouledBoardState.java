/*
 * Ported from src/Lawn/Challenge.h:56-59 (struct BeghouledBoardState).
 */
package cn.enaium.fabricpvz.portable.lawn;

/**
 * Ported from src/Lawn/Challenge.h:56-59.
 *
 * <p>Snapshot of the Beghouled board: {@code mSeedType[MAX_GRID_SIZE_X][MAX_GRID_SIZE_Y]} (indexed
 * {@code [gridX][gridY]}, exactly like upstream). The seed type is a {@link SeedType} int constant;
 * {@code SEED_NONE} marks an empty square.
 */
public class BeghouledBoardState {

    /** Upstream {@code mSeedType[9][6]} ({@code SeedType}). */
    public int[][] mSeedType = new int[Board.MAX_GRID_SIZE_X][Board.MAX_GRID_SIZE_Y];

    /**
     * Upstream's C++ {@code BeghouledBoardState} is an aggregate whose array is only given values by
     * {@code Challenge::LoadBeghouledBoardState} (which fills it with {@code SEED_NONE}); a Java array
     * would otherwise default to {@code 0}, which is {@code SEED_PEASHOOTER}, so the constructor fills
     * it with {@link SeedType#SEED_NONE} to match the freshly loaded state.
     */
    public BeghouledBoardState() {
        for (int i = 0; i < Board.MAX_GRID_SIZE_X; i++) {
            java.util.Arrays.fill(mSeedType[i], SeedType.SEED_NONE);
        }
    }
}
