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

package org.gradle.test.fixtures.server.http

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.junit.rules.ExternalResource
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.servlet.RestFilter

class HttpBuildCache extends ExternalResource implements HttpServerFixture {
    private final TestDirectoryProvider provider
    private final WebAppContext webapp

    HttpBuildCache(TestDirectoryProvider provider) {
        this.provider = provider
        webapp = new WebAppContext()
        this.webapp.addFilter(RestFilter, "/*", 1)

        server.setHandler(this.webapp)
    }

    @Override
    void start() {
        def baseDir = provider.testDirectory.createDir('http-cache-dir')
        webapp.resourceBase = baseDir
        HttpServerFixture.super.start()
    }
}
