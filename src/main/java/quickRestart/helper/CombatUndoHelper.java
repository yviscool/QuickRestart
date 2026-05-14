package quickRestart.helper;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import quickRestart.QuickRestart;

import java.util.ArrayList;
import java.util.List;

public class CombatUndoHelper {
    private static final ArrayList<RecordedAction> recordedActions = new ArrayList<RecordedAction>();
    private static ArrayList<RecordedAction> pendingReplayActions = null;
    private static boolean replayQueued = false;
    private static boolean replayRunning = false;
    private static boolean replayWaitingForResolution = false;
    private static int replayIndex = 0;
    private static boolean unsupportedThisCombat = false;

    public static void resetAll() {
        recordedActions.clear();
        pendingReplayActions = null;
        replayQueued = false;
        replayRunning = false;
        replayWaitingForResolution = false;
        replayIndex = 0;
        unsupportedThisCombat = false;
    }

    public static void onCombatStart() {
        recordedActions.clear();
        unsupportedThisCombat = false;
        replayRunning = false;
        replayWaitingForResolution = false;
        replayIndex = 0;
        if (!replayQueued) {
            pendingReplayActions = null;
        }
    }

    public static boolean hasUndoableHistory() {
        return !unsupportedThisCombat && findLastCardActionIndex(recordedActions) >= 0;
    }

    public static boolean requestUndo() {
        if (!RoomSnapshotHelper.canUndoLastCard()) {
            return false;
        }

        int lastCardIndex = findLastCardActionIndex(recordedActions);
        if (lastCardIndex < 0) {
            return false;
        }

        pendingReplayActions = new ArrayList<RecordedAction>(recordedActions.subList(0, lastCardIndex));
        recordedActions.clear();
        replayQueued = true;
        replayRunning = false;
        replayWaitingForResolution = false;
        replayIndex = 0;
        RestartRunHelper.queuedRoomRestart = true;
        QuickRestart.runLogger.info("Queued combat undo replay with " + pendingReplayActions.size() + " prior player actions.");
        return true;
    }

    public static void cancelPendingReplay() {
        pendingReplayActions = null;
        replayQueued = false;
        replayRunning = false;
        replayWaitingForResolution = false;
        replayIndex = 0;
    }

    public static void markUnsupported(String reason) {
        if (replayQueued || replayRunning) {
            return;
        }

        if (!RoomSnapshotHelper.isUndoContextActive()) {
            return;
        }

        unsupportedThisCombat = true;
        QuickRestart.runLogger.info("Disabled Ctrl+Z combat replay for this fight: " + reason);
    }

    public static void recordCardPlay(AbstractPlayer player, AbstractCard card, AbstractMonster target) {
        if (replayQueued || replayRunning || unsupportedThisCombat || player == null || card == null) {
            return;
        }

        if (!RoomSnapshotHelper.isUndoContextActive()) {
            return;
        }

        int handIndex = player.hand.group.indexOf(card);
        if (handIndex < 0) {
            QuickRestart.runLogger.warn("Skipped Ctrl+Z record because played card was not found in hand.");
            unsupportedThisCombat = true;
            return;
        }

        int targetIndex = getMonsterIndex(target);
        recordedActions.add(RecordedAction.card(card, handIndex, targetIndex));
    }

    public static void recordEndTurn() {
        if (replayQueued || replayRunning || unsupportedThisCombat) {
            return;
        }

        if (!RoomSnapshotHelper.isUndoContextActive()) {
            return;
        }

        recordedActions.add(RecordedAction.endTurn());
    }

    public static void updateReplay() {
        if (!replayQueued || pendingReplayActions == null) {
            return;
        }

        if (!isReplayContextReady()) {
            return;
        }

        replayRunning = true;

        if (replayWaitingForResolution) {
            if (!isReplayInputWindowOpen()) {
                return;
            }
            replayWaitingForResolution = false;
            ++replayIndex;
        }

        if (replayIndex >= pendingReplayActions.size()) {
            finishReplay();
            return;
        }

        if (!isReplayInputWindowOpen()) {
            return;
        }

        RecordedAction nextAction = pendingReplayActions.get(replayIndex);
        if (!dispatchReplayAction(nextAction)) {
            RoomSnapshotHelper.flashUndoReplayFailedMessage();
            cancelPendingReplay();
            return;
        }

        replayWaitingForResolution = true;
    }

    private static boolean dispatchReplayAction(RecordedAction action) {
        switch (action.type) {
            case CARD:
                return replayCardAction(action);
            case END_TURN:
                AbstractDungeon.overlayMenu.endTurnButton.disable(true);
                return true;
            default:
                return false;
        }
    }

    private static boolean replayCardAction(RecordedAction action) {
        AbstractCard card = findReplayCard(action);
        if (card == null) {
            QuickRestart.runLogger.warn("Failed Ctrl+Z replay: could not find card " + action.cardId + " in hand.");
            return false;
        }

        AbstractMonster target = getMonsterAtIndex(action.targetIndex);
        AbstractDungeon.actionManager.addCardQueueItem(
                new CardQueueItem(card, target, action.energyOnUse, false, false)
        );
        return true;
    }

