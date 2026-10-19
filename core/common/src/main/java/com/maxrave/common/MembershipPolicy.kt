package com.maxrave.common

/** Local edits take precedence; any other phone's membership survives one phone removing it. */
fun effectiveImportedMembership(incoming: Boolean, localIntent: Boolean?, otherPhoneOwns: Boolean): Boolean =
    localIntent ?: (incoming || otherPhoneOwns)
