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

package org.gradle.cache.internal

import org.gradle.cache.PersistentCache
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(FixedSizeOldestCacheCleanup)
class FixedSizeOldestCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def persistentCache = Mock(PersistentCache)
    def cleanupAction = new FixedSizeOldestCacheCleanup(10L)

    def setup() {
        persistentCache.getBaseDir() >> cacheDir
        persistentCache.reservedCacheFiles >> Collections.emptyList()
    }

    def "finds eligible files"() {
        def cacheEntries = [
            createCacheEntry(1024), // 1KB
            createCacheEntry(1024*1024), // 1MB
            createCacheEntry(1024*1024*10), // 10MB
        ]
        expect:
        def eligibleFiles = Arrays.asList(cleanupAction.findEligibleFiles(persistentCache))
        eligibleFiles.size() == cacheEntries.size()
        eligibleFiles.containsAll(cacheEntries)
    }

    def "finds files to delete when cache is larger than limit"() {
        def cacheEntries = [
            createCacheEntry(1024, 1000), // 1KB, newest file
            createCacheEntry(1024*1024, 500), // 1MB
            createCacheEntry(1024*1024*5, 250), // 5MB
            createCacheEntry(1024*1024*10, 0), // 10MB, oldest file
        ]
        expect:
        def filesToDelete = cleanupAction.findFilesToDelete(persistentCache, cacheEntries as File[])
        filesToDelete.size() == 1
        // we should only delete the last one
        filesToDelete[0] == cacheEntries.last()
    }

    def "finds no files to delete when cache is smaller than limit"() {
        def cacheEntries = [
            createCacheEntry(1024), // 1KB
            createCacheEntry(1024*1024), // 1MB
            createCacheEntry(1024*1024*5), // 5MB
        ]
        expect:
        def filesToDelete = cleanupAction.findFilesToDelete(persistentCache, cacheEntries as File[])
        filesToDelete.size() == 0
    }

    private Random r = new Random()
    def createCacheEntry(int size=1024, long timestamp=0) {
        def cacheEntry = cacheDir.file(String.format("%032x", r.nextInt()))
        def data = new byte[size]
        r.nextBytes(data)
        cacheEntry.bytes = data
        cacheEntry.lastModified = timestamp
        return cacheEntry
    }
}
