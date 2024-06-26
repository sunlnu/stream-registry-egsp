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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.expediagroup.streamplatform.streamregistry.core.handlers.HandlerService;
import com.expediagroup.streamplatform.streamregistry.core.validators.ConsumerValidator;
import com.expediagroup.streamplatform.streamregistry.core.views.ConsumerBindingView;
import com.expediagroup.streamplatform.streamregistry.core.views.ConsumerView;
import com.expediagroup.streamplatform.streamregistry.model.Consumer;
import com.expediagroup.streamplatform.streamregistry.model.ConsumerBinding;
import com.expediagroup.streamplatform.streamregistry.model.Specification;
import com.expediagroup.streamplatform.streamregistry.model.Status;
import com.expediagroup.streamplatform.streamregistry.model.keys.ConsumerBindingKey;
import com.expediagroup.streamplatform.streamregistry.model.keys.ConsumerKey;
import com.expediagroup.streamplatform.streamregistry.repository.ConsumerBindingRepository;
import com.expediagroup.streamplatform.streamregistry.repository.ConsumerRepository;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerServiceTest {

  @Mock
  private HandlerService handlerService;

  @Mock
  private ConsumerValidator consumerValidator;

  @Mock
  private ConsumerRepository consumerRepository;

  @Mock
  private  ConsumerBindingService consumerBindingService;

  @Mock
  private ConsumerBindingRepository consumerBindingRepository;

  private ConsumerService consumerService;

  @Before
  public void before() {
    consumerService = new ConsumerService(
      new ConsumerView(consumerRepository),
      handlerService,
      consumerValidator,
      consumerRepository,
      consumerBindingService,
      new ConsumerBindingView(consumerBindingRepository)
    );
  }

  @Test
  public void create() {
    final ConsumerKey key = mock(ConsumerKey.class);
    final Specification specification = mock(Specification.class);

    final Consumer entity = mock(Consumer.class);
    when(entity.getKey()).thenReturn(key);
    when(consumerRepository.findById(key)).thenReturn(Optional.empty());

    doNothing().when(consumerValidator).validateForCreate(entity);
    when(handlerService.handleInsert(entity)).thenReturn(specification);

    when(consumerRepository.saveSpecification(entity)).thenReturn(entity);

    consumerService.create(entity);

    verify(entity).getKey();
    verify(consumerRepository).findById(key);
    verify(consumerValidator).validateForCreate(entity);
    verify(handlerService).handleInsert(entity);
    verify(consumerRepository).saveSpecification(entity);
  }

  @Test
  public void update() {
    final ConsumerKey key = mock(ConsumerKey.class);
    final Specification specification = mock(Specification.class);

    final Consumer entity = mock(Consumer.class);
    final Consumer existingEntity = mock(Consumer.class);

    when(entity.getKey()).thenReturn(key);

    when(consumerRepository.findById(key)).thenReturn(Optional.of(existingEntity));
    doNothing().when(consumerValidator).validateForUpdate(entity, existingEntity);
    when(handlerService.handleUpdate(entity, existingEntity)).thenReturn(specification);

    when(consumerRepository.saveSpecification(entity)).thenReturn(entity);

    consumerService.update(entity);

    verify(entity).getKey();
    verify(consumerRepository).findById(key);
    verify(consumerValidator).validateForUpdate(entity, existingEntity);
    verify(handlerService).handleUpdate(entity, existingEntity);
    verify(consumerRepository).saveSpecification(entity);
  }

  @Test
  public void updateStatus() {
    final Status status = mock(Status.class);
    final Consumer entity = mock(Consumer.class);

    when(consumerRepository.saveStatus(entity)).thenReturn(entity);

    consumerService.updateStatus(entity, status);

    verify(consumerRepository).saveStatus(entity);
  }

  @Test
  public void delete() {
    final ConsumerKey key = mock(ConsumerKey.class);
    final Consumer entity = mock(Consumer.class);
    when(entity.getKey()).thenReturn(key);

    final ConsumerBindingKey bindingKey = mock(ConsumerBindingKey.class);
    final ConsumerBinding binding = mock(ConsumerBinding.class);
    when(bindingKey.getConsumerKey()).thenReturn(key);
    when(binding.getKey()).thenReturn(bindingKey);

    when(consumerBindingRepository.findAll()).thenReturn(List.of(binding));

    consumerService.delete(entity);

    verify(consumerBindingService).delete(binding);
    verify(consumerRepository).delete(entity);
  }

  @Test
  public void delete_noChildren() {
    final Consumer entity = mock(Consumer.class);
    when(consumerBindingRepository.findAll()).thenReturn(emptyList());

    consumerService.delete(entity);

    verify(consumerBindingService, never()).delete(any());
    verify(consumerRepository).delete(entity);
  }

  @Test
  public void delete_multi() {
    final ConsumerKey key = mock(ConsumerKey.class);
    final Consumer entity = mock(Consumer.class);
    when(entity.getKey()).thenReturn(key);

    final ConsumerBindingKey bindingKey1 = mock(ConsumerBindingKey.class);
    final ConsumerBinding binding1 = mock(ConsumerBinding.class);
    when(bindingKey1.getConsumerKey()).thenReturn(key);
    when(binding1.getKey()).thenReturn(bindingKey1);

    final ConsumerBindingKey bindingKey2 = mock(ConsumerBindingKey.class);
    final ConsumerBinding binding2 = mock(ConsumerBinding.class);
    when(bindingKey2.getConsumerKey()).thenReturn(key);
    when(binding2.getKey()).thenReturn(bindingKey2);

    when(consumerBindingRepository.findAll()).thenReturn(asList(binding1, binding2));

    consumerService.delete(entity);

    verify(consumerBindingService).delete(binding1);
    verify(consumerBindingService).delete(binding2);
    verify(consumerRepository).delete(entity);
  }
}
