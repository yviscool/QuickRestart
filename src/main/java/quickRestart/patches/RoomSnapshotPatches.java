package quickRestart.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.helpers.File;
import quickRestart.helper.RoomSnapshotHelper;

public class RoomSnapshotPatches {
    @SpirePatch2(clz = File.class, method = "save")
    public static class CaptureSnapshotPatch {
        @SpirePostfixPatch
        public static void patch(File __instance) {
            RoomSnapshotHelper.maybeUpdateSnapshot(__instance.getFilepath());
        }
    }
}
