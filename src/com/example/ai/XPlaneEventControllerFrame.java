package com.example.ai;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.nio.file.Path;

public class XPlaneEventControllerFrame extends JFrame {
    private final String sessionId;
    private final Path stateCsvPath;
    private final Path eventCsvPath;
    private final XPlaneEventLogger eventLogger;
    private final Runnable quitAction;
    private final JLabel lastEventLabel;

    public XPlaneEventControllerFrame(
            String sessionId,
            Path stateCsvPath,
            Path eventCsvPath,
            XPlaneEventLogger eventLogger,
            Runnable quitAction
    ) {
        super("X-Plane Event Controller");
        this.sessionId = sessionId;
        this.stateCsvPath = stateCsvPath;
        this.eventCsvPath = eventCsvPath;
        this.eventLogger = eventLogger;
        this.quitAction = quitAction;
        this.lastEventLabel = new JLabel("Last event: none");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(560, 260);
        setLocationByPlatform(true);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.add(buildInfoPanel(), BorderLayout.NORTH);
        content.add(buildButtonPanel(), BorderLayout.CENTER);
        content.add(lastEventLabel, BorderLayout.SOUTH);
        setContentPane(content);

        bindKey("F5", "scenario_marker", () -> logEvent(XPlaneEventType.SCENARIO_MARKER, "Hotkey scenario marker"));
        bindKey("F6", "advisory_shown", () -> logEvent(XPlaneEventType.ADVISORY_SHOWN, "Hotkey advisory shown"));
        bindKey("F7", "advisory_cleared", () -> logEvent(XPlaneEventType.ADVISORY_CLEARED, "Hotkey advisory cleared"));
        bindKey("F8", "manual_note", this::promptManualNote);
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Session ID: " + sessionId));
        panel.add(new JLabel("State CSV: " + stateCsvPath));
        panel.add(new JLabel("Event CSV: " + eventCsvPath));
        panel.add(new JLabel("Hotkeys: F5 marker | F6 advisory on | F7 advisory off | F8 note"));
        return panel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 5, 8, 8));
        panel.add(createButton("F5 Scenario", () -> logEvent(XPlaneEventType.SCENARIO_MARKER, "Button scenario marker")));
        panel.add(createButton("F6 Advisory On", () -> logEvent(XPlaneEventType.ADVISORY_SHOWN, "Button advisory shown")));
        panel.add(createButton("F7 Advisory Off", () -> logEvent(XPlaneEventType.ADVISORY_CLEARED, "Button advisory cleared")));
        panel.add(createButton("F8 Note", this::promptManualNote));
        panel.add(createButton("Quit", quitAction));
        return panel;
    }

    private JButton createButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void bindKey(String keyStrokeText, String actionKey, Runnable action) {
        JComponent root = getRootPane();
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke(keyStrokeText), actionKey);
        root.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void promptManualNote() {
        String note = JOptionPane.showInputDialog(
                this,
                "메모를 입력하세요.",
                "Manual Note",
                JOptionPane.PLAIN_MESSAGE
        );
        if (note != null && !note.trim().isEmpty()) {
            logEvent(XPlaneEventType.MANUAL_NOTE, note.trim());
        }
    }

    private void logEvent(XPlaneEventType eventType, String detail) {
        eventLogger.log(sessionId, eventType, "event_controller", detail);
        lastEventLabel.setText("Last event: " + eventType.name() + " | " + detail);
        System.out.println("Event logged: " + eventType + " | " + detail);
    }

    public void closeFrame() {
        SwingUtilities.invokeLater(this::dispose);
    }
}
