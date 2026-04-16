package com.hh.agent.android;

import android.content.Context;
import com.hh.agent.android.log.AgentLogs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Workspace Manager
 * Manages preset workspace files initialization from assets to user directory
 */
public class WorkspaceManager {

    private static final String TAG = "WorkspaceManager";
    private static final String WORKSPACE_DIR = ".icraw/workspace";
    private static final String BUILTIN_ASSETS_WORKSPACE = "builtin_workspace";
    private static final String ASSETS_WORKSPACE = "workspace";
    private static final String[] WORKSPACE_ROOT_FILES = {
            "SOUL.md",
            "USER.md",
            "AGENTS.md",
            "TOOLS.md"
    };
    private static final String[] MANAGED_PROFILES = {
            AgentRuntimeProfiles.FULL,
            AgentRuntimeProfiles.VISUAL_ONLY
    };


    private final Context context;
    private final String promptProfile;

    public WorkspaceManager(Context context) {
        this(context, AgentRuntimeProfiles.FULL);
    }

    public WorkspaceManager(Context context, String promptProfile) {
        this.context = context.getApplicationContext();
        this.promptProfile = AgentRuntimeProfiles.normalize(promptProfile);
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
            try {
                ensureWorkspaceDirectoryExists(workspaceDir);
                syncWorkspaceFilesAndSkills(workspaceDir);
                AgentLogs.info(TAG, "workspace_ready", "mode=created path=" + workspaceDir.getAbsolutePath());
            } catch (IOException e) {
                AgentLogs.error(TAG, "workspace_prepare_failed", "path=" + workspaceDir.getAbsolutePath());
                AgentLogs.error(TAG, "workspace_copy_failed", "message=" + e.getMessage(), e);
            }
        } else {
            syncWorkspaceIfNeeded(workspaceDir);
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

    private void ensureWorkspaceDirectoryExists(File workspaceDir) throws IOException {
        if (!workspaceDir.mkdirs()) {
            throw new IOException("Failed to create workspace directory: " + workspaceDir.getAbsolutePath());
        }
    }

    private void syncWorkspaceIfNeeded(File workspaceDir) {
        try {
            syncWorkspaceFilesAndSkills(workspaceDir);
        } catch (IOException e) {
            AgentLogs.warn(TAG, "builtin_skill_sync_failed", "message=" + e.getMessage());
        }
    }

    private void syncWorkspaceFilesAndSkills(File workspaceDir) throws IOException {
        syncWorkspaceRootFiles(workspaceDir);

        File skillsDir = new File(workspaceDir, "skills");
        if (!skillsDir.exists() && !skillsDir.mkdirs()) {
            AgentLogs.warn(TAG, "builtin_skill_dir_create_failed", "path=" + skillsDir.getAbsolutePath());
            return;
        }

        replaceBuiltinSkills(skillsDir);
        syncSkillsFromAssetRoot(resolveWorkspaceSkillRoot(), skillsDir, "workspace");
    }

    private void syncWorkspaceRootFiles(File workspaceDir) throws IOException {
        for (String fileName : WORKSPACE_ROOT_FILES) {
            copyWorkspaceRootFile(fileName, workspaceDir);
        }
    }

    private void replaceBuiltinSkills(File skillsDir) throws IOException {
        Set<String> builtinSkillNames = listManagedBuiltinSkillNames();

        for (String skillName : builtinSkillNames) {
            File targetSkillDir = new File(skillsDir, skillName);
            if (!targetSkillDir.exists()) {
                continue;
            }
            AgentLogs.debug(TAG, "builtin_skill_replace_remove", "skill_name=" + skillName);
            deleteRecursively(targetSkillDir);
        }

        syncSkillsFromAssetRoot(resolveBuiltinSkillRoot(), skillsDir, "builtin");
    }

    private Set<String> listManagedBuiltinSkillNames() throws IOException {
        Set<String> builtinSkillNames = new LinkedHashSet<>();
        for (String profile : MANAGED_PROFILES) {
            builtinSkillNames.addAll(listSkillNames(BUILTIN_ASSETS_WORKSPACE + "/" + profile));
        }
        builtinSkillNames.addAll(listSkillNames(BUILTIN_ASSETS_WORKSPACE));
        return builtinSkillNames;
    }

    private void syncSkillsFromAssetRoot(String assetRoot, File skillsDir, String sourceTag) throws IOException {
        Set<String> currentSkillNames = listSkillNames(assetRoot);
        if (currentSkillNames.isEmpty()) {
            AgentLogs.debug(TAG, "builtin_skill_sync_skipped",
                    "source=" + sourceTag + " reason=assets_list_empty");
            return;
        }

        for (String skillName : currentSkillNames) {
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

    private Set<String> listSkillNames(String assetRoot) throws IOException {
        String[] skillNames = context.getAssets().list(assetRoot + "/skills");
        Set<String> names = new LinkedHashSet<>();
        if (skillNames == null) {
            return names;
        }
        for (String skillName : skillNames) {
            if (skillName != null && !skillName.trim().isEmpty()) {
                names.add(skillName.trim());
            }
        }
        return names;
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

    private void copyWorkspaceRootFile(String fileName, File workspaceDir) throws IOException {
        String assetPath = resolveAssetFilePath(ASSETS_WORKSPACE, fileName);
        if (assetPath == null) {
            return;
        }
        copyAssetFile(assetPath, new File(workspaceDir, fileName));
    }

    private String resolveAssetFilePath(String assetBasePath, String fileName) {
        String profilePath = assetBasePath + "/" + promptProfile + "/" + fileName;
        if (assetFileExists(profilePath)) {
            return profilePath;
        }
        String fullPath = assetBasePath + "/" + AgentRuntimeProfiles.FULL + "/" + fileName;
        if (assetFileExists(fullPath)) {
            return fullPath;
        }
        String defaultPath = assetBasePath + "/" + fileName;
        return assetFileExists(defaultPath) ? defaultPath : null;
    }

    private String resolveBuiltinSkillRoot() {
        return resolveSkillRoot(BUILTIN_ASSETS_WORKSPACE);
    }

    private String resolveWorkspaceSkillRoot() {
        return resolveSkillRoot(ASSETS_WORKSPACE);
    }

    private String resolveSkillRoot(String assetBasePath) {
        String profileRoot = assetBasePath + "/" + promptProfile + "/skills";
        if (assetDirectoryExists(profileRoot)) {
            return assetBasePath + "/" + promptProfile;
        }
        String fullRoot = assetBasePath + "/" + AgentRuntimeProfiles.FULL + "/skills";
        if (assetDirectoryExists(fullRoot)) {
            return assetBasePath + "/" + AgentRuntimeProfiles.FULL;
        }
        return assetBasePath;
    }

    private boolean assetFileExists(String assetPath) {
        try (InputStream ignored = context.getAssets().open(assetPath)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean assetDirectoryExists(String assetPath) {
        try {
            String[] files = context.getAssets().list(assetPath);
            return files != null;
        } catch (IOException ignored) {
            return false;
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
