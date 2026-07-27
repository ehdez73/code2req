package com.github.ehdez73.code2req.extraction.adapter.agent.model;

import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorldState {

    private boolean knowledgeLoaded;
    private boolean entryPointsDiscovered;
    private boolean flowTraced;
    private boolean flowAnalyzed;
    private boolean flowEnriched;
    private boolean allFlowsTraced;
    private boolean flowsGrouped;
    private boolean crossRefsResolved;
    private boolean specSynthesized;
    private boolean flowQuarantined;
    private List<AmbiguityGap> quarantineGaps = List.of();

    public boolean isKnowledgeLoaded() { return knowledgeLoaded; }
    public void setKnowledgeLoaded(boolean v) { this.knowledgeLoaded = v; }

    public boolean isEntryPointsDiscovered() { return entryPointsDiscovered; }
    public void setEntryPointsDiscovered(boolean v) { this.entryPointsDiscovered = v; }

    public boolean isFlowTraced() { return flowTraced; }
    public void setFlowTraced(boolean v) { this.flowTraced = v; }

    public boolean isFlowAnalyzed() { return flowAnalyzed; }
    public void setFlowAnalyzed(boolean v) { this.flowAnalyzed = v; }

    public boolean isFlowEnriched() { return flowEnriched; }
    public void setFlowEnriched(boolean v) { this.flowEnriched = v; }

    public boolean isAllFlowsTraced() { return allFlowsTraced; }
    public void setAllFlowsTraced(boolean v) { this.allFlowsTraced = v; }

    public boolean isFlowsGrouped() { return flowsGrouped; }
    public void setFlowsGrouped(boolean v) { this.flowsGrouped = v; }

    public boolean isCrossRefsResolved() { return crossRefsResolved; }
    public void setCrossRefsResolved(boolean v) { this.crossRefsResolved = v; }

    public boolean isSpecSynthesized() { return specSynthesized; }
    public void setSpecSynthesized(boolean v) { this.specSynthesized = v; }

    public boolean isFlowQuarantined() { return flowQuarantined; }
    public void setFlowQuarantined(boolean v) { this.flowQuarantined = v; }

    public List<AmbiguityGap> getQuarantineGaps() { return quarantineGaps; }
    public void setQuarantineGaps(List<AmbiguityGap> v) { this.quarantineGaps = v; }

    public void reset() {
        this.knowledgeLoaded = false;
        this.entryPointsDiscovered = false;
        this.flowTraced = false;
        this.flowAnalyzed = false;
        this.flowEnriched = false;
        this.allFlowsTraced = false;
        this.flowsGrouped = false;
        this.crossRefsResolved = false;
        this.specSynthesized = false;
        this.flowQuarantined = false;
        this.quarantineGaps = List.of();
    }
}
