package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/widget/ListListener.h:27.
 */
public interface ListListener {
    // ListListener.h:31
    void listClicked(int theId, int theIdx, int theClicks);

    // ListListener.h:32
    void listClosed(int theId);

    // ListListener.h:33-34
    void listHiliteChanged(int theId, int theCurIdx, int theNewIdx);
}
