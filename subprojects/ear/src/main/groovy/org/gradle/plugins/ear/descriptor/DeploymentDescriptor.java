/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear.descriptor;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;

import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

/**
 * A deployment descriptor such as application.xml.
 * 
 * @author David Gileadi
 */
public interface DeploymentDescriptor {

    /**
     * The name of the descriptor file, typically "application.xml"
     */
    public String getFileName();

    public void setFileName(String fileName);

    /**
     * The version of application.xml. Required. Valid versions are "1.3", "1.4", "5" and "6". Defaults to "6".
     */
    public String getVersion();

    public void setVersion(String version);

    /**
     * The application name. Optional. Only valid with version 6.
     */
    public String getApplicationName();

    public void setApplicationName(String applicationName);

    /**
     * Whether to initialize modules in the order they appear in the descriptor, with the exception of client modules.
     * Optional. Only valid with version 6.
     */
    public Boolean getInitializeInOrder();

    public void setInitializeInOrder(Boolean initializeInOrder);

    /**
     * The application description. Optional.
     */
    public String getDescription();

    public void setDescription(String description);

    /**
     * The application display name. Optional.
     */
    public String getDisplayName();

    public void setDisplayName(String displayName);

    /**
     * The name of the directory to look for libraries in. Optional. If not specified then "lib" is assumed. Typically
     * this should be set via {@link org.gradle.plugins.ear.EarPluginConvention#setLibDirName(String)} instead of this property.
     */
    public String getLibraryDirectory();

    public void setLibraryDirectory(String libraryDirectory);

    /**
     * List of module descriptors. Must not be empty. Non-null and order-maintaining by default. Must maintain order if
     * initializeInOrder is <code>true</code>.
     */
    public Set<? extends EarModule> getModules();

    public void setModules(Set<? extends EarModule> modules);

    /**
     * Add a module to the deployment descriptor.
     * 
     * @param module
     *            The module to add.
     * @param type
     *            The type of the module, such as "ejb", "java", etc.
     * @return this.
     */
    public DeploymentDescriptor module(EarModule module, String type);

    /**
     * Add a module to the deployment descriptor.
     * 
     * @param path
     *            The path of the module to add.
     * @param type
     *            The type of the module, such as "ejb", "java", etc.
     * @return this.
     */
    public DeploymentDescriptor module(String path, String type);

    /**
     * Add a web module to the deployment descriptor.
     * 
     * @param path
     *            The path of the module to add.
     * @param contextRoot
     *            The context root type of the web module.
     * @return this.
     */
    public DeploymentDescriptor webModule(String path, String contextRoot);

    /**
     * List of security roles. Optional. Non-null and order-maintaining by default.
     */
    public Set<? extends EarSecurityRole> getSecurityRoles();

    public void setSecurityRoles(Set<? extends EarSecurityRole> securityRoles);

    /**
     * Add a security role to the deployment descriptor.
     * 
     * @param role
     *            The security role to add.
     * @return this.
     */
    public DeploymentDescriptor securityRole(EarSecurityRole role);

    /**
     * Add a security role to the deployment descriptor.
     * 
     * @param role
     *            The name of the security role to add.
     * @return this.
     */
    public DeploymentDescriptor securityRole(String role);

    /**
     * Mapping of module paths to module types. Non-null by default. For example, to specify that a module is a java
     * module, set <code>moduleTypeMappings["myJavaModule.jar"] = "java"</code>.
     */
    public Map<String, String> getModuleTypeMappings();

    public void setModuleTypeMappings(Map<String, String> moduleTypeMappings);

    /**
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a {@link groovy.util.Node}. The closure can modify the XML before it is written to the
     * output file. This allows additional JavaEE version 6 elements like "data-source" or "resource-ref" to be
     * included.
     * 
     * @param closure
     *            The closure to execute when the XML has been created
     * @return this
     */
    public DeploymentDescriptor withXml(Closure closure);

    /**
     * Adds an action to be called when the XML document has been created. The XML is passed to the action as a
     * parameter in form of a {@link groovy.util.Node}. The action can modify the XML before it is written to the output
     * file. This allows additional JavaEE version 6 elements like "data-source" or "resource-ref" to be included.
     * 
     * @param action
     *            The action to execute when the XML has been created
     * @return this
     */
    public DeploymentDescriptor withXml(Action<? super XmlProvider> action);

    /**
     * Reads the deployment descriptor from a reader.
     * 
     * @param reader
     *            The reader to read the deployment descriptor from
     * @return this
     */
    DeploymentDescriptor readFrom(Reader reader);

    /**
     * Reads the deployment descriptor from a file. The paths are resolved as defined by
     * {@link org.gradle.api.Project#file(Object)}
     * 
     * @param path
     *            The path of the file to read the deployment descriptor from
     * @return whether the descriptor could be read from the given path
     */
    boolean readFrom(Object path);

    /**
     * Writes the deployment descriptor into a writer.
     * 
     * @param writer
     *            The writer to write the deployment descriptor to
     * @return this
     */
    DeploymentDescriptor writeTo(Writer writer);

    /**
     * Writes the deployment descriptor into a file. The paths are resolved as defined by
     * {@link org.gradle.api.Project#file(Object)}
     * 
     * @param path
     *            The path of the file to write the deployment descriptor into.
     * @return this
     */
    DeploymentDescriptor writeTo(Object path);
}