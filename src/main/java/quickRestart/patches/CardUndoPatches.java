package quickRestart.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import quickRestart.helper.RoomSnapshotHelper;

public class CardUndoPatches {
    @SpirePatch2(clz = AbstractPlayer.class, method = "playCard")
    public static class CaptureUndoSnapshotPatch {
        @SpirePrefixPatch
        public static void patch(AbstractPlayer __instance) {
            RoomSnapshotHelper.maybeCaptureUndoSnapshot();
        }
    }
}
