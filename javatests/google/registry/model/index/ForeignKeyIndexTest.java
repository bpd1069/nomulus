// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.domain.registry.model.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.collect.ImmutableList;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyHostIndex;
import com.google.domain.registry.testing.ExceptionRule;

import com.googlecode.objectify.Ref;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link ForeignKeyIndex}. */
public class ForeignKeyIndexTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Before
  public void setUp() throws Exception {
    createTld("com");
  }

  @Test
  public void testPersistence() throws Exception {
    // Persist a host and implicitly persist a ForeignKeyIndex for it.
    HostResource host = persistActiveHost("ns1.example.com");
    ForeignKeyIndex<HostResource> fki =
        ForeignKeyIndex.load(HostResource.class, "ns1.example.com", clock.nowUtc());
    assertThat(fki.getReference().get()).isEqualTo(host);
    assertThat(fki.getDeletionTime()).isEqualTo(END_OF_TIME);
  }

  @Test
  public void testIndexing() throws Exception {
    // Persist a host and implicitly persist a ForeignKeyIndex for it.
    persistActiveHost("ns1.example.com");
    verifyIndexing(
        ForeignKeyIndex.load(HostResource.class, "ns1.example.com", clock.nowUtc()),
        "deletionTime");
  }

  @Test
  public void testLoadForNonexistentForeignKey_returnsNull() {
    assertThat(ForeignKeyIndex.load(HostResource.class, "ns1.example.com", clock.nowUtc()))
        .isNull();
  }

  @Test
  public void testLoadForDeletedForeignKey_returnsNull() {
    HostResource host = persistActiveHost("ns1.example.com");
    persistResource(ForeignKeyIndex.create(host, clock.nowUtc().minusDays(1)));
    assertThat(ForeignKeyIndex.load(HostResource.class, "ns1.example.com", clock.nowUtc()))
        .isNull();
  }

  @Test
  public void testLoad_newerKeyHasBeenSoftDeleted() throws Exception {
    HostResource host1 = persistActiveHost("ns1.example.com");
    clock.advanceOneMilli();
    ForeignKeyHostIndex fki = new ForeignKeyHostIndex();
    fki.foreignKey = "ns1.example.com";
    fki.topReference = Ref.create(host1);
    fki.deletionTime = clock.nowUtc();
    persistResource(fki);
    assertThat(ForeignKeyIndex.load(
        HostResource.class, "ns1.example.com", clock.nowUtc())).isNull();
  }

  @Test
  public void testBatchLoad_skipsDeletedAndNonexistent() {
    persistActiveHost("ns1.example.com");
    HostResource host = persistActiveHost("ns2.example.com");
    persistResource(ForeignKeyIndex.create(host, clock.nowUtc().minusDays(1)));
    assertThat(ForeignKeyIndex.load(
        HostResource.class,
        ImmutableList.of("ns1.example.com", "ns2.example.com", "ns3.example.com"),
        clock.nowUtc()).keySet())
            .containsExactly("ns1.example.com");
  }

  @Test
  public void testDeadCodeThatDeletedScrapCommandsReference() throws Exception {
    persistActiveHost("omg");
    assertThat(ForeignKeyIndex.load(HostResource.class, "omg", clock.nowUtc()).getForeignKey())
        .isEqualTo("omg");
  }
}