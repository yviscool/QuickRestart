package quickRestart.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoomBoss;
import com.megacrit.cardcrawl.rooms.VictoryRoom;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.saveAndContinue.SaveFileObfuscator;
import quickRestart.QuickRestart;

public class RoomSnapshotHelper {
    private static final Gson gson = new Gson();
    private static final String ROOM_SNAPSHOT_SUFFIX = ".quickRestartRoom";
    private static final String MANUAL_SNAPSHOT_SUFFIX = ".quickRestartManual";
    private static final float STATUS_CENTER_X = Settings.WIDTH / 2.0F;
    private static final float BASE_STATUS_LINE_SPACING = 26.0F;

    public static boolean suppressAutoRoomSnapshot = false;
    public static boolean forceSyncSave = false;

    private static String statusToast = null;
    private static boolean statusToastSuccess = true;
    private static float statusToastTimer = 0.0F;

    public static boolean canRoomRestart() {
        AbstractRoom currentRoom = getCurrentRoomSafely();
        if (!CardCrawlGame.isInARun() || currentRoom == null || !isRestartContextRoom(currentRoom)) {
            return false;
        }

        return hasCurrentManualSnapshot() || ensureCurrentRoomSnapshot();
    }

    public static void maybeUpdateSnapshot(String savePath) {
        if (suppressAutoRoomSnapshot || !isBaseAutosavePath(savePath)) {
            return;
        }

        try {
            String encodedData = Gdx.files.local(savePath).readString();
            String decodedData = SaveFileObfuscator.isObfuscated(encodedData)
                    ? SaveFileObfuscator.decode(encodedData, SaveFileObfuscator.key)
                    : encodedData;
            JsonObject root = gson.fromJson(decodedData, JsonObject.class);

            if (!shouldTrackSnapshot(root)) {
                return;
            }

            writeSnapshot(getRoomSnapshotPath(savePath), encodedData, encodedData);
            clearManualSnapshot(savePath);
        } catch (Exception e) {
            QuickRestart.runLogger.warn("Failed to update room snapshot for " + savePath, e);
        }
    }

    public static boolean restorePreferredSnapshot() {
        String savePath = getCurrentSavePath();
        if (savePath == null) {
            return false;
        }

        if (hasCurrentManualSnapshot()) {
            return restoreSnapshot(savePath, getManualSnapshotPath(savePath));
        }

        return restoreRoomSnapshot();
    }

    public static boolean restoreRoomSnapshot() {
        String savePath = getCurrentSavePath();
        if (savePath == null || !ensureCurrentRoomSnapshot()) {
            return false;
        }

        return restoreSnapshot(savePath, getRoomSnapshotPath(savePath));
    }

    public static boolean createManualSnapshot() {
        String savePath = getCurrentSavePath();
        if (!CardCrawlGame.isInARun() || AbstractDungeon.player == null || savePath == null || getCurrentRoomSafely() == null) {
            showStatusToast(false, localize("No run is active, checkpoint was not saved.", "当前不在一局游戏中，无法保存检查点。"));
            return false;
        }

        String originalSave = readFileIfExists(savePath);
        String originalBackup = readFileIfExists(savePath + ".backUp");

        suppressAutoRoomSnapshot = true;
        forceSyncSave = true;
        try {
            SaveAndContinue.save(new SaveFile(SaveFile.SaveType.ENDLESS_NEOW));

            String encodedData = readFileIfExists(savePath);
            if (encodedData == null) {
                showStatusToast(false, localize("Checkpoint save failed.", "保存检查点失败。"));
                return false;
            }

            String encodedBackup = readFileIfExists(savePath + ".backUp");
            writeSnapshot(getManualSnapshotPath(savePath), encodedData, encodedBackup != null ? encodedBackup : encodedData);
            showStatusToast(true, localize("Checkpoint saved.", "已保存检查点。"));
            QuickRestart.runLogger.info("Manual snapshot saved.");
            return true;
        } catch (Exception e) {
            QuickRestart.runLogger.warn("Failed to create manual snapshot.", e);
            showStatusToast(false, localize("Checkpoint save failed.", "保存检查点失败。"));
            return false;
        } finally {
            forceSyncSave = false;
            suppressAutoRoomSnapshot = false;
            restoreFile(savePath, originalSave);
            restoreFile(savePath + ".backUp", originalBackup);
        }
    }

    public static void clearCurrentSnapshots() {
        String savePath = getCurrentSavePath();
        if (savePath == null) {
            return;
        }

        deleteSnapshot(getRoomSnapshotPath(savePath));
        deleteSnapshot(getManualSnapshotPath(savePath));
    }

