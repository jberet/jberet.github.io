---
layout:     post
title:      "Using JBeret With Quarkus"
subtitle:   ""
date:       2022-12-07
author:     Wei Nan Li
---

JBeret can be used within Quarkus:

- [GitHub - quarkusio/quarkus: Quarkus: Supersonic Subatomic Java.](https://github.com/quarkusio/quarkus)

In this article I won’t go into details of Quarkus. If you are not familiar with Quarkus, you can learn and check the above project link. Quarkus has the JBeret extension:

- [Quarkus JBeret Extension](https://github.com/quarkiverse/quarkus-jberet)

Here is the class diagram of the core part of `quarkus-jberet`:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/jberet_quarkus/class.jpg)

From the above class diagram, we can see it adds a layer for JBeret to be enabled in Quarkus, so the project that uses JBeret can be deployed into Quarkus with minimum configuration. We can clone this project and play with the tests inside the project:

```bash
➤ git clone https://github.com/quarkiverse/quarkus-jberet.git
```

There are multiple tests that can be used as example:

- [quarkus-jberet/core/deployment/src/test/java/io/quarkiverse/jberet/deployment at main · quarkiverse/quarkus-jberet · GitHub](https://github.com/quarkiverse/quarkus-jberet/tree/main/core/deployment/src/test/java/io/quarkiverse/jberet/deployment)

One of the above tests is `BatchletTest`:

- https://github.com/quarkiverse/quarkus-jberet/blob/main/core/deployment/src/test/java/io/quarkiverse/jberet/deployment/BatchletTest.java

This test uses the Quarkus test framework to set up the service running environment, and it will start the Quarkus before test, and then register the components and run the test. Here is the setup part of the test:

```java
@RegisterExtension
static QuarkusUnitTest TEST = new QuarkusUnitTest()
    .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
        .addClasses(DummyBatchlet.class)
        .addAsManifestResource("batchlet.xml", "batch-jobs/batchlet.xml"));
```

In the above code, it creates the test JAR archive, and it puts the `batchlet.xml` into the JAR file. This `batchlet.xml` is defined in the test code directory:

- https://github.com/quarkiverse/quarkus-jberet/blob/main/core/deployment/src/test/resources/batchlet.xml

The `batchlet.xml` defines a basic batch job called `batchlet-job` for testing:

```xml
<job id="batchlet-job" xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="1.0">
    <step id="batchlet-step">
        <batchlet ref="batchlet">
            <properties>
                <property name="name" value="#{jobParameters['name']}"/>
            </properties>
        </batchlet>
    </step> 
</job>
```

In the above `batchlet-job`, it defines a batchlet just called `batchlet`. In the `batchlet` it defines a property called `name`, which reads the value from a parameter `#{jobParameters['name']}`. The `batchlet` and its `name` are all defined in the `BatchletTest` code:

```java
@Named("batchlet")
@Dependent
public static class DummyBatchlet implements Batchlet {
      @Inject
      @BatchProperty(name = "name")
      String name;

      @Override
      public String process() {
          if (!name.equals("david")) {
              throw new RuntimeException("Unexpected value injected to 'name': " + name);
          }
          return BatchStatus.COMPLETED.toString();
      }

      @Override
      public void stop() {
      }
}
```

In the above `DummyBatchlet`, it defines the `process()` method that will be run in the job. And the value `name` of the property will be injected during the test running process. Here is one of the test methods inside the test class:

```java
@Test
public void runBatchletJob() {
    Properties jobParameters = new Properties();
    jobParameters.setProperty("name", "david");
    JobOperator jobOperator = BatchRuntime.getJobOperator();
    long executionId = jobOperator.start("batchlet", jobParameters);

    await().atMost(5, TimeUnit.SECONDS).until(() -> {
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);
        return BatchStatus.COMPLETED.equals(jobExecution.getBatchStatus());
    });
}
```

In the above test, it sets the `name` property to be used for execution, and then it starts the `batchlet` defined in the `batchlet-job`. After the `batchlet` is running, the test waits for 5 seconds for the execution to be finished, and then check the task finished. 

Now we can run the test by running the following commands in the test directory:

```bash
➤ pwd
/Users/weli/works/quarkus-jberet/core/deployment
```

Here is the command to run the test:

```bash
➤ mvn test -Dtest=BatchletTest
```

If everything goes fine we can see the tests passed:

```bash
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Until now, we have a basic understanding on how JBeret works with Quarkus.


