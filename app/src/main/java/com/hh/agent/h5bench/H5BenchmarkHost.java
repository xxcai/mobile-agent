package com.hh.agent.h5bench;

public class H5BenchmarkHost {
    public interface Starter {
        void start(H5BenchmarkHost host);
    }

    private final Starter starter;
    private volatile H5BenchmarkRunState state = H5BenchmarkRunState.IDLE;

    public H5BenchmarkHost(Starter starter) {
        this.starter = starter;
    }

    public H5BenchmarkRunState getState() {
        return state;
    }

    public void start() {
        state = H5BenchmarkRunState.STARTING;
        starter.start(this);
    }

    public void markRunning() {
        state = H5BenchmarkRunState.RUNNING;
    }

    public void markCompleted() {
        state = H5BenchmarkRunState.COMPLETED;
    }

    public void markFailed() {
        state = H5BenchmarkRunState.FAILED;
    }
}
