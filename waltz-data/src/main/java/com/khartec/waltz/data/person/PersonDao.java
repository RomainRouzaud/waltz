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

package com.khartec.waltz.data.person;

import com.khartec.waltz.model.person.ImmutablePerson;
import com.khartec.waltz.model.person.Person;
import com.khartec.waltz.model.person.PersonKind;
import com.khartec.waltz.schema.tables.records.PersonRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.Checks.checkNotEmptyString;
import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.schema.tables.Person.PERSON;
import static com.khartec.waltz.schema.tables.PersonHierarchy.PERSON_HIERARCHY;


@Repository
public class PersonDao {

    private static final Logger LOG = LoggerFactory.getLogger(PersonDao.class);
    public static final RecordMapper<? super Record, Person> personMapper = r -> {
        PersonRecord record = r.into(PersonRecord.class);
        return ImmutablePerson.builder()
                .id(record.getId())
                .email(record.getEmail())
                .displayName(record.getDisplayName())
                .userPrincipalName(record.getUserPrincipalName())
                .departmentName(record.getDepartmentName())
                .employeeId(record.getEmployeeId())
                .managerEmployeeId(Optional.ofNullable(record.getManagerEmployeeId()))
                .title(record.getTitle())
                .kind(PersonKind.valueOf(record.getKind()))
                .mobilePhone(Optional.ofNullable(record.getMobilePhone()))
                .officePhone(Optional.ofNullable(record.getOfficePhone()))
                .organisationalUnitId(Optional.ofNullable(record.getOrganisationalUnitId()))
                .build();
    };
    private final DSLContext dsl;


    @Autowired
    public PersonDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl must not be null");

        this.dsl = dsl;
    }


    public Person getByEmployeeId(String employeeId) {
        checkNotEmptyString(employeeId, "Cannot find person without an employeeId");
        return dsl.select()
                .from(PERSON)
                .where(PERSON.EMPLOYEE_ID.eq(employeeId))
                .fetchOne(personMapper);
    }

    public Person getById(long id) {
        return dsl.select()
                .from(PERSON)
                .where(PERSON.ID.eq(id))
                .fetchOne(personMapper);
    }


    public List<Person> findDirectsByEmployeeId(String employeeId) {
        checkNotEmptyString(employeeId, "Cannot find directs without an employeeId");
        return dsl.select()
                .from(PERSON)
                .where(dsl.renderInlined(PERSON.MANAGER_EMPLOYEE_ID.eq(employeeId)))
                .fetch(personMapper);
    }


    /**
     * Returned in order, immediate manager first
     **/
    public List<Person> findAllManagersByEmployeeId(String employeeId) {
        checkNotEmptyString(employeeId, "Cannot find directs without an employeeId");
        return dsl.select(PERSON.fields())
                .from(PERSON)
                .join(PERSON_HIERARCHY)
                .on(PERSON.EMPLOYEE_ID.eq(PERSON_HIERARCHY.MANAGER_ID))
                .where(PERSON_HIERARCHY.EMPLOYEE_ID.eq(employeeId))
                .orderBy(PERSON_HIERARCHY.LEVEL.desc())
                .fetch(personMapper);
    }


    public List<Person> all() {
        return dsl.select()
                .from(PERSON)
                .fetch(personMapper);
    }


    public int[] bulkSave(List<ImmutablePerson> people) {

        checkNotNull(people, "Cannot bulk save a null collection of people");

        LOG.info("Bulk saving " + people.size() + "records");

        List<PersonRecord> records = people.stream()
                .map(p -> {
                    PersonRecord r = dsl.newRecord(PERSON);
                    r.setDepartmentName(p.departmentName());
                    r.setDisplayName(p.displayName());
                    r.setEmail(p.email());
                    r.setEmployeeId(p.employeeId());
                    r.setKind(p.kind().name());
                    r.setManagerEmployeeId(p.managerEmployeeId().orElse(""));
                    r.setTitle(p.title().orElse(""));
                    r.setOfficePhone(p.officePhone().orElse(""));
                    r.setMobilePhone(p.mobilePhone().orElse(""));
                    r.setUserPrincipalName(p.userPrincipalName());
                    return r;
                })
                .collect(Collectors.toList());

        return dsl.batchInsert(records).execute();
    }


    public List<Person> findByOrganisationalUnitId(Long... orgUnitIds) {
        return dsl.select(PERSON.fields())
                .from(PERSON)
                .where(PERSON.ORGANISATIONAL_UNIT_ID.in(orgUnitIds))
                .fetch(personMapper);
    }


    public Person findPersonByUserId(String userId) {
        return dsl.select(PERSON.fields())
                .from(PERSON)
                .where(PERSON.EMAIL.eq(userId)) // TODO: change as part of 247
                .fetchOne(personMapper);
    }
}
