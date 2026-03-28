package com.hh.agent.android.route;

/**
 * Policy used by resolver to validate direct URI access.
 */
public interface UriAccessPolicy {

    UriAccessDecision evaluate(String uri);
}
