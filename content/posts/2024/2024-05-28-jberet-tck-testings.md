---
layout: post
title:      "Adding rawhide TCK tests into the jberet-tck-runner project"
subtitle:   ""
date:       2024-05-29
author:     Wei Nan Li
aliases: [jberet-tck-testings]
---

Recently I have finished working on a series of tasks to enable the rawhide tests in the `jberet-tck-runner` project.

The goal of this task is to leverage the automation level of the Batch TCK tests of the JBeret project.

The detailed requirements are:

* Test the main branch of the JBeret project with the main branch of WildFly against the latest release of the Batch TCK.
* Test against the latest versions of Java (current Java 17 and 21), and it will
* Do the TCK deployment tests in both the WildFly Preview and default WildFly server.

I’d like to describe some details on the additions of the project code and configurations to achieve the above goals. Firstly, the `wildfly-maven-plugin` is used in the `pom.xml` to do the WildFly provision:


```xml
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <version>$\{version.wildfly-maven-plugin}</version>
    <configuration>
        <jboss-home>$\{jboss.home}</jboss-home>
    </configuration>
    <executions>
        <execution>
            <id>server-provisioning</id>
            <phase>generate-test-resources</phase>
            <goals>
                <goal>provision</goal>
            </goals>
            <configuration>
                <provisioning-dir>$\{jboss.home}</provisioning-dir>
                <galleon-options>
                    <jboss-fork-embedded>$\{galleon.fork.embedded}</jboss-fork-embedded>
                </galleon-options>
                <feature-packs>
                    <feature-pack>
                        <groupId>$\{server.test.feature.pack.groupId}</groupId>
                        <artifactId>$\{server.test.feature.pack.artifactId}</artifactId>
                        <version>$\{version.org.wildfly}</version>
                        <inherit-configs>false</inherit-configs>
                        <included-configs>
                            <config>
                                <model>standalone</model>
                                <name>standalone-full.xml</name>
                            </config>
                            <config>
                                <model>standalone</model>
                                <name>standalone.xml</name>
                            </config>
                        </included-configs>
                        <excluded-packages>
                            <name>docs.schema</name>
                            <name>appclient</name>
                            <name>domain</name>
                        </excluded-packages>
                    </feature-pack>
                </feature-packs>
                <channels>
                    <channel>
                        <manifest>
                            <groupId>org.jberet</groupId>
                            <artifactId>jberet-channel-manifest</artifactId>
                            <version>$\{version.jberet}</version>
                        </manifest>
                    </channel>
                </channels>
            </configuration>
        </execution>
        <execution>
            <id>wildfly-start</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>start</goal>
                <goal>deploy</goal>
            </goals>
            <configuration>
                <filename>test-deployment.war</filename>
            </configuration>
        </execution>
        <execution>
            <phase>post-integration-test</phase>
            <id>wildfly-stop</id>
            <goals>
                <goal>shutdown</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The above configuration can be used to generate a provisioned WildFly server for testing. Some properties can be defined during runtime:

```xml
<feature-pack>
	<groupId>$\{server.test.feature.pack.groupId}</groupId>
	<artifactId>$\{server.test.feature.pack.artifactId}</artifactId>
	<version>$\{version.org.wildfly}</version>
...
```

These properties can be injected during runtime, so the CI task can always use the main branch build of WildFly and different feature packs of WildFly can be used. We’ll check the details of this part later. The other part of the configuration worth noting is:

```xml
<channels>
    <channel>
        <manifest>
            <groupId>org.jberet</groupId>
            <artifactId>jberet-channel-manifest</artifactId>
            <version>$\{version.jberet}</version>
        </manifest>
    </channel>
</channels>
```

The above configuration can be used to override the JBeret version used in the provisioned WildFly server, so we can always inject the main branch build of JBeret in the CI process. We’ll check the details of this too later.

Secondly, the rawhide build tasks are divided into two files: `rawhide-default.yml` and `rawhide-preview.yml`. In these two tasks, there is a job step called `wildfly-build`:

```yml
jobs:
  wildfly-build:
    uses: wildfly/wildfly/.github/workflows/shared-wildfly-build.yml@main
    with:
      wildfly-branch: "main"
      wildfly-repo: "wildfly/wildfly"
...
```

This task is defined in the [WildFly repo](https://github.com/wildfly/wildfly/blob/main/.github/workflows/shared-wildfly-build.yml). Generally speaking, it will build a WildFly distribution from its main branch at runtime and the built distribution can be used in the CI task. The built version of WildFly can be used in the following step like:

```yml
- name: Run Rawhide Tests With Default WildFly
  run: WFLY_VER=$\{\{needs.wildfly-build.outputs.wildfly-version}} USE_BRANCH=$\{\{ matrix.use_branch }} ./run-tck-rawhide.sh
```

As the configuration shown above, the `needs.wildfly-build.outputs.wildfly-version` property is output in the `wildfly-build` step and it’s injected into the `WFLY_VER` property which is used in `run-tck-rawhide.sh`.

The only difference between `rawhide-default.yml`  and  `rawhide-preview.yml` is the parameters passed for running the `./run-tck-rawhide.sh`. In `rawhide-preview.yml` an additional `USE_PROFILE` parameter is passed to the running script:

```bash
USE_PROFILE=provision-preview WFLY_VER=$\{\{needs.wildfly-build.outputs.wildfly-version}} USE_BRANCH=$\{\{ matrix.use_branch }} ./run-tck-rawhide.sh
```

This will override the WildFly feature pack used for testing. There is a profile defined in the `pom.xml`:

```xml
<profiles>
    <profile>
        <id>provision-preview</id>
        <properties>
            <server.test.feature.pack.groupId>org.wildfly</server.test.feature.pack.groupId>
            <server.test.feature.pack.artifactId>wildfly-preview-feature-pack</server.test.feature.pack.artifactId>
        </properties>
    </profile>
</profiles>
```

Which will provision a WildFly Preview server for testing. With the setup, different WildFly installations can be tested.

The above are some major points worth noting on how the tasks are set up. Here is the relative issue about this task:

- [JBERET-606 Add a Github CI task to do the rawhide TCK testings](https://issues.redhat.com/browse/JBERET-606)

There is a list of the relative PRs and you can check it if you’d like to see the details of the work.










