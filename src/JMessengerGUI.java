import com.formdev.flatlaf.FlatDarculaLaf;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

public class JMessengerGUI extends JFrame {
    // ... (i componenti UI rimangono gli stessi)
    private JPanel mainPanel;
    private JTextArea messageArea;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> contactList;
    private JLabel statusLabel;
    private JButton scanButton;
    private JButton autoGroupButton;
    private JButton clearGroupButton;
    private JButton removeSelectedButton;
    private JTextField addIpField;
    private JButton addIpButton;
    private JTextField targetIpField;
    private JButton setTargetButton;
    private PrintStream commandSender;

    public JMessengerGUI() {
        super("JMessenger");
        // ... (il resto del costruttore rimane uguale)
        setContentPane(mainPanel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 650);
        setMinimumSize(new Dimension(650, 550));
        setLocationRelativeTo(null);

        initListeners();
        redirectSystemIO();

        setVisible(true);
    }

    private void redirectSystemIO() {
        try {
            PipedOutputStream commandOutput = new PipedOutputStream();
            PipedInputStream commandInput = new PipedInputStream(commandOutput);
            commandSender = new PrintStream(commandOutput, true); // Aggiunto auto-flush
            System.setIn(commandInput);

            PipedOutputStream consoleOutput = new PipedOutputStream();
            PipedInputStream consoleInput = new PipedInputStream(consoleOutput);
            PrintStream guiPrintStream = new PrintStream(consoleOutput, true);
            System.setOut(guiPrintStream);
            System.setErr(guiPrintStream);

            // Thread lettore di output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(consoleInput))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String finalLine = line;
                        SwingUtilities.invokeLater(() -> displayMessage(finalLine));
                    }
                } catch (IOException e) {
                    logErrorToFile(e); // Logga l'errore su file
                }
            }).start();

            // Thread che esegue JMessenger
            Thread jmessengerThread = new Thread(() -> {
                try {
                    JMessenger.main(new String[]{});
                } catch (Throwable t) {
                    logErrorToFile(t); // Cattura QUALSIASI errore da JMessenger e lo logga
                }
            });
            jmessengerThread.setUncaughtExceptionHandler((th, ex) -> logErrorToFile(ex)); // Sicurezza extra
            jmessengerThread.start();

        } catch (IOException e) {
            logErrorToFile(e);
            JOptionPane.showMessageDialog(this, "Errore critico IO. Controlla jmessenger_error.log", "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ... (gli altri metodi come initListeners, sendMessage, etc. rimangono invariati)
    private void sendCommand(String command) {
        if (commandSender != null) {
            commandSender.println(command);
        }
    }

    private void initListeners() {
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        scanButton.addActionListener(e -> sendCommand("scan"));
        autoGroupButton.addActionListener(e -> sendCommand("autogroup"));
        addIpButton.addActionListener(e -> {
            String ip = addIpField.getText().trim();
            if (!ip.isEmpty()) {
                sendCommand("add " + ip);
                addIpField.setText("");
            }
        });
        removeSelectedButton.addActionListener(e -> {
            String selectedIp = contactList.getSelectedValue();
            if (selectedIp != null) {
                sendCommand("remove " + selectedIp);
            }
        });
        clearGroupButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Sei sicuro di voler svuotare il gruppo?", "Conferma", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                sendCommand("cleargroup");
            }
        });
        setTargetButton.addActionListener(e -> {
            String ip = targetIpField.getText().trim();
            if (!ip.isEmpty()) {
                sendCommand("ip " + ip);
            }
        });
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            sendCommand(message);
            messageField.setText("");
        }
    }

    public void displayMessage(String message) {
        if (message.contains("Group contacts:") || message.contains("Updated Group contacts:")) {
            updateContactListFromMessage(message);
        }
        // Filtra i prompt della CLI
        if (!message.trim().equals(">")) {
            messageArea.append(message + "\n");
        }
    }

    private void updateContactListFromMessage(String message) {
        try {
            String ipListStr = message.substring(message.indexOf('[') + 1, message.indexOf(']'));
            String[] ips = ipListStr.split(",\\s*");
            DefaultListModel<String> model = new DefaultListModel<>();
            for (String ip : ips) {
                if (!ip.isEmpty()) model.addElement(ip);
            }
            contactList.setModel(model);
        } catch (Exception e) {
            contactList.setModel(new DefaultListModel<>());
        }
    }

    // NUOVO METODO per loggare gli errori su file
    private static void logErrorToFile(Throwable e) {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get("jmessenger_error.log"), StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            e.printStackTrace(pw);
        } catch (IOException ioe) {
            ioe.printStackTrace(); // Se anche il logging fallisce, stampa sulla console originale
        }
    }

    public static void main(String[] args) {
        // Imposta il gestore di eccezioni per il thread principale della UI
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> logErrorToFile(e));
        
        System.setProperty("flatlaf.useWindowDecorations", "false");
        try {
            FlatDarculaLaf.setup();
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
        } catch (Exception e) {
            logErrorToFile(e);
        }

        SwingUtilities.invokeLater(JMessengerGUI::new);
    }
}