package com.hh.agent.mockemail.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.hh.agent.R;
import com.hh.agent.mockemail.model.MockEmail;

import java.util.List;

public class EmailListAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<MockEmail> emails;

    public EmailListAdapter(Context context, List<MockEmail> emails) {
        this.inflater = LayoutInflater.from(context);
        this.emails = emails;
    }

    @Override
    public int getCount() {
        return emails.size();
    }

    @Override
    public Object getItem(int position) {
        return emails.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_email_message, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        MockEmail email = emails.get(position);
        holder.senderView.setText(email.getSender());
        holder.subjectView.setText(email.getSubject());
        holder.previewView.setText(email.getPreview());
        holder.timeView.setText(email.getReceivedTime());
        holder.labelView.setText(email.getLabel());
        holder.unreadDot.setVisibility(email.isUnread() ? View.VISIBLE : View.INVISIBLE);
        return convertView;
    }

    private static final class ViewHolder {
        private final TextView senderView;
        private final TextView subjectView;
        private final TextView previewView;
        private final TextView timeView;
        private final TextView labelView;
        private final View unreadDot;

        private ViewHolder(View itemView) {
            senderView = itemView.findViewById(R.id.emailSenderView);
            subjectView = itemView.findViewById(R.id.emailSubjectView);
            previewView = itemView.findViewById(R.id.emailPreviewView);
            timeView = itemView.findViewById(R.id.emailTimeView);
            labelView = itemView.findViewById(R.id.emailLabelView);
            unreadDot = itemView.findViewById(R.id.emailUnreadDot);
        }
    }
}
