/*
 * The Fascinator - Plugin - Transformer - Scripting
 * Copyright (C) 2010-2011  University of Southern Queensland
 * Copyright (C) 2014 Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.googlecode.fascinator.transformer;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.fascinator.api.PluginDescription;
import com.googlecode.fascinator.api.PluginException;
import com.googlecode.fascinator.api.storage.DigitalObject;
import com.googlecode.fascinator.api.transformer.Transformer;
import com.googlecode.fascinator.api.transformer.TransformerException;
import com.googlecode.fascinator.common.JsonSimple;
import com.googlecode.fascinator.common.JsonSimpleConfig;

/**
 * <p>
 * This plugin provides method to execute Transformers using scripts written in Jython or Groovy 
 * </p>
 * 
 * <h3>Configuration</h3> Standard configuration table:
 * <table border="1">
 * <tr>
 * <th>Option</th>
 * <th>Description</th>
 * <th>Required</th>
 * <th>Default</th>
 * </tr>
 * 
 * <tr>
 * <td>id</td>
 * <td>Transformer Id</td>
 * <td><b>Yes</b></td>
 * <td>scripting</td>
 * </tr>
 * 
 * </table>
 * 
 * <h3>Examples</h3>
 * <ol>
 * <li>
 * Scripting transformer attached to the transformer list in The Fascinator
 * 
 * <pre>
 *      "scripting": {
 *             "id": "scripting",
 *             "scriptType": "groovy",
 *             "scriptPath": "${fascinator.home}/transformer-scripts/exampleScript.groovy"
 *         }
 * </pre>
 * 
 * </li>
 * </ol> 
 * 
 * @author Andrew Brazzatti
 */
public class ScriptingTransformer implements Transformer {
	/** Logger */
	private static Logger log = LoggerFactory
			.getLogger(ScriptingTransformer.class);

	/** Json config file **/
	@SuppressWarnings("unused")
	private JsonSimpleConfig config;

	private static final String PYTHON_SCRIPT_CLASS_NAME = "Transformer";
	private static final String PYTHON_SCRIPT_ACTIVATE_METHOD = "transform";
	
	/**
	 * Extractor Constructor
	 */
	public ScriptingTransformer() {
	}

	/**
	 * Overridden method init to initialize
	 * 
	 * @param jsonString
	 *            of configuration for Extractor
	 * @throws PluginException
	 *             if fail to parse the config
	 */
	@Override
	public void init(String jsonString) throws PluginException {
		try {
			config = new JsonSimpleConfig(jsonString);
			reset();
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	/**
	 * Overridden method init to initialize
	 * 
	 * @param jsonFile
	 *            to retrieve the configuration for Extractor
	 * @throws PluginException
	 *             if fail to read the config file
	 */
	@Override
	public void init(File jsonFile) throws PluginException {
		try {
			config = new JsonSimpleConfig(jsonFile);
			reset();
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	/**
	 * Reset the transformer in preparation for a new object
	 */
	private void reset() throws TransformerException {

	}

	/**
	 * Overridden method getId
	 * 
	 * @return plugin id
	 */
	@Override
	public String getId() {
		return "scripting";
	}

	/**
	 * Overridden method getName
	 * 
	 * @return plugin name
	 */
	@Override
	public String getName() {
		return "Jython Extractor";
	}

	/**
	 * Gets a PluginDescription object relating to this plugin.
	 * 
	 * @return a PluginDescription
	 */
	@Override
	public PluginDescription getPluginDetails() {
		return new PluginDescription(this);
	}

	/**
	 * Overridden method shutdown method
	 * 
	 * @throws PluginException
	 */
	@Override
	public void shutdown() throws PluginException {
	}

	/**
	 * Overridden transform method
	 * 
	 * @param DigitalObject
	 *            to be processed
	 * @return processed DigitalObject with the rdf metadata
	 */
	@Override
	public DigitalObject transform(DigitalObject in, String jsonConfig)
			throws TransformerException {
		JsonSimple config;
		try {
			config = new JsonSimple(jsonConfig);
		} catch (IOException e1) {
			throw new TransformerException(e1);
		}
		DigitalObject response = null;
		String scriptType = config.getString("python", "scriptType");
		String scriptPath = config.getString(null, "scriptPath");
		if (scriptPath == null) {
			throw new TransformerException("Please specify a scriptPath");
		}
		

		
		if ("groovy".equals(scriptType)) {
			Binding binding = new Binding();
			binding.setVariable("digitalObject", in);
			binding.setVariable("config",config);

			GroovyShell shell = new GroovyShell(binding);

			GroovyCodeSource groovyScript;
			try {
				groovyScript = new GroovyCodeSource(new File(
						scriptPath));
				response = (DigitalObject) shell.evaluate(groovyScript);
			} catch (IOException e) {
				throw new TransformerException(e);
			}
			
		} else if ("python".equals(scriptType)) {
			Map<String, Object> binding = new HashMap<String, Object>();
			Map<String, List<String>> fields = new HashMap<String, List<String>>();
			binding.put("digitalObject", in);
			binding.put("config", config);
			
			// Run the data through our script
			PyObject script = getPythonObject(scriptPath);

			if (script.__findattr__(PYTHON_SCRIPT_ACTIVATE_METHOD) != null) {
				PyObject result = script.invoke(PYTHON_SCRIPT_ACTIVATE_METHOD,
						Py.java2py(binding));
				response = (DigitalObject) result;
			} else {
				log.warn("Activation method not found!");
			}
			// ApplicationContextProvider.getApplicationContext().getBean("fascinatorStorage");
		}

		return response;
	}

	private PyObject getPythonObject(String scriptPath)
			throws TransformerException {
		// Execute the script
		PythonInterpreter python = new PythonInterpreter();
		try {
			python.execfile(new FileInputStream(new File(scriptPath)), "Processor");
		} catch (FileNotFoundException e) {
			throw new TransformerException(e);
		}
		// Get the result and cleanup
		PyObject scriptClass = python.get(PYTHON_SCRIPT_CLASS_NAME);
		python.cleanup();
		return scriptClass.__call__();
	}
}
