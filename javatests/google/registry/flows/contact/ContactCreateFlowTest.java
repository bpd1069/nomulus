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

package com.google.domain.registry.flows.contact;

import static com.google.domain.registry.testing.ContactResourceSubject.assertAboutContacts;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedContact;

import com.google.domain.registry.flows.ResourceCreateFlow.ResourceAlreadyExistsException;
import com.google.domain.registry.flows.ResourceFlowTestCase;
import com.google.domain.registry.flows.contact.ContactFlowUtils.BadInternationalizedPostalInfoException;
import com.google.domain.registry.flows.contact.ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException;
import com.google.domain.registry.model.contact.ContactResource;

import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link ContactCreateFlow}. */
public class ContactCreateFlowTest
    extends ResourceFlowTestCase<ContactCreateFlow, ContactResource> {

  public ContactCreateFlowTest() {
    setEppInput("contact_create.xml");
    clock.setTo(DateTime.parse("1999-04-03T22:00:00.0Z"));
  }

  private void doSuccessfulTest() throws Exception {
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("contact_create_response.xml"));
    // Check that the contact was created and persisted with a history entry.
    assertAboutContacts().that(reloadResourceByUniqueId())
        .hasOnlyOneHistoryEntryWhich().hasNoXml();
    assertNoBillingEvents();
    assertEppResourceIndexEntityFor(reloadResourceByUniqueId());
  }

  @Test
  public void testDryRun() throws Exception {
    dryRunFlowAssertResponse(readFile("contact_create_response.xml"));
  }

  @Test
  public void testSuccess_neverExisted() throws Exception {
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_existedButWasDeleted() throws Exception {
    persistDeletedContact(getUniqueIdFromCommand(), clock.nowUtc());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_alreadyExists() throws Exception {
    thrown.expect(
        ResourceAlreadyExistsException.class,
        String.format("Object with given ID (%s) already exists", getUniqueIdFromCommand()));
    persistActiveContact(getUniqueIdFromCommand());
    runFlow();
  }

  @Test
  public void testSuccess_nonAsciiInLocAddress() throws Exception {
    setEppInput("contact_create_hebrew_loc.xml");
    doSuccessfulTest();
  }

  @Test
  public void testFailure_nonAsciiInIntAddress() throws Exception {
    thrown.expect(BadInternationalizedPostalInfoException.class);
    setEppInput("contact_create_hebrew_int.xml");
    runFlow();
  }

  @Test
  public void testFailure_declineDisclosure() throws Exception {
    thrown.expect(DeclineContactDisclosureFieldDisallowedPolicyException.class);
    setEppInput("contact_create_decline_disclosure.xml");
    runFlow();
  }
}