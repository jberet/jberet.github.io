---
layout:     post
title:      "Creating the jberet-tck-runner project"
subtitle:   ""
date:       2024-03-18
author:     Wei Nan Li
---

Recently I have created the `jberet-tck-runner`[^tck_link] project which will run the Batch TCK tests against JBeret periodically. The script of the runner is located here:

- [https://github.com/jberet/jberet-tck-runner/blob/main/run-tck.sh](https://github.com/jberet/jberet-tck-runner/blob/main/run-tck.sh)

The script will download the official tck ZIP file firstly(and the version of the tck can be set via the `BATCH_TCK_VER` variable during the runtime process). 

And then it will clone the `jberet-tck-porting`[^porting_link] project and build it, which contains the necessary files for the JBeret to test against the official tck.

The next step is to clone the JBeret main branch and build it. Then it will copy the necessary files from the `jberet-tck-porting` project into the unzipped official tck project directory, for it can be tested against the built JBeret main branch.

In addition, the project will detect abd download the latest version of the `wildfly`[^wildfly_link] project, which will be used for the deployment tests.

After everything is ready, the script will run three kinds of the tests provided by the official tck project:

- The `sigtest`, which are the API signature tests.
- The `se` tests, which are the standalone tests using JBeret SE.
- The `deployment` tests, which will run the TCK tests by starting the WildFly server and run the tests inside the WildFly runtime.

To see the above steps in action, you can check one of the running tests in GitHub. Here is one of the run process:

- [https://github.com/jberet/jberet-tck-runner/actions/runs/8319441676](https://github.com/jberet/jberet-tck-runner/actions/runs/8319441676)

The above link contains the detail running process output run by GitHub CI. The script is configured to be run daily.

The purpose of this project is to improve the agility of the TCK tests against the main branch of JBeret and the latest version of WildFly. Here is the relative issue that is resolved by the project:

- [JBERET-604 Create a TCK runner project on Github to run TCK testings periodically.](https://issues.redhat.com/browse/JBERET-604)

Above is a brief introduction to the projectm, and in the future this project may be kept updated.

## References

[^tck_link]: [https://github.com/jberet/jberet-tck-runner](https://github.com/jberet/jberet-tck-runner)
[^porting_link]: [https://github.com/jberet/jberet-tck-porting](https://github.com/jberet/jberet-tck-porting)
[^wildfly_link]: [https://github.com/wildfly/wildfly](https://github.com/wildfly/wildfly)


