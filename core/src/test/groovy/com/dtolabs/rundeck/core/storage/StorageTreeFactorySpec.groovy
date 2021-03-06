/*
 * Copyright 2018 Rundeck, Inc. (http://rundeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dtolabs.rundeck.core.storage

import com.dtolabs.rundeck.core.plugins.ConfiguredPlugin
import com.dtolabs.rundeck.core.plugins.PluggableProviderService
import com.dtolabs.rundeck.core.plugins.PluginRegistry
import com.dtolabs.rundeck.core.utils.FileUtils
import com.dtolabs.rundeck.core.utils.IPropertyLookup
import com.dtolabs.rundeck.plugins.storage.StoragePlugin
import org.rundeck.storage.data.DataUtil
import spock.lang.Specification

import java.nio.file.Files


class StorageTreeFactorySpec extends Specification {
    public static final String PROJECT_NAME = 'StorageTreeFactorySpec'
    File tempDir1
    File tempDir2

    def setup() {

        tempDir1 = Files.createTempDirectory("StorageTreeFactorySpec").toFile()
        tempDir2 = Files.createTempDirectory("StorageTreeFactorySpec").toFile()
    }

    def teardown() {
        FileUtils.deleteDir(tempDir1)
        FileUtils.deleteDir(tempDir2)
    }

    def "multiple storage trees with file provider should have unique instances"() {
        given:
        StorageTreeFactory factory1 = new StorageTreeFactory()
        def storagePluginProviderService1 = Mock(PluggableProviderService)
        def storageConverterPluginProviderService1 = Mock(PluggableProviderService)
        def storagePlugin = Mock(StoragePlugin)
        Map<String, String> config1 = [:]
        Map<String, String> config2 = [:]
        def mockPluginRegistry = Stub(PluginRegistry) {
            retainConfigurePluginByName(_,_,_,_) >>> new ConfiguredPlugin<>(storagePlugin,[:])
        }

        def storageConfig1 = ['baseDir': tempDir1.getAbsolutePath()]
        def storageConfig2 = ['baseDir': tempDir2.getAbsolutePath()]

        factory1.with {
            frameworkPropertyLookup = Mock(IPropertyLookup)

            pluginRegistry = mockPluginRegistry
            storagePluginProviderService = storagePluginProviderService1
            storageConverterPluginProviderService = storageConverterPluginProviderService1
            configuration = config1
            storageConfigPrefix = 'provider'
            converterConfigPrefix = 'converter'
            baseStorageType = 'file'
            baseStorageConfig = storageConfig1
            loggerName = 'logger1'
        }
        StorageTreeFactory factory2 = new StorageTreeFactory()
        factory2.with {
            frameworkPropertyLookup = Mock(IPropertyLookup)
            pluginRegistry = mockPluginRegistry
            storagePluginProviderService = storagePluginProviderService1
            storageConverterPluginProviderService = storageConverterPluginProviderService1
            configuration = config2
            storageConfigPrefix = 'provider'
            converterConfigPrefix = 'converter'
            baseStorageType = 'file'
            baseStorageConfig = storageConfig2
            loggerName = 'logger2'

        }
        StorageTree tree1 = factory1.createTree()
        StorageTree tree2 = factory2.createTree()

        tree1.createResource("testfile1", DataUtil.withText('test1data', [:], StorageUtil.factory()))

        when:
        def result = tree2.hasResource('testfile1')

        then:
        !result

    }
}
