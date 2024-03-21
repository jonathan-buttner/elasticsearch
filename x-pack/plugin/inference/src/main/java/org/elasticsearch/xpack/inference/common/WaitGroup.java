/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provides a mechanism for dynamically adjusting the number of events to wait for. An example use would
 * be when a thread starts, call {@link #add()}, when the thread finish execution call {@link #done()}. Then
 * to wait for the thread to complete call {@link #awaitTermination(long, TimeUnit)}. This is helpful when you don't know
 * how many events to wait for during execution.
 */
public class WaitGroup {

    private static final Logger logger = LogManager.getLogger(WaitGroup.class);
    private final Phaser phaser = new Phaser();

    public void add() {
        try {
            phaser.register();
        } catch (Exception e) {
            logger.warn("Failed to increment wait group", e);
        }
    }

    public void done() {
        try {
            phaser.arriveAndDeregister();
        } catch (Exception e) {
            logger.warn("Failed to decrement wait group", e);
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            phaser.register();
            while (phaser.isTerminated() == false) {
                // TODO is this right?
                phaser.awaitAdvanceInterruptibly(phaser.arrive(), timeout, unit);
                phaser.arriveAndDeregister();
            }
        } catch (TimeoutException e) {
            return false;
        }

        return true;
    }

    public boolean isTerminated() {
        return phaser.isTerminated();
    }
}
