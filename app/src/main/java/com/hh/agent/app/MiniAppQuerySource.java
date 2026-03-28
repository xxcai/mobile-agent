package com.hh.agent.app;

import java.util.List;

interface MiniAppQuerySource {

    List<MiniAppQueryResult> search(String query);
}
