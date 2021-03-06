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

package org.gradle.integtests.tooling.r213
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.util.GradleVersion
import spock.lang.Ignore

/**
 * Dependency substitution is performed for composite build accessed via the `GradleConnection` API.
 */
@Ignore
class DependencySubstitutionGradleConnectionCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()

    def "dependencies report shows external dependencies substituted with project dependencies"() {
        given:
        def build1 = singleProjectBuild("build1") {
                    buildFile << """
        configurations { compile }
        dependencies {
            compile "org.A:build2:1.0"
        }
"""
}
        def build2 = singleProjectBuild("build2") {
                    buildFile << """
        apply plugin: 'base'
"""
}

        def expectedOutput = "org.A:build2:1.0 FAILED"
        if (targetSupportsSubstitution()) {
            expectedOutput = "org.A:build2:1.0 -> project build2::"
        }

        when:
        withCompositeConnection([build1, build2]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.forTasks(build1, "dependencies")
            buildLauncher.run()
        }

        then:
        output.contains """
compile
\\--- $expectedOutput
"""
    }

    private static boolean targetSupportsSubstitution() {
        def targetBaseVersion = targetDistVersion.baseVersion
        def supportsSubstitution = targetBaseVersion >= GradleVersion.version("2.14")
        println "Checking if ${targetBaseVersion} supports substitution: " + supportsSubstitution
        return supportsSubstitution
    }

    def getOutput() {
        stdOut.toString()
    }
}
