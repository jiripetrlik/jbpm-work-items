package org.jbpm.process.workitem.camel.osgi;

/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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

import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.karaf.options.configs.CustomProperties;
import org.ops4j.pax.exam.options.DefaultCompositeOption;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFileExtend;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

public class AbstractKarafIntegrationTest {


    /**
     * Path to file containing container binary archive.
     */
    public static final String PROP_KARAF_DISTRIBUTION_FILE = "karaf.dist.file";

    /**
     * Defines version of Karaf. This parameter is mandatory when a custom
     * Karaf distribution file is specified (for example JBoss Fuse 6.2
     * uses Karaf container version 2.4.0).
     */
    public static final String PROP_KARAF_VERSION = "karaf.version";

    /**
     * Maximal size of perm gen memory. For example "512M". This property
     * is useful only in Java 7.
     */
    public static final String PROP_KARAF_MAXPERMSIZE = "karaf.maxpermsize";

    /**
     * Whether to keep pax-exam runtime folder after the test execution is completed.
     * It can be very useful for debugging to keep the content of runtime folder.
     */
    public static final String PROP_KEEP_RUNTIME_FOLDER = "karaf.keep.runtime.folder";

    /**
     * Karaf group id.
     */
    private static final String KARAF_GROUP_ID = "org.apache.karaf";

    /**
     * Karaf artifact id.
     */
    private static final String KARAF_ARTIFACT_ID = "apache-karaf";

    /**
     * Base OSGi framework used by Karaf. Default is Felix.
     */
    public static final String PROP_KARAF_FRAMEWORK = "karaf.osgi.framework";

    /**
     * Additional Maven repositories. Value of this property is added to "org.ops4j.pax.url.mvn.repositories"
     * property in "org.ops4j.pax.url.mvn.cfg" configuration file.
     */
    public static final String PROP_ADDITIONAL_MAVEN_REPOS = "karaf.maven.repos";

    /**
     * Defines location of local Maven settings XML file.
     */
    public static final String SYSTEM_PROP_MAVEN_CUSTOM_SETTINGS = "kie.maven.settings.custom";

    public static final String TEST_PROPERTIES_FILE = "test.properties";

    public static final String KIE_MAVEN_SETTINGS_CUSTOM_PROPERTY = "kie.maven.settings.custom";

    protected static final String KIE_VERSION;

    private static final transient Logger logger = LoggerFactory.getLogger(AbstractKarafIntegrationTest.class);

    static {
        Properties testProps = new Properties();
        try {
            testProps.load(AbstractKarafIntegrationTest.class.getResourceAsStream("/test.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize KIE_VERSION property: " + e.getMessage(), e);
        }
        KIE_VERSION = testProps.getProperty("project.version");
        logger.info("KIE Project Version : " + KIE_VERSION);
    }

    public static Option getKarafDistributionOption() {

        List<Option> options = new ArrayList<Option>();

        String karafVersion = getKarafVersion();
        logger.info("*** The karaf version is " + karafVersion + " ***");

        KarafDistributionBaseConfigurationOption karafConfiguration = karafDistributionConfiguration();

        /* Use default or custom container */
        if (System.getProperty(PROP_KARAF_DISTRIBUTION_FILE) == null) {
            karafConfiguration.frameworkUrl(maven().groupId(KARAF_GROUP_ID).artifactId(KARAF_ARTIFACT_ID).type("tar.gz").versionAsInProject());
        } else {
            File fuseDistributionFile = new File(System.getProperty(PROP_KARAF_DISTRIBUTION_FILE));
            karafConfiguration.frameworkUrl("file:" + fuseDistributionFile.getAbsolutePath());
        }

        karafConfiguration
                .karafVersion(karafVersion)
                .name("Apache Karaf")
                .useDeployFolder(false)
                .unpackDirectory(new File("target/paxexam/unpack/"));
        options.add(karafConfiguration);

        if (System.getProperty("additional.features.url") != null) {
            options.add(editConfigurationFileExtend("etc/org.apache.karaf.features.cfg", "featuresRepositories",
                    System.getProperty("additional.features.url")));
        }

        /* Set maximal perm space size */
        if (System.getProperty(PROP_KARAF_MAXPERMSIZE) != null) {
            options.add(vmOption("-XX:MaxPermSize=" + System.getProperty(PROP_KARAF_MAXPERMSIZE)));
        }

        /* Keep pax exam runtime folder after the test execution is completed */
        if (System.getProperty(PROP_KEEP_RUNTIME_FOLDER) != null) {
            options.add(keepRuntimeFolder());
        }

        options.add(localMavenRepoOption());

        /* Add additional Maven repositories */
        String additionalMavenRepositories = "";
        if (System.getProperty(PROP_ADDITIONAL_MAVEN_REPOS) != null) {
            additionalMavenRepositories = "," + System.getProperty(PROP_ADDITIONAL_MAVEN_REPOS);
        }
        options.add(editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg", "org.ops4j.pax.url.mvn.repositories",
                "http://repo1.maven.org/maven2@id=central," +
                        "https://repository.jboss.org/nexus/content/groups/public@id=jboss-public" +
                        additionalMavenRepositories
        ));
        options.add(editConfigurationFilePut("etc/system.properties", "patching.disabled", "true"));
        if (!"features-fuse".equals(System.getProperty("kie.features.classifier"))) {
            // when not running on Fuse, we have to configure overrides and add some missing features
            options.add(editConfigurationFilePut("etc/startup.properties", "mvn:org.ops4j.pax.url/pax-url-wrap/2.5.4/jar/uber", "5"));
            options.add(editConfigurationFileExtend("etc/org.apache.karaf.features.cfg",
                    "featuresRepositories", "mvn:org.apache.karaf.features/spring-legacy/" + karafVersion + "/xml/features"));
            options.add(replaceConfigurationFile("etc/org.apache.karaf.features.xml", new File("target/test-classes/org.apache.karaf.features.xml")));
            options.add(editConfigurationFileExtend("etc/org.apache.karaf.features.cfg", "featuresBoot", "aries-blueprint"));
        }

        if (System.getProperty(PROP_KARAF_FRAMEWORK) != null) {
            options.add(editConfigurationFilePut(CustomProperties.KARAF_FRAMEWORK, System.getProperty(PROP_KARAF_FRAMEWORK)));
        }
        return new DefaultCompositeOption(options.toArray(new Option[1]));
    }

