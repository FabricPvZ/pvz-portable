package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/widget/DialogListener.h:27.
 */
public interface DialogListener {
    // DialogListener.h:31
    void dialogButtonPress(int theDialogId, int theButtonId);

    // DialogListener.h:32
    void dialogButtonDepress(int theDialogId, int theButtonId);
}
