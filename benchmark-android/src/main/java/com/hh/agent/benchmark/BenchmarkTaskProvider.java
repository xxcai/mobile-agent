package com.hh.agent.benchmark;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class BenchmarkTaskProvider extends ContentProvider {

    private BenchmarkTaskManager manager;

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }
        manager = new BenchmarkTaskManager(getContext());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (BenchmarkTaskManager.METHOD_RUN_TASK.equals(method) && manager != null) {
            return manager.runTask(extras);
        }
        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
