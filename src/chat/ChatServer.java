package chat;

import config.DatabaseConfig;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChatServer {
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private boolean running;

    public ChatServer() {
        clients = new ConcurrentHashMap<>();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(DatabaseConfig.CHAT_SERVER_PORT);
            running = true;
            System.out.println("Chat Server started on port " + DatabaseConfig.CHAT_SERVER_PORT);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting chat server: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error stopping chat server: " + e.getMessage());
        }
    }

    public void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        System.out.println("User " + username + " connected. Total clients: " + clients.size());
    }

    public void removeClient(String username) {
        clients.remove(username);
        System.out.println("User " + username + " disconnected. Total clients: " + clients.size());
    }

    public void sendMessage(String senderUsername, String receiverUsername, String message) {
        ClientHandler receiverHandler = clients.get(receiverUsername);
        if (receiverHandler != null) {
            receiverHandler.sendMessage(senderUsername + ":" + receiverUsername + ":" + message);
        }


    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // First message should be username
            username = reader.readLine();
            if (username != null) {
                server.addClient(username, this);

                String message;
                while ((message = reader.readLine()) != null) {
                    // Message format: receiver:message
                    String[] parts = message.split(":", 2);
                    if (parts.length == 2) {
                        String receiver = parts[0];
                        String messageText = parts[1];
                        server.sendMessage(username, receiver, messageText);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    private void cleanup() {
        try {
            if (username != null) {
                server.removeClient(username);
            }
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}
