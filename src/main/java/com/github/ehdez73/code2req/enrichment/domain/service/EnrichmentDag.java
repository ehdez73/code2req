package com.github.ehdez73.code2req.enrichment.domain.service;

import com.github.ehdez73.code2req.enrichment.domain.model.PlannerDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class EnrichmentDag {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentDag.class);

    private final ConcurrentMap<String, BranchState> branches;
    private final ConcurrentMap<String, String> parentMap;
    private final Set<String> visited;
    private final ConcurrentLinkedQueue<PlannerDecision> pendingQueue;
    private final int maxDepth;

    public EnrichmentDag(int maxDepth) {
        this.maxDepth = maxDepth;
        this.branches = new ConcurrentHashMap<>();
        this.parentMap = new ConcurrentHashMap<>();
        this.visited = ConcurrentHashMap.newKeySet();
        this.pendingQueue = new ConcurrentLinkedQueue<>();
    }

    public void registerRootTask(PlannerDecision decision) {
        branches.put(decision.taskId(), new BranchState(decision.taskId(), maxDepth));
        pendingQueue.add(decision);
    }

    public String registerDiscoveredDependency(String parentTaskId, String filePath) {
        BranchState branch = branches.get(parentTaskId);
        if (branch == null) {
            branch = findBranchForTask(parentTaskId);
        }
        if (branch == null) {
            log.warn("No branch found for parent task {}, cannot register dependency {}", parentTaskId, filePath);
            return null;
        }
        String childTaskId = parentTaskId + ":" + filePath;
        parentMap.put(childTaskId, parentTaskId);
        branch.addPendingDependency();
        branch.pause();
        return childTaskId;
    }

    public void markVisited(String hash) {
        visited.add(hash);
    }

    public boolean isVisited(String hash) {
        return visited.contains(hash);
    }

    public boolean markTaskComplete(String taskId) {
        String parentId = parentMap.get(taskId);
        if (parentId != null) {
            BranchState branch = branches.get(parentId);
            if (branch == null) {
                branch = branches.values().stream()
                    .filter(b -> b.rootTaskId().equals(parentId))
                    .findFirst().orElse(null);
            }
            if (branch != null) {
                branch.removePendingDependency();
                if (branch.pendingDependencies() == 0) {
                    branch.resume();
                }
            }
        }
        visited.remove(taskId);
        return true;
    }

    public boolean allBranchesComplete() {
        return branches.values().stream().allMatch(BranchState::isComplete);
    }

    public List<PlannerDecision> drainPending() {
        List<PlannerDecision> decisions = new ArrayList<>();
        while (!pendingQueue.isEmpty()) {
            decisions.add(pendingQueue.poll());
        }
        return decisions;
    }

    public void enqueue(PlannerDecision decision) {
        pendingQueue.add(decision);
    }

    public boolean hasPending() {
        return !pendingQueue.isEmpty();
    }

    public BranchState getBranch(String taskId) {
        BranchState branch = branches.get(taskId);
        if (branch == null) {
            String rootId = parentMap.get(taskId);
            if (rootId != null) {
                branch = branches.get(rootId);
            }
        }
        return branch;
    }

    public BranchState getBranchForRoot(String rootTaskId) {
        return branches.get(rootTaskId);
    }

    private BranchState findBranchForTask(String taskId) {
        BranchState branch = branches.get(taskId);
        if (branch != null) return branch;
        String rootId = parentMap.get(taskId);
        if (rootId != null) {
            return branches.get(rootId);
        }
        for (BranchState b : branches.values()) {
            if (parentMap.values().stream().anyMatch(v -> v.equals(taskId) && branches.containsKey(v))) {
                return b;
            }
        }
        return null;
    }

    public Set<String> visited() {
        return Collections.unmodifiableSet(visited);
    }

    public int branchCount() {
        return branches.size();
    }

    public int pendingCount() {
        return pendingQueue.size();
    }
}
