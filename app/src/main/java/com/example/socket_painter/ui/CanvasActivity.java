package com.example.socket_painter.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class CanvasActivity extends AppCompatActivity {

    private PaintClient paintClient;
    private PaintView paintView;
    private TextView tvRoomInfo, tvNotification;
    private Button btnEraser;

    private String serverIp;
    private int port;
    private String nickname;

    private String myUserId;
    private String myColor = "#FF0000";
    private boolean isEraserActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_canvas);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.paintView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        serverIp = getIntent().getStringExtra("serverIp");
        port = getIntent().getIntExtra("port", 8888);
        nickname = getIntent().getStringExtra("nickname");

        paintView = findViewById(R.id.paintView);
        tvRoomInfo = findViewById(R.id.tvRoomInfo);
        tvNotification = findViewById(R.id.tvNotification);

        setupToolbar();

        // 建立 Client 端並連線至 Server
        paintClient = new PaintClient(serverIp, port, nickname, new PaintClient.ClientListener() {
            @Override
            public void onConnected(String userId, String color, List<User> users, List<Stroke> strokes) {
                runOnUiThread(() -> {
                    myUserId = userId;
                    myColor = color;
                    paintView.setMyInfo(userId, nickname, color);
                    paintView.setStrokes(strokes);
                    tvRoomInfo.setText("線上: " + users.size() + "人");
                    tvNotification.setText("已成功連線至伺服器 (" + serverIp + ":" + port + ")");
                    Toast.makeText(CanvasActivity.this, "連線成功！", Toast.LENGTH_SHORT).show();
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
                            runOnUiThread(() -> {
                                paintView.clearAll();
                                tvNotification.setText("畫布已被清空");
                            });
                            break;
                        }
                        case "SYNC_USERS": {
                            JSONArray jUsers = json.getJSONArray("users");
                            runOnUiThread(() -> tvRoomInfo.setText("線上: " + jUsers.length() + "人"));
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
                    tvNotification.setText("連線中斷: " + reason);
                    Toast.makeText(CanvasActivity.this, reason, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });

        paintClient.connect();

        // 畫布事件回呼：繪製完成時傳送網路封包，或使用橡皮擦整筆刪除時發送刪除封包
        paintView.setStrokeEventListener(new PaintView.OnStrokeEventListener() {
            @Override
            public void onStrokeFinished(Stroke stroke) {
                paintClient.sendStroke(stroke);
            }

            @Override
            public void onStrokeDeleted(String strokeId) {
                paintClient.sendDeleteStroke(strokeId);
                tvNotification.setText("已刪除自己的筆跡");
            }
        });
    }

    private void setupToolbar() {
        Button btnUndo = findViewById(R.id.btnUndo);
        btnEraser = findViewById(R.id.btnEraser);
        Button btnClear = findViewById(R.id.btnClear);
        Button btnLeave = findViewById(R.id.btnLeave);

        Button btnRed = findViewById(R.id.btnColorRed);
        Button btnBlue = findViewById(R.id.btnColorBlue);
        Button btnGreen = findViewById(R.id.btnColorGreen);
        Button btnBlack = findViewById(R.id.btnColorBlack);
        Button btnOrange = findViewById(R.id.btnColorOrange);

        btnRed.setOnClickListener(v -> {
            disableEraser();
            myColor = "#F44336";
            paintView.setCurrentColor(myColor);
            tvNotification.setText("已切換顏色: 紅色");
        });
        btnBlue.setOnClickListener(v -> {
            disableEraser();
            myColor = "#2196F3";
            paintView.setCurrentColor(myColor);
            tvNotification.setText("已切換顏色: 藍色");
        });
        btnGreen.setOnClickListener(v -> {
            disableEraser();
            myColor = "#4CAF50";
            paintView.setCurrentColor(myColor);
            tvNotification.setText("已切換顏色: 綠色");
        });
        btnBlack.setOnClickListener(v -> {
            disableEraser();
            myColor = "#000000";
            paintView.setCurrentColor(myColor);
            tvNotification.setText("已切換顏色: 黑色");
        });
        btnOrange.setOnClickListener(v -> {
            disableEraser();
            myColor = "#FF9800";
            paintView.setCurrentColor(myColor);
            tvNotification.setText("已切換顏色: 橙色");
        });

        // 橡皮擦切換按鈕：點選後進入橡皮擦模式，點擊自己的筆跡可整筆刪除
        btnEraser.setOnClickListener(v -> {
            isEraserActive = !isEraserActive;
            paintView.setEraserMode(isEraserActive);
            if (isEraserActive) {
                btnEraser.setBackgroundColor(Color.parseColor("#FF5722"));
                tvNotification.setText("【橡皮擦模式】點擊自己的筆跡可整筆刪除");
            } else {
                btnEraser.setBackgroundColor(Color.LTGRAY);
                tvNotification.setText("已切換回畫筆模式");
            }
        });

        btnUndo.setOnClickListener(v -> {
            disableEraser();
            paintClient.sendUndo(myUserId);
            paintView.undoLast(myUserId);
            tvNotification.setText("已撤銷自己最近一筆");
        });

        btnClear.setOnClickListener(v -> {
            disableEraser();
            paintClient.sendClearAll();
            tvNotification.setText("已要求清空畫布");
        });

        btnLeave.setOnClickListener(v -> {
            leaveRoom();
        });
    }

    private void disableEraser() {
        if (isEraserActive) {
            isEraserActive = false;
            paintView.setEraserMode(false);
            btnEraser.setBackgroundColor(Color.LTGRAY);
        }
    }

    private void leaveRoom() {
        if (paintClient != null) {
            paintClient.sendLeave();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        leaveRoom();
    }
}
