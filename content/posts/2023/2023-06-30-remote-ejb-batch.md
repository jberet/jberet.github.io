---
layout: post
title:      "Remote EJB Client for Batch Application"
subtitle:   ""
date:       2023-06-30
author:     Cheng Fang
aliases: [remote-ejb-batch]
---

## Introduction

Since its addition to Java EE around 2010, Java batch processing has been an integral part of that platform. With the
subsequent migration from Java EE to Jakarta EE platform, the rebranded Jakarta Batch continues to be a valuable tool
for tackling various enterprise data tasks. It works seamlessly with the rest of the platform, such as CDI, Web Services,
Restful Services, servlet, JSF and other web components. Many developers have successfully built and deployed enterprise
batch applications leveraging all these technologies. In this post, I'll try to explain how to use standalone remote EJB
client to connect to WildFly application server and initiate and manage batch jobs.

You might be wondering why using remote ejb with batch application. After all, with the ubiquity of web app and REST API,
it's only natural to choose HTTP client for batch applications. This is certainly a valid architecture choice for many
applications. But on the other hand, EJB, with its robust services including concurrency, transaction, security, and
scalability, offers a viable option for many use cases.

### Benefits of remote EJB client
Consider the following benefits of having a remote EJB client for batch application:
* Easy to distribute different parts of the application in different hosting environment. You can deploy the bulk of the
  batch processing logic inside an application server, while keeping a thin layer of client controller in a separate
  host. The same client co-located with the server-side services should work as well. This is the kind of location-
  transparency and deployment flexibility offered by using a remote EJB client.
* Easy to embed into other applications. Batch applications do not operate in a vacuum; instead it is oftentimes a piece
  in the whole enterprise system puzzle. With batch processing service is exposed via EJB remote interfaces, it becomes
  a breeze to embed the client into other programs. While integrating with other Java programs is most typical, it is
  also feasible to connect to non-Java programs via language-agnostic protocols like IIOP.
* Easy to automate with scripts. A remote EJB client running as a standalone Java application can be wrapped into a
  script, be it Bash, Python, Ant, Maven, or Groovy, without the dependency on any extra tools. It is also
  straightforward to pass any parameters to the EJB client either inside the script or directly in commend line.
* Better coordination of global transaction between client- and server-side. With WildFly remote ejb client, it is
  possible to propagate the client transaction to the server components, and maintain ACID properties across the board.

## Sample batch application

I'll be using a sample batch application, [remote-ejb-batch](https://github.com/jberet/remote-ejb-batch.git), to
showcase such a batch application design and implementation.

### Project layout
It is a single-module maven project consisting of both server- and client-side classes. After a successful build,
it produces a single artifact `target/remote-ejb-batch.war` This WAR archive can then be deployed to WildFly
application server.

`remote-ejb-batch.war` contains all the server-side classes (EJB classes, batchlet, and job.xml) necessary to run
the batch processing service in WildFly. Recall that EJB classes can be packaged as a WAR archive, even without any
web-related classes.

Also note the client class must not be packaged into the WAR archive. This is achived via the following exclusion
clause in `pom.xml`
```xml
            <plugin>
                <artifactId>maven-war-plugin</artifactId>
                <version>$\{version.war.plugin}</version>
                <configuration>
                    <packagingExcludes>
                        WEB-INF/lib/*.jar,
                        WEB-INF/classes/META-INF/wildfly-config.xml,
                        WEB-INF/classes/org/jberet/samples/ejb/Client.class
                    </packagingExcludes>
                </configuration>
            </plugin>
```