    public static void flashNoSnapshotMessage() {
        showStatusToast(false, localize("No checkpoint is available here.", "这里没有可用检查点。"));
    }

    public static void flashNoRoomSnapshotMessage() {
        showStatusToast(false, localize("No room-start checkpoint is available here.", "这里没有房间起点检查点。"));
    }

    public static void renderStatus(SpriteBatch sb) {
        if (!QuickRestart.isRR() || !CardCrawlGame.isInARun() || AbstractDungeon.player == null) {
            return;
        }

        AbstractRoom currentRoom = getCurrentRoomSafely();
        if (currentRoom == null) {
            return;
        }

        if (!hasCurrentManualSnapshot() && !hasCurrentRoomSnapshot() && currentRoom != null && isSnapshotEligibleRoom(currentRoom.getClass().getName())) {
            ensureCurrentRoomSnapshot();
        }

        if (statusToast != null) {
            statusToastTimer -= Gdx.graphics.getDeltaTime();
            if (statusToastTimer <= 0.0F) {
                statusToast = null;
            }
        }

        boolean showHotkeyHint = QuickRestart.isHotkeyHintEnabled();
        boolean showStatusLine = QuickRestart.isStatusLineEnabled();
        if (!showHotkeyHint && !showStatusLine && statusToast == null) {
            return;
        }

        float textScale = QuickRestart.getStatusTextScale();
        float lineSpacing = BASE_STATUS_LINE_SPACING * Settings.scale * textScale;
        float currentY = Settings.HEIGHT - QuickRestart.getStatusYOffset() * Settings.scale;

        if (showHotkeyHint) {
            FontHelper.renderFontCentered(
                    sb,
                    FontHelper.topPanelInfoFont,
                    getHotkeyHelpText(),
                    STATUS_CENTER_X,
                    currentY,
                    Settings.CREAM_COLOR,
                    textScale
            );
            currentY -= lineSpacing;
        }

        if (showStatusLine) {
            FontHelper.renderFontCentered(
                    sb,
                    FontHelper.tipBodyFont,
                    getSnapshotStatusText(),
                    STATUS_CENTER_X,
                    currentY,
                    Settings.CREAM_COLOR,
                    textScale
            );
            currentY -= lineSpacing;
        }

        if (statusToast != null) {
            Color toastColor = statusToastSuccess ? Settings.GREEN_TEXT_COLOR : Settings.RED_TEXT_COLOR;
            FontHelper.renderFontCentered(
                    sb,
                    FontHelper.tipBodyFont,
                    statusToast,
                    STATUS_CENTER_X,
                    currentY,
                    toastColor,
                    textScale
            );
        }
    }

    private static boolean hasCurrentRoomSnapshot() {
        String savePath = getCurrentSavePath();
        return savePath != null && Gdx.files.local(getRoomSnapshotPath(savePath)).exists();
    }

    private static boolean hasCurrentManualSnapshot() {
        String savePath = getCurrentSavePath();
        return savePath != null && Gdx.files.local(getManualSnapshotPath(savePath)).exists();
    }

    private static boolean ensureCurrentRoomSnapshot() {
        if (hasCurrentRoomSnapshot()) {
            return true;
        }

        String savePath = getCurrentSavePath();
        if (savePath == null || !Gdx.files.local(savePath).exists()) {
            return false;
        }

        try {
            String encodedData = Gdx.files.local(savePath).readString();
            if (!shouldTrackSnapshot(decodeSnapshotJson(encodedData))) {
                return false;
            }

            String encodedBackup = readFileIfExists(savePath + ".backUp");
            writeSnapshot(getRoomSnapshotPath(savePath), encodedData, encodedBackup != null ? encodedBackup : encodedData);
            QuickRestart.runLogger.info("Bootstrapped room snapshot from current autosave.");
            return true;
        } catch (Exception e) {
            QuickRestart.runLogger.warn("Failed to bootstrap room snapshot from current autosave.", e);
            return false;
        }
    }

    private static String getCurrentSavePath() {
        if (AbstractDungeon.player != null) {
            return SaveAndContinue.getPlayerSavePath(AbstractDungeon.player.chosenClass);
        }

        if (CardCrawlGame.chosenCharacter != null) {
            return SaveAndContinue.getPlayerSavePath(CardCrawlGame.chosenCharacter);
        }

        return null;
    }

    private static String getRoomSnapshotPath(String savePath) {
        return savePath + ROOM_SNAPSHOT_SUFFIX;
    }

