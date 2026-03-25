package com.hh.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.mockemail.MockEmailRepository;
import com.hh.agent.mockemail.model.MockEmail;

public class EmailDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL_ID = "email_id";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_detail);

        String emailId = getIntent().getStringExtra(EXTRA_EMAIL_ID);
        MockEmail email = MockEmailRepository.findById(emailId);
        if (email == null) {
            finish();
            return;
        }

        ImageView backButton = findViewById(R.id.emailDetailBackButton);
        TextView titleView = findViewById(R.id.emailDetailTitleView);
        View loadingView = findViewById(R.id.emailDetailLoadingView);
        View contentView = findViewById(R.id.emailDetailContentView);
        TextView subjectView = findViewById(R.id.emailSubjectValueView);
        TextView senderView = findViewById(R.id.emailSenderValueView);
        TextView dateView = findViewById(R.id.emailDateValueView);
        TextView bodyView = findViewById(R.id.emailBodyValueView);

        backButton.setOnClickListener(v -> finish());
        titleView.setText(email.getSender());
        subjectView.setText(email.getSubject());
        senderView.setText(email.getSender());
        dateView.setText(email.getReceivedDate());
        bodyView.setText(email.getBody());

        loadingView.setVisibility(View.VISIBLE);
        contentView.setVisibility(View.GONE);
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            loadingView.setVisibility(View.GONE);
            contentView.setVisibility(View.VISIBLE);
        }, buildDelayMillis(email.getId()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private int buildDelayMillis(String seed) {
        return 1000 + Math.abs(seed.hashCode() % 1000);
    }
}
