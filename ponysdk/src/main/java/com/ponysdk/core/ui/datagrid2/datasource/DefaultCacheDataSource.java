/*
 * Copyright (c) 2019 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.core.ui.datagrid2.datasource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.ponysdk.core.server.service.query.PResultSet;
import com.ponysdk.core.ui.datagrid2.data.AbstractFilter;
import com.ponysdk.core.ui.datagrid2.data.DefaultRow;
import com.ponysdk.core.ui.datagrid2.data.Interval;
import com.ponysdk.core.ui.datagrid2.data.LiveDataView;
import com.ponysdk.core.util.MappedList;

public class DefaultCacheDataSource<K, V> extends AbstractDataSource<K, V> {

	private final Map<K, DefaultRow<V>> cache = new HashMap<>();
	private final List<DefaultRow<V>> liveData = new ArrayList<>();
	private final List<DefaultRow<V>> lastRequestedData = new ArrayList<>();

	@Override
	public PResultSet<V> getFilteredData() {
		// return a copy to avoid concurrent modification exception
		return PResultSet.of(new MappedList<>(new ArrayList<>(liveData), DefaultRow::getData));
	}

	@Override
	public PResultSet<V> getLastRequestedData() {
		return PResultSet.of(new MappedList<>(new ArrayList<>(lastRequestedData), DefaultRow::getData));
	}

	@Override
	public DefaultRow<V> getRow(final K k) {
		return cache.get(k);
	}

	@Override
	public Collection<DefaultRow<V>> getRows() {
		return cache.values();
	}

	@Override
	public int getRowCount() {
		return liveData.size();
	}

	@Override
	public LiveDataView<V> getRows(final int dataSrcRowIndex, int dataSize) {
		dataSize = dataSrcRowIndex + dataSize > liveData.size() ? liveData.size() - dataSrcRowIndex : dataSize;
		lastRequestedData.clear();
		for (int i = dataSrcRowIndex; i < dataSrcRowIndex + dataSize; i++) {
			lastRequestedData.add(liveData.get(i));
		}
		return new LiveDataView<>(getRowCount(), lastRequestedData);
	}

	@Override
	public Interval setData(final V v) {
		Objects.requireNonNull(v);
		final K k = adapter.getKey(v);
		final DefaultRow<V> row = cache.get(k);
		Interval interval;
		if (row != null) {
            if (row.getData() == v) return null;
			interval = updateData(k, row, v);
		} else {
			interval = insertData(k, v);
		}
		return interval;
	}

	private Interval updateData(final K k, final DefaultRow<V> row, final V newV) {
		if (row.isAccepted()) {
			final int oldLiveDataSize = liveData.size();
			final int oldRowIndex = removeRow(liveData, row);
			final boolean selected = selectedKeys.contains(k);
            if (selected) removeRow(liveSelectedData, row);
			row.setData(newV);
			return onWasAcceptedAndRemoved(selected, row, oldLiveDataSize, oldRowIndex);
		} else {
			row.setData(newV);
			return onWasNotAccepted(k, row);
		}
	}

	@Override
	public Interval updateData(final K k, final Consumer<V> updater) {
		final DefaultRow<V> row = cache.get(k);
        if (row == null) return null;
		if (row.isAccepted()) {
			final int oldLiveDataSize = liveData.size();
			final int oldRowIndex = removeRow(liveData, row);
			final boolean selected = selectedKeys.contains(k);
            if (selected) removeRow(liveSelectedData, row);
			updater.accept(row.getData());
			return onWasAcceptedAndRemoved(selected, row, oldLiveDataSize, oldRowIndex);
		} else {
			updater.accept(row.getData());
			return onWasNotAccepted(k, row);
		}
	}

	@Override
	public V removeData(final K k) {

		final DefaultRow<V> row = cache.remove(k);
		final boolean selected = selectedKeys.remove(k);
		if (row.isAccepted()) {
			removeRow(liveData, row);
            if (selected) removeRow(liveSelectedData, row);
		}
		return row.getData();
	}

	private Interval insertData(final K k, final V data) {
		final DefaultRow<V> row = new DefaultRow<>(rowCounter++, data);
		row.setAcceptance(accept(row));
		cache.put(k, row);
        if (!row.isAccepted()) return null;
		final int rowIndex = insertRow(liveData, row);
		return new Interval(rowIndex, liveData.size());
	}

	private Interval onWasAcceptedAndRemoved(final boolean selected, final DefaultRow<V> row, final int oldLiveDataSize,
			final int oldRowIndex) {
		clearRenderingHelpers(row);
		if (accept(row)) {
			final int rowIndex = insertRow(liveData, row);
            if (selected) insertRow(liveSelectedData, row);
			if (oldRowIndex <= rowIndex) {
				return new Interval(oldRowIndex, rowIndex + 1);
			} else {
				return new Interval(rowIndex, oldRowIndex + 1);
			}
		} else {
			row.setAcceptance(false);
			return new Interval(oldRowIndex, oldLiveDataSize);
		}
	}

	private Interval onWasNotAccepted(final K k, final DefaultRow<V> row) {
		clearRenderingHelpers(row);
		if (accept(row)) {
			row.setAcceptance(true);
			final int rowIndex = insertRow(liveData, row);
            if (selectedKeys.contains(k)) insertRow(liveSelectedData, row);
			return new Interval(rowIndex, liveData.size());
		} // else do nothing
		return null;
	}

	private void clearRenderingHelpers(final DefaultRow<V> row) {
		if (renderingHelpersCache.get(row.getData()) != null) {
			renderingHelpersCache.remove(row.getData());
		}
	}

	@Override
	public void resetLiveData() {
		liveSelectedData.clear();
		liveData.clear();
		for (final DefaultRow<V> row : cache.values()) {
			row.setAcceptance(accept(row));
			if (row.isAccepted()) {
				insertRow(liveData, row);
				if (selectedKeys.contains(adapter.getKey(row.getData()))) {
					insertRow(liveSelectedData, row);
				}
			}
		}
	}

	@Override
	public void sort() {
		super.sort();
		liveData.sort(this::compare);
	}

	@Override
	public String toString() {
		return cache.toString();
	}

	@Override
	public void forEach(final BiConsumer<K, V> action) {
		cache.forEach((k, r) -> action.accept(k, r.getData()));
	}

	@Override
	public void selectAllLiveData() {
		liveSelectedData.clear();
		for (final DefaultRow<V> row : liveData) {
			liveSelectedData.add(row);
			selectedKeys.add(adapter.getKey(row.getData()));
		}
	}

	@Override
	public void setFilter(Object key, final String id, final boolean reinforcing, final AbstractFilter<V> filter) {
		final AbstractFilter<V> oldFilter = filterController.addFilter(key.toString(), filter);
		if (oldFilter == null || reinforcing) {
			// We need to apply filter(s) on all rows because a single change on a filter
			// can change the acceptance state of a row that is already rejected by the
			// group,
			// so we need to use cache instead of liveData
			reinforceFilter(cache.values());
		} else {
			resetLiveData();
		}
	}

	private int reinforceFilter(final Collection<DefaultRow<V>> collection) {
		final Iterator<DefaultRow<V>> iterator = collection.iterator();
		int from = -1;
		for (int i = 0; iterator.hasNext(); i++) {
			final DefaultRow<V> row = iterator.next();
			if (!accept(row)) {
				row.setAcceptance(false);
				liveSelectedData.remove(row);
				liveData.remove(row);
				if (from < 0)
					from = i;
			}
		}
		return from;
	}

	@Override
	public Interval select(final K k) {
		final DefaultRow<V> row = cache.get(k);
        if (row == null || !selectedKeys.add(k) || !row.isAccepted()) return null;
		final int i = insertRow(liveSelectedData, row);
		return new Interval(i, i);
	}

	@Override
	public Interval unselect(final K k) {
		final DefaultRow<V> row = cache.get(k);
        if (row == null || !selectedKeys.remove(k) || !row.isAccepted()) return null;
		final int i = removeRow(liveSelectedData, row);
		return new Interval(i, i);
	}

}
