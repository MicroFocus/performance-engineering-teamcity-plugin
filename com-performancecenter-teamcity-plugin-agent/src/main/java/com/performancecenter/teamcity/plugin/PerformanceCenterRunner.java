package com.performancecenter.teamcity.plugin;

import jetbrains.buildServer.RunBuildException;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.artifacts.ArtifactsWatcher;
import org.jetbrains.annotations.NotNull;

/**
 * Created by bemh on 2/12/2018.
 */
public class PerformanceCenterRunner implements AgentBuildRunner, AgentBuildRunnerInfo {

    private ArtifactsWatcher artifactsWatcher;

    public PerformanceCenterRunner(@NotNull final ArtifactsWatcher artifactsWatcher) {
        this.artifactsWatcher = artifactsWatcher;

    }

    @NotNull
    @Override
    public BuildProcess createBuildProcess(@NotNull AgentRunningBuild agentRunningBuild, @NotNull BuildRunnerContext buildRunnerContext) throws RunBuildException {
        return new PerformanceCenterProcess(agentRunningBuild, buildRunnerContext, artifactsWatcher);
    }

    @NotNull
    @Override
    public AgentBuildRunnerInfo getRunnerInfo() {
        return this;
    }

    @NotNull
    @Override
    public String getType() {
        return StringsConstants.RunTypeName;
    }

    @Override
    public boolean canRun(@NotNull BuildAgentConfiguration buildAgentConfiguration) {
        return true;
    }
}
