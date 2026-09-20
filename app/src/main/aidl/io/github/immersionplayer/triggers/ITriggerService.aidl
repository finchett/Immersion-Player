package io.github.immersionplayer.triggers;

import io.github.immersionplayer.triggers.ITriggerListener;

/** Runs in Shizuku's shell-level process; reads the shoulder trigger sensors. */
interface ITriggerService {
    /** Turns the sensors on and starts reporting presses. */
    void start(ITriggerListener listener) = 1;

    /** Stops reporting and turns the sensors off again. */
    void stop() = 2;

    /** Trigger input devices found, for diagnostics ("" if none). */
    String devices() = 3;

    /** Required by Shizuku to tear the service down. */
    void destroy() = 16777114;
}
