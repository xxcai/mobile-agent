# ActivityLifecycleCallbacks 注册点分析报告

## 1. 注册点清单

| # | 文件位置 | 注册方式 | 监听方法 | 作用 |
|---|----------|----------|----------|------|
| 1 | MainActivity.java:53 | 匿名内部类 | onActivityResumed, onActivityPaused | 控制 MainActivity 页面悬浮球显示/隐藏 |
| 2 | AppLifecycleObserver.java (App.java:61 注册) | 独立类 | onActivityStarted, onActivityStopped | 监听应用整体前后台切换，发送广播 |

---

## 2. 职责分析

### 2.1 MainActivity 回调
- **触发条件**：MainActivity 恢复/暂停
- **具体行为**：
  - onActivityResumed: FloatingBallManager.show()
  - onActivityPaused: FloatingBallManager.hide()
- **目标 Activity**：仅限 MainActivity

### 2.2 AppLifecycleObserver 回调
- **触发条件**：任意 Activity 启动/停止
- **具体行为**：
  - 通过 foregroundActivityCount 计数器判断
  - count == 1: 发送 ACTION_APP_FOREGROUND 广播
  - count == 0: 发送 ACTION_APP_BACKGROUND 广播
- **目标**：全局应用级别

---

## 3. 可合并性评估

### 3.1 可以合并

**合并方案**：将 MainActivity 的显示/隐藏逻辑整合到 AppLifecycleObserver

**实现思路**：
```java
// 在 AppLifecycleObserver 中添加
@Override
public void onActivityResumed(android.app.Activity activity) {
    if (activity instanceof MainActivity) {
        FloatingBallManager.getInstance(application).show();
    }
}

@Override
public void onActivityPaused(android.app.Activity activity) {
    if (activity instanceof MainActivity) {
        FloatingBallManager.getInstance(application).hide();
    }
}
```

**合并后优势**：
1. 减少代码重复（消除 MainActivity 中的匿名回调）
2. 统一生命周期管理
3. 更容易维护

### 3.2 当前不合并的理由

**MainActivity 选择单独注册的原因**：
- 职责分离：Application 级别和 Activity 级别分开
- MainActivity 的逻辑更简单直接，不需要计数器逻辑

---

## 4. 建议

### 推荐方案：合并

**理由**：
1. 两个回调都与悬浮球显示相关
2. MainActivity 的逻辑很简单，可以整合到 AppLifecycleObserver
3. 减少代码维护成本

**合并步骤**：
1. 在 AppLifecycleObserver 中添加 onActivityResumed/onActivityPaused 回调
2. 判断 activity 是否为 MainActivity
3. 调用 FloatingBallManager 显示/隐藏
4. 删除 MainActivity.java 中的匿名回调（line 53-83）

### 注意事项

合并后需要确保：
1. FloatingBallManager 的实例获取方式兼容（可能需要 Application context）
2. 广播仍然正常工作（如果有其他 Receiver 依赖）

---

## 5. 总结

| 项目 | 状态 |
|------|------|
| 注册点数量 | 2 处 |
| 可合并性 | 是 |
| 建议操作 | 合并到 AppLifecycleObserver |
| 风险 | 低 |
