package quickRestart.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import quickRestart.helper.CombatUndoHelper;

public class CardUndoPatches {
    @SpirePatch2(clz = AbstractPlayer.class, method = "playCard")
    public static class CaptureCombatSnapshotPatch {
        @SpirePrefixPatch
        public static void patch(AbstractPlayer __instance) {
            if (__instance.hoveredCard == null) {
                return;
            }

            for (Object queuedItem : com.megacrit.cardcrawl.dungeons.AbstractDungeon.actionManager.cardQueue) {
                if (((com.megacrit.cardcrawl.cards.CardQueueItem) queuedItem).card == __instance.hoveredCard) {
                    return;
                }
            }

            AbstractMonster hoveredMonster = ReflectionHacks.getPrivate(__instance, AbstractPlayer.class, "hoveredMonster");
            if (__instance.hoveredCard.canUse(__instance, hoveredMonster)) {
                CombatUndoHelper.captureSnapshotBeforeCardPlay(__instance, __instance.hoveredCard);
            }
        }
    }

    @SpirePatch2(clz = CardGroup.class, method = "getHoveredCard")
    public static class BlockImmediateHoverAfterUndoPatch {
        @SpirePrefixPatch
        public static SpireReturn<AbstractCard> patch() {
            if (CombatUndoHelper.shouldBlockHoveredCardSelection()) {
                return SpireReturn.Return(null);
            }

            return SpireReturn.Continue();
        }
    }

    @SpirePatch2(clz = MonsterRoom.class, method = "onPlayerEntry")
    public static class ResetOnMonsterRoomStartPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.onCombatStart();
        }
    }

    @SpirePatch2(clz = MonsterRoomElite.class, method = "onPlayerEntry")
    public static class ResetOnEliteRoomStartPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.onCombatStart();
        }
    }

    @SpirePatch2(clz = MonsterRoomBoss.class, method = "onPlayerEntry")
    public static class ResetOnBossRoomStartPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.onCombatStart();
        }
    }
}
