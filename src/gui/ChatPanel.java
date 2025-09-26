package gui;

import models.User;
import models.Message;
import rmi.SkillSwapService;
import chat.ChatClient;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.time.*;

public class ChatPanel extends JPanel implements ChatClient.ChatMessageListener {
    private User currentUser;
    private SkillSwapService service;
    private ChatClient chatClient;
    private JList<String> chatList;
    private DefaultListModel<String> chatListModel;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private String currentChatUser;
    private Set<String> chatUsers;

    // Enhanced Colors
    private final Color BACKGROUND_COLOR = new Color(240, 242, 245);
    private final Color SIDE_PANEL_COLOR = new Color(255, 255, 255);
    private final Color LIST_SELECTION_COLOR = new Color(239, 246, 255);
    private final Color LIST_HOVER_COLOR = new Color(245, 245, 245);
    private final Color SEND_BUTTON_COLOR = new Color(0, 123, 255);
    private final Color SEND_BUTTON_HOVER_COLOR = new Color(0, 98, 204);
    private final Color BORDER_COLOR = new Color(222, 226, 230);

    public ChatPanel(User currentUser, SkillSwapService service, ChatClient chatClient) {
        this.currentUser = currentUser;
        this.service = service;
        this.chatClient = chatClient;
        this.chatUsers = new HashSet<>();

        if (chatClient != null) {
            chatClient.setMessageListener(this);
        }

        initializeGUI();
        loadChatUsers();
    }

    private void initializeGUI() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Left panel - Chat list (more visible)
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.setBackground(SIDE_PANEL_COLOR);
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        // Header for chat list
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setBackground(new Color(248, 249, 250));
        listHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        JLabel listTitle = new JLabel("Conversations");
        listTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        listTitle.setForeground(new Color(33, 37, 41));
        listHeader.add(listTitle, BorderLayout.WEST);
        leftPanel.add(listHeader, BorderLayout.NORTH);

