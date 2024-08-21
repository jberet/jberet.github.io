---
layout:     post
title:      "Configuring The WildFly To Use The JBeret JDBC Job Repository"
subtitle:   ""
date:       2024-01-15
author:     Wei Nan Li
---

By default, the batch subsystem in WildFly uses the `in-memory` job repository provided by JBeret. Here is the configuration in `standalone.xml` by default:

```xml
<subsystem xmlns="urn:jboss:domain:batch-jberet:4.0">
    <default-job-repository name="in-memory"/>
    <default-thread-pool name="batch"/>
    <security-domain name="ApplicationDomain"/>
    <job-repository name="in-memory">
        <in-memory/>
    </job-repository>
    <thread-pool name="batch">
        <max-threads count="10"/>
        <keepalive-time time="30" unit="seconds"/>
    </thread-pool>
</subsystem>
```

As the configuration shown above, the default `job-repository` is `in-memory`. We can change it to use the JDBC repository like this:

```xml
<subsystem xmlns="urn:jboss:domain:batch-jberet:4.0">
    <default-job-repository name="jdbc"/>
    <default-thread-pool name="batch"/>
    <security-domain name="ApplicationDomain"/>
    <job-repository name="jdbc">
        <jdbc data-source="ExampleDS" />
    </job-repository>
    <thread-pool name="batch">
        <max-threads count="10"/>
        <keepalive-time time="30" unit="seconds"/>
    </thread-pool>
</subsystem>
```

As the configuration shown above, we have changed the `job-repository` to `jdbc`, and we have set the `data-source` to `ExampleDS`. The `ExampleDS` datasource is provided by WildFly server by default, here is the configuration of the `ExampleDS`  datasource in `standalone.xml`:

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


As the configuration shown above, the `ExampleDS` datasource is configured to use H2 database and it’s a in-memory database by default. To verify it works,  we can change the `connection-url` from:

```
jdbc:h2:mem:test;...
```

to:

```
jdbc:h2:/tmp/jberet-job;...
```

After the above change is done, the H2 database will store the job repository as files named with `jberet-job*` in `/tmp` directory. We can verify this by starting the WildFly server:

```bash
$ ./standalone.sh
```

And then deploy the sample project located at here:

- [jberet-playground/deployment at main · liweinan/jberet-playground](https://github.com/liweinan/jberet-playground/tree/main/deployment)

The project can be deployed with WildFly CLI tool:

```bash
[standalone@localhost:9990 /] deploy /Users/weli/works/jberet-playground/deployment/target/batch-deployment.war
```

At the server output side it has the following output:

```
01:47:52,786 INFO  [org.jboss.as.connector.subsystems.datasources] (MSC service thread 1-3) WFLYJCA0001: Bound data source [java:jboss/datasources/ExampleDS]
01:47:52,922 INFO  [org.jboss.as.server.deployment.scanner] (MSC service thread 1-4) WFLYDS0013: Started FileSystemDeploymentService for directory /Users/weli/works/wildfly/dist/target/wildfly-31.0.0.Final-SNAPSHOT/standalone/deployments
01:47:52,983 INFO  [org.jboss.ws.common.management] (MSC service thread 1-6) JBWS022052: Starting JBossWS 7.0.0.Final (Apache CXF 4.0.0) 
01:47:53,035 INFO  [org.jberet] (ServerService Thread Pool -- 78) JBERET000021: About to initialize batch job repository with ddl-file: sql/jberet.ddl for database H2
01:47:53,162 INFO  [org.jboss.as.server] (Controller Boot Thread) WFLYSRV0212: Resuming server
01:47:53,166 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0060: Http management interface listening on http://127.0.0.1:9990/management
01:47:53,166 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0051: Admin console listening on http://127.0.0.1:9990
01:47:53,167 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0025: WildFly Full 31.0.0.Final-SNAPSHOT (WildFly Core 23.0.0.Beta5) started in 2384ms - Started 280 of 522 services (317 services are lazy, passive or on-demand) - Server configuration file in use: standalone.xml
01:48:15,688 INFO  [org.jboss.as.repository] (management-handler-thread - 1) WFLYDR0001: Content added at location /Users/weli/works/wildfly/dist/target/wildfly-31.0.0.Final-SNAPSHOT/standalone/data/content/f3/b112acba0692afcb82c7c44dcd44d0cae46fc3/content
01:48:15,707 INFO  [org.jboss.as.server.deployment] (MSC service thread 1-7) WFLYSRV0027: Starting deployment of "batch-deployment.war" (runtime-name: "batch-deployment.war")
01:48:16,306 INFO  [org.jboss.weld.deployer] (MSC service thread 1-7) WFLYWELD0003: Processing weld deployment batch-deployment.war
01:48:16,363 INFO  [org.hibernate.validator.internal.util.Version] (MSC service thread 1-7) HV000001: Hibernate Validator 8.0.1.Final
01:48:16,575 INFO  [org.jboss.as.connector.deployers.jdbc] (MSC service thread 1-8) WFLYJCA0004: Deploying JDBC-compliant driver class org.h2.Driver (version 2.2)
01:48:16,600 INFO  [org.jboss.weld.Version] (MSC service thread 1-8) WELD-000900: 5.1.2 (Final)
01:48:16,657 INFO  [org.jboss.as.connector.deployers.jdbc] (MSC service thread 1-8) WFLYJCA0018: Started Driver service with driver-name = batch-deployment.war_org.h2.Driver_2_2
01:48:17,481 INFO  [org.jboss.resteasy.resteasy_jaxrs.i18n] (ServerService Thread Pool -- 82) RESTEASY002225: Deploying jakarta.ws.rs.core.Application: class io.weli.jberet.RestApplication$Proxy$_$$_WeldClientProxy
01:48:17,514 INFO  [org.wildfly.extension.undertow] (ServerService Thread Pool -- 82) WFLYUT0021: Registered web context: '/batch-deployment' for server 'default-server'
01:48:17,569 INFO  [org.jboss.as.server] (management-handler-thread - 1) WFLYSRV0010: Deployed "batch-deployment.war" (runtime-name : "batch-deployment.war")
```

And the service can be accessed with the following command:

```bash
➤ curl http://localhost:8080/batch-deployment/batch/start
COMPLETED
```

And now we can check whether the job repository database files exists:

```bash
➤ ls /tmp/jberet-job*
/tmp/jberet-job.mv.db
/tmp/jberet-job.trace.db
```

As the output shown above, the H2 database files are created, and we can use the H2 database binary jar file to view the content of the above database. Here is the binary jar file and you can download it firstly:

- [https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.2.224/h2-2.2.224.jar](https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.2.224/h2-2.2.224.jar)

And then it can be invoked like this:

```bash
➤ java -jar h2-2.2.224.jar
```

It will open the H2 database admin page in the browser, and you need to modify the `JDBC URL` to point to the job repository file:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/jberet_jdbc_wildfly/connect.png)

The content of the `JDBC URL` is:

```
jdbc:h2:/tmp/jberet-job
```

And the `username` and `password` are both `sa`, which is configured in `standalone.xml`. Please note that you have to shutdown the WildFly server firstly, and then connect to the database by using this admin console, or the database files are locked by the WildFly. After clicking the `Connect` button, we can see the job tables:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/jberet_jdbc_wildfly/tables.png)

And we can check the data items in `JOB_EXECUTION`:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/jberet_jdbc_wildfly/items.png)

Above is an introduction to the usage of the JDBC job repository of JBeret in WildFly.

