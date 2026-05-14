package quickRestart.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import com.megacrit.cardcrawl.ui.panels.PotionPopUp;
import quickRestart.helper.CombatUndoHelper;

public class CardUndoPatches {
    @SpirePatch2(clz = AbstractPlayer.class, method = "playCard")
    public static class RecordCardPlayPatch {
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
            CombatUndoHelper.recordCardPlay(__instance, __instance.hoveredCard, hoveredMonster);
        }
    }

    @SpirePatch2(clz = AbstractRoom.class, method = "endTurn")
    public static class RecordEndTurnPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.recordEndTurn();
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

    @SpirePatch(
            clz = HandCardSelectScreen.class,
            method = "open",
            paramtypez = {String.class, int.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class}
    )
    public static class DisableUndoForHandSelectPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.markUnsupported("hand card selection screen opened");
        }
    }

    @SpirePatch(
            clz = HandCardSelectScreen.class,
            method = "open",
            paramtypez = {String.class, int.class, boolean.class, boolean.class}
    )
    public static class DisableUndoForSimpleHandSelectPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.markUnsupported("hand card selection screen opened");
        }
    }

    @SpirePatch(
            clz = GridCardSelectScreen.class,
            method = "open",
            paramtypez = {com.megacrit.cardcrawl.cards.CardGroup.class, int.class, String.class, boolean.class, boolean.class, boolean.class, boolean.class}
    )
    public static class DisableUndoForGridSelectPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.markUnsupported("grid card selection screen opened");
        }
    }

    @SpirePatch(
            clz = GridCardSelectScreen.class,
            method = "open",
            paramtypez = {com.megacrit.cardcrawl.cards.CardGroup.class, int.class, boolean.class, String.class}
    )
    public static class DisableUndoForSimpleGridSelectPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.markUnsupported("grid card selection screen opened");
        }
    }

    @SpirePatch2(clz = PotionPopUp.class, method = "open")
    public static class DisableUndoForPotionPopupPatch {
        @SpirePrefixPatch
        public static void patch() {
            CombatUndoHelper.markUnsupported("potion popup opened");
        }
    }
}
