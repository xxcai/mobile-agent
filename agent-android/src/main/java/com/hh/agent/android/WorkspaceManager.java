package com.hh.agent.android;

import android.content.Context;
import com.hh.agent.android.log.AgentLogs;

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
    private static final String BUILTIN_ASSETS_WORKSPACE = "builtin_workspace";
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
            AgentLogs.info(TAG, "workspace_prepare", "mode=create path=" + workspaceDir.getAbsolutePath());
            if (copyAssetsToWorkspace()) {
                AgentLogs.info(TAG, "workspace_ready", "mode=created path=" + workspaceDir.getAbsolutePath());
            } else {
                AgentLogs.error(TAG, "workspace_prepare_failed", "path=" + workspaceDir.getAbsolutePath());
            }
        } else {
            syncBuiltinSkillsIfNeeded(workspaceDir);
            AgentLogs.info(TAG, "workspace_ready", "mode=existing path=" + workspaceDir.getAbsolutePath());
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
            AgentLogs.error(TAG, "workspace_dir_create_failed", "path=" + workspaceDir.getAbsolutePath());
            return false;
        }

        try {
            // Copy SOUL.md
            copyAssetFile(ASSETS_WORKSPACE + "/SOUL.md", new File(workspaceDir, "SOUL.md"));

            // Copy USER.md
            copyAssetFile(ASSETS_WORKSPACE + "/USER.md", new File(workspaceDir, "USER.md"));

            // Copy builtin skills first, then overlay with app workspace skills.
            syncBuiltinSkillsIntoWorkspace(workspaceDir);

            return true;
        } catch (IOException e) {
            AgentLogs.error(TAG, "workspace_copy_failed", "message=" + e.getMessage(), e);
            return false;
        }
    }

    private void syncBuiltinSkillsIfNeeded(File workspaceDir) {
        try {
            syncBuiltinSkillsIntoWorkspace(workspaceDir);
        } catch (IOException e) {
            AgentLogs.warn(TAG, "builtin_skill_sync_failed", "message=" + e.getMessage());
        }
    }

    private void syncBuiltinSkillsIntoWorkspace(File workspaceDir) throws IOException {
        File skillsDir = new File(workspaceDir, "skills");
        if (!skillsDir.exists() && !skillsDir.mkdirs()) {
            AgentLogs.warn(TAG, "builtin_skill_dir_create_failed", "path=" + skillsDir.getAbsolutePath());
            return;
        }

        syncSkillsFromAssetRoot(BUILTIN_ASSETS_WORKSPACE, skillsDir, "builtin");
        syncSkillsFromAssetRoot(ASSETS_WORKSPACE, skillsDir, "workspace");
    }

    private void syncSkillsFromAssetRoot(String assetRoot, File skillsDir, String sourceTag) throws IOException {
        String[] skillNames = context.getAssets().list(assetRoot + "/skills");
        if (skillNames == null) {
            AgentLogs.debug(TAG, "builtin_skill_sync_skipped", "source=" + sourceTag + " reason=assets_list_empty");
            return;
        }
        for (String skillName : skillNames) {
            File targetSkillDir = new File(skillsDir, skillName);
            if (targetSkillDir.exists()) {
                AgentLogs.debug(TAG, "builtin_skill_overwrite", "source=" + sourceTag + " skill_name=" + skillName);
                deleteRecursively(targetSkillDir);
            } else {
                AgentLogs.debug(TAG, "builtin_skill_copy", "source=" + sourceTag + " skill_name=" + skillName);
            }
            copyAssetDirectory(assetRoot + "/skills/" + skillName, targetSkillDir);
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
            AgentLogs.debug(TAG, "asset_copied", "asset_path=" + assetPath + " dest=" + destFile.getAbsolutePath());
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

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
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
