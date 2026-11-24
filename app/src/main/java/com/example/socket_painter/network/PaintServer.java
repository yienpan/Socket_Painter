package com.example.socket_painter.network;

import com.example.socket_painter.model.Stroke;
import com.example.socket_painter.model.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 伺服器端核心網路管理類別 (PaintServer)
 * 負責監聽指定的 Port、管理多個 Client 連線執行緒、維護線上使用者與畫布筆跡資料。
 *
 * 執行緒同步 (Thread Synchronization) 說明：
 * 1. 由於每位連線的 Client 都在各自獨立的背景執行緒 (ClientHandler) 中運行讀寫迴圈，
 *    當多個客戶端同時傳送畫筆座標、請求撤銷或斷線時，會同時存取共用的 clients 與 strokes 集合。
 * 2. 為了避免並行修改造成的資料競態條件 (Race Condition) 與 ConcurrentModificationException：
 *    - 我們使用 CopyOnWriteArrayList 來儲存 clients 與 strokes，確保在多執行緒疊代與增刪時的執行緒安全。
 *    - 對於需要保證邏輯一致性的操作（如顏色指派、使用者加入、刪除特定筆跡、踢出使用者），
 *      使用 synchronized 關鍵字或區塊進行同步鎖定，確保同一時間只有一個執行緒修改伺服器狀態。
 */
public class PaintServer {
    private final int port;
    private final String serverName;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private boolean isRunning = false;

    // 執行緒安全的集合，供多個 ClientHandler 背景執行緒安全存取
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<Stroke> strokes = new CopyOnWriteArrayList<>();

    // 顏色配置池，供自動指派給新連線的使用者
    private final String[] COLOR_PALETTE = {
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#009688", "#4CAF50",
            "#FF9800", "#795548", "#607D8B", "#FF5722"
    };
    private int colorIndex = 0;

    public interface ServerListener {
        void onServerLog(String log);
        void onUserListUpdated(List<User> users);
    }

    private final ServerListener listener;

