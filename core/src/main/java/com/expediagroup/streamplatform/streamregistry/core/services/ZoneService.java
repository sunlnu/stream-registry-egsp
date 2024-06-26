/**
 * Copyright (C) 2018-2024 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.streamplatform.streamregistry.core.services;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.val;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import com.expediagroup.streamplatform.streamregistry.core.handlers.HandlerService;
import com.expediagroup.streamplatform.streamregistry.core.validators.ValidationException;
import com.expediagroup.streamplatform.streamregistry.core.validators.ZoneValidator;
import com.expediagroup.streamplatform.streamregistry.core.views.ConsumerBindingView;
import com.expediagroup.streamplatform.streamregistry.core.views.InfrastructureView;
import com.expediagroup.streamplatform.streamregistry.core.views.ProcessBindingView;
import com.expediagroup.streamplatform.streamregistry.core.views.ProcessView;
import com.expediagroup.streamplatform.streamregistry.core.views.ProducerBindingView;
import com.expediagroup.streamplatform.streamregistry.core.views.StreamBindingView;
import com.expediagroup.streamplatform.streamregistry.core.views.ZoneView;
import com.expediagroup.streamplatform.streamregistry.model.ProcessBinding;
import com.expediagroup.streamplatform.streamregistry.model.Status;
import com.expediagroup.streamplatform.streamregistry.model.Zone;
import com.expediagroup.streamplatform.streamregistry.model.keys.ZoneKey;
import com.expediagroup.streamplatform.streamregistry.repository.ZoneRepository;

@Component
@RequiredArgsConstructor
public class ZoneService {
  private final HandlerService handlerService;
  private final ZoneValidator zoneValidator;
  private final ZoneRepository zoneRepository;
  private final ZoneView zoneView;
  private final StreamBindingView streamBindingView;
  private final ConsumerBindingView consumerBindingView;
  private final ProducerBindingView producerBindingView;
  private final ProcessBindingView processBindingView;
  private final ProcessView processView;
  private final InfrastructureView infrastructureView;

  @PreAuthorize("hasPermission(#zone, 'CREATE')")
  public Optional<Zone> create(Zone zone) throws ValidationException {
    if (zoneView.get(zone.getKey()).isPresent()) {
      throw new ValidationException("Can't create " + zone.getKey() + " because it already exists");
    }
    zoneValidator.validateForCreate(zone);
    zone.setSpecification(handlerService.handleInsert(zone));
    return saveSpecification(zone);
  }

  @PreAuthorize("hasPermission(#zone, 'UPDATE')")
  public Optional<Zone> update(Zone zone) throws ValidationException {
    val existing = zoneView.get(zone.getKey());
    if (existing.isEmpty()) {
      throw new ValidationException("Can't update " + zone.getKey().getName() + " because it doesn't exist");
    }
    zoneValidator.validateForUpdate(zone, existing.get());
    zone.setSpecification(handlerService.handleUpdate(zone, existing.get()));
    return saveSpecification(zone);
  }

  @PreAuthorize("hasPermission(#zone, 'UPDATE_STATUS')")
  public Optional<Zone> updateStatus(Zone zone, Status status) {
    zone.setStatus(status);
    return saveStatus(zone);
  }

  private Optional<Zone> saveSpecification(Zone zone) {
    return Optional.ofNullable(zoneRepository.saveSpecification(zone));
  }

  private Optional<Zone> saveStatus(Zone zone) {
    return Optional.ofNullable(zoneRepository.saveStatus(zone));
  }

  @PostAuthorize("returnObject.isPresent() ? hasPermission(returnObject, 'READ') : true")
  public Optional<Zone> get(ZoneKey key) {
    return zoneView.get(key);
  }

  @PostFilter("hasPermission(filterObject, 'READ')")
  public List<Zone> findAll(Predicate<Zone> filter) {
    return zoneRepository.findAll().stream().filter(filter).collect(toList());
  }

  @PreAuthorize("hasPermission(#zone, 'DELETE')")
  public void delete(Zone zone) {
    handlerService.handleDelete(zone);
    streamBindingView
      .findAll(sb -> sb.getKey().getInfrastructureKey().getZoneKey().equals(zone.getKey()))
      .findAny()
      .ifPresent(sb -> { throw new IllegalStateException("Zone is used in stream: " + sb.getKey().getStreamKey()); });

    consumerBindingView
      .findAll(cb -> cb.getKey().getConsumerKey().getZoneKey().equals(zone.getKey()))
      .findAny()
      .ifPresent(cb -> { throw new IllegalStateException("Zone is used in consumer binding: " + cb.getKey()); });

    producerBindingView
      .findAll(pb -> pb.getKey().getProducerKey().getZoneKey().equals(zone.getKey()))
      .findAny()
      .ifPresent(pb -> { throw new IllegalStateException("Zone is used in producer binding: " + pb.getKey()); });

    processBindingView
      .findAll(pb -> isZoneUsedInProcessBinding(zone, pb))
      .findAny()
      .ifPresent(pb -> { throw new IllegalStateException("Zone is used in process binding: " + pb.getKey()); });

    processView
      .findAll(p -> p.getZones().contains(zone.getKey()))
      .findAny()
      .ifPresent(p -> { throw new IllegalStateException("Zone is used in process: " + p.getKey()); });

    infrastructureView
      .findAll(infra -> infra.getKey().getZoneKey().equals(zone.getKey()))
      .findAny()
      .ifPresent(infra -> { throw new IllegalStateException("Zone is used in infrastructure: " + infra.getKey()); });

    zoneRepository.delete(zone);
  }

  private boolean isZoneUsedInProcessBinding(Zone zone, ProcessBinding processBinding) {
    return processBinding.getKey().getZoneKey().equals(zone.getKey()) ||
      isZoneUsedInProcessBindingOutput(zone, processBinding) ||
      isZoneUsedInProcessBindingInput(zone, processBinding);
  }

  private boolean isZoneUsedInProcessBindingOutput(Zone zone, ProcessBinding processBinding) {
    return processBinding.getOutputs().stream().map(o -> o.getStreamBindingKey().getInfrastructureKey().getZoneKey())
      .anyMatch(z -> z.equals(zone.getKey()));
  }

  private boolean isZoneUsedInProcessBindingInput(Zone zone, ProcessBinding processBinding) {
    return processBinding.getInputs().stream().map(i -> i.getStreamBindingKey().getInfrastructureKey().getZoneKey())
      .anyMatch(z -> z.equals(zone.getKey()));
  }
}
