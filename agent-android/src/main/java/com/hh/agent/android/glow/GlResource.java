package com.hh.agent.android.glow;

import android.opengl.GLES30;

/**
 * 管理 OpenGL program 和 shader 资源的生命周期。
 */
class GlResource {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    GlResource(int programId, int vertexShaderId, int fragmentShaderId) {
        this.programId = programId;
        this.vertexShaderId = vertexShaderId;
        this.fragmentShaderId = fragmentShaderId;
    }

    int getProgramId() {
        return programId;
    }

    void release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId);
            programId = 0;
        }
        if (vertexShaderId != 0) {
            GLES30.glDeleteShader(vertexShaderId);
            vertexShaderId = 0;
        }
        if (fragmentShaderId != 0) {
            GLES30.glDeleteShader(fragmentShaderId);
            fragmentShaderId = 0;
        }
    }
}
