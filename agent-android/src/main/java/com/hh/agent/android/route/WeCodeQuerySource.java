package com.hh.agent.android.route;

import java.util.List;

public interface WeCodeQuerySource {

    List<WeCodeQueryResult> search(String query);
}
