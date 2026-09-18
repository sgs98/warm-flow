/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程表单服务特征测试：补齐表单生命周期、版本和内容保存契约。
 *
 * @author warm
 */
class FormServiceCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void save_assignsNextNumericVersion_andGetByCodeFindsIt() {
        Form first = FlowEngine.newForm().setFormCode("form-a").setFormName("表单 A")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        assertTrue(FlowEngine.formService().save(first));
        assertEquals("1", first.getVersion());

        Form second = FlowEngine.newForm().setFormCode("form-a").setFormName("表单 A2")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        assertTrue(FlowEngine.formService().save(second));
        assertEquals("2", second.getVersion());
        assertEquals(second.getId(), FlowEngine.formService().getByCode("form-a", "2").getId());
    }

    @Test
    void publishAndSaveContent_guardPublishedState() {
        Form form = FlowEngine.newForm().setFormCode("form-b").setFormName("表单 B")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        FlowEngine.formService().save(form);

        assertTrue(FlowEngine.formService().publish(form.getId()));
        assertEquals(PublishStatus.PUBLISHED.getKey(), harness.formDao.raw(form.getId()).getIsPublish());
        FlowException published = assertThrows(FlowException.class,
            () -> FlowEngine.formService().publish(form.getId()));
        assertEquals(ExceptionCons.FORM_ALREADY_PUBLISH, published.getMessage());

        FlowException contentGuard = assertThrows(FlowException.class,
            () -> FlowEngine.formService().saveContent(form.getId(), "content-v2"));
        assertEquals(ExceptionCons.FORM_ALREADY_PUBLISH, contentGuard.getMessage());
    }

    @Test
    void saveContent_unpublishedForm_savesAndCopyCreatesUnpublishedNextVersion() {
        Form form = FlowEngine.newForm().setFormCode("form-c").setFormName("表单 C")
            .setFormContent("content-v1").setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        FlowEngine.formService().save(form);

        assertTrue(FlowEngine.formService().saveContent(form.getId(), "content-v2"));
        assertEquals("content-v2", harness.formDao.raw(form.getId()).getFormContent());

        assertTrue(FlowEngine.formService().copyForm(form.getId()));
        Form copied = FlowEngine.formService().getByCode("form-c", "2");
        assertNotNull(copied);
        assertEquals(PublishStatus.UNPUBLISHED.getKey(), copied.getIsPublish());
        assertEquals("content-v2", copied.getFormContent());
    }

    @Test
    void getByCode_multipleVersionsSameVersion_throwsFormNotOne() {
        Form first = FlowEngine.newForm().setFormCode("form-d").setVersion("1")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        Form second = FlowEngine.newForm().setFormCode("form-d").setVersion("1")
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        harness.formDao.save(first);
        harness.formDao.save(second);

        FlowException duplicate = assertThrows(FlowException.class,
            () -> FlowEngine.formService().getByCode("form-d", "1"));
        assertEquals(ExceptionCons.FORM_NOT_ONE, duplicate.getMessage());
    }
}
