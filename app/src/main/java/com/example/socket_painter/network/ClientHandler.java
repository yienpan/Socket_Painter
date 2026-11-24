package com.example.socket_painter.network;

import android.util.Log;

import com.example.socket_painter.model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private static final String TAG = "ClientHandler";
    private final Socket socket;
    private final PaintServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private User user;
    private boolean isRunning = true;
    private final String clientIp;

    public ClientHandler(Socket socket, PaintServer server) {
        this.socket = socket;
        this.server = server;
        this.clientIp = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "";
        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            Log.d(TAG, "ClientHandler created for IP: " + clientIp);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing streams: " + e.getMessage(), e);
        }
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void sendMessage(String jsonString) {
        if (writer != null) {
            writer.println(jsonString);
            Log.d(TAG, "Sent to " + clientIp + ": " + jsonString);
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while (isRunning && (message = reader.readLine()) != null) {
                Log.d(TAG, "Received from " + clientIp + ": " + message);
                server.handleClientMessage(this, message);
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection read error from " + clientIp + ": " + e.getMessage());
        } finally {
            close();
            server.onClientDisconnected(this);
        }
    }

    public void close() {
        isRunning = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            Log.d(TAG, "Closed connection for " + clientIp);
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket: " + e.getMessage(), e);
        }
    }
}
