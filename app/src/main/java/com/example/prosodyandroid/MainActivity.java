package com.example.prosodyandroid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView statusView, logView;
    private ScrollView scrollView;
    private Button startBtn, stopBtn;
    private EditText hostnameEdit;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView   = findViewById(R.id.status);
        logView      = findViewById(R.id.log);
        scrollView   = findViewById(R.id.scroll);
        startBtn     = findViewById(R.id.btn_start);
        stopBtn      = findViewById(R.id.btn_stop);
        hostnameEdit = findViewById(R.id.hostname);

        hostnameEdit.setText("localhost");

        startBtn.setOnClickListener(v -> {
            String host = hostnameEdit.getText().toString().trim();
            if (host.isEmpty()) host = "localhost";
            ProsodyManager.getInstance().start(this, host);
        });

        stopBtn.setOnClickListener(v ->
            ProsodyManager.getInstance().stop());

        updater = new Runnable() {
            @Override public void run() {
                updateUI();
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(updater);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updater);
    }

    private void updateUI() {
        boolean running = ProsodyManager.getInstance().isRunning();
        statusView.setText(running ? "● Prosody running" : "○ Prosody stopped");
        statusView.setTextColor(running ? 0xFF00C853 : 0xFF757575);
        startBtn.setEnabled(!running);
        stopBtn.setEnabled(running);

        String log = ProsodyManager.getInstance().getLog();
        logView.setText(log);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
