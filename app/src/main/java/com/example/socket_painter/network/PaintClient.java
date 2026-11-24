package com.example.socket_painter.network;

import android.util.Log;

import com.example.socket_painter.model.Stroke;
import com.example.socket_painter.model.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PaintClient {
    private static final String TAG = "PaintClient";
    private String serverIp;
    private int serverPort;
    private String userName;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean isConnected = false;

    private String myUserId;
    private String myColor;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();

    public interface ClientListener {
        void onConnected(String userId, String color, List<User> users, List<Stroke> strokes);
        void onMessageReceived(JSONObject json);
        void onDisconnected(String reason);
    }

    private ClientListener listener;

    public PaintClient(String serverIp, int serverPort, String userName, ClientListener listener) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.userName = userName;
        this.listener = listener;
    }

    public void connect() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Connecting to " + serverIp + ":" + serverPort + " as " + userName);
                socket = new Socket(serverIp, serverPort);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(socket.getOutputStream(), true);
                isConnected = true;
                Log.d(TAG, "Connected successfully to server!");

                // Send JOIN_REQUEST immediately upon connection
                try {
                    JSONObject joinJson = new JSONObject();
                    joinJson.put("type", "JOIN_REQUEST");
                    joinJson.put("userName", userName);
                    writer.println(joinJson.toString());
                    Log.d(TAG, "Sent JOIN_REQUEST: " + joinJson.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Error building JOIN_REQUEST: " + e.getMessage(), e);
                }

                // Listen for messages from server
                String message;
                while (isConnected && (message = reader.readLine()) != null) {
                    Log.d(TAG, "Received from server: " + message);
                    try {
                        JSONObject json = new JSONObject(message);
                        String type = json.getString("type");

                        if ("JOIN_RESPONSE".equals(type)) {
                            myUserId = json.getString("userId");
                            myColor = json.getString("assignedColor");

                            List<User> users = new ArrayList<>();
                            JSONArray jUsers = json.getJSONArray("users");
                            for (int i = 0; i < jUsers.length(); i++) {
                                users.add(User.fromJSONObject(jUsers.getJSONObject(i)));
                            }

                            List<Stroke> strokes = new ArrayList<>();
                            JSONArray jStrokes = json.getJSONArray("strokes");
                            for (int i = 0; i < jStrokes.length(); i++) {
                                strokes.add(Stroke.fromJSONObject(jStrokes.getJSONObject(i)));
                            }

                            if (listener != null) {
                                listener.onConnected(myUserId, myColor, users, strokes);
                            }
                        } else if ("SERVER_SHUTDOWN".equals(type)) {
                            String reason = json.optString("reason", "Server shutdown.");
                            if (listener != null) {
                                listener.onDisconnected(reason);
                            }
                            disconnect();
                            break;
                        } else {
                            if (listener != null) {
                                listener.onMessageReceived(json);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parse error: " + e.getMessage(), e);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection error: " + e.getMessage(), e);
                if (isConnected) {
                    if (listener != null) {
                        listener.onDisconnected("Connection lost: " + e.getMessage());
                    }
                }
            } finally {
                disconnect();
            }
        }).start();
    }

    public void sendStroke(Stroke stroke) {
        try {
            JSONObject json = stroke.toJSONObject();
            json.put("type", "STROKE_DATA");
            sendJson(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending stroke: " + e.getMessage(), e);
        }
    }

    public void sendUndo(String userId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "UNDO_LAST");
            json.put("userId", userId);
            sendJson(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending undo: " + e.getMessage(), e);
        }
    }

    public void sendDeleteStroke(String strokeId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "STROKE_DELETE");
            json.put("strokeId", strokeId);
            sendJson(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending delete stroke: " + e.getMessage(), e);
        }
    }

    public void sendClearAll() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "CLEAR_ALL");
            sendJson(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending clear all: " + e.getMessage(), e);
        }
    }

    public void sendLeave() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "LEAVE");
            synchronized (this) {
                if (writer != null && isConnected) {
                    writer.println(json.toString());
                    Log.d(TAG, "Sent LEAVE to server");
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error sending leave: " + e.getMessage(), e);
        }
        disconnect();
    }

    private void sendJson(String jsonString) {
        sendExecutor.execute(() -> {
            synchronized (this) {
                if (writer != null && isConnected) {
                    writer.println(jsonString);
                    Log.d(TAG, "Sent to server: " + jsonString);
                } else {
                    Log.w(TAG, "Cannot send json, writer is null or not connected.");
                }
            }
        });
    }

    public void disconnect() {
        isConnected = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            Log.d(TAG, "Client disconnected and resources closed.");
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting: " + e.getMessage(), e);
        }
    }

    public String getMyUserId() {
        return myUserId;
    }

    public String getMyColor() {
        return myColor;
    }
}
