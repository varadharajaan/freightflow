package com.freightflow.vesselschedule.application.port;

/**
 * Contract for dispatching pending outbox events to the message broker.
 */
public interface OutboxDispatcher {

    void dispatchPending();
}
