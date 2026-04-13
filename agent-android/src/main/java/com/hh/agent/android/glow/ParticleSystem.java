package com.hh.agent.android.glow;

import java.util.Random;

/**
 * 粒子系统。
 * 管理 4 个发射区（左/右/上/下），粒子从屏幕边缘向内飞行。
 * 预分配固定数组，无 GC。
 */
class ParticleSystem {

    /** 最大粒子数（也是 uniform 数组大小） */
    static final int MAX_PARTICLES = 80;

    /** 每秒发射粒子数 */
    private float emitRate = 40f;

    private final Particle[] particles;
    private final Random random = new Random();
    private final float[] uniformData; // 打包好的 uniform 数据 [x, y, size, alpha] × N

    // 画布尺寸（像素），用于速度归一化
    private float screenWidth;
    private float screenHeight;

    // 发射累积器
    private float emitAccumulator;

    // 是否正在发射
    private boolean emitting;

    ParticleSystem() {
        particles = new Particle[MAX_PARTICLES];
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles[i] = new Particle();
        }
        uniformData = new float[MAX_PARTICLES * 4];
    }

    /** 设置画布尺寸 */
    void setScreenSize(float width, float height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    /** 开始/停止发射 */
    void setEmitting(boolean emitting) {
        this.emitting = emitting;
    }

    /**
     * 每帧更新。
     * @param dt 帧间隔（秒）
     * @return 当前存活粒子数
     */
    int update(float dt) {
        // 发射新粒子
        if (emitting && screenWidth > 0 && screenHeight > 0) {
            emitAccumulator += emitRate * dt;
            while (emitAccumulator >= 1f) {
                emitAccumulator -= 1f;
                emitOne();
            }
        }

        // 更新所有存活粒子
        int aliveCount = 0;
        for (Particle p : particles) {
            if (p.alive) {
                p.update(dt);
            }
            if (p.alive) {
                aliveCount++;
            }
        }

        return aliveCount;
    }

    /** 发射一个粒子，随机选发射区 */
    private void emitOne() {
        // 找一个死亡粒子复用
        Particle p = null;
        for (Particle candidate : particles) {
            if (!candidate.alive) {
                p = candidate;
                break;
            }
        }
        if (p == null) return; // 池满

        // 随机选发射区：0=左 1=右 2=上 3=下
        int edge = random.nextInt(4);

        // 归一化速度（像素/秒 → UV坐标/秒）
        float speed = (80f + random.nextFloat() * 40f); // 80~120 px/s
        float size = 6f + random.nextFloat() * 4.5f;     // 6~10.5 px
        float lifetime = 1.0f + random.nextFloat() * 0.8f; // 1.0~1.8 s

        p.alive = true;
        p.elapsed = 0f;
        p.lifetime = lifetime;
        p.size = size;
        p.alpha = 0f;

        switch (edge) {
            case 0: // 左边，向右飞
                p.x = 0f;
                p.y = random.nextFloat();
                p.vx = speed / screenWidth;
                p.vy = (random.nextFloat() - 0.5f) * speed * 0.6f / screenHeight;
                break;
            case 1: // 右边，向左飞
                p.x = 1f;
                p.y = random.nextFloat();
                p.vx = -speed / screenWidth;
                p.vy = (random.nextFloat() - 0.5f) * speed * 0.6f / screenHeight;
                break;
            case 2: // 上边，向下飞
                p.x = random.nextFloat();
                p.y = 0f;
                p.vx = (random.nextFloat() - 0.5f) * speed * 0.6f / screenWidth;
                p.vy = speed / screenHeight;
                break;
            case 3: // 下边，向上飞
                p.x = random.nextFloat();
                p.y = 1f;
                p.vx = (random.nextFloat() - 0.5f) * speed * 0.6f / screenWidth;
                p.vy = -speed / screenHeight;
                break;
        }
    }

    /**
     * 将存活粒子数据打包为 uniform 数组。
     * 每个粒子 4 个 float：[x, y, size, alpha]
     * @return 打包数据引用
     */
    float[] packUniforms() {
        int idx = 0;
        for (Particle p : particles) {
            if (p.alive) {
                uniformData[idx++] = p.x;
                uniformData[idx++] = p.y;
                uniformData[idx++] = p.size;
                uniformData[idx++] = p.alpha;
            }
        }
        return uniformData;
    }

    /** 当前存活粒子数 */
    int aliveCount() {
        int count = 0;
        for (Particle p : particles) {
            if (p.alive) count++;
        }
        return count;
    }
}