    private static String getManualSnapshotPath(String savePath) {
        return savePath + MANUAL_SNAPSHOT_SUFFIX;
    }

    private static boolean isBaseAutosavePath(String savePath) {
        String normalized = savePath.replace('\\', '/');
        return normalized.startsWith("saves/")
                && normalized.endsWith(".autosave")
                && !normalized.contains(ROOM_SNAPSHOT_SUFFIX)
                && !normalized.contains(MANUAL_SNAPSHOT_SUFFIX);
    }

    private static boolean shouldTrackSnapshot(JsonObject root) {
        if (root == null || !root.has("current_room") || !root.has("post_combat")) {
            return false;
        }

        if (root.get("post_combat").getAsBoolean()) {
            return false;
        }

        return isSnapshotEligibleRoom(root.get("current_room").getAsString());
    }

    private static JsonObject decodeSnapshotJson(String encodedData) {
        String decodedData = SaveFileObfuscator.isObfuscated(encodedData)
                ? SaveFileObfuscator.decode(encodedData, SaveFileObfuscator.key)
                : encodedData;
        return gson.fromJson(decodedData, JsonObject.class);
    }

    private static boolean isSnapshotEligibleRoom(String roomClassName) {
        return roomClassName.equals(MonsterRoom.class.getName())
                || roomClassName.equals(MonsterRoomElite.class.getName())
                || roomClassName.equals(MonsterRoomBoss.class.getName())
                || roomClassName.equals(EventRoom.class.getName())
                || roomClassName.equals(ShopRoom.class.getName());
    }

    private static boolean isRestartContextRoom(AbstractRoom room) {
        return room instanceof MonsterRoom
                || room instanceof MonsterRoomElite
                || room instanceof MonsterRoomBoss
                || room instanceof EventRoom
                || room instanceof ShopRoom
                || room instanceof TreasureRoomBoss
                || room instanceof VictoryRoom;
    }

    private static boolean restoreSnapshot(String savePath, String snapshotPath) {
        if (!Gdx.files.local(snapshotPath).exists()) {
            return false;
        }

        String encodedData = Gdx.files.local(snapshotPath).readString();
        String encodedBackup = readFileIfExists(snapshotPath + ".backUp");
        writeSnapshot(savePath, encodedData, encodedBackup != null ? encodedBackup : encodedData);
        return true;
    }

    private static void writeSnapshot(String snapshotPath, String encodedData, String encodedBackup) {
        Gdx.files.local(snapshotPath).writeString(encodedData, false);
        Gdx.files.local(snapshotPath + ".backUp").writeString(encodedBackup, false);
    }

    private static void clearManualSnapshot(String savePath) {
        deleteSnapshot(getManualSnapshotPath(savePath));
    }

    private static void deleteSnapshot(String snapshotPath) {
        Gdx.files.local(snapshotPath).delete();
        Gdx.files.local(snapshotPath + ".backUp").delete();
    }

    private static String readFileIfExists(String path) {
        return Gdx.files.local(path).exists() ? Gdx.files.local(path).readString() : null;
    }

    private static void restoreFile(String path, String contents) {
        if (contents == null) {
            Gdx.files.local(path).delete();
        } else {
            Gdx.files.local(path).writeString(contents, false);
        }
    }

    private static void showStatusToast(boolean success, String message) {
        statusToastSuccess = success;
        statusToast = message;
        statusToastTimer = 3.0F;
    }

    private static String getSnapshotStatusText() {
        if (hasCurrentManualSnapshot()) {
            return localize("Checkpoint: manual", "当前检查点：手动");
        }

        if (hasCurrentRoomSnapshot()) {
            return localize("Checkpoint: room start", "当前检查点：房间起点");
        }

        return localize("Checkpoint: unavailable", "当前检查点：无");
    }

    private static String getHotkeyHelpText() {
        return localize(
                "Quick Restart  " + QuickRestart.getLatestRestartBindingText() + " latest  "
                        + QuickRestart.getSaveCheckpointBindingText() + " save  "
                        + QuickRestart.getRoomStartBindingText() + " room start",
                "快速重开  " + QuickRestart.getLatestRestartBindingText() + " 最近  "
                        + QuickRestart.getSaveCheckpointBindingText() + " 保存  "
                        + QuickRestart.getRoomStartBindingText() + " 房间起点"
        );
    }

    private static String localize(String english, String chinese) {
        return Settings.language == Settings.GameLanguage.ZHS ? chinese : english;
    }

    private static AbstractRoom getCurrentRoomSafely() {
        return AbstractDungeon.currMapNode != null ? AbstractDungeon.currMapNode.room : null;
    }
}