    public PaintServer(String serverName, int port, ServerListener listener) {
        this.serverName = serverName;
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        executorService = Executors.newCachedThreadPool();

        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                log("伺服器 [" + serverName + "] 已啟動，監聽 Port: " + port);

                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    String clientIp = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "";
                    log("新客戶端連線來自: " + clientIp);

                    ClientHandler handler = new ClientHandler(socket, this);
                    clients.add(handler);
                    executorService.execute(handler);
                }
            } catch (IOException e) {
                if (isRunning) {
                    log("伺服器錯誤: " + e.getMessage());
                }
            }
        });
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;

        // 1. 廣播伺服器關閉通知給所有線上客戶端
        try {
            JSONObject shutdownJson = new JSONObject();
            shutdownJson.put("type", "SERVER_SHUTDOWN");
            shutdownJson.put("reason", "伺服器已關閉");
            broadcast(shutdownJson.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 2. 關閉所有客戶端連線
        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();
        strokes.clear();

        // 3. 關閉 ServerSocket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }
        log("伺服器已完全關閉。");
    }

    /**
     * 處理來自 ClientHandler 的訊息，使用 synchronized 確保多執行緒並行呼叫時的資料同步
     */
    public synchronized void handleClientMessage(ClientHandler sender, String jsonMessage) {
        try {
            JSONObject json = new JSONObject(jsonMessage);
            String type = json.getString("type");

            switch (type) {
                case "JOIN_REQUEST": {
                    String userName = json.optString("userName", "訪客");
                    String userId = "u_" + UUID.randomUUID().toString().substring(0, 6);
                    String color = getNextColor();
                    String ip = sender.getClientIp();

                    User user = new User(userId, userName, color, ip);
                    sender.setUser(user);

                    // 回應 JOIN_RESPONSE 給該客戶端
                    JSONObject response = new JSONObject();
                    response.put("type", "JOIN_RESPONSE");
                    response.put("status", "SUCCESS");
                    response.put("userId", userId);
                    response.put("assignedColor", color);

                    // 放入目前所有線上使用者清單
                    JSONArray jUsers = new JSONArray();
                    for (ClientHandler c : clients) {
                        if (c.getUser() != null) {
                            jUsers.put(c.getUser().toJSONObject());
                        }
                    }
                    response.put("users", jUsers);

                    // 放入目前所有歷史筆跡
                    JSONArray jStrokes = new JSONArray();
                    for (Stroke s : strokes) {
                        jStrokes.put(s.toJSONObject());
                    }
                    response.put("strokes", jStrokes);

                    sender.sendMessage(response.toString());
                    log(userName + " (" + ip + ") 加入了畫板");

                    // 廣播更新使用者名單
                    broadcastUserList();
                    break;
                }
                case "STROKE_DATA": {
                    Stroke stroke = Stroke.fromJSONObject(json);
                    strokes.add(stroke);
                    broadcast(jsonMessage); // 轉發給其他所有連線者
                    break;
                }
                case "UNDO_LAST": {
                    String targetUserId = json.optString("userId", "");
                    for (int i = strokes.size() - 1; i >= 0; i--) {
                        Stroke s = strokes.get(i);
                        if (targetUserId.isEmpty() || s.getUserId().equals(targetUserId)) {
                            strokes.remove(i);
                            break;
                        }
                    }
                    broadcast(jsonMessage);
                    break;
                }
                case "CLEAR_ALL": {
                    strokes.clear();
                    broadcast(jsonMessage);
                    log("畫布已被清空");
                    break;
                }
                case "STROKE_DELETE": {
                    String strokeId = json.getString("strokeId");
                    strokes.removeIf(s -> s.getStrokeId().equals(strokeId));
                    broadcast(jsonMessage);
                    break;
                }
                case "LEAVE": {
                    sender.close();
                    onClientDisconnected(sender);
                    break;
                }
            }
        } catch (JSONException e) {
            log("解析訊息失敗: " + e.getMessage());
        }
    }

    /**
     * 當客戶端斷線或被踢出時的清理機制
     */
    public synchronized void onClientDisconnected(ClientHandler handler) {
        if (clients.remove(handler)) {
            User user = handler.getUser();
            if (user != null) {
                log(user.getUserName() + " 離開了畫板");
                // 課程需求：使用者離開時，Server 需清除該使用者的所有筆跡
                removeStrokesByUser(user.getUserId());
            }
            handler.close();
            broadcastUserList();
            broadcastCanvasState();
        }
    }

    /**
     * 伺服器端主動管理功能：踢出指定使用者
     */
    public synchronized void kickUser(String userId) {
        ClientHandler targetClient = null;
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client.getUser().getUserId().equals(userId)) {
                targetClient = client;
                break;
            }
        }

        if (targetClient != null) {
            try {
                JSONObject kickJson = new JSONObject();
                kickJson.put("type", "SERVER_SHUTDOWN"); // 或 KICKED
                kickJson.put("reason", "您已被管理員踢出房間");
                targetClient.sendMessage(kickJson.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (targetClient.getUser() != null) {
                log("管理員踢出了使用者: " + targetClient.getUser().getUserName());
            }
            onClientDisconnected(targetClient);
        }
    }

    /**
     * 伺服器端主動管理功能：清除指定使用者的所有筆跡
     */
    public synchronized void clearUserStrokes(String userId) {
        removeStrokesByUser(userId);
        broadcastCanvasState();
        log("已清除指定使用者的所有筆跡 (UserId: " + userId + ")");
    }

    /**
     * 伺服器端主動管理功能：清除全部筆跡
     */
    public synchronized void clearAllStrokes() {
        strokes.clear();
        try {
            JSONObject json = new JSONObject();
            json.put("type", "CLEAR_ALL");
            broadcast(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        log("管理員已清空全部畫布");
    }

    private void removeStrokesByUser(String userId) {
        strokes.removeIf(s -> s.getUserId().equals(userId));
    }

    public void broadcast(String jsonString) {
        for (ClientHandler client : clients) {
            client.sendMessage(jsonString);
        }
    }

    private void broadcastUserList() {
        try {
            JSONArray jUsers = new JSONArray();
            List<User> userList = new ArrayList<>();
            for (ClientHandler c : clients) {
                if (c.getUser() != null) {
                    jUsers.put(c.getUser().toJSONObject());
                    userList.add(c.getUser());
                }
            }

            JSONObject syncJson = new JSONObject();
            syncJson.put("type", "SYNC_USERS");
            syncJson.put("users", jUsers);

            broadcast(syncJson.toString());

            if (listener != null) {
                listener.onUserListUpdated(userList);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void broadcastCanvasState() {
        try {
            JSONArray jStrokes = new JSONArray();
            for (Stroke s : strokes) {
                jStrokes.put(s.toJSONObject());
            }

            JSONObject syncJson = new JSONObject();
            syncJson.put("type", "SYNC_CANVAS");
            syncJson.put("strokes", jStrokes);

            broadcast(syncJson.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private synchronized String getNextColor() {
        String color = COLOR_PALETTE[colorIndex % COLOR_PALETTE.length];
        colorIndex++;
        return color;
    }

    private void log(String message) {
        if (listener != null) {
            listener.onServerLog(message);
        }
    }

    public List<ClientHandler> getClients() {
        return clients;
    }
}
