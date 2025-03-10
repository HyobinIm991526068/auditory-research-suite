/*
 * Copyright (C) 2011 McMaster University PI: Dr. Michael Schutz
 * <schutz@mcmaster.ca>
 * 
 * Distributed under the terms of the GNU Lesser General Public License (LGPL).
 * See LICENSE.TXT that came with this file.
 */
package edu.mcmaster.maplelab.av.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mcmaster.maplelab.av.datamodel.AVSession;
import edu.mcmaster.maplelab.common.LogContext;
import edu.mcmaster.maplelab.common.datamodel.Pair;

/**
 * Class for enumerating all parameters contributing to media object creation.
 * 
 * @author bguseman
 */
public class MediaParams {
	private static final Map<String, MediaParams> _parameters = new HashMap<String, MediaParams>();
	private static boolean _loaded = false;
	
	public static void loadMediaParams(AVSession<?, ?, ?> session) {
		if (_loaded) return;
		else _loaded = true;
		
		for (MediaType<?> mt : MediaType.values()) {
			for (String param : mt.getParams(session)) {
				if (_parameters.containsKey(param)) continue;
				_parameters.put(param, new MediaParams(param, session));
			}
		}
	}
	
	public static MediaParams getAvailableValues(String param) {
		return _parameters.get(param);
	}
	
	public static MediaParams valueOf(String param) {
		return getAvailableValues(param);
	}
	
	public enum LabelKeys {
		LABEL {
			@Override
			public String getKey(String param) {
				return param + ".label";
			}
		},
		VAL_LABELS {
			@Override
			public String getKey(String param) {
				return param + ".labels";
			}
		};
		
		public abstract String getKey(String param);
	}
	
	private final String _name;
	private final String _displayName;
	private final List<MediaParamValue> _values;
	
	private MediaParams(String name, AVSession<?, ?, ?> session) {
		_name = name;
		_values = new ArrayList<MediaParamValue>();
		String label = session.getString(LabelKeys.LABEL.getKey(_name), null);
		_displayName = label != null ? label : _name;
        List<String> strValues = session.getStringList(_name, (String[]) null);
        List<String> strLabels = session.getStringList(LabelKeys.VAL_LABELS.getKey(_name), (String[]) null);
		if (strValues != null) {
		    int idx = 0;
			for (String str : strValues) {
			    String disp = strLabels != null && strLabels.size() > idx ? strLabels.get(idx++) : null; 
				_values.add(new MediaParamValue(_name, str, disp));
			}
		}
		else {
			LogContext.getLogger().warning(String.format(
					"No values available for parameter %s", _name));
		}
	};
	
	public List<MediaParamValue> getValues() {
		return Collections.unmodifiableList(_values);
	}
	
	public String paramName() {
		return _name;
	}
	
	public String displayLabel() {
		return _displayName;
	}
	
	@Override
	public String toString() {
		return paramName();
	}
	
	/**
	 * Encapsulation of the pairing between parameter name and value
	 * 
	 * 
	 * @author <a href="mailto:fitch@datamininglab.com">Simeon H.K. Fitch</a>
	 * @since Jan 24, 2012
	 */
	public static class MediaParamValue extends Pair<String, String>{

		private final String _displayName;

        private MediaParamValue(String paramName, String paramValue, String displayName) {
		    super(paramName, paramValue);
            _displayName = displayName != null ? displayName : paramValue;
		}
		
		public String paramName() {
			return getFirst();
		}
		
		public String paramValue() {
			return getLast();
		}
		
		@Override
		public String toString() {
		    return _displayName;
		}
	}
}
