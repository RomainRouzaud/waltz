/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
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
 * See the License for the specific
 *
 */
import _ from "lodash";
import {initialiseData} from "../../../common";
import {CORE_API} from "../../../common/services/core-api-utils";
import {availableRelationshipKinds} from "./related-measurable-editor-utils";
import {buildHierarchies, doSearch, prepareSearchNodes} from "../../../common/hierarchy-utils";
import {refToString, toEntityRef} from "../../../common/entity-utils";

import template from "./create-related-measurable-editor.html";
import {displayError} from "../../../common/error-utils";


const bindings = {
    parentEntityRef: "<",
    type: "<",
    onCancel: "<",
    onRefresh: "<",
    onRemove: "<"
};


const initialState = {
    form: {
        relationshipKind: "RELATES_TO",
        description: null,
        counterpart: null
    },
    counterpartType: "",
    measurables: [],
    categories: [],
    availableRelationshipKinds,
    relationshipKindsKey: "",
    onCancel: () => console.log("wcrme: onCancel - default impl"),
    onRefresh: () => console.log("wcrme: onRefresh - default impl"),
    onRemove: () => console.log("wcrme: onRemove - default impl"),
    visibility: {
        appGroupSelector: false,
        changeInitiativeSelector: false,
        measurableSelector: false
    },
    checkedItemIds: [],
};


function readCategoryId(compoundId) {
    // e.g. MEASURABLE/12
    return +_.split(compoundId, "/")[1];
}


function prepareTree(nodes = []) {
    return buildHierarchies(nodes, false);
}


function controller(notification, serviceBroker) {
    const vm = initialiseData(this, initialState);

    const loadRelationships = (autoExpand = false) => {

        return serviceBroker
            .loadViewData(
                CORE_API.MeasurableRelationshipStore.findByEntityReference,
                [vm.parentEntityRef],
                { force: true })
            .then(r => {
                vm.relationships = r.data;
                vm.relatedEntityMap = _.chain(r.data)
                    .flatMap((rel) => [rel.a, rel.b])
                    .keyBy(ref => refToString(ref))
                    .value();
                vm.checkedItemIds = _
                    .chain(vm.relationships)
                    .flatMap(rel => {
                        const ids = [];
                        if (rel.a.kind === "MEASURABLE") ids.push(rel.a.id);
                        if (rel.b.kind === "MEASURABLE") ids.push(rel.b.id);
                        return ids;
                    })
                    .uniq()
                    .value();
            })
            .then(() => {
                if (autoExpand) vm.expandedItemIds = _.clone(vm.checkedItemIds);
            });
    };

    const loadMeasurableTree = (categoryId) => {

        // load measurable tree for category
        serviceBroker
            .loadAppData(CORE_API.MeasurableStore.findAll)
            .then(r => {
                vm.measurables = _
                    .chain(r.data)
                    .filter(m => m.categoryId === categoryId)
                    .map(d => Object.assign({}, d, {disable: !d.concrete}))
                    .value();
                vm.measurablesById = _.keyBy(vm.measurables, d => d.id);
                vm.searchNodes = prepareSearchNodes(vm.measurables);
                vm.nodes = prepareTree(vm.measurables);
            });

    };


    vm.$onInit = () => {
        loadRelationships(true);

        const isMeasurable = _.startsWith(vm.type.id, "MEASURABLE");

        if (isMeasurable) {
            vm.visibility.measurableSelector = true;
            const categoryId = readCategoryId(vm.type.id);

            // load categories
            serviceBroker
                .loadAppData(CORE_API.MeasurableCategoryStore.findAll)
                .then(r => {
                    vm.categories = r.data;
                    return vm.categories;
                })
                .then(cs => {
                    if(categoryId) {
                        vm.category = _.find(cs, {id: categoryId});
                        vm.counterpartType = vm.category.name;
                    }
                });

            if(categoryId) {
                loadMeasurableTree(categoryId);
            }

        } else {
            switch (vm.type.id) {
                case "CHANGE_INITIATIVE":
                    vm.visibility.changeInitiativeSelector = true;
                    vm.counterpartType = "Change Initiative";
                    break;

                case "APP_GROUP":
                    vm.visibility.appGroupSelector = true;
                    vm.counterpartType = "Application Group";
                    break;
            }
        }

        vm.relationshipKindsKey = vm.parentEntityRef.kind + "-" + (isMeasurable ? "MEASURABLE" : vm.type.id);
    };


    // -- INTERACT --

    vm.selectionFilterFn = (proposed) => {
        const proposedRefString = refToString(proposed);
        return vm.relatedEntityMap[proposedRefString] == null;
    };

    vm.onAppGroupSelection = (appGroup) => {
        vm.form.counterpart = appGroup;
    };

    vm.onChangeInitiativeSelection = (changeInitiative) => {
        vm.form.counterpart = changeInitiative;
    };

    vm.onItemCheck = (node) => {

        const selected = vm.measurablesById[node];
        vm.form.counterpart = toEntityRef(selected, "MEASURABLE");

        save(vm.form)
            .then(loadRelationships)
            .catch(e => displayError(notification, "Could not save because: ", e));
    };

    vm.onItemUncheck = (node) => {

        const relationshipToRemove = _.find(vm.relationships, d =>
            (d.a.id === vm.parentEntityRef.id && d.b.id === node)
            || (d.a.id === node && d.b.id === vm.parentEntityRef.id));

        vm.onRemove(relationshipToRemove)
            .then(r => notification.warning("Relationship Removed"))
            .then(() => {
                loadRelationships();
                vm.onRefresh();
            })
            .catch(e => {
                displayError(notification, "Could not remove relationship because: ", e);
            });
    };

    vm.onMeasurableCategorySelection = (category) => {
        loadMeasurableTree(category.id);
        vm.counterpartType = category.name;
    };

    vm.isFormValid = () => {
        const hasCounterpart = vm.form.counterpart !== null;
        const hasRelationship = vm.form.relationshipKind !== null;
        return hasCounterpart && hasRelationship;
    };

    vm.submit = () => {
        if (vm.isFormValid()) {
            save(vm.form)
                .catch(e => displayError(notification, "Could not save because: ", e))
                .finally(() =>  vm.onCancel());
        }
    };


    vm.searchTermsChanged = (termStr = "") => {
        vm.nodes = prepareTree(doSearch(termStr, vm.searchNodes));
    };

    // -- API ---

    const save = d => {

        const form = d;

        const submission = {
            a: vm.parentEntityRef,
            b: form.counterpart,
            relationshipKind: form.relationshipKind,
            description: form.description
        };
        return serviceBroker
            .execute(CORE_API.MeasurableRelationshipStore.create, [submission])
            .then(() => {
                notification.success("Relationship saved");
                vm.onRefresh();
            });
    };

}


controller.$inject = [
    "Notification",
    "ServiceBroker"
];


const component = {
    bindings,
    template,
    controller
};


const id = "waltzCreateRelatedMeasurableEditor";


export default {
    id,
    component
};