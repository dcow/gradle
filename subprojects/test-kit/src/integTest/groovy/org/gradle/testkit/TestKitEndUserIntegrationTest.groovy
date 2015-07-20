/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit

import org.gradle.api.internal.GradleDistributionLocator
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GFileUtils

class TestKitEndUserIntegrationTest extends AbstractIntegrationSpec {
    public static final String[] GROOVY_SRC_PATH = ['src', 'test', 'groovy'] as String[]
    public static final String[] TEST_CLASS_PACKAGE = ['org', 'gradle', 'test'] as String[]
    public static final String[] TEST_CLASS_PATH = GROOVY_SRC_PATH + TEST_CLASS_PACKAGE
    File functionalTestClassDir

    def setup() {
        executer.requireGradleHome()
        executer.withStackTraceChecksDisabled()
        functionalTestClassDir = testDirectoryProvider.createDir(TEST_CLASS_PATH)
        buildFile << buildFileForGroovyProject()
    }

    def "use of GradleRunner API in test class without declaring test-kit dependency causes compilation error"() {
        given:
        GFileUtils.writeFile(buildLogicFunctionalTestCreatingGradleRunner(), new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionFailure failure = fails('build')

        then:
        result.executedTasks.contains(':compileTestGroovy')
        !result.skippedTasks.contains(':compileTestGroovy')
        failure.error.contains("unable to resolve class $GradleRunner.name")
        failure.error.contains('Compilation failed; see the compiler error output for details.')
    }

    def "use of GradleRunner API in test class by depending on external test-kit dependency causes compilation error"() {
        File gradleDistPluginsDir = new File(distribution.gradleHomeDir, 'lib/plugins')
        File[] gradleTestKitLibs = gradleDistPluginsDir.listFiles(new GradleTestKitJarFilenameFilter())
        assert gradleTestKitLibs.length == 1
        File gradleTestKitLib = gradleTestKitLibs[0]
        TestFile libDir = testDirectoryProvider.createDir('lib')
        GFileUtils.copyFile(gradleTestKitLib, new File(libDir, gradleTestKitLib.name))

        buildFile << libDirDependency()
        GFileUtils.writeFile(buildLogicFunctionalTestCreatingGradleRunner(), new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionFailure failure = fails('build')

        then:
        result.executedTasks.contains(':compileTestGroovy')
        !result.skippedTasks.contains(':compileTestGroovy')
        failure.error.contains("Unable to load class $GradleRunner.name due to missing dependency ${GradleDistributionLocator.name.replaceAll('\\.', '/')}")
    }

    def "creating GradleRunner instance by depending on Gradle libraries outside of Gradle distribution throws exception"() {
        File gradleDistLibDir = new File(distribution.gradleHomeDir, 'lib')
        File gradleDistPluginsDir = new File(gradleDistLibDir, 'plugins')
        File[] gradleCoreLibs = gradleDistLibDir.listFiles(new GradleCoreJarFilenameFilter())
        File[] gradleTestKitLibs = gradleDistPluginsDir.listFiles(new GradleTestKitJarFilenameFilter())
        assert gradleCoreLibs.length == 3
        assert gradleTestKitLibs.length == 1
        File[] allGradleLibs = gradleCoreLibs + gradleTestKitLibs
        TestFile libDir = testDirectoryProvider.createDir('lib')

        allGradleLibs.each {
            GFileUtils.copyFile(it, new File(libDir, it.name))
        }

        buildFile << libDirDependency()
        GFileUtils.writeFile(buildLogicFunctionalTestCreatingGradleRunner(), new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionFailure failure = fails('build')

        then:
        result.executedTasks.contains(':test')
        !result.skippedTasks.contains(':test')
        failure.output.contains('java.lang.IllegalStateException: Could not create a GradleRunner, as the GradleRunner class was not loaded from a Gradle distribution')
    }

    def "successfully execute functional test and verify expected result"() {
        buildFile << gradleTestKitDependency()
        GFileUtils.writeFile("""package org.gradle.test

import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskResult.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildLogicFunctionalTest extends Specification {
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "execute helloWorld task"() {
        given:
        buildFile << '''
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        '''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('helloWorld')
            .build()

        then:
        result.standardOutput.contains('Hello world!')
        result.standardError == ''
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }
}
""", new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionResult result = succeeds('build')

        then:
        result.executedTasks.containsAll([':test', ':build'])
        !result.skippedTasks.contains(':test')
    }

    def "functional test fails due to invalid JVM parameter for test execution"() {
        buildFile << gradleTestKitDependency()
        GFileUtils.writeFile("""package org.gradle.test

import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskResult.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildLogicFunctionalTest extends Specification {
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        new File(testProjectDir.root, 'gradle.properties') << 'org.gradle.jvmargs=-unknown'
    }

    def "execute helloWorld task"() {
        given:
        buildFile << '''
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        '''

        expect:
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('helloWorld')
            .build()
    }
}
""", new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionFailure failure = fails('build')

        then:
        result.executedTasks.contains(':test')
        !result.executedTasks.contains(':build')
        !result.skippedTasks.contains(':test')
        failure.output.contains('Unrecognized option: -unknown')
    }

    private String buildFileForGroovyProject() {
        """
            apply plugin: 'groovy'

            dependencies {
                testCompile localGroovy()
                testCompile 'org.spockframework:spock-core:1.0-groovy-2.3'
            }

            repositories {
                mavenCentral()
            }

            test.testLogging.exceptionFormat = 'full'
        """
    }

    private String gradleTestKitDependency() {
        """
            dependencies {
                testCompile gradleTestKit()
            }
        """
    }

    private String libDirDependency() {
        """
            dependencies {
                testCompile fileTree(dir: 'lib', include: '*.jar')
            }
        """
    }

    private String buildLogicFunctionalTestCreatingGradleRunner() {
        """package org.gradle.test

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

class BuildLogicFunctionalTest extends Specification {
    def "create GradleRunner"() {
        expect:
        GradleRunner.create()
    }
}
"""
    }

    private class GradleTestKitJarFilenameFilter implements FilenameFilter {
        boolean accept(File dir, String name) {
            name.startsWith('gradle-test-kit')
        }
    }

    private class GradleCoreJarFilenameFilter implements FilenameFilter {
        boolean accept(File dir, String name) {
            name.startsWith('gradle-core') || name.startsWith('gradle-base-services')
        }
    }
}