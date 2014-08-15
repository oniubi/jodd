// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.json;

import jodd.introspector.ClassDescriptor;
import jodd.introspector.ClassIntrospector;
import jodd.introspector.Getter;
import jodd.introspector.PropertyDescriptor;
import jodd.json.meta.JsonAnnotationManager;
import jodd.util.ArraysUtil;

import java.util.List;

/**
 * Bean visitor that serializes properties of a bean.
 * It analyzes the rules for inclusion/exclusion of a property.
 */
public class BeanSerializer {

	private final JsonContext jsonContext;
	private final Object source;
	private boolean declared;
	private final String classMetadataName;
	private final Class type;

	private int count;
	private String[] includes;
	private String[] excludes;

	public BeanSerializer(JsonContext jsonContext, Object bean) {
		this.jsonContext = jsonContext;
		this.source = bean;
		this.count = 0;
		this.declared = false;
		this.classMetadataName = jsonContext.jsonSerializer.classMetadataName;

		type = bean.getClass();

		JsonAnnotationManager jsonAnnotationManager = JsonAnnotationManager.getInstance();

		includes = jsonAnnotationManager.lookupIncludes(type);
		excludes = jsonAnnotationManager.lookupExcludes(type);
	}

	/**
	 * Serializes a bean.
	 */
	public void serialize() {
		Class type = source.getClass();

		ClassDescriptor classDescriptor = ClassIntrospector.lookup(type);

		if (classMetadataName != null) {
			// process first 'meta' fields 'class'
			onProperty(classMetadataName, null, null);
		}

		PropertyDescriptor[] propertyDescriptors = classDescriptor.getAllPropertyDescriptors();

		for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
			String propertyName = null;
			Class propertyType = null;

			Getter getter = propertyDescriptor.getGetter(declared);
			if (getter != null) {
				propertyName = propertyDescriptor.getName();
				propertyType = propertyDescriptor.getType();
			}

			if (propertyName != null) {
				onProperty(propertyName, propertyType, propertyDescriptor);
			}
		}
	}

	/**
	 * Invoked on each property.
	 */
	protected boolean onProperty(String propertyName, Class propertyType, PropertyDescriptor pd) {
		Path currentPath = jsonContext.path;

		currentPath.push(propertyName);

		// determine if name should be included/excluded

		boolean include = true;

		// + all collections are not serialized by default

		if (propertyType != null && !jsonContext.jsonSerializer.includeCollections) {

			ClassDescriptor propertyTypeClassDescriptor = ClassIntrospector.lookup(propertyType);

			if (propertyTypeClassDescriptor.isCollection()) {
				include = false;
			}
		}

		// + annotations

		if (include == true) {
			if (ArraysUtil.contains(excludes, propertyName)) {
				include = false;
			}
		}
		else {
			if (ArraysUtil.contains(includes, propertyName)) {
				include = true;
			}
		}

		// + path queries: excludes/includes

		List<PathQuery> pathQueries = jsonContext.jsonSerializer.pathQueries;

		if (pathQueries != null) {
			for (int iteration = 0; iteration < 2; iteration++) {
				for (PathQuery pathQuery : pathQueries) {
					if (iteration == 0 && !pathQuery.isWildcard()) {
						continue;
					}
					if (iteration == 1 && pathQuery.isWildcard()) {
						continue;
					}
					if (pathQuery.matches(currentPath)) {
						include = pathQuery.isIncluded();
					}
				}
			}
		}

		// done

		if (!include) {
			currentPath.pop();
			return true;
		}

		Object value;

		if (propertyType == null) {
			// metadata - classname
			value = source.getClass().getName();
		} else {
			value = readProperty(source, pd);

			// change name for properties

			propertyName = JsonAnnotationManager.getInstance().resolveName(type, propertyName);
		}

		jsonContext.pushName(propertyName, count > 0);
		jsonContext.serialize(value);

		if (jsonContext.isNamePoped()) {
			count++;
		}

		currentPath.pop();

		return true;
	}

	/**
	 * Reads property using property descriptor.
	 */
	private Object readProperty(Object source, PropertyDescriptor propertyDescriptor) {
		Getter getter = propertyDescriptor.getGetter(declared);

		if (getter != null) {
			try {
				return getter.invokeGetter(source);
			}
			catch (Exception ex) {
				throw new JsonException(ex);
			}
		}

		return null;
	}

}