---
layout:     post
title:      "Batch Application with WildFly on OpenShift Using Image Stream"
subtitle:   ""
date:       2023-03-31
author:     Cheng Fang
---
This post demonstrates a few simple steps to deploy and manage batch applications with WildFly on OpenShift.
We'll reuse an existing sample batch application, [numbers-chunk](https://github.com/jberet/numbers-chunk.git), to
test out the OpenShift deployment. 

## Set up OpenShift account and command line tool
In this article, we'll be using [OpenShift Developer Sandbox](https://developers.redhat.com/developer-sandbox), 
which is free to use for development purpose. While all the operations can be performed within the online 
web console, I decide to use OpenShift command line tool called `oc`. 

To install `oc`, first login to your sandbox online account, then click the question mark (help) icon in the upper
right corner, and choose `Command line tools` in the dropdown.

Save the downloaded `oc` executable somewhere in your local computer, and make sure its parent directory is in
the shell `$PATH`.

To verify that you're running the right `oc` command:

```bash
which oc        
# this should point to the oc executable installed in the above step

oc version

Client Version: 4.12.0-202301042354.p0.g854f807.assembly.stream-854f807
Kustomize Version: v4.5.7
Server Version: 4.12.0
Kubernetes Version: v1.25.4+77bec7a
```

> There are other ways to install `oc`, such as installing via the local system package manager. 
> It is important to use the correct version of `oc`. 
> The most reliable way is to directly download from OpenShift account.

## Login from command line

In order to login from terminal, you need to obtain the credential from your OpenShift online account.
In your account page upper right corner, click your username, and choose `Copy login command` in the 
dropdown. You may need to further click `DevSandBox` and `Display Token` button/link in subsequent pages.

Then you can copy and paste your login command, similar to the following:

```bash
oc login --token=xxx --server=https://api.sandbox-m2.xxx.xx.openshiftapps.com:6443

# to display the current project:
oc project
```

## Choose WildFly Image Stream

Search wildfly image streams, and if not found, create one:

```bash
oc get is -n openshift | grep wildfly

# create one if it's not available
oc create -f https://raw.githubusercontent.com/wildfly/wildfly-s2i/wf-26.0/imagestreams/wildfly-centos7.json
```

## Creating OpenShift Application based on Image Stream

We'll build our sample batch application based on the above image streams:

```bash
oc new-app wildfly:26.0~https://github.com/jberet/numbers-chunk --name=numbers-chunk --allow-missing-images --strategy=source

--> Found image 3ffc18b (15 months old) in image stream "cfang-dev/wildfly" under tag "26.0" for "wildfly:26.0"

    WildFly 26.0.0.Final
    --------------------
    Platform for building and running JEE applications on WildFly 26.0.0.Final

    Tags: builder, wildfly, wildfly26

    * A source build using source code from https://github.com/jberet/numbers-chunk will be created
      * The resulting image will be pushed to image stream tag "numbers-chunk:latest"
      * Use 'oc start-build' to trigger a new build

--> Creating resources ...
    imagestream.image.openshift.io "numbers-chunk" created
    buildconfig.build.openshift.io "numbers-chunk" created
    deployment.apps "numbers-chunk" created
    service "numbers-chunk" created
--> Success
    Build scheduled, use 'oc logs -f buildconfig/numbers-chunk' to track its progress.
    Application is not exposed. You can expose services to the outside world by executing one or more of the commands below:
     'oc expose service/numbers-chunk'
    Run 'oc status' to view your app.   
```

## Expose the application service

Now we need to expose the application externally:
```bash
oc expose service numbers-chunk

oc get route

NAME            HOST/PORT                                                           PATH   SERVICES        PORT       TERMINATION   WILDCARD
numbers-chunk   numbers-chunk-cfang-dev.apps.sandbox-m2.ll9k.p1.openshiftapps.com          numbers-chunk   8080-tcp                 None
```

## Manage batch jobs with WildFly CLI

Once the application is fully started on OpenShift, there should be 1-2 pods running. Go to one of the pods, and enter its
terminal. There you can start WildFly CLI to manage batch job executions:

For example, below are some sample CLI operations to manage batch jobs in pod `numbers-chunk-787b44c4f5-5vklw`

```bash
sh-4.2$ /opt/wildfly/bin/jboss-cli.sh --connect

[standalone@localhost:9990 /] /deployment=ROOT.war/subsystem=batch-jberet:start-job(job-xml-name=numbers)
{
    "outcome" => "success",
    "result" => 1L
}

[standalone@localhost:9990 /] /deployment=ROOT.war/subsystem=batch-jberet/job=numbers/execution=1:read-resource(include-runtime)
{
    "outcome" => "success",
    "result" => {
        "batch-status" => "COMPLETED",
        "create-time" => "2023-04-01T01:30:49.934",
        "end-time" => "2023-04-01T01:30:50.185",
        "exit-status" => "COMPLETED",
        "instance-id" => 1L,
        "last-updated-time" => "2023-04-01T01:30:50.185",
        "start-time" => "2023-04-01T01:30:49.942"
    }
}

[standalone@localhost:9990 /] /deployment=ROOT.war/subsystem=batch-jberet/job=numbers/execution=1:stop-job()

[standalone@localhost:9990 /] /deployment=ROOT.war/subsystem=batch-jberet/job=numbers/execution=1:restart-job() 
```

## Stop and clean up all resources
```bash
oc delete route/numbers-chunk
oc delete service/numbers-chunk
oc delete deploy/numbers-chunk
oc delete is/numbers-chunk
```