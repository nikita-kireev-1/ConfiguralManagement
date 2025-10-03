import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;

class VFSNode {
    String name;
    boolean isDirectory;
    String content;
    Map<String, VFSNode> children = new HashMap<>();
    VFSNode parent;

    VFSNode(String name, boolean isDirectory, VFSNode parent) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.parent = parent;
    }
    // Метод для получения содержимого как обычной строки (декодирует Base64 если нужно)
    public String getContentAsString() {
        if (content == null || content.isEmpty()) return "";

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(content);
            return new String(decodedBytes);
        } catch (IllegalArgumentException e) {
            return content;
        }
    }

    // Метод для получения исходного содержимого (Base64)
    public String getRawContent() {
        return content != null ? content : "";
    }

    // Метод для проверки, является ли содержимое Base64
    public boolean isBase64Content() {
        if (content == null || content.isEmpty()) return false;

        try {
            Base64.getDecoder().decode(content);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

class VirtualFileSystem {
    VFSNode root = new VFSNode("", true,null);
    VFSNode currentDir = root;

    public void loadFromCSV(String csvPath) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(csvPath));
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;

            List<String> parts = parseCSVLine(line);
            if (parts.size() < 3) throw new Exception("Invalid CSV format, expected at least 3 columns");

            String type = parts.get(0).trim();
            String path = parts.get(1).trim();
            String content = parts.size() > 2 ? parts.get(2).trim() : "";


            if (!path.startsWith("/")) {
                throw new Exception("Paths must be absolute (start with /)");
            }

            String[] components = path.substring(1).split("/");
            VFSNode current = root;

            // Создаём вложенные папки
            for (int i = 0; i < components.length - 1; i++) {
                String component = components[i];
                if (!current.children.containsKey(component)) {
                    current.children.put(component, new VFSNode(component, true, current));
                }
                current = current.children.get(component);
            }

            // Создаём файл или конечную папку
            String lastName = components[components.length - 1];
            if ("file".equals(type)) {
                VFSNode file = new VFSNode(lastName, false, current);
                file.content = content;
                current.children.put(lastName, file);
            } else if ("dir".equals(type)) {
                if (!current.children.containsKey(lastName)) {
                    current.children.put(lastName, new VFSNode(lastName, true, current));
                }
            }
        }
    }
    private List<String> parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean inEscape = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

// На случай если понадобятся символы кавычек и точки с запятой в тексе(content), а не как разделители
//            if (inEscape) {
//                current.append(c);
//                inEscape = false;
//                continue;
//            }
//
//            if (c == '\\') {
//                inEscape = true;
//                continue;
//            }

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Обработка двойных кавычек внутри кавычек ("")
                    current.append('"');
                    i++; // Пропускаем следующую кавычку
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ';' && !inQuotes) {
                // Точка с запятой вне кавычек - разделитель полей
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        result.add(current.toString());
        return result;
    }

    public List<String> listCurrentDir() {
        List<String> result = new ArrayList<>();
        for (VFSNode node : currentDir.children.values()) {
            result.add(node.name + (node.isDirectory ? "/" : ""));
        }
        Collections.sort(result);
        return result;
    }

    public boolean changeDirectory(String path) {
        if (path.equals("/")) {
            currentDir = root;
            return true;
        }

        if (path.equals("..")) {
            if (currentDir.parent != null) {
                currentDir = currentDir.parent;
            }
            return true;
        }

        if (!path.startsWith("/")) {
           //System.out.println(path);
            // Относительный путь
            if (currentDir.children.containsKey(path) &&
                    currentDir.children.get(path).isDirectory) {
                //System.out.println(path);
                currentDir = currentDir.children.get(path);
                return true;
            }
            return false;
        }

        // Абсолютный путь
        String[] components = path.substring(1).split("/");
        VFSNode current = root;

        for (String component : components) {
            if (component.isEmpty()) continue;

            if (!current.children.containsKey(component) ||
                    !current.children.get(component).isDirectory) {
                return false;
            }
            current = current.children.get(component);
        }

        currentDir = current;
        return true;
    }

    public String getCurrentPath() {
        if (currentDir == root) return "/";

        List<String> pathComponents = new ArrayList<>();
        VFSNode node = currentDir;

        while (node != null && node != root) {
            pathComponents.add(0, node.name);
            node = node.parent;
        }

        return "/" + String.join("/", pathComponents);
    }
}

public class TerminalEmulator {
    private VirtualFileSystem vfs;
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
        if (vfsPath != null && !vfsPath.isEmpty()) {
            try {
                vfs = new VirtualFileSystem();
                vfs.loadFromCSV(vfsPath);
                outputArea.append("VFS loaded successfully from: " + vfsPath + "\n");
            } catch (Exception e) {
                outputArea.append("Error loading VFS: " + e.getMessage() + "\n");
            }
        }
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
                    if (vfs == null) {
                        outputArea.append("VFS not loaded\n");
                        return true;
                    } else {
                        List<String> files = vfs.listCurrentDir();
                        if (files.isEmpty()) {
                            outputArea.append("(empty)\n");
                        } else {
                            for (String file : files) {
                                outputArea.append(file + "\n");
                            }
                        }
                        return true;
                    }
                case "cd":
                    if (vfs == null) {
                        outputArea.append("VFS not loaded\n");
                        return true;
                    } else if (args.size() < 2) {
                        outputArea.append("cd: missing argument\n");
                        return false;
                    } else {
                        boolean success = vfs.changeDirectory(args.get(1));
                        if (!success) {
                            outputArea.append("cd: no such directory: " + args.get(1) + "\n");
                            return false;
                        }
                        return true;
                    }
                case "exit":
                    outputArea.append("exit command in script - ignoring\n");
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

        String currentPath = "~";
        if (vfs != null) {
            currentPath = vfs.getCurrentPath();
        }
        outputArea.append(username + "@" + hostname + ":" + currentPath + "$ " + input + "\n");

        if (input.isEmpty()) return;

        try {
            List<String> args = parseArguments(input);
            String command = args.get(0);

            switch (command) {
                case "ls":
                    if (vfs == null) {
                        outputArea.append("VFS not loaded\n");
                    } else {
                        List<String> files = vfs.listCurrentDir();
                        if (files.isEmpty()) {
                            outputArea.append("(empty)\n");
                        } else {
                            for (String file : files) {
                                outputArea.append(file + "\n");
                            }
                        }
                    }
                    break;
                case "cd":
                    if (vfs == null) {
                        outputArea.append("VFS not loaded\n");
                    } else if (args.size() < 2) {
                        outputArea.append("cd: missing argument\n");
                    } else {
                        boolean success = vfs.changeDirectory(args.get(1));
                        if (!success) {
                            outputArea.append("cd: no such directory: " + args.get(1) + "\n");
                        }
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
        //C:\Users\admin\Desktop\ideaproj\configm\practice1\test.vfs.csv
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