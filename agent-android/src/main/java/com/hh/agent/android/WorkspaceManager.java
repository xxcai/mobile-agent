package com.hh.agent.android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Workspace Manager
 * Manages preset workspace files initialization from assets to user directory
 */
public class WorkspaceManager {

    private static final String TAG = "WorkspaceManager";
    private static final String WORKSPACE_DIR = ".icraw/workspace";
    private static final String ASSETS_WORKSPACE = "workspace";


    private final Context context;

    public WorkspaceManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Initialize workspace from assets if not exists
     *
     * @return The workspace directory path
     */
    public String initialize() {
        File workspaceDir = getWorkspaceDirectory();

        if (!workspaceDir.exists()) {
            Log.i(TAG, "Workspace not found, copying from assets: " + workspaceDir.getAbsolutePath());
            if (copyAssetsToWorkspace()) {
                Log.i(TAG, "Workspace initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize workspace from assets");
            }
        } else {
            Log.i(TAG, "Workspace already exists, using user workspace: " + workspaceDir.getAbsolutePath());
        }

        return workspaceDir.getAbsolutePath();
    }

    /**
     * Get the workspace directory path
     * Uses external files directory from Context.getExternalFilesDir()
     *
     * @return Workspace directory
     */
    public File getWorkspaceDirectory() {
        // Use Context.getExternalFilesDir() to get SD card path
        // This returns: /storage/emulated/0/Android/data/{package}/files/
        File filesDir = context.getExternalFilesDir(null);
        if (filesDir == null) {
            // Fallback to internal storage if external storage is not available
            filesDir = context.getFilesDir();
        }
        return new File(filesDir, WORKSPACE_DIR);
    }

    /**
     * Copy preset workspace files from assets to user directory
     *
     * @return true if successful
     */
    private boolean copyAssetsToWorkspace() {
        File workspaceDir = getWorkspaceDirectory();

        // Create workspace directory
        if (!workspaceDir.mkdirs()) {
            Log.e(TAG, "Failed to create workspace directory: " + workspaceDir.getAbsolutePath());
            return false;
        }

        try {
            // Copy SOUL.md
            copyAssetFile(ASSETS_WORKSPACE + "/SOUL.md", new File(workspaceDir, "SOUL.md"));

            // Copy USER.md
            copyAssetFile(ASSETS_WORKSPACE + "/USER.md", new File(workspaceDir, "USER.md"));

            // Copy skills directory - dynamically read from assets/skills/
            File skillsDir = new File(workspaceDir, "skills");
            String[] skillNames = context.getAssets().list(ASSETS_WORKSPACE + "/skills");
            if (skillNames != null) {
                for (String skillName : skillNames) {
                    File targetSkillDir = new File(skillsDir, skillName);
                    if (!targetSkillDir.exists()) {
                        Log.i(TAG, "Copying built-in skill: " + skillName);
                        copyAssetDirectory(ASSETS_WORKSPACE + "/skills/" + skillName, targetSkillDir);
                    } else {
                        Log.i(TAG, "Skipping built-in skill (user version exists): " + skillName);
                    }
                }
            }

            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copying workspace files", e);
            return false;
        }
    }

    /**
     * Copy a single file from assets
     */
    private void copyAssetFile(String assetPath, File destFile) throws IOException {
        try (InputStream is = context.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }

            os.flush();
            Log.d(TAG, "Copied asset: " + assetPath + " -> " + destFile.getAbsolutePath());
        }
    }

    /**
     * Copy a directory from assets recursively
     */
    private void copyAssetDirectory(String assetPath, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        String[] files = context.getAssets().list(assetPath);
        if (files == null || files.length == 0) {
            return;
        }

        for (String file : files) {
            String assetSubPath = assetPath + "/" + file;
            File destSubPath = new File(destDir, file);

            try {
                // Try as file first
                copyAssetFile(assetSubPath, destSubPath);
            } catch (IOException e) {
                // If not a file, try as directory
                copyAssetDirectory(assetSubPath, destSubPath);
            }
        }
    }

    /**
     * Check if workspace has been initialized
     *
     * @return true if workspace directory exists
     */
    public boolean isWorkspaceInitialized() {
        return getWorkspaceDirectory().exists();
    }
}
