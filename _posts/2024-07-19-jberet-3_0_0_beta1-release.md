---
layout:     post
title:      "The Release Note Of JBeret 3.0.0.Beta1"
subtitle:   ""
date:       2024-07-19
author:     Wei Nan Li
---

Recently JBeret has a major version release::

- [Release v3.0.0.Beta1 · jberet/jsr352](https://github.com/jberet/jsr352/releases/tag/3.0.0.Beta1)

In this release it include these major changes:

- [JBERET-445: JUnit5 Migration #467](https://github.com/jberet/jsr352/pull/467)
- [JBERET-472: Extract database props from env-vars #473](https://github.com/jberet/jsr352/pull/473)

And it added the tests against main branch of WildFly server, both preview and non-preview ones:

- [JBERET-599: Setup the CI testings against WildFly main branch on #490](https://github.com/jberet/jsr352/pull/490)
- [Add WildFly Preview CI Testing. #531](https://github.com/jberet/jsr352/pull/531)

In addition, it removes some unnecessary API dependencies:

- [Remove the dependency of jakarta.persistence-api #536](https://github.com/jberet/jsr352/pull/536)
- [Remove the dependency of the validation-api #540](https://github.com/jberet/jsr352/pull/540)

And it contains these Jakarta API related components upgrades:

- [Bump jakarta.annotation:jakarta.annotation-api from 2.1.1 to 3.0.0 #506](https://github.com/jberet/jsr352/pull/501)
- [Bump jakarta.enterprise:jakarta.enterprise.cdi-api from 4.0.1 to 4.1.0 #511](https://github.com/jberet/jsr352/pull/511)

Because JBeret uses the basic usages of the `annotation-api` and the `cdi-api`, so generally speaking, the upgrade of the above two components doesn't introduce any major changes into the project. In addition, when the JBeret is used in an integrated environment, such as the `batch-jberet` module inside the WildFly, it will inherit the `annotation-api` and `cdi-api` versions used by WildFly itself. So this is a difference between JBeret SE and JBeret used inside an integrated environment like WildFly.

At last, there are relative tasks related with the Batch TCK testings that are done during the phase of this release:

- [Update 2.1 TCK to allow certification using Java 21 · Issue \#66 · jakartaee/batch-tck](https://github.com/jakartaee/batch-tck/issues/66)

The above is the information of the `3.0.0.Beta1` release.

