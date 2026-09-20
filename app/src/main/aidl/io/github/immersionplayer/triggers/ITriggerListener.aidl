package io.github.immersionplayer.triggers;

oneway interface ITriggerListener {
    /** trigger: 0 = left, 1 = right. */
    void onTrigger(int trigger, boolean down);
}
