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

import io.gravitee.am.management.service.impl.upgrades.helpers.InlineOrganizationProviderConfiguration;
import io.gravitee.am.management.service.impl.upgrades.helpers.MembershipHelper;
import io.gravitee.am.management.service.impl.upgrades.helpers.OrganizationProviderConfiguration;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.service.*;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.impl.upgrades.helpers.InlineOrganizationProviderConfiguration.INLINE_TYPE;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultOrganizationUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOrganizationUpgrader.class);
    private static final String ADMIN_DOMAIN = "admin";
    private static final int PAGE_SIZE = 10;

    private final OrganizationService organizationService;

    private final IdentityProviderService identityProviderService;

    private final UserService userService;

    private final MembershipHelper membershipHelper;

    private final RoleService roleService;

    private final DomainService domainService;

    private final Environment environment;

    public DefaultOrganizationUpgrader(OrganizationService organizationService,
                                       IdentityProviderService identityProviderService,
                                       UserService userService,
                                       MembershipHelper membershipHelper,
                                       RoleService roleService,
                                       DomainService domainService,
                                       Environment environment) {
        this.organizationService = organizationService;
        this.identityProviderService = identityProviderService;
        this.userService = userService;
        this.membershipHelper = membershipHelper;
        this.roleService = roleService;
        this.domainService = domainService;
        this.environment = environment;
    }

    @Override
    public boolean upgrade() {

        try {
            // load all identity providers configured into the gravitee file
            List<OrganizationProviderConfiguration> providerDefinitions = loadProviders();

            // This call create default organization with :
            // - default roles
            // - default entry point
            Organization organization = organizationService.createDefault().blockingGet();

            // No existing organization, finish the following set up :
            // - retrieve information from the old admin domain
            // - migrate all existing users permissions to default ORGANIZATION_OWNER
            if (organization != null) {
                logger.info("Default organization successfully created");

                // check if old domain admin exists
                Domain adminDomain = domainService.findById(ADMIN_DOMAIN).blockingGet();
                if (adminDomain != null) {
                    // update organization identities
                    PatchOrganization patchOrganization = new PatchOrganization();
                    patchOrganization.setIdentities(adminDomain.getIdentities() != null ? new ArrayList<>(adminDomain.getIdentities()) : null);
                    organizationService.update(organization.getId(), patchOrganization, null).blockingGet();

                    // Must grant owner power to all existing users to be iso-functional with v2 where all users could do everything.
                    Role organizationOwnerRole = roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION).blockingGet();
                    Page<User> userPage;
                    int page = 0;
                    do {
                        userPage = userService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT, page, PAGE_SIZE).blockingGet();
                        // membership helper create membership only if
                        userPage.getData().forEach(user -> membershipHelper.setOrganizationRole(user, organizationOwnerRole));
                        page++;
                    } while (userPage.getData().size() == PAGE_SIZE);

                    // then delete the domain
                    domainService.delete(ADMIN_DOMAIN).blockingGet();
                } else {

                    // Need to create providers and users for this newly created default organization.
                    List<String> providerIds = providerDefinitions.stream().map(providerDefinition -> {
                        IdentityProvider provider = createProvider(providerDefinition);
                        providerDefinition.upsertUsers(provider);
                        return provider.getId();
                    }).collect(Collectors.toList());

                    logger.info("Associate providers to default organization");
                    PatchOrganization patchOrganization = new PatchOrganization();
                    patchOrganization.setIdentities(providerIds);
                    organizationService.update(Organization.DEFAULT, patchOrganization, null).blockingGet();
                }
            } else {
                // Perform this block only if the organization isn't a newly created one.
                // Get organization with fresh data.
                organization = organizationService.findById(Organization.DEFAULT).blockingGet();
                logger.info("Check if default organization is up to date");

                // Need to check that inline idp and default admin user has 'admin' role.
                final List<String> identities = organization.getIdentities();

                List<IdentityProvider> identityProviders = identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT).toList().blockingGet();

                providerDefinitions.forEach(providerDefinition -> {
                    Optional<IdentityProvider> filteredIdentity = identityProviders.stream()
                            .filter(idp -> idp.getType().equals(providerDefinition.getType())
                                    && !idp.isExternal()
                                    && identities.contains(idp.getId())).findFirst();

                    if (providerDefinition.isRefresh() && !providerDefinition.isExternal()) {
                        if (filteredIdentity.isEmpty()) {
                            logger.info("Provider '{}' missing from the default organization, create it", providerDefinition.getType());
                            //  Refresh is required for a 'internal' Provider, create it
                            IdentityProvider provider = createProvider(providerDefinition);
                            providerDefinition.upsertUsers(provider);

                            logger.info("Associate provider '{}' to default organization", providerDefinition.getType());
                            PatchOrganization patchOrganization = new PatchOrganization();
                            identities.add(provider.getId());
                            patchOrganization.setIdentities(identities);
                            organizationService.update(Organization.DEFAULT, patchOrganization, null).blockingGet();
                        } else if (filteredIdentity.get().getRoleMapper() == null || filteredIdentity.get().getRoleMapper().isEmpty()) {
                            logger.info("Provider '{}' exists for the default organization, update users", providerDefinition.getType());
                            // update user definition if there are no role mapper on Idp
                            updateProvider(providerDefinition, filteredIdentity.get());
                            providerDefinition.upsertUsers(filteredIdentity.get());
                        } else {
                            logger.info("Provider '{}' exists for the default organization, update users ignored due to definition of RoleMapper", providerDefinition.getType());
                        }
                    }
                });
            }

            // The primary owner of the default organization must be considered as platform admin.
            membershipHelper.setPlatformAdminRole();
        } catch (Exception e) {
            logger.error("An error occurred trying to initialize default organization", e);
            return false;
        }

        return true;
    }

    private List<OrganizationProviderConfiguration> loadProviders(){
        List<OrganizationProviderConfiguration> providers = new ArrayList<>();
        boolean found = true;
        int idx = 0;

        while (found) {
            String type = environment.getProperty("security.providers[" + idx + "].type");
            found = (type != null);
            if (found) {
                switch (type) {
                    case INLINE_TYPE:
                        providers.add(new InlineOrganizationProviderConfiguration(environment, idx, this.userService, this.membershipHelper));
                        break;
                    default:
                        logger.warn("Unsupported provider with type '{}'", type);
                }
            }
            idx++;
        }

        return providers;
    }

    private IdentityProvider createProvider(OrganizationProviderConfiguration config) {
        // Create an inline identity provider
        logger.info("Create a new provider with type {}", config.getType());
        NewIdentityProvider adminIdentityProvider = new NewIdentityProvider();
        adminIdentityProvider.setType(config.getType());
        adminIdentityProvider.setName(config.getName());

        adminIdentityProvider.setConfiguration(config.generateConfiguration());

        IdentityProvider createdIdentityProvider = identityProviderService.create(ReferenceType.ORGANIZATION, Organization.DEFAULT, adminIdentityProvider, null).blockingGet();
        return createdIdentityProvider;
    }

    private IdentityProvider updateProvider(OrganizationProviderConfiguration config, IdentityProvider existingProvider) {
        // Create an inline identity provider
        logger.info("Update provider with type {} and id {}", config.getType(), existingProvider.getId());
        UpdateIdentityProvider updatedIdentityProvider = new UpdateIdentityProvider();
        updatedIdentityProvider.setName(config.getName());
        updatedIdentityProvider.setMappers(existingProvider.getMappers());
        updatedIdentityProvider.setRoleMapper(existingProvider.getRoleMapper());

        updatedIdentityProvider.setConfiguration(config.mergeConfiguration(existingProvider.getConfiguration()));

        return identityProviderService.update(ReferenceType.ORGANIZATION, Organization.DEFAULT, existingProvider.getId(), updatedIdentityProvider, null).blockingGet();
    }

    @Override
    public int getOrder() {
        return 2;
    }

}
