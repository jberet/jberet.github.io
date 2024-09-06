---
layout:     post
title:      "Configuring The WildFly To Use The JBeret JDBC Job Repository (Part 2)"
subtitle:   ""
date:       2024-08-29
author:     Wei Nan Li
---


In the [previous blog post on this topic](https://jberet.org/configure-jberet-wildfly-jdbc-job-repo/), I have introduced how to manually edit the configuration file of WildFly to configure the `batch-jberet` module to use the JDBC repository. In this article, I'd like to introduce how to use the **CLI Command Tool** and the **Admin Console** to do this.

In the previous blog post, we see that WildFly has provided a default JDBC-based data source called `ExampleDS`:

```xml
<subsystem xmlns="urn:jboss:domain:datasources:7.1">
    <datasources>
        <datasource jndi-name="java:jboss/datasources/ExampleDS" pool-name="ExampleDS" enabled="true" use-java-context="true" statistics-enabled="${wildfly.datasources.statistics-enabled:${wildfly.statistics-enabled:false}}">
            <connection-url>jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=${wildfly.h2.compatibility.mode:REGULAR}</connection-url>
            <driver>h2</driver>
            <security user-name="sa" password="sa"/>
        </datasource>
        <drivers>
            <driver name="h2" module="com.h2database.h2">
                <xa-datasource-class>org.h2.jdbcx.JdbcDataSource</xa-datasource-class>
            </driver>
        </drivers>
    </datasources>
</subsystem>
```

Now we can use the CLI tool to configure the `batch-jberet` subsystem to use this data source. Firstly we can use the `jboss-cli.sh` in the `bin` directory of WildFly to enter the CLI interface and connect to the local running WildFly server:

```bash
➤ ./jboss-cli.sh --connect
[standalone@localhost:9990 /]
```

After the CLI is connected to the running WildFly server, we can add the `ExampleDS` data source into the `batch-jberet` subsystem. Here is the command to do so:

```bash
[standalone@localhost:9990 /] /subsystem=batch-jberet/jdbc-job-repository=jdbc:add(data-source=ExampleDS)
{"outcome" => "success"}
```

The above output shows that the data source was added as the `jdbc-job-repository` into the `batch-jberet` subsystem successfully. Then we can configure the `batch-jberet` subsystem to use the JDBC job repository by default. Here is the command to do so:

```bash
[standalone@localhost:9990 /] /subsystem=batch-jberet:write-attribute(name=default-job-repository,value=jdbc)
{
    "outcome" => "success",
    "response-headers" => {
        "operation-requires-reload" => true,
        "process-state" => "reload-required"
    }
}
```

As the command output shown above, the configuration is done and the server needs to reload the configuration for the change to take effect. We can use the following command to do so:

```bash
[standalone@localhost:9990 /] :reload
{
    "outcome" => "success",
    "result" => undefined
}
```

Now the server configuration is reloaded. If you check the configuration file of the WildFly server, you can see the file content is updated accordingly. For example, if the WildFly server is running under the standalone mode, you can see the `batch-jberet` subsystem configuration is changed to use the `jdbc` repository by default, and the data source `ExampleDS` is added for usage:

```xml
<subsystem xmlns="urn:jboss:domain:batch-jberet:3.0">
    <default-job-repository name="jdbc"/>
    <default-thread-pool name="batch"/>
    <security-domain name="ApplicationDomain"/>
    <job-repository name="in-memory">
        <in-memory/>
    </job-repository>
    <job-repository name="jdbc">
        <jdbc data-source="ExampleDS"/>
    </job-repository>
    <thread-pool name="batch">
        <max-threads count="10"/>
        <keepalive-time time="30" unit="seconds"/>
    </thread-pool>
</subsystem>
```

Above is the introduction to using the CLI tool to configure the job repository. Now let's see how to do the same configuration by using the **Admin Console**.

I won't introduce the details on how to set up the management user to access the admin console, which is not the focus of this blog post. You can refer to this article if you want to understand the details of this topic:

- [How to access WildFly Admin Console](https://www.mastertheboss.com/jbossas/jboss-configuration/how-to-access-wildfly-admin-console/)

After entering the WildFly Admin Console, we can click the `Configuration` tab on top of the menu, and select the `Subsystems` section at left, and then click the `View` button of the `Batch` subsystem:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/2024-08-29/image.png)

And then we enter the `Batch Subsystem` configuration page like this:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/2024-08-29/image 2.png)

As the screenshot shows above, the configuration we have made with the CLI tool is also reflected on the above configuration page. This is the design of WildFly: It doesn’t matter if the configuration you have made is from **CLI**, **Admin Console**, or manually edit the configuration file, the changes can be seen from all the other places. Now we can click the `JDBC` section and click the `Edit` button  to see the job repository configuration:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/2024-08-29/image 3.png)

After clicking the `Edit` button, we can see the `Data Source` we have added by the CLI tool:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/2024-08-29/image 4.png)

Until now, we have learned about using the **CLI tool** and the **Admin console** to configure the **Batch Subsystem** to use the JDBC job repository.

## Extra Ways of Configuration

If you'd like to see how to add a data source and configure the batch subsystem via CLI command line, here is an example for reference:

- [https://github.com/jberet/jberet-examples/blob/main/deployment/setup-server.sh](https://github.com/jberet/jberet-examples/blob/main/deployment/setup-server.sh)

If you'd like to see how to integrate the configuration process into `wildfly-maven-plugin`, you can write a `.cli` file like this:

- [https://github.com/jberet/jberet-tck-runner/blob/main/src/main/resources/configure-wildfly.cli](https://github.com/jberet/jberet-tck-runner/blob/main/src/main/resources/configure-wildfly.cli)

And configure it into `wildfly-maven-plugin` like this:

- [https://github.com/jberet/jberet-tck-runner/blob/main/pom.xml#L126](https://github.com/jberet/jberet-tck-runner/blob/main/pom.xml#L126)

```xml
<execution>
    <id>configure-wildfly</id>
    <phase>pre-integration-test</phase>
    <goals>
        <goal>execute-commands</goal>
    </goals>
    <configuration>
        <skip>${skip.configuration}</skip>
        <jboss-home>${jboss.home}</jboss-home>
        <offline>true</offline>
        <scripts>
            <script>${project.build.outputDirectory}/configure-wildfly.cli</script>
        </scripts>
    </configuration>
</execution>
```

Above is the more automated configuration methods.