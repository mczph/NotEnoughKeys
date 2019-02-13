package de.morrien.nekeys.voice;

import de.morrien.nekeys.Keybindings;
import de.morrien.nekeys.NotEnoughKeys;
import de.morrien.nekeys.gui.voice.popup.AbstractPopup;
import de.morrien.nekeys.voice.command.IVoiceCommand;
import de.morrien.nekeys.voice.command.IVoiceCommandTickable;
import de.morrien.nekeys.voice.command.OpenGuiVoiceCommand;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static de.morrien.nekeys.NotEnoughKeys.logger;

/**
 * Created by Timor Morrien
 */
public class VoiceHandler {

    private SpeechRecognizer recognizer;
    public HashMap<Class<? extends IVoiceCommand>, Class<? extends AbstractPopup>> popupBindingMap;
    private List<IVoiceCommand> voiceCommands;
    private Path configFile;

    public VoiceHandler() {
        voiceCommands = new ArrayList<>();
        popupBindingMap = new HashMap<>();
        configFile = NotEnoughKeys.instance.configDirectory.resolve("voice.config");
    }

    public void init() {
        logger.debug("Initializing recognizer.");
        recognizer = new SpeechRecognizer();

        reloadConfig();
        updateGrammar();
        recognizer.setEnabled(true);
    }

    private int tickBuffer;

    public void tickUpdate() {
        if (Keyboard.isKeyDown(Keybindings.PUSH_TO_TALK.getKeyCode())) {
            if (!recognizer.recording){
                recognizer.recording = true;
                tickBuffer = 20;
            }
        } else {
            if (recognizer.recording) {
                if (tickBuffer <= 0) {
                    recognizer.recording = false;
                } else {
                    tickBuffer--;
                }
            }
        }

        // Let the IVoiceCommandTickables tick
        voiceCommands.stream().filter(iVoiceCommand -> iVoiceCommand instanceof IVoiceCommandTickable).forEach(iVoiceCommand -> ((IVoiceCommandTickable)iVoiceCommand).tick());
        // Check if the user gave any command(s)
        while (recognizer.getQueueSize() > 0) {
            String command = recognizer.popString();
            command = command.toLowerCase();
            logger.debug("Command recognized: " + command);
            for (IVoiceCommand voiceKeybind : voiceCommands) {
                if (voiceKeybind.isValidCommand(command)) {
                    voiceKeybind.activate(command);
                    break;
                }
            }
        }
    }

    public void bindPopup(Class<? extends IVoiceCommand> voiceCommandClass, Class<? extends AbstractPopup> popupClass) {
        popupBindingMap.put(voiceCommandClass, popupClass);
    }

    public void reloadConfig() {
        if (Files.exists(configFile)) {
            try {
                voiceCommands = new ArrayList<>();
                List<String> lines = Files.readAllLines(configFile);
                for (String line : lines) {
                    IVoiceCommand cmd = parseLineToVoiceCommand(line);
                    if (cmd != null) voiceCommands.add(cmd);
                }
            } catch (IOException e) {
                logger.error("Could not read voice.config. Settings were not loaded!");
                logger.error(e.toString());
            }
        } else {
            voiceCommands = new ArrayList<>();
            voiceCommands.add(new OpenGuiVoiceCommand("voice", "(open voice settings)|(open voice)|(open speech settings)", OpenGuiVoiceCommand.AllowedGuis.VOICE_COMMANDS));
            saveConfig();
        }
    }

    public void saveConfig() {
        List<String> configLines = new ArrayList<>();
        for (IVoiceCommand voiceCommand : voiceCommands) {
            List<String> params = voiceCommand.getConfigParams();
            params.add(0, voiceCommand.getClass().getName());
            StringBuilder builder = new StringBuilder();
            for (String param : params) {
                builder.append(param).append(":");
            }
            builder.replace(builder.length()-1, builder.length(), "");
            configLines.add(builder.toString());
        }
        try {
            Files.write(configFile, configLines);
        } catch (IOException e) {
            logger.warn("Unable to save config file");
            logger.warn(e.toString());
        }
    }

    public IVoiceCommand parseLineToVoiceCommand(String line) {
        String[] splitLine = line.split(":");
        if (splitLine.length == 0) return null;
        try {
            Class<?> clazz = Class.forName(splitLine[0]);
            Constructor constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object object = constructor.newInstance();
            if (object instanceof IVoiceCommand) {
                IVoiceCommand command = (IVoiceCommand) object;
                command.fromConfigParams(line.split(":"));
                return command;
            } else {
                logger.warn("Line does not correspond to a IVoiceCommand " + line);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            logger.warn("Unable to read line " + line);
            logger.warn(e.toString());
        }
        return null;
    }

    public void updateGrammar() {
        boolean enabled = recognizer.isEnabled();
        if (enabled) recognizer.setEnabled(false);
        recognizer.removeAllRules();
        voiceCommands.forEach((voiceCommand) -> recognizer.setRule(voiceCommand.getName(), voiceCommand.getRuleContent()));
        if (enabled) recognizer.setEnabled(true);
    }

    public void addVoiceCommand(IVoiceCommand voiceCommand) {
        voiceCommands.add(voiceCommand);
    }

    public void removeVoiceCommand(IVoiceCommand voiceCommand) {
        voiceCommands.remove(voiceCommand);
    }

    public List<IVoiceCommand> getVoiceCommands() {
        return voiceCommands;
    }

    public SpeechRecognizer getRecognizer() {
        return recognizer;
    }
}