    private static AbstractCard findReplayCard(RecordedAction action) {
        if (AbstractDungeon.player == null) {
            return null;
        }

        AbstractCard masterDeckCard = getMasterDeckCardAtIndex(action.masterDeckIndex);
        if (masterDeckCard != null) {
            for (AbstractCard handCard : AbstractDungeon.player.hand.group) {
                if (handCard.uuid.equals(masterDeckCard.uuid)) {
                    return handCard;
                }
            }
        }

        List<AbstractCard> hand = AbstractDungeon.player.hand.group;
        if (action.handIndex >= 0 && action.handIndex < hand.size()) {
            AbstractCard indexedCard = hand.get(action.handIndex);
            if (action.matches(indexedCard)) {
                return indexedCard;
            }
        }

        for (AbstractCard card : hand) {
            if (action.matches(card)) {
                return card;
            }
        }

        return null;
    }

    private static int getMonsterIndex(AbstractMonster monster) {
        if (monster == null || AbstractDungeon.getMonsters() == null) {
            return -1;
        }

        return AbstractDungeon.getMonsters().monsters.indexOf(monster);
    }

    private static int getMasterDeckIndex(AbstractCard combatCard) {
        if (combatCard == null || AbstractDungeon.player == null) {
            return -1;
        }

        for (int i = 0; i < AbstractDungeon.player.masterDeck.group.size(); ++i) {
            AbstractCard masterDeckCard = AbstractDungeon.player.masterDeck.group.get(i);
            if (masterDeckCard.uuid.equals(combatCard.uuid)) {
                return i;
            }
        }

        return -1;
    }

    private static AbstractCard getMasterDeckCardAtIndex(int masterDeckIndex) {
        if (masterDeckIndex < 0 || AbstractDungeon.player == null) {
            return null;
        }

        if (masterDeckIndex >= AbstractDungeon.player.masterDeck.group.size()) {
            return null;
        }

        return AbstractDungeon.player.masterDeck.group.get(masterDeckIndex);
    }

    private static AbstractMonster getMonsterAtIndex(int monsterIndex) {
        if (monsterIndex < 0 || AbstractDungeon.getMonsters() == null) {
            return null;
        }

        if (monsterIndex >= AbstractDungeon.getMonsters().monsters.size()) {
            return null;
        }

        return AbstractDungeon.getMonsters().monsters.get(monsterIndex);
    }

    private static boolean isReplayContextReady() {
        AbstractRoom currentRoom = getCurrentRoomSafely();
        return !CardCrawlGame.loadingSave
                && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY
                && CardCrawlGame.isInARun()
                && AbstractDungeon.player != null
                && currentRoom != null
                && currentRoom.phase == AbstractRoom.RoomPhase.COMBAT
                && AbstractRoom.waitTimer <= 0.0f
                && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.NONE
                && AbstractDungeon.actionManager != null
                && AbstractDungeon.overlayMenu != null
                && AbstractDungeon.overlayMenu.endTurnButton != null;
    }

    private static boolean isReplayInputWindowOpen() {
        return isReplayContextReady()
                && AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER
                && !AbstractDungeon.actionManager.hasControl
                && AbstractDungeon.actionManager.actions.isEmpty()
                && AbstractDungeon.actionManager.cardQueue.isEmpty()
                && !AbstractDungeon.player.endTurnQueued
                && !AbstractDungeon.player.isEndingTurn;
    }

    private static AbstractRoom getCurrentRoomSafely() {
        return AbstractDungeon.currMapNode == null ? null : AbstractDungeon.currMapNode.room;
    }

    private static void finishReplay() {
        QuickRestart.runLogger.info("Ctrl+Z combat replay finished.");
        recordedActions.clear();
        recordedActions.addAll(pendingReplayActions);
        cancelPendingReplay();
    }

    private static int findLastCardActionIndex(List<RecordedAction> actions) {
        for (int i = actions.size() - 1; i >= 0; --i) {
            if (actions.get(i).type == ActionType.CARD) {
                return i;
            }
        }

        return -1;
    }

    private enum ActionType {
        CARD,
        END_TURN
    }

    private static class RecordedAction {
        private final ActionType type;
        private final String cardId;
        private final int handIndex;
        private final int masterDeckIndex;
        private final int targetIndex;
        private final int timesUpgraded;
        private final int misc;
        private final int costForTurn;
        private final int energyOnUse;

        private RecordedAction(ActionType type, String cardId, int handIndex, int masterDeckIndex, int targetIndex, int timesUpgraded, int misc, int costForTurn, int energyOnUse) {
            this.type = type;
            this.cardId = cardId;
            this.handIndex = handIndex;
            this.masterDeckIndex = masterDeckIndex;
            this.targetIndex = targetIndex;
            this.timesUpgraded = timesUpgraded;
            this.misc = misc;
            this.costForTurn = costForTurn;
            this.energyOnUse = energyOnUse;
        }

        private static RecordedAction card(AbstractCard card, int handIndex, int targetIndex) {
            return new RecordedAction(
                    ActionType.CARD,
                    card.cardID,
                    handIndex,
                    getMasterDeckIndex(card),
                    targetIndex,
                    card.timesUpgraded,
                    card.misc,
                    card.costForTurn,
                    EnergyPanel.getCurrentEnergy()
        );
        }

        private static RecordedAction endTurn() {
            return new RecordedAction(ActionType.END_TURN, null, -1, -1, -1, 0, 0, 0, 0);
        }

        private boolean matches(AbstractCard card) {
            return card != null
                    && safeEquals(this.cardId, card.cardID)
                    && this.timesUpgraded == card.timesUpgraded
                    && this.misc == card.misc
                    && this.costForTurn == card.costForTurn;
        }

        private static boolean safeEquals(String left, String right) {
            if (left == null) {
                return right == null;
            }

            return left.equals(right);
        }
    }
}
