/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.management.service.impl.upgrades.helpers.MembershipHelper;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.*;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultOrganizationUpgraderTest {
    public static final String ADMIN_USERNAME = "admin";
    public static String DEFAULT_INLINE_IDP_CONFIG = "{\"users\":[{\"firstname\":\"Administrator\",\"lastname\":\"Administrator\",\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"adminadmin\"}]}";

    @Mock
    private OrganizationService organizationService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private UserService userService;

    @Mock
    private MembershipHelper membershipHelper;

    @Mock
    private RoleService roleService;

    @Mock
    private DomainService domainService;

    @Spy
    private Environment environment = new StandardEnvironment();

    private DefaultOrganizationUpgrader cut;

    @Before
    public void before() {
        cut = new DefaultOrganizationUpgrader(organizationService, identityProviderService, userService, membershipHelper, roleService, domainService, environment);
        defineDefaultSecurityConfig();
    }

    private void defineDefaultSecurityConfig() {
        defineDefaultSecurityConfig(false);
    }

    private void defineDefaultSecurityConfig(boolean refresh) {
        reset(environment);
        doReturn("inline-am-idp").when(environment).getProperty("security.providers[0].type");
        doReturn("none").when(environment).getProperty(eq("security.providers[0].password-encoding-algo"), any(), any());
        doReturn(ADMIN_USERNAME).when(environment).getProperty("security.providers[0].users[0].username");
        doReturn("adminadmin").when(environment).getProperty("security.providers[0].users[0].password");
        doReturn("ORGANIZATION_PRIMARY_OWNER").when(environment).getProperty("security.providers[0].users[0].role");
        doReturn(refresh).when(environment).getProperty("security.providers[0].refresh", boolean.class, false);
    }

    @Test
    public void shouldCreateDefaultOrganization() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");

        User adminUser = new User();
        adminUser.setId("admin-id");

        final Organization organization = new Organization();
        when(organizationService.createDefault()).thenReturn(Maybe.just(organization));
        when(userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, "admin", idp.getId())).thenReturn(Maybe.empty());
        when(identityProviderService.create(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(NewIdentityProvider.class), isNull())).thenReturn(Single.just(idp));
        when(organizationService.update(eq(Organization.DEFAULT), any(PatchOrganization.class), isNull())).thenReturn(Single.just(organization));
        when(userService.create(argThat(user -> !user.isInternal()
                && user.getUsername().equals("admin")
                && user.getSource().equals(idp.getId())
                && user.getReferenceType() == ReferenceType.ORGANIZATION
                && user.getReferenceId().equals(Organization.DEFAULT)))).thenReturn(Single.just(adminUser));
        when(domainService.findById("admin")).thenReturn(Maybe.empty());
        doNothing().when(membershipHelper).setOrganizationPrimaryOwnerRole(argThat(user -> user.getId().equals(adminUser.getId())));

        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
    }

    @Test
    public void shouldNotUpdateIdentityProvider_noInlineIdp_refreshFalse() {
        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");
        idp.setType("idpType");

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("test"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
        verify(identityProviderService, never()).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(userService, never()).create(any());
        verify(userService, never()).update(any(), any(), any(), any());
        verify(membershipHelper, never()).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
        verify(membershipHelper, never()).resetOrganizationRole(any(), any());
    }

    @Test
    public void shouldUpdateIdentityProvider_noInlineIdp_refreshTrue() {
        defineDefaultSecurityConfig(true);

        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("test"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));
        when(identityProviderService.update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull())).thenReturn(Single.just(idp));
        User item = new User();
        item.setId("uid");
        when(userService.findByUsernameAndSource(any(), any(), any(), any())).thenReturn(Maybe.just(item));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());

        assertTrue(cut.upgrade());

        verify(membershipHelper, times(1)).setPlatformAdminRole();
        verify(identityProviderService, times(1)).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(userService, never()).create(any());
        verify(membershipHelper, never()).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
        verify(membershipHelper, times(1)).resetOrganizationRole(any(), any());
    }

    @Test
    public void shouldNotUpdateIdentityProviderRoleMapper_adminUserRemoved_refreshFalse() {
        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration("{}"); // no admin user.
        idp.setRoleMapper(Collections.singletonMap("role1", new String[]{"username=test"}));

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
        verify(identityProviderService, never()).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(userService, never()).create(any());
        verify(userService, never()).update(any(), any(), any(), any());
        verify(membershipHelper, never()).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
        verify(membershipHelper, never()).resetOrganizationRole(any(), any());
    }

    @Test
    public void shouldNotUpdateIdentityProviderRoleMapper_roleMapperIsAlreadySet_refreshTrue() {
        defineDefaultSecurityConfig(true);

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(Collections.singletonMap("role1", new String[]{"username=test"})); // RoleMapper already set.

        final Role adminRole = new Role();
        adminRole.setId("role-id");

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();

        verify(identityProviderService, never()).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(userService, never()).create(any());
        verify(userService, never()).update(any(), any(), any(), any());
        verify(membershipHelper, never()).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
        verify(membershipHelper, never()).resetOrganizationRole(any(), any());
    }

    @Test
    public void shouldCreateAdminUser_noAdminUser_refreshTrue() {
        defineDefaultSecurityConfig(true);

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(new HashMap<>());

        User adminUser = new User();
        adminUser.setId("admin-id");
        adminUser.setUsername(ADMIN_USERNAME);

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));
        when(identityProviderService.update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull())).thenReturn(Single.just(idp));

        when(userService.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(ADMIN_USERNAME), any())).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(adminUser));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(identityProviderService, times(1)).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(membershipHelper, times(1)).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
        verify(membershipHelper, never()).resetOrganizationRole(any(), any());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
        verify(userService, times(1)).create(any());
        verify(userService, never()).update(any(), any(), any(), any());
    }

    @Test
    public void shouldNotCreateAdminUser_noAdminUser_refreshFalse() {
        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(new HashMap<>());

        User adminUser = new User();
        adminUser.setId("admin-id");
        adminUser.setUsername(ADMIN_USERNAME);

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(identityProviderService, never()).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(userService, never()).create(any());
        verify(userService, never()).update(any(), any(), any(), any());
        verify(membershipHelper, never()).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
        verify(membershipHelper, never()).resetOrganizationRole(any(), any());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
    }

    @Test
    public void shouldUpdateAdminUser_refreshTrue() {
        defineDefaultSecurityConfig(true);

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(new HashMap<>());

        User adminUser = new User();
        adminUser.setId("admin-id");

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));
        when(identityProviderService.update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull())).thenReturn(Single.just(idp));

        when(userService.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(ADMIN_USERNAME), any())).thenReturn(Maybe.just(adminUser));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(identityProviderService, times(1)).update(any(), eq(Organization.DEFAULT), any(), any(UpdateIdentityProvider.class), isNull());
        verify(membershipHelper, times(1)).resetOrganizationRole(any(), any());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
        verify(userService, never()).create(any());
        verify(membershipHelper, never()).setOrganizationPrimaryOwnerRole(argThat(u -> u.getUsername().equals(ADMIN_USERNAME)));
    }

    @Test
    public void shouldNotUpdateAdminUser_adminAlreadyExist() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(new HashMap<>());

        User adminUser = new User();
        adminUser.setId("adminId");
        adminUser.setUsername("admin");
        adminUser.setLoginsCount(10L);
        adminUser.setRoles(Arrays.asList("role-id"));

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));
        when(organizationService.createDefault()).thenReturn(Maybe.empty());

        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
    }

    @Test
    public void shouldCreateDefaultOrganization_technicalError() {

        when(organizationService.createDefault()).thenReturn(Maybe.error(TechnicalException::new));

        assertFalse(cut.upgrade());
    }

    @Test
    public void shouldCreateSystemRoles_setOwnerRoleToExistingUsers() {

        int totalUsers = 22;

        User user = new User();
        user.setId("user-1");

        Role adminRole = new Role();
        adminRole.setId("role-1");

        Organization organization = new Organization();
        organization.setId("orga-id");

        List<User> users = Stream.iterate(0, i -> i++).limit(10).map(i -> user)
                .collect(Collectors.toList());

        when(organizationService.createDefault()).thenReturn(Maybe.just(organization));
        when(organizationService.update(any(), any(), any())).thenReturn(Single.just(organization));
        when(domainService.findById("admin")).thenReturn(Maybe.just(new Domain()));
        when(domainService.delete("admin")).thenReturn(Completable.complete());

        when(roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION))
                .thenReturn(Maybe.just(adminRole)); // Role has been created.

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(0), anyInt()))
                .thenReturn(Single.just(new Page<>(users, 0, totalUsers)));

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(1), anyInt()))
                .thenReturn(Single.just(new Page<>(users, 1, totalUsers)));

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(2), anyInt()))
                .thenReturn(Single.just(new Page<>(Arrays.asList(user, user), 2, totalUsers)));

        doNothing().when(membershipHelper).setOrganizationRole(eq(user), eq(adminRole));

        cut.upgrade();

        verify(membershipHelper, times(totalUsers)).setOrganizationRole(eq(user), eq(adminRole));
        verify(membershipHelper, times(1)).setPlatformAdminRole();
    }
}
