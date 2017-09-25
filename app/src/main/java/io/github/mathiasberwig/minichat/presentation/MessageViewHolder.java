package io.github.mathiasberwig.minichat.presentation;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import de.hdodenhof.circleimageview.CircleImageView;
import io.github.mathiasberwig.minichat.R;

/**
 * Created by mathias.berwig on 25/09/2017.
 */

public class MessageViewHolder extends RecyclerView.ViewHolder {
    TextView message;
    TextView username;
    CircleImageView userPhoto;

    public MessageViewHolder(View v) {
        super(v);
        message = itemView.findViewById(R.id.tv_message);
        username = itemView.findViewById(R.id.tv_username);
        userPhoto = itemView.findViewById(R.id.img_user);
    }
}