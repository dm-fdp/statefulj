/***
 * 
 * Copyright 2014 Andrew Hall
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.statefulj.framework.core.actions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.ReflectionUtils;
import org.statefulj.fsm.FSM;
import org.statefulj.fsm.RetryException;
import org.statefulj.fsm.TooBusyException;
import org.statefulj.fsm.model.Action;

public class MethodInvocationAction implements Action<Object> {

	private final Pattern protocol = Pattern.compile("(([^:]*):)?(.*)");

	private Object controller;
	
	private String method;
	
	private Class<?>[] parameters;

	private FSM<Object> fsm;
	
	public MethodInvocationAction(
			String method,
			Class<?>[] parameters,
			FSM<Object> fsm,
			Object controller) {
		this.method = method;
		this.parameters = parameters;
		this.fsm = fsm;	
		this.controller = controller;
	}

	public Object getController() {
		return controller;
	}

	public void setController(Object controller) {
		this.controller = controller;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public Class<?>[] getParameters() {
		return parameters;
	}

	public void setParameters(Class<?>[] parameters) {
		this.parameters = parameters;
	}

	public FSM<Object> getFsm() {
		return fsm;
	}

	public void setFsm(FSM<Object> fsm) {
		this.fsm = fsm;
	}

	public Pattern getProtocol() {
		return protocol;
	}

	@SuppressWarnings("unchecked")
	public void execute(Object stateful, String event, Object... parms) throws RetryException {
		try {
			// Remove the first Object in the parm list - it's our Return Value
			//
			List<Object> parmList = new ArrayList<Object>(Arrays.asList(parms));
			MutableObject<Object> returnValue = (MutableObject<Object>)parmList.remove(0);
			List<Object> invokeParmList = buildInvokeParameters(stateful, event, parmList);
			
			if (invokeParmList.size() < this.parameters.length) {
				throw new RuntimeException(
						"Incoming parameter list is incorrect, expected " 
						+ this.parameters.length 
						+ " parameters, but have " 
						+ invokeParmList.size());
			}
			
			// Call the method on the Controller
			// TODO : Add test case
			//
			Object retVal = invoke(stateful, event, invokeParmList);
			if (retVal instanceof String) {
				Pair<String, String> pair = this.parseResponse((String)retVal);
				if ("event".equals(pair.getLeft())) {
					this.fsm.onEvent(stateful, pair.getRight(), returnValue, parms);
				} else {
					returnValue.setValue(retVal);
				}
			} else {
				returnValue.setValue(retVal);
			}
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException) {
				throw (RuntimeException)e.getCause();
			}
			if (e.getCause() instanceof RetryException) {
				throw (RetryException)e.getCause();
			}
		} catch (TooBusyException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return this.method;
	}
	
	protected Object invoke(Object stateful, String event, List<Object> invokeParmList) throws RetryException, SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		return invoke(this.controller, invokeParmList);
	}
	
	protected Object invoke(Object context, List<Object> invokeParmList) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Method method = ReflectionUtils.findMethod(context.getClass(), this.method, this.parameters);
		if (method == null) {
			throw new NoSuchMethodException(this.method);
		}
		Object[] methodParms = invokeParmList.subList(0, this.parameters.length).toArray();
		method.setAccessible(true);
		return method.invoke(context, methodParms);
	}
	
	protected List<Object> buildInvokeParameters(Object stateful, String event, List<Object> parmList) {

		// Add the Entity and Event to the parm list to pass to the Controller
		// TODO : Inspect method signature - make entity and event optional
		//
		ArrayList<Object> invokeParmList = new ArrayList<Object>(parmList.size() + 2);
		invokeParmList.add(stateful);
		invokeParmList.add(event);
		invokeParmList.addAll(parmList);
		
		return invokeParmList;
	}
	
	private Pair<String, String> parseResponse(String response) {
		Matcher matcher = this.protocol.matcher(response);
		if (!matcher.matches()) {
			throw new RuntimeException("Unable to parse response=" + response);
		}
		return new ImmutablePair<String, String>(matcher.group(2), matcher.group(3));
	}
}