    protected static String getKarafVersion() {
        String karafVersion = System.getProperty(PROP_KARAF_VERSION);
        if (karafVersion == null) {
            if(System.getProperty(PROP_KARAF_DISTRIBUTION_FILE) != null) {
                throw new RuntimeException("When you are running against custom container "
                        + "it is necessary to define Karaf version by defining system property karaf.version.");
            }

            // set the Karaf version defined by Maven
            MavenUrlReference.VersionResolver versionResolver = MavenUtils.asInProject();
            karafVersion = versionResolver.getVersion(KARAF_GROUP_ID, KARAF_ARTIFACT_ID);
        }
        return karafVersion;
    }

    public static MavenArtifactProvisionOption getFeaturesUrl(String groupId, String artifactId, String version) {
        String classifier = "features";
        if (System.getProperty("kie.features.classifier") != null && "kie-karaf-features".equals(artifactId)) {
            classifier = System.getProperty("kie.features.classifier");
        }
        MavenArtifactProvisionOption mapo = mavenBundle().groupId(groupId).artifactId(artifactId);
        mapo.type("xml");
        mapo.classifier(classifier);

        if (version == null) {
            mapo.versionAsInProject();
        } else {
            mapo.version(version);
        }

        logger.info("Features URL: " + mapo.getURL());

        return mapo;
    }

    public static Option loadKieFeatures(String... features) {
        MavenArtifactProvisionOption featuresUrl = getFeaturesUrl("org.kie", "kie-karaf-features", KIE_VERSION);
        return features(featuresUrl, features);
    }

    public static Option localMavenRepoOption() {
        String localRepo = System.getProperty("maven.repo.local", "");
        if (localRepo.length() > 0) {
            logger.info("Using alternative local Maven repository in {}.", new File(localRepo).getAbsolutePath());
        }
        return when(localRepo.length() > 0).useOptions(
                //                systemProperty("org.ops4j.pax.url.mvn.localRepository").value(new File(localRepo).getAbsolutePath()));
                editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg",
                        "org.ops4j.pax.url.mvn.localRepository",
                        new File(localRepo).getAbsolutePath()),
                systemProperty(SYSTEM_PROP_MAVEN_CUSTOM_SETTINGS).value(kieCustomMavenSettingsXML()));
    }

    private static String kieCustomMavenSettingsXML() {
        InputStream testPropertiesStream = AbstractKarafIntegrationTest.class.getClassLoader().getResourceAsStream(TEST_PROPERTIES_FILE);
        Properties testProperties = new Properties();
        try {
            testProperties.load(testPropertiesStream);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read test.properties file", e);
        }
        return testProperties.getProperty(KIE_MAVEN_SETTINGS_CUSTOM_PROPERTY);
    }
}