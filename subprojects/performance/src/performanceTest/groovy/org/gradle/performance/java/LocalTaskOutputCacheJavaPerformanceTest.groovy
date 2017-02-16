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

package org.gradle.performance.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

@Unroll
class LocalTaskOutputCacheJavaPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "Builds '#testProject' calling #tasks with local cache"() {
        given:
        runner.testId = "cached ${tasks.join(' ')} $testProject project"
        runner.previousTestIds = ["cached Java $testProject ${tasks.join(' ')} (daemon)"]
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.gradleOpts = ["-Xms768m", "-Xmx768m"]
        runner.args = ['-Dorg.gradle.cache.tasks=true', '--parallel']
        /*
         * Since every second build is a 'clean', we need more iterations
         * than usual to get reliable results.
         */
        runner.runs = 40
        runner.setupCleanupOnOddRounds()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject      | tasks
        'bigOldJava'     | ['assemble']
        'largeWithJUnit' | ['build']
    }

    def "Builds '#testProject' calling #tasks with local cache - push only"() {
        given:
        runner.testId = "cached ${tasks.join(' ')} $testProject project - local cache, push only"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.gradleOpts = ["-Xms768m", "-Xmx768m"]
        runner.args = ['-Dorg.gradle.cache.tasks=true', '-Dorg.gradle.cache.tasks.pull=false', '--parallel']
        /*
         * Since every second build is a 'clean', we need more iterations
         * than usual to get reliable results.
         */
        runner.warmUpRuns = 4
        runner.runs = 10
        runner.setupCleanupOnOddRounds()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject      | tasks
        'bigOldJava'     | ['assemble']
        'largeWithJUnit' | ['build']
    }
}
