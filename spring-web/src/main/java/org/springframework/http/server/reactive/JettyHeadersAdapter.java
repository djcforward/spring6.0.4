/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.server.reactive;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

/**
 * {@code MultiValueMap} implementation for wrapping Jetty HTTP headers.
 *
 * <p>There is a duplicate of this class in the client package!
 *
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @since 5.1.1
 */
class JettyHeadersAdapter implements MultiValueMap<String, String> {

	private final HttpFields.Mutable headers;


	JettyHeadersAdapter(HttpFields.Mutable headers) {
		this.headers = headers;
	}


	@Override
	public String getFirst(String key) {
		return this.headers.get(key);
	}

	@Override
	public void add(String key, @Nullable String value) {
		this.headers.add(key, value);
	}

	@Override
	public void addAll(String key, List<? extends String> values) {
		values.forEach(value -> add(key, value));
	}

	@Override
	public void addAll(MultiValueMap<String, String> values) {
		values.forEach(this::addAll);
	}

	@Override
	public void set(String key, @Nullable String value) {
		this.headers.put(key, value);
	}

	@Override
	public void setAll(Map<String, String> values) {
		values.forEach(this::set);
	}

	@Override
	public Map<String, String> toSingleValueMap() {
		Map<String, String> singleValueMap = CollectionUtils.newLinkedHashMap(this.headers.size());
		Iterator<HttpField> iterator = this.headers.iterator();
		iterator.forEachRemaining(field -> {
			if (!singleValueMap.containsKey(field.getName())) {
				singleValueMap.put(field.getName(), field.getValue());
			}
		});
		return singleValueMap;
	}

	@Override
	public int size() {
		return this.headers.getFieldNamesCollection().size();
	}

	@Override
	public boolean isEmpty() {
		return (this.headers.size() == 0);
	}

	@Override
	public boolean containsKey(Object key) {
		return (key instanceof String headerName && this.headers.contains(headerName));
	}

	@Override
	public boolean containsValue(Object value) {
		return (value instanceof String searchString &&
				this.headers.stream().anyMatch(field -> field.contains(searchString)));
	}

	@Nullable
	@Override
	public List<String> get(Object key) {
		if (containsKey(key)) {
			return this.headers.getValuesList((String) key);
		}
		return null;
	}

	@Nullable
	@Override
	public List<String> put(String key, List<String> value) {
		List<String> oldValues = get(key);
		this.headers.put(key, value);
		return oldValues;
	}

	@Nullable
	@Override
	public List<String> remove(Object key) {
		if (key instanceof String headerName) {
			List<String> oldValues = get(key);
			this.headers.remove(headerName);
			return oldValues;
		}
		return null;
	}

	@Override
	public void putAll(Map<? extends String, ? extends List<String>> map) {
		map.forEach(this::put);
	}

	@Override
	public void clear() {
		this.headers.clear();
	}

	@Override
	public Set<String> keySet() {
		return new HeaderNames();
	}

	@Override
	public Collection<List<String>> values() {
		return this.headers.getFieldNamesCollection().stream()
				.map(this.headers::getValuesList).toList();
	}

	@Override
	public Set<Entry<String, List<String>>> entrySet() {
		return new AbstractSet<>() {
			@Override
			public Iterator<Entry<String, List<String>>> iterator() {
				return new EntryIterator();
			}
			@Override
			public int size() {
				return headers.size();
			}
		};
	}


	@Override
	public String toString() {
		return HttpHeaders.formatHeaders(this);
	}


	private class EntryIterator implements Iterator<Entry<String, List<String>>> {

		private final Iterator<String> names = headers.getFieldNamesCollection().iterator();

		@Override
		public boolean hasNext() {
			return this.names.hasNext();
		}

		@Override
		public Entry<String, List<String>> next() {
			return new HeaderEntry(this.names.next());
		}
	}


	private class HeaderEntry implements Entry<String, List<String>> {

		private final String key;

		HeaderEntry(String key) {
			this.key = key;
		}

		@Override
		public String getKey() {
			return this.key;
		}

		@Override
		public List<String> getValue() {
			return headers.getValuesList(this.key);
		}

		@Override
		public List<String> setValue(List<String> value) {
			List<String> previousValues = headers.getValuesList(this.key);
			headers.put(this.key, value);
			return previousValues;
		}
	}


	private class HeaderNames extends AbstractSet<String> {

		@Override
		public Iterator<String> iterator() {
			return new HeaderNamesIterator(headers.getFieldNamesCollection().iterator());
		}

		@Override
		public int size() {
			return headers.getFieldNamesCollection().size();
		}
	}


	private final class HeaderNamesIterator implements Iterator<String> {

		private final Iterator<String> iterator;

		@Nullable
		private String currentName;

		private HeaderNamesIterator(Iterator<String> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return this.iterator.hasNext();
		}

		@Override
		public String next() {
			this.currentName = this.iterator.next();
			return this.currentName;
		}

		@Override
		public void remove() {
			if (this.currentName == null) {
				throw new IllegalStateException("No current Header in iterator");
			}
			if (!headers.contains(this.currentName)) {
				throw new IllegalStateException("Header not present: " + this.currentName);
			}
			headers.remove(this.currentName);
		}
	}

}
