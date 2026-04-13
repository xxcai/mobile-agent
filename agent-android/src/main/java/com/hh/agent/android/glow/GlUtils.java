package com.hh.agent.android.glow;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * OpenGL shader 编译与 program 链接工具。
 */
class GlUtils {

    private static final String TAG = "GlUtils";

    static int compileShader(int type, String source) {
        int shaderId = GLES30.glCreateShader(type);
        if (shaderId == 0) {
            Log.e(TAG, "glCreateShader failed");
            return 0;
        }
        GLES30.glShaderSource(shaderId, source);
        GLES30.glCompileShader(shaderId);

        int[] compileStatus = new int[1];
        GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compile error: " + GLES30.glGetShaderInfoLog(shaderId));
            GLES30.glDeleteShader(shaderId);
            return 0;
        }
        return shaderId;
    }

    static int linkProgram(int vertexShaderId, int fragmentShaderId) {
        int programId = GLES30.glCreateProgram();
        if (programId == 0) {
            Log.e(TAG, "glCreateProgram failed");
            return 0;
        }
        GLES30.glAttachShader(programId, vertexShaderId);
        GLES30.glAttachShader(programId, fragmentShaderId);
        GLES30.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: " + GLES30.glGetProgramInfoLog(programId));
            GLES30.glDeleteProgram(programId);
            return 0;
        }
        return programId;
    }

    static String loadShaderFromRaw(Context context, int resId) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load shader resource", e);
        }
        return sb.toString();
    }
}
