package com.hh.agent.h5bench;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class H5BenchmarkHostTest {
    @Test
    public void start_rejectsDuplicateStartsWhileStartingOrRunning() {
        final int[] starts = {0};
        H5BenchmarkHost host = new H5BenchmarkHost(ignored -> starts[0]++);

        assertTrue(host.start());
        assertFalse(host.start());

        host.markRunning();

        assertFalse(host.start());
        assertEquals(1, starts[0]);
        assertEquals(H5BenchmarkRunState.RUNNING, host.getState());
    }
}
