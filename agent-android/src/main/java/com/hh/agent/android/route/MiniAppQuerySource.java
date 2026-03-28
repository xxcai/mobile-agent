package com.hh.agent.android.route;

import java.util.List;

public interface MiniAppQuerySource {

    List<MiniAppQueryResult> search(String query);
}
