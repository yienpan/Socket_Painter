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

public class ClientJoinActivity extends AppCompatActivity {

    private EditText etNickname, etServerIp, etPort;
    private Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_client_join);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnConnect), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etNickname = findViewById(R.id.etNickname);
        etServerIp = findViewById(R.id.etServerIp);
        etPort = findViewById(R.id.etPort);
        btnConnect = findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(v -> {
            String nickname = etNickname.getText().toString().trim();
            String serverIp = etServerIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();

            if (nickname.isEmpty()) {
                Toast.makeText(this, "請輸入您的暱稱", Toast.LENGTH_SHORT).show();
                return;
            }
            if (serverIp.isEmpty()) {
                Toast.makeText(this, "請輸入伺服器 IP", Toast.LENGTH_SHORT).show();
                return;
            }
            if (portStr.isEmpty()) {
                portStr = "8888";
            }

            int port = Integer.parseInt(portStr);

            Intent intent = new Intent(ClientJoinActivity.this, CanvasActivity.class);
            intent.putExtra("serverIp", serverIp);
            intent.putExtra("port", port);
            intent.putExtra("nickname", nickname);
            startActivity(intent);
        });
    }
}
