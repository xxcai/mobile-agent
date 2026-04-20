package com.hh.agent.h5bench;

public class H5BenchmarkHost {
    public interface Starter {
        void start(H5BenchmarkHost host);
    }

    private final Starter starter;
    private volatile H5BenchmarkRunState state = H5BenchmarkRunState.IDLE;

    public H5BenchmarkHost(Starter starter) {
        if (starter == null) {
            throw new IllegalArgumentException("starter cannot be null");
        }
        this.starter = starter;
    }

    public H5BenchmarkRunState getState() {
        return state;
    }

    public synchronized boolean start() {
        if (state == H5BenchmarkRunState.STARTING || state == H5BenchmarkRunState.RUNNING) {
            return false;
        }
        state = H5BenchmarkRunState.STARTING;
        starter.start(this);
        return true;
    }

    public synchronized void markRunning() {
        state = H5BenchmarkRunState.RUNNING;
    }

    public synchronized void markCompleted() {
        state = H5BenchmarkRunState.COMPLETED;
    }

    public synchronized void markFailed() {
        state = H5BenchmarkRunState.FAILED;
    }
}
