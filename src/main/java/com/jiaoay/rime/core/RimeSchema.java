package com.jiaoay.rime.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Rime方案
 */
public class RimeSchema {
    private final String kRadioSelected = " ✓";

    Map<String, Object> schema = new HashMap<String, Object>();
    List<Map<String, Object>> switches = new ArrayList<Map<String, Object>>();

    public RimeSchema(String schema_id) {
        Object o;
        o = RimeManager.Companion.getInstance().schemaGetValue(schema_id, "schema");
        if (o == null || !(o instanceof Map)) return;
        schema = (Map<String, Object>) o;
        o = RimeManager.Companion.getInstance().schemaGetValue(schema_id, "switches");
        if (o == null || !(o instanceof List)) return;
        switches = (List<Map<String, Object>>) o;
        check(); // 檢查不在選單中顯示的選項
        o = RimeManager.Companion.getInstance().schemaGetValue(schema_id, "menu");
        if (o == null || !(o instanceof HashMap)) return;
    }

    public void check() {
        if (switches.isEmpty()) return;
        for (Iterator<?> it = switches.iterator(); it.hasNext(); ) {
            Map<?, ?> o = (Map<?, ?>) it.next();
            if (!o.containsKey("states")) it.remove();
        }
    }

    public RimeCandidate[] getCandidates() {
        if (switches.isEmpty()) return null;
        RimeCandidate[] candidates = new RimeCandidate[switches.size()];
        int i = 0;
        for (Map<String, Object> o : switches) {
            candidates[i] = new RimeCandidate();
            final List<?> states = (List<?>) o.get("states");
            Integer value = (Integer) o.get("value");
            if (value == null) value = 0;
            candidates[i].text = states.get(value).toString();

            String kRightArrow = "→ ";
            if (RimeManager.Companion.getInstance().getShowSwitchArrow())
                candidates[i].comment =
                        o.containsKey("options") ? "" : kRightArrow + states.get(1 - value).toString();
            else
                candidates[i].comment = o.containsKey("options") ? "" : states.get(1 - value).toString();
            i++;
        }
        return candidates;
    }

    public void getValue() {
        if (switches.isEmpty()) return; // 無方案
        for (int j = 0; j < switches.size(); j++) {
            final Map<String, Object> o = switches.get(j);
            if (o.containsKey("options")) {
                List<?> options = (List<?>) o.get("options");
                for (int i = 0; i < options.size(); i++) {
                    final String s = (String) options.get(i);
                    if (RimeManager.Companion.getInstance().getOption(s)) {
                        o.put("value", i);
                        break;
                    }
                }
            } else {
                o.put("value", RimeManager.Companion.getInstance().getOption(o.get("name").toString()) ? 1 : 0);
            }
            switches.set(j, o);
        }
    }

    public void toggleOption(int i) {
        if (switches.isEmpty()) return;
        Map<String, Object> o = switches.get(i);
        Integer value = (Integer) o.get("value");
        if (value == null) value = 0;
        if (o.containsKey("options")) {
            List<String> options = (List<String>) o.get("options");
            RimeManager.Companion.getInstance().setOption(options.get(value), false);
            value = (value + 1) % options.size();
            RimeManager.Companion.getInstance().setOption(options.get(value), true);
        } else {
            value = 1 - value;
            RimeManager.Companion.getInstance().setOption(o.get("name").toString(), value == 1);
        }
        o.put("value", value);
        switches.set(i, o);
    }
}