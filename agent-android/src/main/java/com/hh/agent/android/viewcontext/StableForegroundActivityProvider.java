package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

public interface StableForegroundActivityProvider {

    @Nullable
    Activity getStableForegroundActivity();
}
