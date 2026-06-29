package com.github.ehdez73.code2req.enrichment.domain.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BranchState {

    private final String rootTaskId;
    private final int maxDepth;
    private final AtomicInteger currentDepth;
    private final AtomicBoolean paused;
    private final AtomicInteger pendingDependencies;

    public BranchState(String rootTaskId, int maxDepth) {
        this.rootTaskId = rootTaskId;
        this.maxDepth = maxDepth;
        this.currentDepth = new AtomicInteger(0);
        this.paused = new AtomicBoolean(false);
        this.pendingDependencies = new AtomicInteger(0);
    }

    public String rootTaskId() {
        return rootTaskId;
    }

    public int currentDepth() {
        return currentDepth.get();
    }

    public boolean isAtMaxDepth() {
        return currentDepth.get() >= maxDepth;
    }

    public boolean incrementDepth() {
        return currentDepth.incrementAndGet() > maxDepth;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public int pendingDependencies() {
        return pendingDependencies.get();
    }

    public void addPendingDependency() {
        pendingDependencies.incrementAndGet();
    }

    public void removePendingDependency() {
        pendingDependencies.decrementAndGet();
    }

    public boolean isComplete() {
        return !paused.get() && pendingDependencies.get() == 0;
    }
}
