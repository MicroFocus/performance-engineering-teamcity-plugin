package com.performancecenter.teamcity.plugin.server;

import com.intellij.openapi.util.text.StringUtil;
import com.performancecenter.teamcity.plugin.StringsConstants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.RunType;
import jetbrains.buildServer.serverSide.RunTypeRegistry;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Created by bemh on 2/6/2018.
 */
public class PerformanceCenterRunType extends RunType {
    private final PluginDescriptor descriptor;

    public PerformanceCenterRunType(@NotNull final RunTypeRegistry registry, @NotNull final PluginDescriptor descriptor) {
        registry.registerRunType(this);
        this.descriptor = descriptor;
    }

    @NotNull
    @Override
    public String getType() {
        return StringsConstants.RunTypeName;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Performance Test";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Executing load test.";
    }

    @Nullable
    @Override
    public PropertiesProcessor getRunnerPropertiesProcessor() {

        return new PropertiesProcessor() {
            @Override
            public Collection<InvalidProperty> process(Map<String, String> map) {
                List<InvalidProperty> errors = new ArrayList<InvalidProperty>();

                if (StringUtil.isEmpty(map.get(StringsConstants.PCSERVER))) {
                    errors.add(new InvalidProperty(StringsConstants.PCSERVER, "Server cannot be empty"));
                }
                if (StringUtil.isEmpty(map.get(StringsConstants.USERNAME))) {
                    errors.add(new InvalidProperty(StringsConstants.USERNAME, "User Name cannot be empty"));
                }
                if (StringUtil.isEmpty(map.get(StringsConstants.DOMAIN))) {
                    errors.add(new InvalidProperty(StringsConstants.DOMAIN, "Domain cannot be empty"));
                }
                if (StringUtil.isEmpty(map.get(StringsConstants.PCPROJECT))) {
                    errors.add(new InvalidProperty(StringsConstants.PCPROJECT, "Project cannot be empty"));
                }
                if (StringUtil.isEmpty(map.get(StringsConstants.TESTID))) {
                    errors.add(new InvalidProperty(StringsConstants.TESTID, "Test ID cannot be empty"));
                } else if (!NumberUtils.isDigits(map.get(StringsConstants.TESTID)) && (!isParameter(map.get(StringsConstants.TESTID)))) {
                    errors.add(new InvalidProperty(StringsConstants.TESTID, "Test ID have to be number or a parameter"));
                }

                if ((map.get(StringsConstants.TESTINSTANCEIDOPTIONS).equals("MANUAL"))) {
                    if (StringUtil.isEmpty(map.get(StringsConstants.TESTINSTANCEID))) {
                        errors.add(new InvalidProperty(StringsConstants.TESTINSTANCEID, "Test Instance ID cannot be empty"));
                    } else if (!NumberUtils.isDigits(map.get(StringsConstants.TESTINSTANCEID)) && (!isParameter(map.get(StringsConstants.TESTINSTANCEID)))) {

                        errors.add(new InvalidProperty(StringsConstants.TESTINSTANCEID, "Test Instance ID have to be number or a parameter"));
                    }
                }


                if (map.get(StringsConstants.TRENDINGOPTIONS).equals("USE_ID")) {
                    if (StringUtil.isEmpty(map.get(StringsConstants.TRENDREPORTID))) {
                        errors.add(new InvalidProperty(StringsConstants.TRENDREPORTID, "Trend Report ID cannot be empty"));
                    } else if (!NumberUtils.isDigits(map.get(StringsConstants.TRENDREPORTID)) && (!isParameter(map.get(StringsConstants.TRENDREPORTID)))) {
                        errors.add(new InvalidProperty(StringsConstants.TRENDREPORTID, "Trend Report ID have to be number or a parameter"));
                    }

                }


                return errors;
            }
        };
    }

    @Nullable
    @Override
    public String getEditRunnerParamsJspFilePath() {
        return descriptor.getPluginResourcesPath("editParameters.jsp");
    }

    @Nullable
    @Override
    public String getViewRunnerParamsJspFilePath() {
        return null;
    }

    @Nullable
    @Override
    public Map<String, String> getDefaultRunnerProperties() {
        return null;
    }


    private boolean isParameter(String str) {
        if (str.startsWith("%") && str.endsWith("%")) {
            return true;
        }
        return false;
    }
}
