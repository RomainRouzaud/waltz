/*
 *  This file is part of Waltz.
 *
 *     Waltz is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Waltz is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Waltz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.jobs;

import com.khartec.waltz.data.user.UserPreferenceDao;
import com.khartec.waltz.model.user.ImmutableUserPreference;
import com.khartec.waltz.model.user.UserPreference;
import com.khartec.waltz.service.DIConfiguration;
import org.jooq.tools.json.ParseException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;


public class UserPreferenceHarness {

    public static void main(String[] args) throws ParseException {

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DIConfiguration.class);

        UserPreferenceDao userPreferenceDao = ctx.getBean(UserPreferenceDao.class);


        List<UserPreference> preferences = new ArrayList<UserPreference>() {{
            add(ImmutableUserPreference.builder()
                    .user_id("admin")
                    .key("org-unit.section.technologies.collapsed")
                    .value("false")
                    .build());

            add(ImmutableUserPreference.builder()
                    .user_id("admin")
                    .key("org-unit.section.indicators.collapsed")
                    .value("true")
                    .build());

            add(ImmutableUserPreference.builder()
                    .user_id("admin")
                    .key("org-unit.section.logicalflows.collapsed")
                    .value("true")
                    .build());

        }};

//        int result = userPreferenceDao.savePreferencesForUser("admin", preferences);
//        System.out.println("result: " + result);


        List<UserPreference> prefs = userPreferenceDao.getPreferencesForUser("admin");
        prefs.forEach(u -> System.out.printf("user: %s, key: %s, value: %s\r\n", u.user_id(), u.key(), u.value()));

//        userPreferenceDao.clearPreferencesForUser("kamran");



    }

}
