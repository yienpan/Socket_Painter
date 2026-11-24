package com.example.socket_painter;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.socket_painter.ui.ClientJoinActivity;
import com.example.socket_painter.ui.ServerSetupActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnGoHost, btnGoJoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnGoHost = findViewById(R.id.btnGoHost);
        btnGoJoin = findViewById(R.id.btnGoJoin);

        btnGoHost.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ServerSetupActivity.class);
            startActivity(intent);
        });
        btnGoJoin.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ClientJoinActivity.class);
            startActivity(intent);
        });
    }
}
