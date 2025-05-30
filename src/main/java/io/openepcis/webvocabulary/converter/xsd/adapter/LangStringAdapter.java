package io.openepcis.webvocabulary.converter.xsd.adapter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The adapter between LangStringWrapper and LangStringAdapter
public class LangStringAdapter extends XmlAdapter<List<LangStringWrapper>, Map<String, String>> {

    @Override
    public Map<String, String> unmarshal(final List<LangStringWrapper> list) {
        final Map<String, String> map = new LinkedHashMap<>();

        if (list != null) {
            for (final LangStringWrapper item : list) {
                if (item.getLang() != null && item.getValue() != null) {
                    map.put(item.getLang(), item.getValue());
                }
            }
        }

        return map;
    }

    @Override
    public List<LangStringWrapper> marshal(final Map<String, String> map) {
        final List<LangStringWrapper> list = new ArrayList<>();

        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                final LangStringWrapper wrapper = new LangStringWrapper();
                wrapper.setLang(entry.getKey());
                wrapper.setValue(entry.getValue());
                list.add(wrapper);
            }
        }

        return list;
    }
}
