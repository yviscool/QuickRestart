package quickRestart.helper;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.EnergyManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.stances.AbstractStance;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import quickRestart.QuickRestart;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public class CombatUndoHelper {
    private static final int MAX_SNAPSHOT_DEPTH = 32;
    private static final HashMap<Class<?>, ArrayList<Field>> FIELD_CACHE = new HashMap<Class<?>, ArrayList<Field>>();
    private static final ArrayList<CombatStateSnapshot> snapshotStack = new ArrayList<CombatStateSnapshot>();
    private static boolean restoringSnapshot = false;
    private static boolean suppressHoveredCardSelection = false;

    public static void resetAll() {
        snapshotStack.clear();
        restoringSnapshot = false;
        suppressHoveredCardSelection = false;
    }

    public static void onCombatStart() {
        snapshotStack.clear();
        restoringSnapshot = false;
        suppressHoveredCardSelection = false;
    }

    public static boolean hasUndoableHistory() {
        return !snapshotStack.isEmpty();
    }

    public static boolean requestUndo() {
        if (!RoomSnapshotHelper.isUndoContextActive() || snapshotStack.isEmpty() || restoringSnapshot) {
            return false;
        }

        CombatStateSnapshot snapshot = snapshotStack.remove(snapshotStack.size() - 1);
        try {
            restoringSnapshot = true;
            snapshot.restore();
            suppressHoveredCardSelection = true;
            QuickRestart.runLogger.info("Combat Ctrl+Z snapshot restored.");
            return true;
        } catch (Exception e) {
            snapshotStack.clear();
            suppressHoveredCardSelection = false;
            QuickRestart.runLogger.warn("Failed to restore combat Ctrl+Z snapshot.", e);
            RoomSnapshotHelper.flashUndoRestoreFailedMessage();
            return false;
        } finally {
            restoringSnapshot = false;
        }
    }

    public static void captureSnapshotBeforeCardPlay(AbstractPlayer player, AbstractCard card) {
        if (restoringSnapshot || player == null || card == null || !isSnapshotCaptureContextReady()) {
            return;
        }

        try {
            snapshotStack.add(CombatStateSnapshot.capture());
            if (snapshotStack.size() > MAX_SNAPSHOT_DEPTH) {
                snapshotStack.remove(0);
            }
        } catch (Exception e) {
            snapshotStack.clear();
            QuickRestart.runLogger.warn("Failed to capture combat Ctrl+Z snapshot.", e);
        }
    }

    public static void cancelPendingReplay() {
        // Compatibility no-op while the old replay-based undo path is retired.
    }

    public static boolean shouldBlockHoveredCardSelection() {
        if (!suppressHoveredCardSelection) {
            return false;
        }

        if (InputHelper.didMoveMouse()) {
            suppressHoveredCardSelection = false;
            return false;
        }

        return true;
    }

    private static boolean isSnapshotCaptureContextReady() {
        AbstractRoom currentRoom = getCurrentRoomSafely();
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY
                && CardCrawlGame.isInARun()
                && !CardCrawlGame.loadingSave
                && AbstractDungeon.player != null
                && currentRoom != null
                && currentRoom.phase == AbstractRoom.RoomPhase.COMBAT
                && AbstractRoom.waitTimer <= 0.0f
                && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.NONE
                && AbstractDungeon.actionManager != null
                && AbstractDungeon.overlayMenu != null
                && AbstractDungeon.overlayMenu.endTurnButton != null
                && AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER
                && !AbstractDungeon.actionManager.hasControl
                && AbstractDungeon.actionManager.currentAction == null
                && AbstractDungeon.actionManager.actions.isEmpty()
                && AbstractDungeon.actionManager.cardQueue.isEmpty()
                && !AbstractDungeon.player.endTurnQueued
                && !AbstractDungeon.player.isEndingTurn;
    }

    private static AbstractRoom getCurrentRoomSafely() {
        return AbstractDungeon.currMapNode == null ? null : AbstractDungeon.currMapNode.room;
    }

    private static boolean shouldSnapshotObject(Object value) {
        return value instanceof AbstractPlayer
                || value instanceof AbstractMonster
                || value instanceof MonsterGroup
                || value instanceof AbstractCard
                || value instanceof CardGroup
                || value instanceof AbstractPower
                || value instanceof AbstractRelic
                || value instanceof AbstractPotion
                || value instanceof AbstractOrb
                || value instanceof AbstractStance
                || value instanceof GameActionManager
                || value instanceof DamageInfo
                || value instanceof EnergyManager
                || value instanceof AbstractRoom;
    }

    private static boolean isDirectValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum
                || value instanceof UUID
                || value instanceof Class;
    }

    private static ArrayList<Field> getSnapshotFields(Class<?> clazz) {
        ArrayList<Field> cachedFields = FIELD_CACHE.get(clazz);
        if (cachedFields != null) {
            return cachedFields;
        }

        ArrayList<Field> fields = new ArrayList<Field>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                field.setAccessible(true);
                fields.add(field);
            }
            current = current.getSuperclass();
        }

        FIELD_CACHE.put(clazz, fields);
        return fields;
    }

    private static class CombatStateSnapshot {
        private final IdentityHashMap<Object, ObjectState> objectStatesByObject = new IdentityHashMap<Object, ObjectState>();
        private final ArrayList<ObjectState> objectStates = new ArrayList<ObjectState>();
        private final StaticCollectionSnapshot effectListSnapshot;
        private final StaticCollectionSnapshot effectsQueueSnapshot;
        private final StaticCollectionSnapshot topLevelEffectsSnapshot;
        private final StaticCollectionSnapshot topLevelEffectsQueueSnapshot;
        private final DungeonRandomState dungeonRandomState;
        private final ActionManagerStaticState actionManagerStaticState;
        private final EnergyPanelState energyPanelState;
        private final EndTurnButtonState endTurnButtonState;
        private final AbstractDungeon.CurrentScreen screen;
        private final boolean isScreenUp;
        private final float roomWaitTimer;
        private final int cardBlizzRandomizer;

        private CombatStateSnapshot() {
            this.effectListSnapshot = StaticCollectionSnapshot.capture(AbstractDungeon.effectList);
            this.effectsQueueSnapshot = StaticCollectionSnapshot.capture(AbstractDungeon.effectsQueue);
            this.topLevelEffectsSnapshot = StaticCollectionSnapshot.capture(AbstractDungeon.topLevelEffects);
            this.topLevelEffectsQueueSnapshot = StaticCollectionSnapshot.capture(AbstractDungeon.topLevelEffectsQueue);
            this.dungeonRandomState = new DungeonRandomState();
            this.actionManagerStaticState = new ActionManagerStaticState();
            this.energyPanelState = new EnergyPanelState();
            this.endTurnButtonState = new EndTurnButtonState(AbstractDungeon.overlayMenu.endTurnButton);
            this.screen = AbstractDungeon.screen;
            this.isScreenUp = AbstractDungeon.isScreenUp;
            this.roomWaitTimer = AbstractRoom.waitTimer;
            this.cardBlizzRandomizer = AbstractDungeon.cardBlizzRandomizer;
        }

        public static CombatStateSnapshot capture() {
            CombatStateSnapshot snapshot = new CombatStateSnapshot();
            AbstractRoom currentRoom = getCurrentRoomSafely();
            if (currentRoom == null || AbstractDungeon.player == null || AbstractDungeon.actionManager == null) {
                throw new IllegalStateException("Combat snapshot capture requires an active combat room.");
            }

            snapshot.captureObject(AbstractDungeon.player);
            snapshot.captureObject(currentRoom);
            snapshot.captureObject(AbstractDungeon.actionManager);
            return snapshot;
        }

        public void restore() throws IllegalAccessException {
            this.dungeonRandomState.restore();
            this.actionManagerStaticState.restore();
            this.energyPanelState.restore();
            AbstractRoom.waitTimer = this.roomWaitTimer;
            AbstractDungeon.cardBlizzRandomizer = this.cardBlizzRandomizer;
            AbstractDungeon.screen = this.screen;
            AbstractDungeon.isScreenUp = this.isScreenUp;

            for (ObjectState objectState : this.objectStates) {
                objectState.restore();
            }

            this.effectListSnapshot.restore(AbstractDungeon.effectList);
            this.effectsQueueSnapshot.restore(AbstractDungeon.effectsQueue);
            this.topLevelEffectsSnapshot.restore(AbstractDungeon.topLevelEffects);
            this.topLevelEffectsQueueSnapshot.restore(AbstractDungeon.topLevelEffectsQueue);
            if (AbstractDungeon.overlayMenu != null && AbstractDungeon.overlayMenu.endTurnButton != null) {
                this.endTurnButtonState.restore(AbstractDungeon.overlayMenu.endTurnButton);
            }

            restoreCombatPresentation();
        }

        private void captureObject(Object target) {
            if (target == null || !shouldSnapshotObject(target) || this.objectStatesByObject.containsKey(target)) {
                return;
            }

            ObjectState objectState = new ObjectState(target);
            this.objectStatesByObject.put(target, objectState);
            this.objectStates.add(objectState);

            for (Field field : getSnapshotFields(target.getClass())) {
                try {
                    Object fieldValue = field.get(target);
                    objectState.addFieldState(createFieldState(field, fieldValue));
                    captureNestedValue(fieldValue);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to capture field " + field.getName() + " on " + target.getClass().getName(), e);
                }
            }
        }

        private FieldState createFieldState(Field field, Object fieldValue) {
            if (fieldValue instanceof Color) {
                return new ColorFieldState(field, (Color) fieldValue);
            }

            if (fieldValue instanceof Hitbox) {
                return new HitboxFieldState(field, (Hitbox) fieldValue);
            }

            if (fieldValue == null || isDirectValue(fieldValue) || !isMutableContainer(fieldValue)) {
                return new SimpleFieldState(field, fieldValue);
            }

            if (fieldValue instanceof Map) {
                return new MapFieldState(field, (Map<?, ?>) fieldValue);
            }

            if (fieldValue instanceof Collection) {
                return new CollectionFieldState(field, (Collection<?>) fieldValue);
            }

            if (fieldValue.getClass().isArray()) {
                return new ArrayFieldState(field, fieldValue);
            }

            return new SimpleFieldState(field, fieldValue);
        }

        private void captureNestedValue(Object value) {
            if (value == null) {
                return;
            }

            if (value instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    captureNestedValue(entry.getKey());
                    captureNestedValue(entry.getValue());
                }
                return;
            }

            if (value instanceof Collection) {
                for (Object item : (Collection<?>) value) {
                    captureNestedValue(item);
                }
                return;
            }

            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; ++i) {
                    captureNestedValue(Array.get(value, i));
                }
                return;
            }

            if (shouldSnapshotObject(value)) {
                captureObject(value);
            }
        }

        private boolean isMutableContainer(Object value) {
            return value instanceof Collection || value instanceof Map || value.getClass().isArray();
        }

        private void restoreCombatPresentation() {
            AbstractDungeon.player.releaseCard();
            AbstractDungeon.player.toHover = null;
            AbstractDungeon.player.hoveredCard = null;
            AbstractDungeon.player.cardInUse = null;
            AbstractDungeon.player.isDraggingCard = false;
            AbstractDungeon.player.isHoveringDropZone = false;
            AbstractDungeon.player.inSingleTargetMode = false;
            AbstractDungeon.player.healthBarUpdatedEvent();
            AbstractDungeon.player.hand.refreshHandLayout();
            AbstractDungeon.player.hand.applyPowers();
            AbstractDungeon.player.hand.glowCheck();
            for (AbstractCard card : AbstractDungeon.player.hand.group) {
                card.unhover();
                card.untip();
                card.hb.unhover();
                card.hb.clickStarted = false;
                card.hb.clicked = false;
                card.hoverTimer = 0.25f;
                card.unfadeOut();
                card.lighten(true);
                card.current_x = card.target_x;
                card.current_y = card.target_y;
                card.drawScale = card.targetDrawScale;
                card.setAngle(card.targetAngle, true);
            }
            AbstractDungeon.player.hand.glowCheck();
            AbstractDungeon.player.updateOrb(EnergyPanel.totalCount);
            if (AbstractDungeon.overlayMenu != null) {
                AbstractDungeon.overlayMenu.showCombatPanels();
            }
            AbstractDungeon.onModifyPower();

            AbstractRoom currentRoom = getCurrentRoomSafely();
            if (currentRoom != null && currentRoom.monsters != null) {
                for (AbstractMonster monster : currentRoom.monsters.monsters) {
                    monster.healthBarUpdatedEvent();
                    monster.createIntent();
                }
            }
        }
    }

    private static class ObjectState {
        private final Object target;
        private final ArrayList<FieldState> fieldStates = new ArrayList<FieldState>();

        private ObjectState(Object target) {
            this.target = target;
        }

        private void addFieldState(FieldState fieldState) {
            this.fieldStates.add(fieldState);
        }

        private void restore() throws IllegalAccessException {
            for (FieldState fieldState : this.fieldStates) {
                fieldState.restore(this.target);
            }
        }
    }

    private interface FieldState {
        void restore(Object target) throws IllegalAccessException;
    }

    private static class SimpleFieldState implements FieldState {
        private final Field field;
        private final Object value;

        private SimpleFieldState(Field field, Object value) {
            this.field = field;
            this.value = value;
        }

        @Override
        public void restore(Object target) throws IllegalAccessException {
            this.field.set(target, this.value);
        }
    }

    private static class ColorFieldState implements FieldState {
        private final Field field;
        private final float r;
        private final float g;
        private final float b;
        private final float a;

        private ColorFieldState(Field field, Color source) {
            this.field = field;
            this.r = source.r;
            this.g = source.g;
            this.b = source.b;
            this.a = source.a;
        }

        @Override
        public void restore(Object target) throws IllegalAccessException {
            Object currentValue = this.field.get(target);
            if (currentValue instanceof Color) {
                Color color = (Color) currentValue;
                color.r = this.r;
                color.g = this.g;
                color.b = this.b;
                color.a = this.a;
                return;
            }

            this.field.set(target, new Color(this.r, this.g, this.b, this.a));
        }
    }

    private static class HitboxFieldState implements FieldState {
        private final Field field;
        private final float x;
        private final float y;
        private final float cX;
        private final float cY;
        private final float width;
        private final float height;
        private final boolean hovered;
        private final boolean justHovered;
        private final boolean clickStarted;
        private final boolean clicked;

        private HitboxFieldState(Field field, Hitbox source) {
            this.field = field;
            this.x = source.x;
            this.y = source.y;
            this.cX = source.cX;
            this.cY = source.cY;
            this.width = source.width;
            this.height = source.height;
            this.hovered = source.hovered;
            this.justHovered = source.justHovered;
            this.clickStarted = source.clickStarted;
            this.clicked = source.clicked;
        }

        @Override
        public void restore(Object target) throws IllegalAccessException {
            Object currentValue = this.field.get(target);
            if (currentValue instanceof Hitbox) {
                Hitbox hitbox = (Hitbox) currentValue;
                hitbox.x = this.x;
                hitbox.y = this.y;
                hitbox.cX = this.cX;
                hitbox.cY = this.cY;
                hitbox.width = this.width;
                hitbox.height = this.height;
                hitbox.hovered = this.hovered;
                hitbox.justHovered = this.justHovered;
                hitbox.clickStarted = this.clickStarted;
                hitbox.clicked = this.clicked;
                return;
            }

            Hitbox hitbox = new Hitbox(this.width, this.height);
            hitbox.x = this.x;
            hitbox.y = this.y;
            hitbox.cX = this.cX;
            hitbox.cY = this.cY;
            hitbox.width = this.width;
            hitbox.height = this.height;
            hitbox.hovered = this.hovered;
            hitbox.justHovered = this.justHovered;
            hitbox.clickStarted = this.clickStarted;
            hitbox.clicked = this.clicked;
            this.field.set(target, hitbox);
        }
    }

    private static class CollectionFieldState implements FieldState {
        private final Field field;
        private final ArrayList<Object> elements = new ArrayList<Object>();

        private CollectionFieldState(Field field, Collection<?> source) {
            this.field = field;
            this.elements.addAll(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void restore(Object target) throws IllegalAccessException {
            Object currentValue = this.field.get(target);
            if (!(currentValue instanceof Collection)) {
                return;
            }

            Collection<Object> collection = (Collection<Object>) currentValue;
            collection.clear();
            collection.addAll(this.elements);
        }
    }

    private static class MapFieldState implements FieldState {
        private final Field field;
        private final ArrayList<MapEntryState> entries = new ArrayList<MapEntryState>();

        private MapFieldState(Field field, Map<?, ?> source) {
            this.field = field;
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                this.entries.add(new MapEntryState(entry.getKey(), entry.getValue()));
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void restore(Object target) throws IllegalAccessException {
            Object currentValue = this.field.get(target);
            if (!(currentValue instanceof Map)) {
                return;
            }

            Map<Object, Object> map = (Map<Object, Object>) currentValue;
            map.clear();
            for (MapEntryState entry : this.entries) {
                map.put(entry.key, entry.value);
            }
        }
    }

    private static class ArrayFieldState implements FieldState {
        private final Field field;
        private final Object snapshotArray;

        private ArrayFieldState(Field field, Object sourceArray) {
            this.field = field;
            int length = Array.getLength(sourceArray);
            this.snapshotArray = Array.newInstance(sourceArray.getClass().getComponentType(), length);
            System.arraycopy(sourceArray, 0, this.snapshotArray, 0, length);
        }

        @Override
        public void restore(Object target) throws IllegalAccessException {
            int length = Array.getLength(this.snapshotArray);
            Object restoredArray = Array.newInstance(this.snapshotArray.getClass().getComponentType(), length);
            System.arraycopy(this.snapshotArray, 0, restoredArray, 0, length);
            this.field.set(target, restoredArray);
        }
    }

    private static class MapEntryState {
        private final Object key;
        private final Object value;

        private MapEntryState(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class StaticCollectionSnapshot {
        private final ArrayList<Object> elements = new ArrayList<Object>();

        private StaticCollectionSnapshot(Collection<?> source) {
            this.elements.addAll(source);
        }

        public static StaticCollectionSnapshot capture(Collection<?> source) {
            return new StaticCollectionSnapshot(source);
        }

        @SuppressWarnings("unchecked")
        public void restore(Collection<?> target) {
            Collection<Object> typedTarget = (Collection<Object>) target;
            typedTarget.clear();
            typedTarget.addAll(this.elements);
        }
    }

    private static class DungeonRandomState {
        private final Random monsterRng = copyRandom(AbstractDungeon.monsterRng);
        private final Random mapRng = copyRandom(AbstractDungeon.mapRng);
        private final Random eventRng = copyRandom(AbstractDungeon.eventRng);
        private final Random merchantRng = copyRandom(AbstractDungeon.merchantRng);
        private final Random cardRng = copyRandom(AbstractDungeon.cardRng);
        private final Random treasureRng = copyRandom(AbstractDungeon.treasureRng);
        private final Random relicRng = copyRandom(AbstractDungeon.relicRng);
        private final Random potionRng = copyRandom(AbstractDungeon.potionRng);
        private final Random monsterHpRng = copyRandom(AbstractDungeon.monsterHpRng);
        private final Random aiRng = copyRandom(AbstractDungeon.aiRng);
        private final Random shuffleRng = copyRandom(AbstractDungeon.shuffleRng);
        private final Random cardRandomRng = copyRandom(AbstractDungeon.cardRandomRng);
        private final Random miscRng = copyRandom(AbstractDungeon.miscRng);

        private void restore() {
            AbstractDungeon.monsterRng = copyRandom(this.monsterRng);
            AbstractDungeon.mapRng = copyRandom(this.mapRng);
            AbstractDungeon.eventRng = copyRandom(this.eventRng);
            AbstractDungeon.merchantRng = copyRandom(this.merchantRng);
            AbstractDungeon.cardRng = copyRandom(this.cardRng);
            AbstractDungeon.treasureRng = copyRandom(this.treasureRng);
            AbstractDungeon.relicRng = copyRandom(this.relicRng);
            AbstractDungeon.potionRng = copyRandom(this.potionRng);
            AbstractDungeon.monsterHpRng = copyRandom(this.monsterHpRng);
            AbstractDungeon.aiRng = copyRandom(this.aiRng);
            AbstractDungeon.shuffleRng = copyRandom(this.shuffleRng);
            AbstractDungeon.cardRandomRng = copyRandom(this.cardRandomRng);
            AbstractDungeon.miscRng = copyRandom(this.miscRng);
        }
    }

    private static class ActionManagerStaticState {
        private final int turn = GameActionManager.turn;
        private final int totalDiscardedThisTurn = GameActionManager.totalDiscardedThisTurn;
        private final int damageReceivedThisTurn = GameActionManager.damageReceivedThisTurn;
        private final int damageReceivedThisCombat = GameActionManager.damageReceivedThisCombat;
        private final int hpLossThisCombat = GameActionManager.hpLossThisCombat;
        private final int playerHpLastTurn = GameActionManager.playerHpLastTurn;
        private final int energyGainedThisCombat = GameActionManager.energyGainedThisCombat;

        private void restore() {
            GameActionManager.turn = this.turn;
            GameActionManager.totalDiscardedThisTurn = this.totalDiscardedThisTurn;
            GameActionManager.damageReceivedThisTurn = this.damageReceivedThisTurn;
            GameActionManager.damageReceivedThisCombat = this.damageReceivedThisCombat;
            GameActionManager.hpLossThisCombat = this.hpLossThisCombat;
            GameActionManager.playerHpLastTurn = this.playerHpLastTurn;
            GameActionManager.energyGainedThisCombat = this.energyGainedThisCombat;
        }
    }

    private static class EnergyPanelState {
        private final int totalCount = EnergyPanel.totalCount;
        private final float fontScale = EnergyPanel.fontScale;
        private final float energyVfxTimer = EnergyPanel.energyVfxTimer;

        private void restore() {
            EnergyPanel.totalCount = this.totalCount;
            EnergyPanel.fontScale = this.fontScale;
            EnergyPanel.energyVfxTimer = this.energyVfxTimer;
        }
    }

    private static class EndTurnButtonState {
        private static final Field LABEL_FIELD = getLabelField();
        private final boolean enabled;
        private final boolean isGlowing;
        private final String label;

        private EndTurnButtonState(EndTurnButton button) {
            this.enabled = button.enabled;
            this.isGlowing = button.isGlowing;
            this.label = readLabel(button);
        }

        private void restore(EndTurnButton button) {
            button.enabled = this.enabled;
            button.isGlowing = this.isGlowing;
            writeLabel(button, this.label);
        }

        private static Field getLabelField() {
            try {
                Field field = EndTurnButton.class.getDeclaredField("label");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Failed to access EndTurnButton.label", e);
            }
        }

        private static String readLabel(EndTurnButton button) {
            try {
                return (String) LABEL_FIELD.get(button);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to read EndTurnButton.label", e);
            }
        }

        private static void writeLabel(EndTurnButton button, String value) {
            try {
                LABEL_FIELD.set(button, value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to write EndTurnButton.label", e);
            }
        }
    }

    private static Random copyRandom(Random rng) {
        return rng == null ? null : rng.copy();
    }
}
