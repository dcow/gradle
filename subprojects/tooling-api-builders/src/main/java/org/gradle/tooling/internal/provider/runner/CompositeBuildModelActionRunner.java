/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeBuildContext;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeContextBuilder;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeScopeServices;
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.*;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.*;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.launcher.daemon.configuration.DaemonUsage;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        BuildModelAction buildModelAction = (BuildModelAction) action;
        List<Object> results = null;
        if (isModelRequest(buildModelAction)) {
            results = fetchCompositeModelsInProcess(buildModelAction, requestContext, actionParameters.getCompositeParameters(), buildController.getBuildScopeServices());
        } else {
            if (!buildModelAction.isRunTasks()) {
                throw new IllegalStateException("No tasks defined.");
            }
            executeTasksInProcess(buildModelAction, actionParameters, requestContext, buildController.getBuildScopeServices());
        }
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(results), null));
    }

    private boolean isModelRequest(BuildModelAction action) {
        final String requestedModelName = action.getModelName();
        return !requestedModelName.equals(Void.class.getName());
    }

    private List<Object> fetchCompositeModelsInProcess(BuildModelAction modelAction, BuildRequestContext buildRequestContext,
                                                       CompositeParameters compositeParameters,
                                                       ServiceRegistry sharedServices) {
        List<GradleParticipantBuild> participantBuilds = compositeParameters.getBuilds();

        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        DefaultServiceRegistry compositeServices = createCompositeAwareServices(participantBuilds, gradleLauncherFactory, sharedServices, false);

        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelsActionRunner());
        org.gradle.launcher.exec.BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, runner);
        // TODO Need to consider how to handle builds in parallel when sharing event consumers/output streams
        DefaultBuildRequestContext requestContext = createRequestContext(buildRequestContext);

        final List<Object> results = Lists.newArrayList();
        for (GradleParticipantBuild participant : participantBuilds) {
            DefaultBuildActionParameters actionParameters = new DefaultBuildActionParameters(Collections.EMPTY_MAP, Collections.<String, String>emptyMap(), participant.getProjectDir(), LogLevel.INFO, DaemonUsage.EXPLICITLY_DISABLED, false, true, ClassPath.EMPTY);

            StartParameter startParameter = modelAction.getStartParameter().newInstance();
            startParameter.setProjectDir(participant.getProjectDir());

            ServiceRegistry buildScopedServices = new BuildSessionScopeServices(compositeServices, startParameter, ClassPath.EMPTY);

            BuildModelAction participantAction = new BuildModelAction(startParameter, modelAction.getModelName(), false, modelAction.getClientSubscriptions());
            try {
                Map<String, Object> result = Cast.uncheckedCast(buildActionExecuter.execute(participantAction, requestContext, actionParameters, buildScopedServices));
                for (Map.Entry<String, Object> e : result.entrySet()) {
                    String projectPath = e.getKey();
                    Object modelValue = e.getValue();
                    results.add(compositeModelResult(participant, projectPath, modelValue));
                }
            } catch (BuildCancelledException e) {
                InternalBuildCancelledException buildCancelledException = new InternalBuildCancelledException(e.getCause());
                results.add(compositeModelResult(participant, null, buildCancelledException));
            } catch (ReportedException e) {
                results.add(compositeModelResult(participant, null, unwrap(e)));
            } catch (Exception e) {
                results.add(compositeModelResult(participant, null, e));
            }
        }
        return results;
    }

    private void executeTasksInProcess(BuildModelAction compositeAction, CompositeBuildActionParameters actionParameters, BuildRequestContext buildRequestContext, ServiceRegistry sharedServices) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeParameters compositeParameters = actionParameters.getCompositeParameters();

        DefaultServiceRegistry compositeServices = createCompositeAwareServices(compositeParameters.getBuilds(), gradleLauncherFactory, sharedServices, true);

        StartParameter startParameter = compositeAction.getStartParameter().newInstance();
        startParameter.setProjectDir(compositeParameters.getTargetBuild().getProjectDir());
        startParameter.setSearchUpwards(false);

        // Use a ModelActionRunner to ensure that model events are emitted
        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelActionRunner());
        org.gradle.launcher.exec.BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, runner);
        BuildModelAction participantAction = new BuildModelAction(startParameter, ModelIdentifier.NULL_MODEL, true, compositeAction.getClientSubscriptions());
        DefaultBuildRequestContext requestContext = createRequestContext(buildRequestContext);
        ServiceRegistry buildScopedServices = new BuildSessionScopeServices(compositeServices, startParameter, ClassPath.EMPTY);

        buildActionExecuter.execute(participantAction, requestContext, null, buildScopedServices);
    }

    private DefaultBuildRequestContext createRequestContext(BuildRequestContext buildRequestContext) {
        // TODO:DAZ Not sure that we can't just use the provided request context
        BuildRequestMetaData metaData = new DefaultBuildRequestMetaData(new GradleLauncherMetaData(), System.currentTimeMillis());
        return new DefaultBuildRequestContext(metaData, buildRequestContext.getCancellationToken(), buildRequestContext.getEventConsumer(), buildRequestContext.getOutputListener(), buildRequestContext.getErrorListener());
    }

    private Exception unwrap(ReportedException e) {
        Throwable t = e.getCause();
        while (t != null) {
            if (t instanceof BuildCancelledException) {
                return new InternalBuildCancelledException(e.getCause());
            }
            t = t.getCause();
        }
        return new BuildExceptionVersion1(e.getCause());
    }

    private Object compositeModelResult(GradleParticipantBuild participant, String projectPath, Object modelValue) {
        return new Object[]{participant.getProjectDir(), projectPath, modelValue};
    }

    private DefaultServiceRegistry createCompositeAwareServices(List<GradleParticipantBuild> builds, GradleLauncherFactory gradleLauncherFactory, ServiceRegistry sharedServices, boolean propagateFailures) {
        CompositeBuildContext context = constructCompositeContext(gradleLauncherFactory, builds, propagateFailures);

        DefaultServiceRegistry compositeServices = (DefaultServiceRegistry) ServiceRegistryBuilder.builder()
            .displayName("Composite services")
            .parent(sharedServices)
            .build();
        compositeServices.add(CompositeBuildContext.class, context);
        compositeServices.addProvider(new CompositeScopeServices());
        return compositeServices;
    }

    private CompositeBuildContext constructCompositeContext(GradleLauncherFactory gradleLauncherFactory, List<GradleParticipantBuild> participantBuilds, boolean propagateFailures) {
        CompositeContextBuilder builder = new CompositeContextBuilder(gradleLauncherFactory, propagateFailures);
        for (GradleParticipantBuild participant : participantBuilds) {
            builder.addParticipant(participant.getProjectDir());
        }
        return builder.build();
    }
}
