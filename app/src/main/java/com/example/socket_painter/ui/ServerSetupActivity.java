package com.example.socket_painter.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.socket_painter.R;

public class ServerSetupActivity extends AppCompatActivity {

    private EditText etServerName, etServerPort;
    private Button btnStartServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_server_setup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnStartServer), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etServerName = findViewById(R.id.etServerName);
        etServerPort = findViewById(R.id.etServerPort);
        btnStartServer = findViewById(R.id.btnStartServer);

        btnStartServer.setOnClickListener(v -> {
            String serverName = etServerName.getText().toString().trim();
            String portStr = etServerPort.getText().toString().trim();

            if (serverName.isEmpty()) {
                serverName = "我的線上畫板";
            }
            if (portStr.isEmpty()) {
                Toast.makeText(this, "請輸入 Port", Toast.LENGTH_SHORT).show();
                return;
            }

            int port = Integer.parseInt(portStr);

            Intent intent = new Intent(ServerSetupActivity.this, ServerCanvasActivity.class);
            intent.putExtra("serverName", serverName);
            intent.putExtra("port", port);
            startActivity(intent);
        });
    }
}