        chatListModel = new DefaultListModel<>();
        chatList = new JList<>(chatListModel);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.setBackground(SIDE_PANEL_COLOR);
        chatList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatList.setFixedCellHeight(50);
        chatList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
                if (isSelected) {
                    setBackground(LIST_SELECTION_COLOR);
                    setForeground(new Color(0, 0, 0));
                } else {
                    setBackground(SIDE_PANEL_COLOR);
                    setForeground(new Color(73, 80, 87));
                }
                return this;
            }
        });

        // Add hover effect
        chatList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseMoved(java.awt.event.MouseEvent evt) {
                int index = chatList.locationToIndex(evt.getPoint());
                if (index >= 0) {
                    chatList.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    Rectangle rect = chatList.getCellBounds(index, index);
                    chatList.repaint(rect);
                } else {
                    chatList.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        chatList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedUser = chatList.getSelectedValue();
                if (selectedUser != null) {
                    openChatWith(selectedUser);
                }
            }
        });

        JScrollPane chatListScroll = new JScrollPane(chatList);
        chatListScroll.setBorder(BorderFactory.createEmptyBorder());
        chatListScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(chatListScroll, BorderLayout.CENTER);


        JButton newChatButton = new JButton("+ New Chat");
        newChatButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        newChatButton.setForeground(Color.BLACK); // Changed to black
        newChatButton.setBackground(new Color(173, 216, 230)); // Light blue (RGB for light blue)
        newChatButton.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        newChatButton.setFocusPainted(false);
        newChatButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                newChatButton.setBackground(new Color(135, 206, 250)); // Slightly darker blue on hover
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                newChatButton.setBackground(new Color(173, 216, 230)); // Reset to light blue
            }
        });
        newChatButton.addActionListener(e -> openNewChatDialog());
        leftPanel.add(newChatButton, BorderLayout.SOUTH);


        // Right panel - Chat area
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(Color.WHITE);

        // Chat header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(new Color(248, 249, 250));
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        JLabel chatTitle = new JLabel("Messages");
        chatTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        chatTitle.setForeground(new Color(33, 37, 41));
        chatHeader.add(chatTitle, BorderLayout.WEST);
        rightPanel.add(chatHeader, BorderLayout.NORTH);

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        chatArea.setMargin(new Insets(0, 0, 0, 0));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightPanel.add(chatScroll, BorderLayout.CENTER);

        // Message input panel (more visible send button)
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        inputPanel.setBackground(Color.WHITE);

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(206, 212, 218)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setForeground(Color.WHITE);
        sendButton.setBackground(SEND_BUTTON_COLOR);
        sendButton.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));
        sendButton.setFocusPainted(false);
        sendButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                sendButton.setBackground(SEND_BUTTON_HOVER_COLOR);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                sendButton.setBackground(SEND_BUTTON_COLOR);
            }
        });

        messageField.addActionListener(new SendMessageActionListener());
        sendButton.addActionListener(new SendMessageActionListener());

        JPanel messageWrapper = new JPanel(new BorderLayout());
        messageWrapper.add(messageField, BorderLayout.CENTER);
        messageWrapper.add(sendButton, BorderLayout.EAST);
        messageWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        inputPanel.add(messageWrapper, BorderLayout.CENTER);
        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        // Initially disable chat area
        enableChatArea(false);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(280);
        splitPane.setResizeWeight(0.0);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(3);

        add(splitPane, BorderLayout.CENTER);
    }

    // [Rest of the methods remain exactly the same as your original implementation]
    private void loadChatUsers() {
        try {
            chatListModel.clear();
            chatUsers.clear();

            Set<String> uniqueChatPartners = new HashSet<>();
            List<Message> allMessages = service.getAllUserMessages(currentUser.getUsername());
            for (Message msg : allMessages) {
                String otherUser = msg.getSenderUsername().equals(currentUser.getUsername())
                        ? msg.getReceiverUsername()
                        : msg.getSenderUsername();

                if (!otherUser.equals(currentUser.getUsername())) {
                    uniqueChatPartners.add(otherUser);
                }
            }

            for (String username : uniqueChatPartners) {
                chatUsers.add(username);
                chatListModel.addElement(username);
            }

        } catch (RemoteException e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading chat users: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void openChatWith(String username) {
        if (!chatUsers.contains(username)) {
            chatUsers.add(username);
            chatListModel.addElement(username);
        }

        currentChatUser = username;
        chatList.setSelectedValue(username, true);
        enableChatArea(true);
        loadChatHistory();
    }

    private void openNewChatDialog() {
        try {
            List<User> users = service.getAllUsers(currentUser.getUsername());
            if (users.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No other users found.",
                        "No Users",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] usernames = users.stream()
                    .map(User::getUsername)
                    .toArray(String[]::new);

            String selectedUser = (String) JOptionPane.showInputDialog(
                    this,
                    "Select a user to chat with:",
                    "New Chat",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    usernames,
                    usernames[0]
            );

            if (selectedUser != null) {
                openChatWith(selectedUser);
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading users: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enableChatArea(boolean enabled) {
        messageField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        if (!enabled) {
            chatArea.setText("Select a chat to start messaging");
            chatArea.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            chatArea.setForeground(new Color(150, 150, 150));
        } else {
            chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            chatArea.setForeground(Color.BLACK);
        }
    }

    private void loadChatHistory() {
        if (currentChatUser == null) return;

        try {
            List<Message> messages = service.getChatHistory(currentUser.getUsername(), currentChatUser);
            displayMessages(messages);
        } catch (Exception e) {
            chatArea.setText("Error loading chat history: " + e.getMessage());
        }
    }

    private void displayMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Message message : messages) {
            String time = message.getTimestamp().format(formatter);
            String sender = message.getSenderUsername().equals(currentUser.getUsername()) ?
                    "You" : message.getSenderUsername();

            sb.append(String.format("[%s] %s: %s\n", time, sender, message.getMessage()));
        }

        chatArea.setText(sb.toString());
    }

    private void sendMessage() {
        if (currentChatUser == null || messageField.getText().trim().isEmpty()) {
            return;
        }

        String messageText = messageField.getText().trim();
        messageField.setText("");

        try {
            Message message = new Message(currentUser.getUsername(), currentChatUser, messageText);
            service.saveMessage(message);

            if (chatClient != null) {
                chatClient.sendMessage(currentChatUser, messageText);
            }

            appendMessageToChat(currentUser.getUsername(), messageText);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error sending message: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void appendMessageToChat(String sender, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String senderName = sender.equals(currentUser.getUsername()) ? "You" : sender;
        String newMessage = String.format("[%s] %s: %s\n", time, senderName, message);

        if (!chatArea.getText().equalsIgnoreCase(newMessage)) {
            chatArea.append(newMessage);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    public void onMessageReceived(String sender, String receiver, String message) {
        SwingUtilities.invokeLater(() -> {
            if (receiver.equals(currentUser.getUsername()) ||
                    sender.equals(currentUser.getUsername())) {

                if (receiver.equals(currentUser.getUsername())) {
                    try {
                        Message msg = new Message(sender, receiver, message);
                        service.saveMessage(msg);
                    } catch (Exception e) {
                        System.err.println("Error saving received message: " + e.getMessage());
                    }
                }

                if (!chatUsers.contains(sender)) {
                    chatUsers.add(sender);
                    chatListModel.addElement(sender);
                }

                appendMessageToChat(sender, message);
            }
        });
    }

    private class SendMessageActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            sendMessage();
        }
    }
}