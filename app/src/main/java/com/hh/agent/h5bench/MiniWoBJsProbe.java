package com.hh.agent.h5bench;

public final class MiniWoBJsProbe {
    private MiniWoBJsProbe() {
    }

    public static String buildStartEpisodeScript(int seed) {
        return "(function(){"
                + "if (typeof Math.seedrandom === 'function') { Math.seedrandom('" + seed + "'); }"
                + "if (typeof WOB_DONE_GLOBAL !== 'undefined') { WOB_DONE_GLOBAL = false; }"
                + "if (typeof WOB_REWARD_GLOBAL !== 'undefined') { WOB_REWARD_GLOBAL = 0; }"
                + "if (typeof WOB_RAW_REWARD_GLOBAL !== 'undefined') { WOB_RAW_REWARD_GLOBAL = 0; }"
                + "if (typeof core === 'undefined' || typeof core.startEpisodeReal !== 'function') {"
                + "return JSON.stringify({ok:false,error:'start_episode_unavailable'});"
                + "}"
                + "core.startEpisodeReal();"
                + "return JSON.stringify({ok:true,seed:" + seed + "});"
                + "})();";
    }

    public static String buildReadStatusScript() {
        return "(function(){return JSON.stringify({"
                + "done: typeof WOB_DONE_GLOBAL === 'undefined' ? null : !!WOB_DONE_GLOBAL,"
                + "reward: typeof WOB_REWARD_GLOBAL === 'undefined' ? null : WOB_REWARD_GLOBAL,"
                + "rawReward: typeof WOB_RAW_REWARD_GLOBAL === 'undefined' ? null : WOB_RAW_REWARD_GLOBAL,"
                + "episodeId: typeof WOB_EPISODE_ID === 'undefined' ? null : WOB_EPISODE_ID"
                + "});})();";
    }
}
