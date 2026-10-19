package com.maxrave.common

/** An incomplete page never authorizes removing library membership. */
fun removedRemoteMembership(
    owned: Set<String>, incoming: Set<String>, complete: Boolean, protected: Set<String> = emptySet(),
): Set<String> = if (complete) owned - incoming - protected else emptySet()
