import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class TerminalEmulator {
    private JFrame frame;
    private JTextArea outputArea;
    private JTextField inputField;
    private final String username = System.getProperty("user.name");
    private final String hostname = java.net.InetAddress.getLocalHost().getHostName();

    public TerminalEmulator() throws Exception {
        initializeGUI();
    }

    private void initializeGUI() {
        // Настройка основного окна
        frame = new JFrame("Эмулятор - [" + username + "@" + hostname + "]");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        // Область вывода
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setBackground(Color.BLACK);
        outputArea.setForeground(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        // Поле ввода
        inputField = new JTextField();
        inputField.setBackground(Color.BLACK);
        inputField.setForeground(Color.WHITE);
        inputField.addActionListener(this::handleCommand);

        // Разметка
        frame.setLayout(new BorderLayout());
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(inputField, BorderLayout.SOUTH);

        frame.setVisible(true);
        inputField.requestFocus();
    }

    private void handleCommand(ActionEvent e) {
        String input = inputField.getText().trim();
        inputField.setText("");
        outputArea.append("$ " + input + "\n");

        if (input.isEmpty()) return;

        try {
            List<String> args = parseArguments(input);
            String command = args.get(0);

            switch (command) {
                case "ls":
                    outputArea.append("ls: " + args.subList(1, args.size()) + "\n");
                    break;
                case "cd":
                    if (args.size() < 2) {
                        outputArea.append("cd: missing argument\n");
                    } else {
                        outputArea.append("cd: " + args.get(1) + "\n");
                    }
                    break;
                case "exit":
                    System.exit(0);
                    break;
                default:
                    outputArea.append("Command not found: " + command + "\n");
            }
        } catch (Exception ex) {
            outputArea.append("Error: " + ex.getMessage() + "\n");
        }
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private List<String> parseArguments(String input) throws Exception {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inQuotes = false;

        for (char c : input.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }

        if (inQuotes) {
            throw new Exception("Unclosed quotes");
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                new TerminalEmulator();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}