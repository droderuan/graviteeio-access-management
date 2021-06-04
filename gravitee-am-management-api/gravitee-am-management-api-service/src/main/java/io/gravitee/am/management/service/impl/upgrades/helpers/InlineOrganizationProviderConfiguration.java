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
package io.gravitee.am.management.service.impl.upgrades.helpers;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.service.UserService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.*;

import static io.gravitee.am.model.permissions.SystemRole.ORGANIZATION_PRIMARY_OWNER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InlineOrganizationProviderConfiguration extends OrganizationProviderConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlineOrganizationProviderConfiguration.class);

    public static final String INLINE_TYPE = "inline-am-idp";

    private static final Set<String> authorizedRoles = Set.of(DefaultRole.ORGANIZATION_OWNER.name(), DefaultRole.ORGANIZATION_USER.name(), ORGANIZATION_PRIMARY_OWNER.name());

    private final String passwordEncoder;

    private final Map<String, UserDefinition> users = new LinkedHashMap<>();

    private final UserService userService;

    private final MembershipHelper membershipHelper;

    public InlineOrganizationProviderConfiguration(Environment env, int index, UserService userService, MembershipHelper membershipHelper) {
        super(INLINE_TYPE, env, index);
        this.userService = userService;
        this.membershipHelper = membershipHelper;
        final String propertyBase = this.getPropertyBase(index);
        this.passwordEncoder = env.getProperty(propertyBase+"password-encoding-algo", String.class, "BCrypt");

        boolean found = true;
        int idx = 0;

        while (found) {
            final String userPropertyBase = propertyBase + "users[" + idx + "].";
            final String username = env.getProperty(userPropertyBase + "username");
            found = (username != null);
            if (found) {
                UserDefinition user = new UserDefinition();
                user.setUsername(username);
                user.setFirstname(env.getProperty(userPropertyBase+"firstname"));
                user.setLastname(env.getProperty(userPropertyBase+"lastname"));
                if (StringUtils.isEmpty(user.getFirstname()) && StringUtils.isEmpty(user.getLastname())) {
                    // if firstname & lastname are empty, set firstname to username to avoid Null Null into top right menu
                    user.setFirstname(user.getUsername());
                    user.setLastname("");
                }
                user.setEmail(env.getProperty(userPropertyBase+"email"));
                user.setPassword(env.getProperty(userPropertyBase+"password"));
                user.setRole(env.getProperty(userPropertyBase+"role"));
                if (StringUtils.isEmpty(user.getPassword()) || StringUtils.isEmpty(user.getRole())) {
                    LOGGER.warn("User definition ignored for '{}': missing role or password", username);
                } else if (!authorizedRoles.contains(user.getRole())) {
                    LOGGER.warn("User definition ignored for '{}': invalid role. (expected: \"ORGANIZATION_OWNER\", \"ORGANIZATION_USER\", \"ORGANIZATION_PRIMARY_OWNER\")", username);
                } else {
                    this.users.put(username, user);
                }
            }
            idx++;
        }

        if (users.values().stream().filter(user -> ORGANIZATION_PRIMARY_OWNER.name().equals(user.getRole())).findFirst().isEmpty()) {
            throw new IllegalArgumentException("At least one user should have " + ORGANIZATION_PRIMARY_OWNER.name() + " role, skip IdentityProvider initialization");
        }
    }

    @Override
    public String generateConfiguration() {
        JsonObject json = new JsonObject();
        if ("BCrypt".equals(passwordEncoder)) {
            json.put("passwordEncoder", passwordEncoder);
        }
        JsonArray arrayOfUsers = new JsonArray();
        json.put("users", arrayOfUsers);
        users.forEach((username, def) -> {
            JsonObject user = new JsonObject()
                    .put("firstname", def.firstname)
                    .put("lastname", def.lastname)
                    .put("username", def.username)
                    .put("email", def.email)
                    .put("password", def.password);
            arrayOfUsers.add(user);
        });
        return json.toString();
    }

    @Override
    public String mergeConfiguration(String existingConfig) {
        JsonObject json = JsonObject.mapFrom(Json.decodeValue(existingConfig));
        if ("BCrypt".equals(passwordEncoder)) {
            json.put("passwordEncoder", passwordEncoder);
        } else {
            json.remove("passwordEncoder");
        }

        List<String> remainingUsersToCreate = new ArrayList<>(users.keySet());

        // update existing users
        JsonArray arrayOfUsers = json.getJsonArray("users");
        arrayOfUsers.stream().forEach((entry) -> {
            JsonObject existingUser = (JsonObject) entry;
            String username = existingUser.getString("username");
            if (users.containsKey(username)) {
                remainingUsersToCreate.remove(username);
                UserDefinition def = this.users.get(username);
                existingUser
                        .put("firstname", def.firstname)
                        .put("lastname", def.lastname)
                        .put("email", def.email)
                        .put("password", def.password);
            }
        });

        // create remaining users
        remainingUsersToCreate.forEach(username -> {
            UserDefinition def = this.users.get(username);
            JsonObject user = new JsonObject()
                    .put("firstname", def.firstname)
                    .put("lastname", def.lastname)
                    .put("username", def.username)
                    .put("email", def.email)
                    .put("password", def.password);
            arrayOfUsers.add(user);
        });
        return json.toString();
    }

    private void assignRoleFor(User user, String role) {
        switch (role) {
            case "ORGANIZATION_PRIMARY_OWNER":
                this.membershipHelper.setOrganizationPrimaryOwnerRole(user);
                break;
            default:
                this.membershipHelper.setOrganizationRole(user, role);
                break;
        }
    }

    public void upsertUsers(IdentityProvider provider) {
        users.values().forEach(userDef -> {
            final User newUser = new User();
            newUser.setInternal(false);
            newUser.setUsername(userDef.getUsername());
            newUser.setSource(provider.getId());
            newUser.setReferenceType(ReferenceType.ORGANIZATION);
            newUser.setReferenceId(Organization.DEFAULT);

            this.userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, userDef.getUsername(), provider.getId())
                    .switchIfEmpty(Maybe.just(newUser))
                    .flatMapSingle(user -> {
                        if (user.getId() == null) {
                            return this.userService.create(newUser).map(createdUser -> {
                                assignRoleFor(createdUser, userDef.getRole());
                                return createdUser;
                            });
                        } else {
                            return Single.just(user).map(userToUpdate -> {
                                membershipHelper.resetOrganizationRole(userToUpdate, userDef.getRole());
                                return userToUpdate;
                            });
                        }
                    })
                    .blockingGet();
        });
    }

    public String getPasswordEncoder() {
        return passwordEncoder;
    }

    public Map<String, UserDefinition> getUsers() {
        return users;
    }

    public final static class UserDefinition {
        private String username;
        private String email;
        private String firstname;
        private String lastname;
        private String password;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstname() {
            return firstname;
        }

        public void setFirstname(String firstname) {
            this.firstname = firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public void setLastname(String lastname) {
            this.lastname = lastname;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
