import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;

public class TerminalEmulator {
    private JFrame frame;
    private JTextArea outputArea;
    private JTextField inputField;
    private final String username = System.getProperty("user.name");
    private final String hostname;
    private String vfsPath;
    private String scriptPath;
    private boolean scriptMode = false;

    public TerminalEmulator(String vfsPath, String scriptPath) throws Exception {
        this.hostname = java.net.InetAddress.getLocalHost().getHostName();
        this.vfsPath = vfsPath;
        this.scriptPath = scriptPath;
        initializeGUI();
        printDebugInfo();

        if (scriptPath != null && !scriptPath.isEmpty()) {
            executeStartupScript();
        }
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

    private void printDebugInfo() {
        outputArea.append("=== Debug Information ===\n");
        outputArea.append("VFS Path: " + (vfsPath != null ? vfsPath : "not specified") + "\n");
        outputArea.append("Script Path: " + (scriptPath != null ? scriptPath : "not specified") + "\n");
        outputArea.append("Username: " + username + "\n");
        outputArea.append("Hostname: " + hostname + "\n");
        outputArea.append("=========================\n\n");
    }

    private void executeStartupScript() {
        scriptMode = true;
        outputArea.append("=== Executing Startup Script ===\n");

        try {
            List<String> lines = Files.readAllLines(Paths.get(scriptPath));
            for (String line : lines) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue; // Пропускаем пустые строки и комментарии
                }
                outputArea.append("$ " + line + "\n");
                boolean success = executeScriptCommand(line);

                if (!success) {
                    outputArea.append("Script execution stopped due to error.\n");
                    break;
                }
            }
        } catch (IOException e) {
            outputArea.append("Error reading script file: " + e.getMessage() + "\n");
        } catch (Exception e) {
            outputArea.append("Error executing script: " + e.getMessage() + "\n");
        }

        outputArea.append("=== Script Execution Finished ===\n\n");
        scriptMode = false;
    }

    private boolean executeScriptCommand(String command) {
        try {
            List<String> args = parseArguments(command);
            if (args.isEmpty()) return true;

            String cmd = args.get(0);
            switch (cmd) {
                case "ls":
                    outputArea.append("ls executed with args: " + args.subList(1, args.size()) + "\n");
                    return true;
                case "cd":
                    if (args.size() < 2) {
                        outputArea.append("cd: missing argument\n");
                        return false;
                    } else {
                        outputArea.append("cd to: " + args.get(1) + "\n");
                        return true;
                    }
                case "exit":
                    outputArea.append("exit command in script - ignoring\n");//Иначе скрипт закроется и не будет виден результат его выполнения
                    return true;
                default:
                    outputArea.append("Command not found: " + cmd + "\n");
                    return false;
            }
        } catch (Exception e) {
            outputArea.append("Error: " + e.getMessage() + "\n");
            return false;
        }
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

        Scanner sc = new Scanner(System.in);
        System.out.println("Do you want to enter script file?");
        //-vfs
        //vfspath
        //-script
        //script path example: C:\Users\admin\Desktop\ideaproj\configm\practice1\script2.txt
        // или  java TerminalEmulator2.java -vfs "\path\to\vfs" -script "C:\Users\admin\Desktop\ideaproj\configm\practice1\script.txt"
        String answer = sc.nextLine();
        String[] arguments;
        if(Objects.equals(answer, "Yes")){
            String v = sc.nextLine();
            String vPath = sc.nextLine();
            String s = sc.nextLine();
            String sPath = sc.nextLine();
            String[] commands = {v,vPath,s,sPath};
            arguments = Arrays.copyOf(commands,commands.length);
        }else{
            arguments = Arrays.copyOf(args,args.length);
        }
        String vfsPath = null;
        String scriptPath = null;

        for (int i = 0; i < arguments.length; i++) {
            switch (arguments[i]) {
                case "-vfs":
                    if (i + 1 < arguments.length) {
                        vfsPath = arguments[++i];
                    }
                    break;
                case "-script":
                    if (i + 1 < arguments.length) {
                        scriptPath = arguments[++i];
                    }
                    break;
            }
        }

        final String finalVfsPath = vfsPath;
        final String finalScriptPath = scriptPath;

        SwingUtilities.invokeLater(() -> {
            try {
                new TerminalEmulator(finalVfsPath, finalScriptPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}