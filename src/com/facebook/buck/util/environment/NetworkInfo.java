/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.util.environment;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.EventKey;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public final class NetworkInfo {
  public static class Event extends AbstractBuckEvent {
    Network network;

    public Event(Network network) {
      super(EventKey.unique());
      this.network = network;
    }

    public Network getNetwork() {
      return network;
    }

    @Override
    public String getEventName() {
      return "NetworkInfoEvent";
    }

    @Override
    protected String getValueString() {
      return network.toString();
    }
  }

  private NetworkInfo() {}

  public static void generateActiveNetworkAsync(
      ExecutorService executorService, BuckEventBus buckEventBus) {
    executorService.submit(
        () -> {
          buckEventBus.post(new Event(getLikelyActiveNetwork()));
        });
  }

  public static Network getLikelyActiveNetwork() {
    return new Network(NetworkMedium.UNKNOWN);
  }

  public static Optional<String> getWifiSsid() {
    // TODO(royw): Support Big Sur, Linux and Windows.
    return Optional.empty();
  }
}
