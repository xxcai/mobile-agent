package com.hh.agent.android.glow;

/**
 * 单个粒子数据。
 * 预分配对象，通过 alive 标记复用，避免 GC。
 */
class Particle {

    // 位置（归一化 [0,1]，与 UV 同坐标系）
    float x;
    float y;

    // 速度（归一化坐标/秒）
    float vx;
    float vy;

    // 大小（像素）
    float size;

    // 透明度 [0,1]
    float alpha;

    // 生命周期
    float lifetime;     // 总寿命（秒）
    float elapsed;      // 已经过时间（秒）

    // 是否存活
    boolean alive;

    /** 重置粒子为死亡状态 */
    void reset() {
        alive = false;
        elapsed = 0f;
        alpha = 0f;
    }

    /** 是否已超过生命周期 */
    boolean isDead() {
        return elapsed >= lifetime;
    }

    /**
     * 更新粒子状态。
     * @param dt 帧间隔（秒）
     * @return true 如果粒子仍然存活
     */
    boolean update(float dt) {
        if (!alive) return false;

        elapsed += dt;
        if (isDead()) {
            reset();
            return false;
        }

        // 位置更新
        x += vx * dt;
        y += vy * dt;

        // alpha 三段淡入淡出：0→1 (前30%) → 0 (后70%)
        float progress = elapsed / lifetime;
        if (progress < 0.3f) {
            alpha = progress / 0.3f;
        } else {
            alpha = 1.0f - (progress - 0.3f) / 0.7f;
        }

        return true;
    }
}
