package chat;

import config.DatabaseConfig;
import java.io.*;
import java.net.*;

import javax.swing.SwingUtilities;

public class ChatClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private ChatMessageListener messageListener;
    private boolean connected;

    public interface ChatMessageListener {
        void onMessageReceived(String sender, String receiver, String message);
    }

    public ChatClient(String username) {
        this.username = username;
    }

    public boolean connect() {
        try {
            socket = new Socket(DatabaseConfig.RMI_HOST, DatabaseConfig.CHAT_SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Send username to server
            writer.println(username);
            connected = true;

            // Start listening for messages
            new Thread(this::listenForMessages).start();

            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to chat server: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        connected = false;
        try {
            // Close resources in reverse order of creation
            if (writer != null) {
                writer.println("DISCONNECT:" + username); // Notify server
                writer.close();
            }
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        } finally {
            writer = null;
            reader = null;
            socket = null;
        }
    }

    public void sendMessage(String receiver, String message) {
        if (writer != null && connected) {
            writer.println(receiver + ":" + message);
        }
    }

    public void setMessageListener(ChatMessageListener listener) {
        this.messageListener = listener;
    }

    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = reader.readLine()) != null) {
                if (!connected) break; // Additional check

                // Check for server-initiated disconnect
                if (message.startsWith("DISCONNECT:")) {
                    disconnect();
                    break;
                }

                // Message format: sender:receiver:message
                String[] parts = message.split(":", 3);
                if (parts.length == 3 && messageListener != null) {
                    SwingUtilities.invokeLater(() -> {
                        messageListener.onMessageReceived(parts[0], parts[1], parts[2]);
                    });
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Error listening for messages: " + e.getMessage());
            }
        }
    }
}
