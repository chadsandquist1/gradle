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

package org.gradle.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.archive.ZipTestFixture

class JavadocWorkAvoidanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "include 'a', 'b'"
        buildFile << '''
            allprojects {
                apply plugin: 'java'
            }
        '''

        file('a/build.gradle') << '''
            dependencies {
                compile project(':b')
            }
        '''

        file('a/src/main/java/A.java') << '''
            public class A {
                public void foo() {
                }
            }
        '''
        file('a/src/main/resources/A.properties') << '''
            aprop=avalue
        '''

        file('b/src/main/java/B.java') << '''
            public class B {
                public int truth() { return 0; }
            }
        '''
        file('b/src/main/resources/B.properties') << '''
            bprop=bvalue
        '''
    }

    def "does not regenerate javadoc when just the upstream jar is just rebuilt without changes"() {
        given:
        succeeds(":a:javadoc")
        def bJar = file("b/build/libs/b.jar")
        def oldHash = bJar.md5Hash
        when:
        // Timestamps in the jar have a 2-second precision, so we need to see a different jar before continuing
        ConcurrentTestUtil.poll(6) {
            // cleaning b and rebuilding will cause b.jar to be different
            succeeds(":b:clean")
            succeeds(":a:javadoc")
            assert oldHash != bJar.md5Hash
        }

        then:
        result.assertTasksNotSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar", ":b:javadoc")
        result.assertTasksSkipped(":a:compileJava", ":a:processResources", ":a:classes", ":a:javadoc")
    }

    def "order of upstream jar entries does not matter"() {
        given:
        file("a/build.gradle") << '''
            dependencies {
                compile rootProject.files("build/libs/external.jar")
            }
        '''
        buildFile << """
            task alphabetic(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")
                
                archiveName = "external.jar"
            }
            task reverseAlphabetic(type: Jar) {
                from("external/d")
                from("external/c")
                from("external/b")
                from("external/a")
                
                archiveName = "external.jar"
            }
        """
        ['a', 'b', 'c', 'd'].each {
            file("external/$it").touch()
        }
        // Generate external jar with entries in alphabetical order
        def externalJar = file('build/libs/external.jar')
        succeeds("alphabetic", ":a:javadoc")
        new ZipTestFixture(externalJar).hasDescendantsInOrder('META-INF/MANIFEST.MF', 'a', 'b', 'c', 'd')

        when:
        // Re-generate external jar with entries in reverse alphabetical order
        succeeds("reverseAlphabetic")
        and:
        succeeds(":a:javadoc")
        then:
        // javadoc should still be up-to-date even though the upstream external.jar changed
        new ZipTestFixture(externalJar).hasDescendantsInOrder('META-INF/MANIFEST.MF', 'd', 'c', 'b', 'a')
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes", ":b:javadoc", ":a:javadoc")
    }

    def "timestamp of upstream jar entries does not matter"() {
        given:
        file("a/build.gradle") << '''
            dependencies {
                compile rootProject.files("build/libs/external.jar")
            }
        '''
        buildFile << """
            task currentTime(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")
                
                archiveName = "external.jar"
            }
            task oldTime(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")
                
                archiveName = "external.jar"
                preserveFileTimestamps = false
            }
        """
        def externalJar = file("build/libs/external.jar")
        ['a', 'b', 'c', 'd'].each {
            file("external/$it").touch()
        }
        // Generate external jar with entries with a current timestamp
        succeeds("currentTime", ":a:javadoc")
        def oldHash = externalJar.md5Hash
        when:
        // Re-generate external jar with entries with a different/fixed timestamp
        succeeds("oldTime")
        and:
        succeeds(":a:javadoc")
        then:
        // check that the upstream jar definitely changed
        oldHash != externalJar.md5Hash
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes", ":b:javadoc", ":a:javadoc")
    }

    def "duplicates in an upstream jar are not ignored"() {
        given:
        file("a/build.gradle") << '''
            dependencies {
                compile rootProject.files("build/libs/external.jar")
            }
        '''
        buildFile << """
            task duplicate(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")
                from("duplicate")
                archiveName = "external.jar"
            }
        """
        def externalJar = file("build/libs/external.jar")
        ['a', 'b', 'c', 'd'].each {
            file("external/$it").touch()
        }
        def duplicate = file("duplicate/a").touch()

        // Generate external jar with entries with a duplicate 'a' file
        succeeds("duplicate", ":a:javadoc")
        def oldHash = externalJar.md5Hash
        
        when:
        // change the second duplicate
        duplicate.text = "changed"
        succeeds("duplicate")
        and:
        succeeds(":a:javadoc")
        then:
        // check that the upstream jar definitely changed
        oldHash != externalJar.md5Hash
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes", ":b:javadoc")
        result.assertTasksNotSkipped(":a:javadoc")
    }
}
