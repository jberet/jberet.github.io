---
layout: post
title:      "The Batch `checkpoint-policy` and the JBeret implementation."
subtitle:   ""
date:       2025-01-23
author:     Wei Nan Li
aliases: [jberet-checkpoint]
---

In the Jakarta Batch spec[1], it defines the `checkpoint-policy` like this:

```txt
checkpoint-policy
Specifies the checkpoint policy that governs commit behavior for this chunk. Valid values are: "item" or "custom". The "item" policy means the chunk is checkpointed after a specified number of items are processed. The "custom" policy means the chunk is checkpointed according to a checkpoint algorithm implementation. Specifying "custom" requires that the checkpoint-algorithm element is also specified. See section 8.2.1.5 for checkpoint-algorithm. It is an optional attribute. The default policy is "item".
```

The syntax is like this:

```xml
 <chunk checkpoint-policy="\{item|custom}"
  item-count="\{value}"
  time-limit="\{value}"
  skip-limit="\{value}"
  retry-limit="\{value}" />
```

I have created an example showing the usage of the custom checkpoint policy:

- [jberet-playground/standalone/src/main/resources/META-INF/batch-jobs/checkpointJob.xml at main · liweinan/jberet-playground](https://github.com/liweinan/jberet-playground/blob/main/standalone/src/main/resources/META-INF/batch-jobs/checkpointJob.xml)

It uses the customized checkpoint policy:

```java
package io.weli.jberet.se;

import jakarta.batch.api.chunk.AbstractCheckpointAlgorithm;
import jakarta.batch.runtime.context.JobContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named
public class CustomCheckPoint extends AbstractCheckpointAlgorithm {

    @Inject
    JobContext jobContext;

    @Override
    public boolean isReadyToCheckpoint() throws Exception {

        int counterRead = (Integer) jobContext.getTransientUserData();

        System.out.println("isReadyToCheckpoint -> counterRead: " + counterRead);

        return counterRead % 3 == 0;
    }
}
```

Here is the class diagram showing the JBeret implementation of `Chunk`, `ChunkRunner` and relative policies:

![](https://raw.githubusercontent.com/jberet/jberet.github.io/main/_imgs/2025-01-23/01.png)

Please note the `CheckpointAlgorithm`  in the above class diagram. The interface is defined like this:

```java
package jakarta.batch.api.chunk;

/**
 * CheckpointAlgorithm provides a custom checkpoint
 * policy for chunk steps.
 *
 */
public interface CheckpointAlgorithm {

 /**
  * The checkpointTimeout is invoked at the beginning of a new
  * checkpoint interval for the purpose of establishing the checkpoint
  * timeout.
  * It is invoked before the next chunk transaction begins. This
  * method returns an integer value, which is the timeout value
  * (expressed in seconds) which will be used for the next chunk
  * transaction.
  * This method is useful to automate the setting of the
  * checkpoint timeout based on factors known outside the job
  * definition.
  * A value of '0' signifies no maximum established by this
  * CheckpointAlgorithm, i.e. the maximum permissible timeout allowed by
  * the runtime environment.
  * @return the timeout interval (expressed in seconds)
  * to use for the next checkpoint interval
  * @throws Exception thrown for any errors.
  */
 public int checkpointTimeout() throws Exception;
 /**
  * The beginCheckpoint method is invoked before the
  * next checkpoint interval begins (before the next
  * chunk transaction begins).
  * @throws Exception thrown for any errors.
  */
 public void beginCheckpoint() throws Exception;
 /**
  * The isReadyToCheckpoint method is invoked by
  * the batch runtime after each item is processed
  * to determine if now is the time to checkpoint
  * the current chunk.
  * @return boolean indicating whether or not
  * to checkpoint now.
  * @throws Exception thrown for any errors.
  */
 public boolean isReadyToCheckpoint() throws Exception;
 /**
  * The endCheckpoint method is invoked after the
  * last checkpoint is taken (after the chunk
  * transaction is committed).
  * @throws Exception thrown for any errors.
  */
 public void endCheckpoint() throws Exception;

}
```

This is used in the  `run()` method of the `ChunkRunner`:

```java
String attrVal = chunk.getCheckpointPolicy();
if (attrVal == null || attrVal.equals("item")) {
    attrVal = chunk.getItemCount();
    if (attrVal != null) {
        itemCount = Integer.parseInt(attrVal);
        if (itemCount < 1) {
            throw MESSAGES.invalidItemCount(itemCount);
        }
    }
    attrVal = chunk.getTimeLimit();
    if (attrVal != null) {
        timeLimit = Integer.parseInt(attrVal);
    }
} else if (attrVal.equals("custom")) {
    checkpointPolicy = "custom";
    final RefArtifact alg = chunk.getCheckpointAlgorithm();
    if (alg != null) {
        checkpointAlgorithm = jobContext.createArtifact(alg.getRef(), null, alg.getProperties(), batchContext);
    } else {
        throw MESSAGES.checkpointAlgorithmMissing(batchContext.getStep().getId());
    }
} else {
    throw MESSAGES.invalidCheckpointPolicy(attrVal);
}
```

The `checkpointAlgorithm` is used in the `readProcessWriteItems()` method:

```java
if (tm.getStatus() != Status.STATUS_ACTIVE) {
    //other part of the app might have marked the transaction as rollback only, so roll it back
    if (tm.getStatus() == Status.STATUS_MARKED_ROLLBACK) {
        tm.rollback();
    }
    if (checkpointAlgorithm != null) {
        tm.setTransactionTimeout(checkpointAlgorithm.checkpointTimeout());
        checkpointAlgorithm.beginCheckpoint();
    }
    tm.begin();
}
```

## Reference

1: [Jakarta Batch](https://jakarta.ee/specifications/batch/2.1/jakarta-batch-spec-2.1#chunk)
