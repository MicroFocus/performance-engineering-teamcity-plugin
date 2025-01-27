package com.performancecenter.teamcity.plugin;

/**
 * Created by bemh on 4/29/2018.
 */
public class ArtifactsUtil {
    public static String getInternalArtifactPath(final String relativePath) {
        return String.format("%s/%s/%s", ".teamcity", StringsConstants.RUNNER_TYPE, relativePath);
    }
}
