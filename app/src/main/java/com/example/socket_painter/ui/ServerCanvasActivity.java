package com.example.socket_painter.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.socket_painter.R;
import com.example.socket_painter.model.Stroke;
import com.example.socket_painter.model.User;
import com.example.socket_painter.network.PaintClient;
import com.example.socket_painter.network.PaintServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ServerCanvasActivity extends AppCompatActivity {

    private PaintServer paintServer;
    private PaintClient paintClient;
    private PaintView paintView;
    private LinearLayout containerUserList;
    private Button btnEraser;

    private String serverName;
    private int port;
    private boolean isEraserActive = false;
    private String myColor = "#FF0000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_server_canvas);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.paintView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        serverName = getIntent().getStringExtra("serverName");
        port = getIntent().getIntExtra("port", 8888);

        TextView tvServerTitle = findViewById(R.id.tvServerTitle);
        tvServerTitle.setText(serverName + " (Port: " + port + ")");

        paintView = findViewById(R.id.paintView);
        containerUserList = findViewById(R.id.containerUserList);

        setupToolbar();

        // 1. Start Server
        paintServer = new PaintServer(serverName, port, new PaintServer.ServerListener() {
            @Override
            public void onServerLog(String log) {
                // Optional log output
            }

            @Override
            public void onUserListUpdated(List<User> users) {
                runOnUiThread(() -> updateUserManagementUI(users));
            }
        });
        paintServer.start();

        // 2. Host connects as client to self for drawing
        paintClient = new PaintClient("127.0.0.1", port, serverName + " (Host)", new PaintClient.ClientListener() {
            @Override
            public void onConnected(String userId, String color, List<User> users, List<Stroke> strokes) {
                runOnUiThread(() -> {
                    myColor = color;
                    paintView.setMyInfo(userId, serverName + " (Host)", color);
                    paintView.setStrokes(strokes);
                });
            }

            @Override
            public void onMessageReceived(JSONObject json) {
                try {
                    String type = json.getString("type");
                    switch (type) {
                        case "STROKE_DATA": {
                            Stroke stroke = Stroke.fromJSONObject(json);
                            runOnUiThread(() -> paintView.addStroke(stroke));
                            break;
                        }
                        case "STROKE_DELETE": {
                            String strokeId = json.getString("strokeId");
                            runOnUiThread(() -> paintView.deleteStroke(strokeId));
                            break;
                        }
                        case "UNDO_LAST": {
                            String targetUserId = json.optString("userId", "");
                            runOnUiThread(() -> paintView.undoLast(targetUserId));
                            break;
                        }
                        case "CLEAR_ALL": {
                            runOnUiThread(() -> paintView.clearAll());
                            break;
                        }
                        case "SYNC_CANVAS": {
                            JSONArray jStrokes = json.getJSONArray("strokes");
                            List<Stroke> updatedStrokes = new java.util.ArrayList<>();
                            for (int i = 0; i < jStrokes.length(); i++) {
                                updatedStrokes.add(Stroke.fromJSONObject(jStrokes.getJSONObject(i)));
                            }
                            runOnUiThread(() -> paintView.setStrokes(updatedStrokes));
                            break;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(String reason) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    Toast.makeText(ServerCanvasActivity.this, "Host connection status: " + reason, Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Delay 500ms to ensure ServerSocket is fully bound before connecting
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) {
                paintClient.connect();
            }
        }, 500);

        paintView.setStrokeEventListener(new PaintView.OnStrokeEventListener() {
            @Override
            public void onStrokeFinished(Stroke stroke) {
                paintClient.sendStroke(stroke);
            }

            @Override
            public void onStrokeDeleted(String strokeId) {
                paintClient.sendDeleteStroke(strokeId);
            }
        });
    }

    private void setupToolbar() {
        btnEraser = findViewById(R.id.btnEraser);
        Button btnClearAll = findViewById(R.id.btnClearAll);
        Button btnShutdown = findViewById(R.id.btnShutdown);

        Button btnRed = findViewById(R.id.btnColorRed);
        Button btnBlue = findViewById(R.id.btnColorBlue);
        Button btnGreen = findViewById(R.id.btnColorGreen);
        Button btnBlack = findViewById(R.id.btnColorBlack);
        Button btnOrange = findViewById(R.id.btnColorOrange);

        btnRed.setOnClickListener(v -> {
            disableEraser();
            myColor = "#F44336";
            paintView.setCurrentColor(myColor);
        });
        btnBlue.setOnClickListener(v -> {
            disableEraser();
            myColor = "#2196F3";
            paintView.setCurrentColor(myColor);
        });
        btnGreen.setOnClickListener(v -> {
            disableEraser();
            myColor = "#4CAF50";
            paintView.setCurrentColor(myColor);
        });
        btnBlack.setOnClickListener(v -> {
            disableEraser();
            myColor = "#000000";
            paintView.setCurrentColor(myColor);
        });
        btnOrange.setOnClickListener(v -> {
            disableEraser();
            myColor = "#FF9800";
            paintView.setCurrentColor(myColor);
        });

        btnEraser.setOnClickListener(v -> {
            isEraserActive = !isEraserActive;
            paintView.setEraserMode(isEraserActive);
            if (isEraserActive) {
                btnEraser.setBackgroundColor(Color.parseColor("#FF5722"));
            } else {
                btnEraser.setBackgroundColor(Color.LTGRAY);
            }
        });

        btnClearAll.setOnClickListener(v -> {
            disableEraser();
            if (paintServer != null) {
                paintServer.clearAllStrokes();
            }
        });

        btnShutdown.setOnClickListener(v -> {
            shutdownServer();
        });
    }

    private void disableEraser() {
        if (isEraserActive) {
            isEraserActive = false;
            paintView.setEraserMode(false);
            btnEraser.setBackgroundColor(Color.LTGRAY);
        }
    }

    private void updateUserManagementUI(List<User> users) {
        containerUserList.removeAllViews();

        for (User user : users) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(4, 4, 4, 4);

            // Color Indicator
            TextView tvColor = new TextView(this);
            tvColor.setLayoutParams(new LinearLayout.LayoutParams(40, 40));
            try {
                tvColor.setBackgroundColor(Color.parseColor(user.getColor()));
            } catch (Exception e) {
                tvColor.setBackgroundColor(Color.RED);
            }

            // Name & IP
            TextView tvInfo = new TextView(this);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvInfo.setLayoutParams(infoParams);
            tvInfo.setPadding(12, 0, 12, 0);
            tvInfo.setText(user.getUserName() + " (" + user.getIp() + ")");
            tvInfo.setTextColor(Color.BLACK);

            // Clear User Strokes Button
            Button btnClearUser = new Button(this);
            btnClearUser.setText("Clear Strokes");
            btnClearUser.setTextSize(12);
            btnClearUser.setOnClickListener(v -> {
                if (paintServer != null) {
                    paintServer.clearUserStrokes(user.getUserId());
                    Toast.makeText(this, "Cleared strokes for " + user.getUserName(), Toast.LENGTH_SHORT).show();
                }
            });

            // Kick Button
            Button btnKick = new Button(this);
            btnKick.setText("Kick");
            btnKick.setTextSize(12);
            btnKick.setOnClickListener(v -> {
                if (paintServer != null) {
                    paintServer.kickUser(user.getUserId());
                    Toast.makeText(this, "Kicked " + user.getUserName(), Toast.LENGTH_SHORT).show();
                }
            });

            row.addView(tvColor);
            row.addView(tvInfo);
            row.addView(btnClearUser);
            row.addView(btnKick);

            containerUserList.addView(row);
        }
    }

    private void shutdownServer() {
        if (paintClient != null) {
            paintClient.disconnect();
        }
        if (paintServer != null) {
            paintServer.stop();
        }
        Toast.makeText(this, "Server shutdown", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutdownServer();
    }
}
