/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
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

package com.ponysdk.core.ui.activity;

import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.ui.basic.IsPWidget;
import com.ponysdk.core.ui.basic.PAcceptsOneWidget;
import com.ponysdk.core.ui.eventbus.BroadcastEventHandler;
import com.ponysdk.core.ui.eventbus.Event;
import com.ponysdk.core.ui.eventbus.Event.Type;
import com.ponysdk.core.ui.eventbus.EventHandler;
import com.ponysdk.core.ui.eventbus.HandlerRegistration;
import com.ponysdk.core.ui.place.Place;
import com.ponysdk.core.ui.place.PlaceChangeRequest;

public abstract class AbstractActivity<T extends IsPWidget> implements Activity {

    protected boolean firstStart = true;
    protected boolean started = false;

    protected PAcceptsOneWidget world;
    protected T view;

    public AbstractActivity() {
    }

    @Override
    public void start(final PAcceptsOneWidget world, final Place place) {
        this.world = world;
        this.started = true;

        this.world.setWidget(getView());

        if (firstStart) {
            buildView();
            firstStart = false;
        }

        updateView(place);
    }

    public T getView() {
        return view;
    }

    @Override
    public void stop() {
        this.started = false;
    }

    protected void buildView() {
        // Nothing to do
    }

    protected void updateView(final Place place) {
        // Nothing to do
    }

    public void goTo(final Place place) {
        PlaceChangeRequest.fire(this, place);
    }

    public <H extends EventHandler> HandlerRegistration addHandler(final Type<H> type, final H handler) {
        return UIContext.addHandler(type, handler);
    }

    public <H extends EventHandler> HandlerRegistration addHandlerToSource(final Type<H> type, final Object source, final H handler) {
        return UIContext.addHandlerToSource(type, source, handler);
    }

    @SuppressWarnings("unchecked")
    public <H extends EventHandler> HandlerRegistration addHandlerToSource(final Type<H> type, final Object source) {
        return addHandlerToSource(type, source, (H) this);
    }

    public void fireEvent(final Event<?> event) {
        UIContext.fireEvent(event);
    }

    public void fireEventFromSource(final Event<?> event, final Object source) {
        UIContext.fireEventFromSource(event, source);
    }

    public void addHandler(final BroadcastEventHandler handler) {
        UIContext.addHandler(handler);
    }

}