### Server-side classes
The following classes work on the server-side
* EJB classes
  * EJB remote business interface `SuspendBatchRemote`
  ```java
    @Remote
    public interface SuspendBatchRemote {
      BatchStatus getStatus();
      void startJob(String jobXmlName, int maxSeconds);
  }
  ```
  * EJB local business interface `SuspendBatchLocal`
  ```java
  @Local
  public interface SuspendBatchLocal {
      BatchStatus getStatus();
      void setStatus(BatchStatus status);
  }
  ```
  * `@Singleton` bean class `SuspendBatchSingleton`
  ```java
  @Singleton
  @ConcurrencyManagement(ConcurrencyManagementType.BEAN)
  @TransactionManagement(TransactionManagementType.BEAN)
  public class SuspendBatchSingleton implements SuspendBatchLocal, SuspendBatchRemote {
    @Inject
    private JobOperator jobOperator;
    // other stuff
    @Override
    public void startJob(final String jobXmlName, final int maxSeconds) {
        // other stuff
        final Properties properties = new Properties();
        properties.setProperty("max.seconds", String.valueOf(maxSeconds));
        jobOperator.start(jobXmlName, properties);
    }
  }
  ```
* Batch processing classes
  * Batchlet impl `Batchlet1` (refer to github repo for details)
  * job.xml file `job1.xml`
  ```xml
  <job id="job1" xmlns="http://xmlns.jcp.org/xml/ns/javaee"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/jobXML_1_0.xsd"
     version="1.0">
    <step id="step1">
        <batchlet ref="batchlet1">
            <properties>
                <property name="max.seconds" value="#\{jobParameters['max.seconds']}"/>
            </properties>
        </batchlet>
    </step>
  </job>
  ```

Worth mentioning is the presence of both remote and local business interfaces of the EJB bean class.
The remote business interface is intended only for the remote client, while the local business interface
should be invoked only by the local component, e.g., the batchlet class. Different aspects of the EJB service is
exposed to different interfaces, with appropriate separation of concerns.

### Client class
The client class is merely a simple POJO, running in standalone Java SE environment. All it does is JNDI-look up the
remote EJB, and invoke its remote business method which in turn starts the batch job. Effectively, the batch job
is initiated from the remote Java client.

```java
public final class Client {
    //other stuff

    public static void main(String[] args) throws Exception {
        final String lookupName = "ejb:/" + ARCHIVE_NAME + "/"
                + SuspendBatchSingleton.class.getSimpleName() + "!"
                + SuspendBatchRemote.class.getName();
        Context jndiContext = getRemoteContext();
        final SuspendBatchRemote bean = (SuspendBatchRemote) jndiContext.lookup(lookupName);
        bean.startJob(JOB_XML_NAME, MAX_SECONDS);
        jndiContext.close();
    }
}
```

### Build, deploy, and undeploy the batch application
```bash
mvn clean package

# use wildfly-maven-plugin to deploy
mvn wildfly:deploy

# use wildfly-maven-plugin to undeploy, after finishing the experiment
mvn wildfly:undeploy
```
Here we are using `wildfly-maven-plugin` to deploy the application to a running WildFly server.
Make sure WildFly standalone server has already been started with default settings in the same host machine.
Alternatively, you can also deploy via WildFly CLI, web console, or hot deploy (copying WAR to
`$JBOSS_HOME/standalone/deployments/`)

### Run Java client
The client program calls the remote EJB, which in turn starts running the batch job named `job1`.
After some duration, the job execution should complete successfully.

#### Run anonymously
```bash
# to run client program as a guest without providing username:
mvn exec:exec
```

#### Run as user `user1`
In order to run the whole application as a particular user/password, you will need to first create the user
in WildFly, and restart it.

```bash
# create user usr1 in WildFly
$JBOSS_HOME/bin/add-user.sh -a -u user1 -p user1

# stop WildFly standalone server, usually with Ctrl-C, and then start it again

# to run client program as user1:
mvn exec:exec -Duser=user1 -Dpassword=user1
```

When running with a credential, the security context is propagated to the target EJB, and the batchlet, allowing for
more fine-tuned access control and authorization.

## Summary
In this post, we just examined writing and running batch application fronted with a remote EJB client, and some of the
benefits of this design. As with any other application design, its usefulness depends largely on your application use
case and business requirements. I hope this post and the companion samples are useful, and would definitely encourage
you to explore many more features in JBeret and WildFly.
