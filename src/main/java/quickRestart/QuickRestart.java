package quickRestart;

import basemod.BaseMod;
import basemod.ModLabel;
import basemod.ModMinMaxSlider;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.ModTextInput;
import basemod.interfaces.EditStringsSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.StartGameSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.UIStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickRestart.helper.RestartRunHelper;
import quickRestart.helper.RoomSnapshotHelper;
import quickRestart.patches.FixAscenscionUnlockOnGameoverWinPatch;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Properties;

import static quickRestart.helper.RestartRunHelper.evilMode;
import static quickRestart.helper.RestartRunHelper.isDownfallMode;

@SpireInitializer
public class QuickRestart implements
        PostInitializeSubscriber,
        EditStringsSubscriber,
        PostRenderSubscriber,
        StartGameSubscriber {

    private static SpireConfig modConfig = null;
    private static String modID;
    public static final Logger runLogger = LogManager.getLogger(QuickRestart.class.getName());
    private static final String CFG_END_RESTART = "EndRestart";
    private static final String CFG_SETTINGS_RESTART = "SettingsRestart";
    private static final String CFG_ROOM_RESTART = "RoomRestart";
    private static final String CFG_LATEST_RESTART_KEY = "LatestRestartKey";
    private static final String CFG_SAVE_CHECKPOINT_KEY = "SaveCheckpointKey";
    private static final String CFG_ROOM_START_KEY = "RoomStartKey";
    private static final String CFG_SAVE_CHECKPOINT_SHIFT = "SaveCheckpointShift";
    private static final String CFG_ROOM_START_CTRL = "RoomStartCtrl";
    private static final String CFG_SHOW_STATUS_LINE = "ShowStatusLine";
    private static final String CFG_SHOW_HOTKEY_HINT = "ShowHotkeyHint";
    private static final String CFG_STATUS_Y_OFFSET = "StatusYOffset";
    private static final String CFG_STATUS_TEXT_SCALE = "StatusTextScale";
    private static final String DEFAULT_LATEST_RESTART_KEY = "F5";
    private static final String DEFAULT_SAVE_CHECKPOINT_KEY = "F5";
    private static final String DEFAULT_ROOM_START_KEY = "F5";
    private static final boolean DEFAULT_SAVE_CHECKPOINT_SHIFT = true;
    private static final boolean DEFAULT_ROOM_START_CTRL = true;
    private static final boolean DEFAULT_SHOW_STATUS_LINE = true;
    private static final boolean DEFAULT_SHOW_HOTKEY_HINT = true;
    private static final float DEFAULT_STATUS_Y_OFFSET = 86.0F;
    private static final float DEFAULT_STATUS_TEXT_SCALE = 1.0F;
    private static final int MAX_KEYCODE = 255;

    public static final boolean hasDownfall;
    static {
        hasDownfall = Loader.isModLoaded("downfall");
    }

    public static void initialize() {
        BaseMod.subscribe(new QuickRestart());
        setModID("quickRestart");

        try {
            Properties defaults = new Properties();
            defaults.put(CFG_END_RESTART, Boolean.toString(true));
            defaults.put(CFG_SETTINGS_RESTART, Boolean.toString(true));
            defaults.put(CFG_ROOM_RESTART, Boolean.toString(true));
            defaults.put(CFG_LATEST_RESTART_KEY, DEFAULT_LATEST_RESTART_KEY);
            defaults.put(CFG_SAVE_CHECKPOINT_KEY, DEFAULT_SAVE_CHECKPOINT_KEY);
            defaults.put(CFG_ROOM_START_KEY, DEFAULT_ROOM_START_KEY);
            defaults.put(CFG_SAVE_CHECKPOINT_SHIFT, Boolean.toString(DEFAULT_SAVE_CHECKPOINT_SHIFT));
            defaults.put(CFG_ROOM_START_CTRL, Boolean.toString(DEFAULT_ROOM_START_CTRL));
            defaults.put(CFG_SHOW_STATUS_LINE, Boolean.toString(DEFAULT_SHOW_STATUS_LINE));
            defaults.put(CFG_SHOW_HOTKEY_HINT, Boolean.toString(DEFAULT_SHOW_HOTKEY_HINT));
            defaults.put(CFG_STATUS_Y_OFFSET, Float.toString(DEFAULT_STATUS_Y_OFFSET));
            defaults.put(CFG_STATUS_TEXT_SCALE, Float.toString(DEFAULT_STATUS_TEXT_SCALE));
            modConfig = new SpireConfig("quickRestart", "Config", defaults);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isER() {
        return getBoolConfig(CFG_END_RESTART, false);
    }

    public static boolean isSR() {
        return getBoolConfig(CFG_SETTINGS_RESTART, false);
    }

    public static boolean isRR() {
        return getBoolConfig(CFG_ROOM_RESTART, false);
    }

    public static String getLatestRestartKeyName() {
        return getConfiguredKeyName(CFG_LATEST_RESTART_KEY, DEFAULT_LATEST_RESTART_KEY);
    }

    public static String getSaveCheckpointKeyName() {
        return getConfiguredKeyName(CFG_SAVE_CHECKPOINT_KEY, DEFAULT_SAVE_CHECKPOINT_KEY);
    }

    public static String getRoomStartKeyName() {
        return getConfiguredKeyName(CFG_ROOM_START_KEY, DEFAULT_ROOM_START_KEY);
    }

    public static boolean isSaveCheckpointShiftEnabled() {
        return getBoolConfig(CFG_SAVE_CHECKPOINT_SHIFT, DEFAULT_SAVE_CHECKPOINT_SHIFT);
    }

    public static boolean isRoomStartCtrlEnabled() {
        return getBoolConfig(CFG_ROOM_START_CTRL, DEFAULT_ROOM_START_CTRL);
    }

    public static boolean isStatusLineEnabled() {
        return getBoolConfig(CFG_SHOW_STATUS_LINE, DEFAULT_SHOW_STATUS_LINE);
    }

    public static boolean isHotkeyHintEnabled() {
        return getBoolConfig(CFG_SHOW_HOTKEY_HINT, DEFAULT_SHOW_HOTKEY_HINT);
    }

    public static float getStatusYOffset() {
        return getFloatConfig(CFG_STATUS_Y_OFFSET, DEFAULT_STATUS_Y_OFFSET, 56.0F, 180.0F);
    }

    public static float getStatusTextScale() {
        return getFloatConfig(CFG_STATUS_TEXT_SCALE, DEFAULT_STATUS_TEXT_SCALE, 0.75F, 1.40F);
    }

    public static String getLatestRestartBindingText() {
        return formatBinding(getLatestRestartKeyName(), false, false);
    }

    public static String getSaveCheckpointBindingText() {
        return formatBinding(getSaveCheckpointKeyName(), isSaveCheckpointShiftEnabled(), false);
    }

    public static String getRoomStartBindingText() {
        return formatBinding(getRoomStartKeyName(), false, isRoomStartCtrlEnabled());
    }

    @Override
    public void receivePostInitialize() {
        runLogger.info("Quick Restart is active.");

        UIStrings UIStrings = CardCrawlGame.languagePack.getUIString(QuickRestart.makeID("OptionsMenu"));
        String[] TEXT = UIStrings.TEXT;

        ModPanel settingsPanel = new ModPanel();
        ModLabeledToggleButton ERBtn = new ModLabeledToggleButton(TEXT[0], 350, 700, Settings.CREAM_COLOR, FontHelper.charDescFont, isER(), settingsPanel, l -> {
        },
                button ->
                {
                    setBoolConfig(CFG_END_RESTART, button.enabled);
                });
        settingsPanel.addUIElement(ERBtn);

        ModLabeledToggleButton SRBtn = new ModLabeledToggleButton(TEXT[1], 350, 650, Settings.CREAM_COLOR, FontHelper.charDescFont, isSR(), settingsPanel, l -> {
        },
                button ->
                {
                    setBoolConfig(CFG_SETTINGS_RESTART, button.enabled);
                });
        settingsPanel.addUIElement(SRBtn);

        ModLabeledToggleButton RRBtn = new ModLabeledToggleButton(TEXT[2], 350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont, isRR(), settingsPanel, l -> {
        },
                button ->
                {
                    setBoolConfig(CFG_ROOM_RESTART, button.enabled);
                });
        settingsPanel.addUIElement(RRBtn);
        settingsPanel.addUIElement(new ModLabel(localize("Hotkeys", "热键"), 350, 555, Settings.GOLD_COLOR, FontHelper.buttonLabelFont, settingsPanel, l -> {
        }));
        settingsPanel.addUIElement(new ModLabel(localize("Latest restart", "最近检查点"), 350, 520, Settings.CREAM_COLOR, FontHelper.tipBodyFont, settingsPanel, l -> {
        }));
        ModTextInput latestRestartInput = new ModTextInput(getLatestRestartKeyName(), 1010, 500, 170, 36, FontHelper.tipBodyFont, settingsPanel, input -> {
            updateConfiguredKeyInput(input, CFG_LATEST_RESTART_KEY, DEFAULT_LATEST_RESTART_KEY);
        });
        latestRestartInput.setCharacterLimit(18);
        settingsPanel.addUIElement(latestRestartInput);

        settingsPanel.addUIElement(new ModLabel(localize("Save checkpoint", "保存检查点"), 350, 470, Settings.CREAM_COLOR, FontHelper.tipBodyFont, settingsPanel, l -> {
        }));
        ModTextInput saveCheckpointInput = new ModTextInput(getSaveCheckpointKeyName(), 1010, 450, 170, 36, FontHelper.tipBodyFont, settingsPanel, input -> {
            updateConfiguredKeyInput(input, CFG_SAVE_CHECKPOINT_KEY, DEFAULT_SAVE_CHECKPOINT_KEY);
        });
        saveCheckpointInput.setCharacterLimit(18);
        settingsPanel.addUIElement(saveCheckpointInput);

        settingsPanel.addUIElement(new ModLabel(localize("Room start restart", "房间起点重开"), 350, 420, Settings.CREAM_COLOR, FontHelper.tipBodyFont, settingsPanel, l -> {
        }));
        ModTextInput roomStartInput = new ModTextInput(getRoomStartKeyName(), 1010, 400, 170, 36, FontHelper.tipBodyFont, settingsPanel, input -> {
            updateConfiguredKeyInput(input, CFG_ROOM_START_KEY, DEFAULT_ROOM_START_KEY);
        });
        roomStartInput.setCharacterLimit(18);
        settingsPanel.addUIElement(roomStartInput);

        settingsPanel.addUIElement(new ModLabel(localize("Use names like F5, R, Space, Left", "按键名称示例：F5、R、Space、Left"), 350, 372, Settings.CREAM_COLOR, FontHelper.tipBodyFont, settingsPanel, l -> {
        }));

        ModLabeledToggleButton saveShiftBtn = new ModLabeledToggleButton(localize("Save checkpoint hotkey requires Shift", "保存检查点热键需要 Shift"), 350, 335, Settings.CREAM_COLOR, FontHelper.charDescFont, isSaveCheckpointShiftEnabled(), settingsPanel, l -> {
        }, button -> {
            setBoolConfig(CFG_SAVE_CHECKPOINT_SHIFT, button.enabled);
        });
        settingsPanel.addUIElement(saveShiftBtn);

        ModLabeledToggleButton roomCtrlBtn = new ModLabeledToggleButton(localize("Room-start hotkey requires Ctrl", "房间起点热键需要 Ctrl"), 350, 285, Settings.CREAM_COLOR, FontHelper.charDescFont, isRoomStartCtrlEnabled(), settingsPanel, l -> {
        }, button -> {
            setBoolConfig(CFG_ROOM_START_CTRL, button.enabled);
        });
        settingsPanel.addUIElement(roomCtrlBtn);

        settingsPanel.addUIElement(new ModLabel(localize("Top Status", "顶部提示"), 350, 235, Settings.GOLD_COLOR, FontHelper.buttonLabelFont, settingsPanel, l -> {
        }));

        ModLabeledToggleButton statusLineBtn = new ModLabeledToggleButton(localize("Show checkpoint status text", "显示检查点状态文字"), 350, 200, Settings.CREAM_COLOR, FontHelper.charDescFont, isStatusLineEnabled(), settingsPanel, l -> {
        }, button -> {
            setBoolConfig(CFG_SHOW_STATUS_LINE, button.enabled);
        });
        settingsPanel.addUIElement(statusLineBtn);

        ModLabeledToggleButton hotkeyHintBtn = new ModLabeledToggleButton(localize("Show hotkey hint text", "显示热键提示文字"), 350, 150, Settings.CREAM_COLOR, FontHelper.charDescFont, isHotkeyHintEnabled(), settingsPanel, l -> {
        }, button -> {
            setBoolConfig(CFG_SHOW_HOTKEY_HINT, button.enabled);
        });
        settingsPanel.addUIElement(hotkeyHintBtn);

        ModMinMaxSlider statusOffsetSlider = new ModMinMaxSlider(localize("Top Offset", "顶部偏移"), 1010, 105, 56.0F, 180.0F, getStatusYOffset(), "%.0f", settingsPanel, slider -> {
            setFloatConfig(CFG_STATUS_Y_OFFSET, slider.getValue());
        });
        settingsPanel.addUIElement(statusOffsetSlider);

        ModMinMaxSlider statusScaleSlider = new ModMinMaxSlider(localize("Text Scale", "文字缩放"), 1010, 60, 0.75F, 1.40F, getStatusTextScale(), "%.2f", settingsPanel, slider -> {
            setFloatConfig(CFG_STATUS_TEXT_SCALE, slider.getValue());
        });
        settingsPanel.addUIElement(statusScaleSlider);

        settingsPanel.addUIElement(new ModLabel(localize("Status text is centered below the top header.", "状态文字会显示在顶部 header 下方中间。"), 350, 30, Settings.CREAM_COLOR, FontHelper.tipBodyFont, settingsPanel, l -> {
        }));

        BaseMod.registerModBadge(ImageMaster.loadImage(getModID() + "Resources/img/modBadge.png"), getModID(), "erasels", "TODO", settingsPanel);
    }

    @Override
    public void receiveEditStrings() {
        loadLocStrings("eng");
        if (!languageSupport().equals("eng")) {
            loadLocStrings(languageSupport());
        }
    }

    public static String getModID() {
        return modID;
    }

    public static void setModID(String id) {
        modID = id;
    }

    public static String makeID(String idText) {
        return getModID() + ":" + idText;
    }

    @Override
    public void receivePostRender(SpriteBatch spriteBatch) {
        handleRoomRestartHotkeys();

        if (RestartRunHelper.queuedScoreRestart) {
            runLogger.info("Scoring and run restart has been initialized. (Settings)");
            RestartRunHelper.scoreAndRestart();
        } else if (RestartRunHelper.queuedRestart) {
            runLogger.info("Run restart has been initialized. (Death/Victory)");
            RestartRunHelper.restartRun();
        } else if (RestartRunHelper.queuedSnapshotRestart) {
            runLogger.info("Snapshot restart has been initialized. (Hotkey)");
            RestartRunHelper.restartLatestSnapshot();
        } else if (RestartRunHelper.queuedRoomRestart) {
            runLogger.info("Room restart has been initialized. (Settings)");
            RestartRunHelper.restartRoom();
        }

        RoomSnapshotHelper.renderStatus(spriteBatch);
    }

    private String languageSupport() {
		String language = Settings.language.name().toLowerCase();
		String urlPath = getModID() + "Resources/localization/" + language + "/UI-Strings.json";
		ClassLoader classLoader = UIStrings.class.getClassLoader();
		URL url = classLoader.getResource(urlPath);

		if (url != null) {
			return language;
		} else {
			return "eng";
		}
		
    }

    private void loadLocStrings(String language) {
        BaseMod.loadCustomStringsFile(UIStrings.class, getModID() + "Resources/localization/" + language + "/UI-Strings.json");
    }

    @Override
    public void receiveStartGame() {
        FixAscenscionUnlockOnGameoverWinPatch.updateAscProgress = true;
        if (!CardCrawlGame.loadingSave) {
            RoomSnapshotHelper.clearCurrentSnapshots();
        }
        if(hasDownfall) {
            evilMode = isDownfallMode();
        }
    }

    private void handleRoomRestartHotkeys() {
        if (!isRR() || !CardCrawlGame.isInARun() || AbstractDungeon.player == null) {
            return;
        }

        if (isConfiguredHotkeyPressed(getConfiguredKeyCode(CFG_SAVE_CHECKPOINT_KEY, DEFAULT_SAVE_CHECKPOINT_KEY), isSaveCheckpointShiftEnabled(), false)) {
            RoomSnapshotHelper.createManualSnapshot();
            return;
        }

        if (isConfiguredHotkeyPressed(getConfiguredKeyCode(CFG_ROOM_START_KEY, DEFAULT_ROOM_START_KEY), false, isRoomStartCtrlEnabled())) {
            if (RoomSnapshotHelper.canRoomRestart()) {
                RestartRunHelper.queuedRoomRestart = true;
            } else {
                RoomSnapshotHelper.flashNoRoomSnapshotMessage();
            }
            return;
        }

        if (isConfiguredHotkeyPressed(getConfiguredKeyCode(CFG_LATEST_RESTART_KEY, DEFAULT_LATEST_RESTART_KEY), false, false)) {
            if (RoomSnapshotHelper.canRoomRestart()) {
                RestartRunHelper.queuedSnapshotRestart = true;
            } else {
                RoomSnapshotHelper.flashNoSnapshotMessage();
            }
        }
    }

    private boolean isConfiguredHotkeyPressed(int keycode, boolean requireShift, boolean requireCtrl) {
        if (keycode < 0 || !Gdx.input.isKeyJustPressed(keycode)) {
            return false;
        }

        return isShiftPressed() == requireShift && isControlPressed() == requireCtrl;
    }

    private boolean isShiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    private boolean isControlPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.SYM);
    }

    private static void updateConfiguredKeyInput(ModTextInput input, String configKey, String defaultKeyName) {
        int keycode = parseKeyCode(input.text);
        if (keycode < 0) {
            input.setText(getConfiguredKeyName(configKey, defaultKeyName));
            return;
        }

        String keyName = Input.Keys.toString(keycode);
        input.setText(keyName);
        setStringConfig(configKey, keyName);
    }

    private static String getConfiguredKeyName(String configKey, String defaultKeyName) {
        String keyName = getStringConfig(configKey, defaultKeyName);
        int keycode = parseKeyCode(keyName);
        return keycode >= 0 ? Input.Keys.toString(keycode) : defaultKeyName;
    }

    private static int getConfiguredKeyCode(String configKey, String defaultKeyName) {
        return parseKeyCode(getConfiguredKeyName(configKey, defaultKeyName));
    }

    private static int parseKeyCode(String keyName) {
        if (keyName == null) {
            return -1;
        }

        String normalized = normalizeKeyToken(keyName);
        if (normalized.isEmpty()) {
            return -1;
        }

        for (int keycode = 0; keycode <= MAX_KEYCODE; ++keycode) {
            String displayName = Input.Keys.toString(keycode);
            if (displayName != null && normalizeKeyToken(displayName).equals(normalized)) {
                return keycode;
            }
        }

        return -1;
    }

    private static String normalizeKeyToken(String keyName) {
        return keyName.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String formatBinding(String keyName, boolean useShift, boolean useCtrl) {
        StringBuilder builder = new StringBuilder();
        if (useCtrl) {
            builder.append("Ctrl+");
        }
        if (useShift) {
            builder.append("Shift+");
        }
        builder.append(keyName);
        return builder.toString();
    }

    private static boolean getBoolConfig(String key, boolean defaultValue) {
        if (modConfig == null) {
            return defaultValue;
        }

        return modConfig.has(key) ? modConfig.getBool(key) : defaultValue;
    }

    private static String getStringConfig(String key, String defaultValue) {
        if (modConfig == null) {
            return defaultValue;
        }

        return modConfig.has(key) ? modConfig.getString(key) : defaultValue;
    }

    private static float getFloatConfig(String key, float defaultValue, float minValue, float maxValue) {
        if (modConfig == null || !modConfig.has(key)) {
            return defaultValue;
        }

        try {
            return clamp(modConfig.getFloat(key), minValue, maxValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void setBoolConfig(String key, boolean value) {
        if (modConfig == null) {
            return;
        }

        modConfig.setBool(key, value);
        saveConfigQuietly();
    }

    private static void setStringConfig(String key, String value) {
        if (modConfig == null) {
            return;
        }

        modConfig.setString(key, value);
        saveConfigQuietly();
    }

    private static void setFloatConfig(String key, float value) {
        if (modConfig == null) {
            return;
        }

        modConfig.setFloat(key, value);
        saveConfigQuietly();
    }

    private static void saveConfigQuietly() {
        if (modConfig == null) {
            return;
        }

        try {
            modConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static float clamp(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private static String localize(String english, String chinese) {
        return Settings.language == Settings.GameLanguage.ZHS ? chinese : english;
    }
}
