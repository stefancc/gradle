/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.internal.DefaultSwiftApplication;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.util.GUtil;

import javax.inject.Inject;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

/**
 * <p>A plugin that produces an executable from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the executable. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftApplication} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.5
 */
@Incubating
public class SwiftApplicationPlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public SwiftApplicationPlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();
        ObjectFactory objectFactory = project.getObjects();

        // Add the component extension
        SwiftApplication application = project.getExtensions().create(SwiftApplication.class, "application", DefaultSwiftApplication.class, "main", project.getLayout(), project.getObjects(), fileOperations, configurations);
        project.getComponents().add(application);
        project.getComponents().add(application.getDebugExecutable());
        project.getComponents().add(application.getReleaseExecutable());

        // Setup component
        application.getModule().set(GUtil.toCamelCase(project.getName()));

        // Wire in this install task
        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(application.getDevelopmentBinary().getInstallDirectory());

        // Configure compile task
        final SwiftCompile compileDebug = (SwiftCompile) tasks.getByName("compileDebugSwift");
        final SwiftCompile compileRelease = (SwiftCompile) tasks.getByName("compileReleaseSwift");

        Configuration implementation = application.getImplementationDependencies();

        Configuration debugApiElements = configurations.maybeCreate("debugSwiftApiElements");
        debugApiElements.extendsFrom(implementation);
        debugApiElements.setCanBeResolved(false);
        debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        debugApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, application.getDebugExecutable().isDebuggable());
        debugApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, application.getDebugExecutable().isOptimized());
        debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

        Configuration releaseApiElements = configurations.maybeCreate("releaseSwiftApiElements");
        releaseApiElements.extendsFrom(implementation);
        releaseApiElements.setCanBeResolved(false);
        releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        releaseApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, application.getReleaseExecutable().isDebuggable());
        releaseApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, application.getReleaseExecutable().isOptimized());
        releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());
    }
}
