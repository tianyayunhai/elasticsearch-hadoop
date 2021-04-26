/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.hadoop.gradle

import org.elasticsearch.hadoop.gradle.buildtools.info.BuildParams
import org.elasticsearch.hadoop.gradle.buildtools.info.GlobalBuildInfoPlugin
import org.elasticsearch.hadoop.gradle.buildtools.info.JavaHome
import org.elasticsearch.hadoop.gradle.util.Resources
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Configures global and shared configurations for all subprojects regardless of their role.
 */
class BaseBuildPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        greet(project)
        configureBuildInfo(project)
        configureVersions(project)
        configureRuntimeSettings(project)
        configureRepositories(project)
    }

    /**
     * Say hello!
     */
    private static void greet(Project project) {
        if (!project.rootProject.hasProperty('versionsConfigured') && !project.rootProject.hasProperty('shush')) {
            println '==================================='
            println 'ES-Hadoop Build Hamster says Hello!'
            println '==================================='
        }
    }

    private static void configureBuildInfo(Project project) {
        // Make sure the global build info plugin is applied to the root project first and foremost
        // Todo: Remove this once this is generated by the plugin
        project.rootProject.ext.testSeed = "DEADBEEF"
        project.rootProject.pluginManager.apply(GlobalBuildInfoPlugin.class)

        // Hack new defaults into Global build info
        if (!project.rootProject.ext.has('buildInfoConfigured')) {

            JavaVersion minimumRuntimeVersion = JavaVersion.toVersion(Resources.getResourceContents("/minimumRuntimeVersion"))
            println "Min runtime: ${minimumRuntimeVersion}"

            // We snap the runtime to java 8 since Hadoop needs to see some significant
            // upgrades to support any runtime higher than that
            JavaHome esHadoopRuntimeJava = BuildParams.javaVersions.find { it.version == 8 }
            if (esHadoopRuntimeJava == null) {
                throw new GradleException(
                        '$JAVA8_HOME must be set to build ES-Hadoop. ' +
                                "Note that if the variable was just set you might have to run `./gradlew --stop` for " +
                                "it to be picked up. See https://github.com/elastic/elasticsearch/issues/31399 details."
                )
            }

            // Set on global build info
            BuildParams.init { params ->
                params.setMinimumRuntimeVersion(minimumRuntimeVersion)
            }

            // Set on build settings
            project.rootProject.ext.runtimeJavaHome = esHadoopRuntimeJava.javaHome.get()
            project.rootProject.ext.minimumRuntimeVersion = minimumRuntimeVersion

            project.rootProject.ext.buildInfoConfigured = true
        }
        // Propagate to current project
        project.ext.runtimeJavaHome = project.rootProject.ext.runtimeJavaHome
        project.ext.minimumRuntimeVersion = project.rootProject.ext.minimumRuntimeVersion
    }

    /**
     * Extract version information and load it into the build's extra settings
     * @param project to be configured
     */
    private static void configureVersions(Project project) {
        if (!project.rootProject.ext.has('versionsConfigured')) {
            project.rootProject.version = EshVersionProperties.ESHADOOP_VERSION
            println "Building version [${project.rootProject.version}]"

            project.rootProject.ext.eshadoopVersion = EshVersionProperties.ESHADOOP_VERSION
            project.rootProject.ext.elasticsearchVersion = EshVersionProperties.ELASTICSEARCH_VERSION
            project.rootProject.ext.luceneVersion = EshVersionProperties.LUCENE_VERSION
            project.rootProject.ext.buildToolsVersion = EshVersionProperties.BUILD_TOOLS_VERSION
            project.rootProject.ext.versions = EshVersionProperties.VERSIONS
            project.rootProject.ext.versionsConfigured = true

            println "Testing against Elasticsearch [${project.rootProject.ext.elasticsearchVersion}] with Lucene [${project.rootProject.ext.luceneVersion}]"

            println "Using Gradle [${project.gradle.gradleVersion}]"

            // Hadoop versions
            project.rootProject.ext.hadoopDistro = project.hasProperty("distro") ? project.getProperty("distro") : "hadoop3"
            switch (project.rootProject.ext.hadoopDistro) {
            // Hadoop YARN/2.0.x
                case "hadoop3":
                    project.rootProject.ext.hadoopVersion = project.hadoop3Version
                    println "Using Apache Hadoop on YARN [$project.hadoop3Version]"
                    break
                case "hadoopYarn":
                    project.rootProject.ext.hadoopVersion = project.hadoop2Version
                    println "Using Apache Hadoop on YARN [$project.hadoop2Version]"
                    break
                case "hadoopStable":
                    project.rootProject.ext.hadoopVersion = project.hadoop22Version
                    println "Using Apache Hadoop [$project.hadoop22Version]"
                    break
                default:
                    throw new GradleException("Invalid [hadoopDistro] setting: [$project.rootProject.ext.hadoopDistro]")
            }
            project.rootProject.ext.hadoopClient = ["org.apache.hadoop:hadoop-client:$project.rootProject.ext.hadoopVersion"]
        }
        project.ext.eshadoopVersion = project.rootProject.ext.eshadoopVersion
        project.ext.elasticsearchVersion = project.rootProject.ext.elasticsearchVersion
        project.ext.luceneVersion = project.rootProject.ext.luceneVersion
        project.ext.buildToolsVersion = project.rootProject.ext.buildToolsVersion
        project.ext.versions = project.rootProject.ext.versions
        project.ext.hadoopVersion = project.rootProject.ext.hadoopVersion
        project.ext.hadoopClient = project.rootProject.ext.hadoopClient
        project.version = project.rootProject.version
    }

    /**
     * Determine dynamic or runtime-based information and load it into the build's extra settings
     * @param project to be configured
     */
    private static void configureRuntimeSettings(Project project) {
        if (!project.rootProject.ext.has('settingsConfigured')) {
            // Force any Elasticsearch test clusters to use packaged java versions if they have them available
            project.rootProject.ext.isRuntimeJavaHomeSet = false
            project.rootProject.ext.settingsConfigured = true
        }
        project.ext.javaVersions = BuildParams.javaVersions
        project.ext.isRuntimeJavaHomeSet = project.rootProject.ext.isRuntimeJavaHomeSet
    }

    /**
     * Add all the repositories needed to pull dependencies for the build
     * @param project to be configured
     */
    private static void configureRepositories(Project project) {
        project.repositories.mavenCentral()
        project.repositories.maven { url "https://conjars.org/repo" }
        project.repositories.maven { url "https://clojars.org/repo" }
        project.repositories.maven { url 'https://repo.spring.io/plugins-release' }

        // For Elasticsearch snapshots.
        project.repositories.maven { url "https://snapshots.elastic.co/maven/" } // default
        project.repositories.maven { url "https://oss.sonatype.org/content/repositories/snapshots" } // oss-only

        // Elastic artifacts
        project.repositories.maven { url "https://artifacts.elastic.co/maven/" } // default
        project.repositories.maven { url "https://oss.sonatype.org/content/groups/public/" } // oss-only

        // Add Ivy repos in order to pull Elasticsearch distributions that have bundled JDKs
        for (String repo : ['snapshots', 'artifacts']) {
            project.repositories.ivy {
                url "https://${repo}.elastic.co/downloads"
                patternLayout {
                    artifact "elasticsearch/[module]-[revision](-[classifier]).[ext]"
                }
            }
        }

        // For Lucene Snapshots, Use the lucene version interpreted from elasticsearch-build-tools version file.
        if (project.ext.luceneVersion.contains('-snapshot')) {
            // Extract the revision number of the snapshot via regex:
            String revision = (project.ext.luceneVersion =~ /\w+-snapshot-([a-z0-9]+)/)[0][1]
            project.repositories.maven {
                name 'lucene-snapshots'
                url "https://s3.amazonaws.com/download.elasticsearch.org/lucenesnapshots/${revision}"
            }
        }
    }
}
